---
name: xilo-backend
description: Use when writing Go/Fiber backend code for the Xilo platform. Covers project structure, conventions, database patterns, gRPC, NATS events, and testing.
---

# Xilo Backend Development

Go 1.22+ with Fiber v2, Clean Architecture, gRPC, NATS, PostgreSQL, Redis, Meilisearch, MinIO/S3 (swappable storage drivers).

## Project Structure

```
backend/
├── cmd/<service>/main.go       # Entry point, wire dependencies
├── internal/<service>/
│   ├── handler/                 # HTTP/gRPC handlers (thin, delegate to service)
│   │   ├── auth_handler.go
│   │   └── post_handler.go
│   ├── service/                 # Business logic
│   │   ├── auth_service.go
│   │   └── post_service.go
│   ├── repository/              # Data access (PostgreSQL, Redis)
│   │   ├── user_repo.go
│   │   └── post_repo.go
│   ├── model/                   # Domain entities, DTOs
│   │   ├── user.go
│   │   └── post.go
│   └── middleware/
│       ├── auth.go              # JWT validation
│       ├── ratelimit.go         # Redis-backed rate limiting
│       └── logging.go           # Structured request logging
├── pkg/                         # Shared packages
│   ├── jwt/jwt.go
│   ├── hash/argon2.go
│   ├── validator/validator.go
│   ├── pagination/cursor.go
│   ├── nats/client.go
│   ├── redis/client.go
│   └── storage/                 # Storage driver interface + implementations
│       ├── storage.go           # Driver interface
│       ├── minio/               # MinIO driver (self-hosted default)
│       └── s3/                  # S3 driver (cloud, optional)
├── proto/                       # gRPC protobuf definitions
│   ├── auth/v1/auth.proto
│   ├── post/v1/post.proto
│   └── ...
├── migrations/                   # SQL migration files
│   ├── 000001_create_users.up.sql
│   └── ...
├── go.mod
└── go.sum
```

## Service Template

```go
// cmd/post-service/main.go
package main

import (
    "log/slog"
    "os"
    
    "github.com/gofiber/fiber/v3"
    "xilo/internal/post/handler"
    "xilo/internal/post/service"
    "xilo/internal/post/repository"
    "xilo/pkg/redis"
    "xilo/pkg/nats"
)

func main() {
    db := connectDB(os.Getenv("DATABASE_URL"))
    rdb := redis.NewClient(os.Getenv("REDIS_URL"))
    nc := nats.NewClient(os.Getenv("NATS_URL"))
    
    repo := repository.NewPostRepo(db)
    svc := service.NewPostService(repo, nc)
    h := handler.NewPostHandler(svc)
    
    app := fiber.New()
    app.Get("/api/posts", h.List)
    app.Get("/api/posts/:slug", h.GetBySlug)
    app.Post("/api/posts", auth.Required, h.Create)
    app.Patch("/api/posts/:id", auth.Required, h.Update)
    app.Delete("/api/posts/:id", auth.Required, h.Delete)
    
    slog.Info("post-service starting", "port", "8030")
    app.Listen(":8030")
}
```

## Database Access Patterns

```go
// Use sqlx or pgx for PostgreSQL
// Always use parameterized queries
// Always use context for cancellation

func (r *postRepo) GetBySlug(ctx context.Context, slug string) (*model.Post, error) {
    var post model.Post
    err := r.db.GetContext(ctx, &post,
        `SELECT id, author_id, title, slug, excerpt, content, cover_image_url,
                category, tags, status, is_premium, word_count, reading_time,
                published_at, created_at, updated_at
         FROM posts WHERE slug = $1 AND status = 'published' AND deleted_at IS NULL`,
        slug,
    )
    if err != nil {
        return nil, fmt.Errorf("get post by slug: %w", err)
    }
    return &post, nil
}

// Cursor pagination
func (r *postRepo) List(ctx context.Context, cursor string, limit int) ([]model.Post, string, error) {
    var posts []model.Post
    query := `SELECT * FROM posts 
              WHERE status = 'published' AND deleted_at IS NULL 
              AND ($1 = '' OR id < $1)
              ORDER BY published_at DESC, id DESC
              LIMIT $2`
    
    err := r.db.SelectContext(ctx, &posts, query, cursor, limit+1)
    // Return posts and next cursor
}

// Redis caching pattern
func (r *postRepo) GetBySlugCached(ctx context.Context, slug string) (*model.Post, error) {
    key := "post:" + slug
    // Try cache first
    cached, err := r.rdb.Get(ctx, key).Bytes()
    if err == nil {
        var post model.Post
        json.Unmarshal(cached, &post)
        return &post, nil
    }
    // Cache miss, fetch from DB
    post, err := r.GetBySlug(ctx, slug)
    if err != nil {
        return nil, err
    }
    // Set cache
    data, _ := json.Marshal(post)
    r.rdb.Set(ctx, key, data, 5*time.Minute)
    return post, nil
}
```

## NATS Event Publishing

```go
// Event naming: <resource>.<action>
// Always include trace ID for observability

type PostPublishedEvent struct {
    PostID    string    `json:"post_id"`
    AuthorID  string    `json:"author_id"`
    Title     string    `json:"title"`
    Tags      []string  `json:"tags"`
    Category  string    `json:"category"`
    Timestamp time.Time `json:"timestamp"`
}

func (s *postService) Publish(ctx context.Context, post *model.Post) error {
    post.Status = "published"
    post.PublishedAt = time.Now()
    
    if err := s.repo.Update(ctx, post); err != nil {
        return err
    }
    
    // Invalidate cache
    s.rdb.Del(ctx, "post:"+post.Slug)
    
    // Emit event
    event := PostPublishedEvent{
        PostID:    post.ID,
        AuthorID:  post.AuthorID,
        // ...
    }
    data, _ := json.Marshal(event)
    return s.nc.Publish("post.published", data)
}
```

## Error Handling

```go
// Use sentinel errors
var (
    ErrNotFound     = errors.New("resource not found")
    ErrUnauthorized = errors.New("unauthorized")
    ErrForbidden    = errors.New("forbidden")
    ErrConflict     = errors.New("resource conflict")
)

// Handler error response
func (h *postHandler) GetBySlug(c *fiber.Ctx) error {
    post, err := h.svc.GetBySlug(c.Context(), c.Params("slug"))
    if errors.Is(err, ErrNotFound) {
        return c.Status(fiber.StatusNotFound).JSON(fiber.Map{
            "error": "post not found",
        })
    }
    if err != nil {
        slog.Error("get post failed", "error", err)
        return c.Status(fiber.StatusInternalServerError).JSON(fiber.Map{
            "error": "internal server error",
        })
    }
    return c.JSON(post)
}
```

## Storage Driver Pattern

Always use the `StorageDriver` interface for object storage operations. Never call MinIO/S3 SDKs directly from service or repository layers.

```go
// pkg/storage/storage.go
type StorageDriver interface {
    Upload(ctx context.Context, key string, reader io.Reader, size int64, contentType string) (*UploadResult, error)
    Download(ctx context.Context, key string) (io.ReadCloser, error)
    Delete(ctx context.Context, key string) error
    GetURL(key string) string
}

type UploadResult struct {
    Key      string
    URL      string
    Size     int64
}

// Driver selection via env var
func NewStorageDriver() (StorageDriver, error) {
    driver := os.Getenv("STORAGE_DRIVER")
    if driver == "" {
        driver = "minio"
    }
    switch driver {
    case "minio":
        return minio.NewDriver(
            os.Getenv("STORAGE_ENDPOINT"),
            os.Getenv("STORAGE_ACCESS_KEY"),
            os.Getenv("STORAGE_SECRET_KEY"),
            os.Getenv("STORAGE_BUCKET"),
        )
    case "s3":
        return s3.NewDriver(
            os.Getenv("STORAGE_REGION"),
            os.Getenv("STORAGE_ACCESS_KEY"),
            os.Getenv("STORAGE_SECRET_KEY"),
            os.Getenv("STORAGE_BUCKET"),
        )
    default:
        return nil, fmt.Errorf("unknown storage driver: %s", driver)
    }
}
```

In services, depend only on the interface:

```go
type MediaService struct {
    storage storage.StorageDriver
    repo    *repository.MediaRepo
}
```

env config:

```bash
STORAGE_DRIVER=minio          # minio | s3
STORAGE_ENDPOINT=localhost:9000
STORAGE_ACCESS_KEY=minioadmin
STORAGE_SECRET_KEY=minioadmin
STORAGE_BUCKET=xilo-media
STORAGE_USE_SSL=false
STORAGE_REGION=us-east-1       # S3 only
```

## Testing

```go
// Table-driven tests
// Use testcontainers-go for integration tests
func TestPostService_Publish(t *testing.T) {
    tests := []struct {
        name    string
        post    *model.Post
        wantErr bool
    }{
        {"valid post", validPost, false},
        {"missing title", postWithoutTitle, true},
    }
    
    for _, tt := range tests {
        t.Run(tt.name, func(t *testing.T) {
            svc := NewPostService(mockRepo, mockNATS)
            err := svc.Publish(context.Background(), tt.post)
            if (err != nil) != tt.wantErr {
                t.Errorf("Publish() error = %v, wantErr %v", err, tt.wantErr)
            }
        })
    }
}
```

## Key Rules

- NEVER panic in HTTP handlers — return error responses
- ALWAYS use `context.Context` for all IO operations
- ALWAYS use parameterized SQL queries (never string concatenation)
- ALWAYS validate input before processing
- ALWAYS log with structured logging (`slog`)
- ALWAYS export Prometheus metrics on `/metrics`
- NEVER hardcode secrets — use environment variables
- Use `golang-migrate` for database migrations
- Docker images: use multi-stage builds, scratch/`distroless` base, target <20MB
