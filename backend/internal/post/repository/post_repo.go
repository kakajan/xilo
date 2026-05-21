package repository

import (
	"context"
	"database/sql"
	"errors"
	"fmt"
	"log/slog"
	"strings"
	"time"

	"github.com/jmoiron/sqlx"
	"github.com/lib/pq"
	authmodel "github.com/xilo-platform/xilo/internal/auth/model"
	"github.com/xilo-platform/xilo/internal/post/model"
	userutil "github.com/xilo-platform/xilo/internal/user/util"
)

var ErrPostNotFound = errors.New("post not found")

type PostRepo struct {
	db *sqlx.DB
}

func NewPostRepo(db *sqlx.DB) *PostRepo {
	return &PostRepo{db: db}
}

func (r *PostRepo) Create(ctx context.Context, req *model.CreatePostRequest, authorID string) (*model.Post, error) {
	wordCount := len(strings.Fields(req.ContentMD))
	readingTime := max(1, wordCount/200)

	slug := req.Slug
	if slug == "" {
		slug = generateSlug(req.Title)
	}

	var post model.Post
	err := r.db.GetContext(ctx, &post, `
		INSERT INTO posts (author_id, title, slug, excerpt, content, content_md,
		                   cover_image_url, category, tags, status, is_premium,
		                   word_count, reading_time, language, scheduled_at, published_at)
		VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9, $10, $11, $12, $13, $14, $15, $16)
		RETURNING id, author_id, title, slug, excerpt, content::text, content_md,
		          cover_image_url, category, tags, status, is_premium,
		          word_count, reading_time, language, scheduled_at, published_at,
		          created_at, updated_at
	`, authorID, req.Title, slug, req.Excerpt, ensureJSON(req.Content), req.ContentMD,
		req.CoverImageURL, req.Category, pq.Array(req.Tags), req.Status, req.IsPremium,
		wordCount, readingTime, req.Language, req.ScheduledAt, nil)
	if err != nil {
		return nil, fmt.Errorf("insert post: %w", err)
	}

	return &post, nil
}

func (r *PostRepo) GetBySlug(ctx context.Context, slug string) (*model.Post, error) {
	var post model.Post
	err := r.db.GetContext(ctx, &post, `
		SELECT p.id, p.author_id, p.title, p.slug, p.excerpt, p.content::text, p.content_md,
		       p.cover_image_url, p.category, p.tags, p.status, p.is_premium,
		       p.word_count, p.reading_time, p.language, p.scheduled_at, p.published_at,
		       p.created_at, p.updated_at
		FROM posts p
		WHERE p.slug = $1 AND p.deleted_at IS NULL AND p.status = 'published'
	`, slug)
	if err != nil {
		if errors.Is(err, sql.ErrNoRows) {
			return nil, ErrPostNotFound
		}
		return nil, fmt.Errorf("get post by slug: %w", err)
	}

	var user authmodel.User
	err = r.db.GetContext(ctx, &user, `
		SELECT id, email, username, phone, password_hash, display_name,
		       COALESCE(avatar_url, '') as avatar_url, COALESCE(bio, '') as bio,
		       role, email_verified, created_at, updated_at, deleted_at
		FROM users WHERE id = $1
	`, post.AuthorID)
	if err != nil {
		slog.Warn("failed to load post author", "author_id", post.AuthorID, "error", err)
	} else {
		slog.Info("loaded author for post", "post_id", post.ID, "author_id", post.AuthorID, "username", user.Username)
		user.IsVerified = userutil.IsVerifiedWriter(user.Role)
		post.Author = &user
	}

	return &post, nil
}

func (r *PostRepo) GetByID(ctx context.Context, id string) (*model.Post, error) {
	var post model.Post
	err := r.db.GetContext(ctx, &post, `
		SELECT id, author_id, title, slug, excerpt, content::text, content_md,
		       cover_image_url, category, tags, status, is_premium,
		       word_count, reading_time, language, scheduled_at, published_at,
		       created_at, updated_at
		FROM posts
		WHERE id = $1 AND deleted_at IS NULL
	`, id)
	if err != nil {
		if errors.Is(err, sql.ErrNoRows) {
			return nil, ErrPostNotFound
		}
		return nil, fmt.Errorf("get post by id: %w", err)
	}
	return &post, nil
}

func (r *PostRepo) Update(ctx context.Context, id string, req *model.UpdatePostRequest) (*model.Post, error) {
	existing, err := r.GetByID(ctx, id)
	if err != nil {
		return nil, err
	}

	if err := r.saveVersion(ctx, existing); err != nil {
		return nil, fmt.Errorf("save version: %w", err)
	}

	title := coalesceStr(req.Title, existing.Title)
	slug := coalesceStr(req.Slug, existing.Slug)
	excerpt := coalesceStr(req.Excerpt, existing.Excerpt)
	contentMD := coalesceStr(req.ContentMD, existing.ContentMD)
	coverImageURL := coalescePtr(req.CoverImageURL, existing.CoverImageURL)
	category := coalescePtr(req.Category, existing.Category)
	language := coalesceStr(req.Language, existing.Language)

	content := existing.Content
	if req.Content != nil {
		content = ensureJSON(*req.Content)
	}

	wordCount := existing.WordCount
	if req.ContentMD != nil {
		wordCount = len(strings.Fields(contentMD))
	}
	readingTime := max(1, wordCount/200)

	status := existing.Status
	if req.Status != nil {
		status = *req.Status
	}

	var publishedAt *time.Time
	if status == "published" && existing.Status != "published" {
		now := time.Now()
		publishedAt = &now
	}

	var post model.Post
	err = r.db.GetContext(ctx, &post, `
		UPDATE posts
		SET title = $2, slug = $3, excerpt = $4, content = $5, content_md = $6,
		    cover_image_url = $7, category = $8, status = $9,
		    word_count = $10, reading_time = $11, language = $12, scheduled_at = $13,
		    published_at = COALESCE($14, published_at), updated_at = NOW()
		WHERE id = $1 AND deleted_at IS NULL
		RETURNING id, author_id, title, slug, excerpt, content::text, content_md,
		          cover_image_url, category, tags, status, is_premium,
		          word_count, reading_time, language, scheduled_at, published_at,
		          created_at, updated_at
	`, id, title, slug, excerpt, content, contentMD, coverImageURL, category, status,
		wordCount, readingTime, language, req.ScheduledAt, publishedAt)
	if err != nil {
		return nil, fmt.Errorf("update post: %w", err)
	}

	return &post, nil
}

func (r *PostRepo) Delete(ctx context.Context, id string) error {
	_, err := r.db.ExecContext(ctx, `
		UPDATE posts SET deleted_at = NOW(), status = 'deleted' WHERE id = $1
	`, id)
	return err
}

func (r *PostRepo) List(ctx context.Context, params model.PostListParams) ([]*model.Post, string, error) {
	limit := params.Limit
	if limit <= 0 || limit > 50 {
		limit = 10
	}

	query := `
		SELECT p.id, p.author_id, p.title, p.slug, p.excerpt, p.cover_image_url,
		       p.category, p.tags, p.status, p.is_premium,
		       p.word_count, p.reading_time, p.language, p.published_at,
		       p.created_at, p.updated_at
		FROM posts p
		WHERE p.status = 'published' AND p.deleted_at IS NULL
	`
	args := []interface{}{}
	argIdx := 1

	if params.Category != "" {
		query += fmt.Sprintf(" AND p.category = $%d", argIdx)
		args = append(args, params.Category)
		argIdx++
	}
	if params.Tag != "" {
		query += fmt.Sprintf(" AND $%d = ANY(p.tags)", argIdx)
		args = append(args, params.Tag)
		argIdx++
	}
	if params.Author != "" {
		query += fmt.Sprintf(" AND p.author_id = (SELECT id FROM users WHERE username = $%d)", argIdx)
		args = append(args, params.Author)
		argIdx++
	}
	if params.Language != "" {
		query += fmt.Sprintf(" AND p.language = $%d", argIdx)
		args = append(args, params.Language)
		argIdx++
	}
	if params.MediaOnly {
		query += " AND p.cover_image_url IS NOT NULL AND p.cover_image_url != ''"
	}
	if params.Cursor != "" {
		query += ` AND p.published_at <= (SELECT published_at FROM posts WHERE id = $` + fmt.Sprint(argIdx) + `)`
		args = append(args, params.Cursor)
		argIdx++
	}

	query += " ORDER BY p.published_at DESC, p.id DESC"
	query += fmt.Sprintf(" LIMIT $%d", argIdx)
	args = append(args, limit+1)

	var posts []*model.Post
	err := r.db.SelectContext(ctx, &posts, query, args...)
	if err != nil {
		return nil, "", fmt.Errorf("list posts: %w", err)
	}

	var nextCursor string
	if len(posts) > limit {
		posts = posts[:limit]
		nextCursor = posts[len(posts)-1].ID
	}

	if posts == nil {
		posts = []*model.Post{}
	}

	authorIDs := make(map[string]bool)
	for _, post := range posts {
		authorIDs[post.AuthorID] = true
	}

	if len(authorIDs) > 0 {
		ids := make([]string, 0, len(authorIDs))
		for id := range authorIDs {
			ids = append(ids, id)
		}

		query := `SELECT id, username, display_name, avatar_url, bio, role FROM users WHERE id = ANY($1)`
		var users []struct {
			ID          string  `db:"id"`
			Username    string  `db:"username"`
			DisplayName string  `db:"display_name"`
			AvatarURL   *string `db:"avatar_url"`
			Bio         *string `db:"bio"`
			Role        string  `db:"role"`
		}
		err := r.db.SelectContext(ctx, &users, query, pq.Array(ids))
		if err == nil {
			authorMap := make(map[string]*authmodel.User)
			for _, u := range users {
				author := &authmodel.User{
					ID:          u.ID,
					Username:    u.Username,
					DisplayName: u.DisplayName,
				}
				if u.AvatarURL != nil {
					author.AvatarURL = *u.AvatarURL
				}
				if u.Bio != nil {
					author.Bio = *u.Bio
				}
				author.Role = u.Role
				author.IsVerified = userutil.IsVerifiedWriter(u.Role)
				authorMap[u.ID] = author
			}
			for _, post := range posts {
				if author, ok := authorMap[post.AuthorID]; ok {
					post.Author = author
				}
			}
		}
	}

	if err := r.EnrichPosts(ctx, posts, params.ViewerID); err != nil {
		return nil, "", err
	}

	return posts, nextCursor, nil
}

func (r *PostRepo) saveVersion(ctx context.Context, post *model.Post) error {
	result, err := r.db.ExecContext(ctx, `
		INSERT INTO post_versions (post_id, title, content, content_md, version)
		VALUES ($1, $2, $3, $4, (SELECT COALESCE(MAX(version), 0) + 1 FROM post_versions WHERE post_id = $1))
	`, post.ID, post.Title, post.Content, post.ContentMD)
	if err != nil {
		return err
	}
	rows, _ := result.RowsAffected()
	if rows == 0 {
		return fmt.Errorf("failed to save version (no rows)")
	}
	return nil
}

func generateSlug(title string) string {
	slug := strings.ToLower(title)
	slug = strings.ReplaceAll(slug, " ", "-")
	slug = strings.Map(func(r rune) rune {
		if (r >= 'a' && r <= 'z') || (r >= '0' && r <= '9') || r == '-' {
			return r
		}
		return -1
	}, slug)
	slug = strings.Trim(slug, "-")
	if len(slug) > 250 {
		slug = slug[:250]
	}
	return slug
}

func coalesceStr(newVal *string, existing string) string {
	if newVal != nil {
		return *newVal
	}
	return existing
}

func coalescePtr(newVal *string, existing *string) *string {
	if newVal != nil {
		return newVal
	}
	return existing
}

func ensureJSON(content string) string {
	if content == "" {
		return "{}"
	}
	if strings.TrimSpace(content)[0] == '{' {
		return content
	}
	return "{}"
}
