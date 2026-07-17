# Design Document: Xilo Platform

## 1. High-Level Architecture

```
                         ┌──────────────────────┐
                         │    CDN (Cloudflare)   │
                         └──────────┬───────────┘
                                    │
                         ┌──────────▼───────────┐
                         │  Reverse Proxy        │
                         │  (Traefik / NGINX)    │
                         └──────────┬───────────┘
                                    │
                     ┌──────────────▼──────────────┐
                     │       API Gateway (Go)       │
                     │   Routing, Auth, Rate Limit  │
                     └──────────────┬──────────────┘
                                    │
           ┌────────────────────────┼────────────────────────┐
           │                        │                        │
   ┌───────▼───────┐       ┌───────▼───────┐       ┌───────▼───────┐
   │  Auth Service  │       │  Post Service  │       │Comment Service│
   │   (Go/Fiber)   │       │   (Go/Fiber)   │       │  (Go/Fiber)   │
   └───────┬───────┘       └───────┬───────┘       └───────┬───────┘
           │                        │                        │
   ┌───────▼───────┐       ┌───────▼───────┐       ┌───────▼───────┐
   │  User Service  │       │ Search Service │       │  Media Service │
   │   (Go/Fiber)   │       │   (Go/Fiber)   │       │  (Go/Fiber)   │
   └───────┬───────┘       └───────┬───────┘       └───────┬───────┘
           │                        │                        │
   ┌───────▼───────┐       ┌───────▼───────┐       ┌───────▼───────┐
   │  Notif. Svc   │       │ Analytics Svc  │       │  Chat Service  │
   │   (Go/Fiber)   │       │  (Go/Fiber)   │       │  (Go/Fiber)   │
   └───────┬───────┘       └───────┬───────┘       └───────┬───────┘
           │                        │                        │
           └───────────┬────────────┘                        │
                       │                                     │
               ┌───────▼───────┐                             │
               │     NATS       │                             │
               │  (Event Bus)   │                             │
               └───────┬───────┘                             │
                       │                                     │
       ┌───────────────┼───────────────┐                     │
       │               │               │                     │
 ┌─────▼─────┐  ┌──────▼──────┐  ┌────▼─────┐               │
 │ PostgreSQL │  │    Redis     │  │Meilisearch│               │
 │  (16+)    │  │  (Cache/Pub) │  │ (Search)  │               │
 └───────────┘  └─────────────┘  └───────────┘               │
       │                                                      │
 ┌─────▼─────┐                                                │
 │   MinIO    │                                                │
 │ (Storage)  │                                                │
 └───────────┘                                                │
```

## 2. Database Schema (PostgreSQL)

### Core Tables

```sql
-- Users & Auth
CREATE TABLE users (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email           VARCHAR(254) UNIQUE NOT NULL,
    username        VARCHAR(32) UNIQUE NOT NULL,
    password_hash   VARCHAR(255) NOT NULL,
    display_name    VARCHAR(100),
    avatar_url      VARCHAR(500),
    bio             TEXT,
    role            VARCHAR(20) DEFAULT 'reader',
    email_verified  BOOLEAN DEFAULT FALSE,
    created_at      TIMESTAMPTZ DEFAULT NOW(),
    updated_at      TIMESTAMPTZ DEFAULT NOW(),
    deleted_at      TIMESTAMPTZ
);

CREATE INDEX idx_users_username ON users(username);
CREATE INDEX idx_users_email ON users(email);

CREATE TABLE refresh_tokens (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token_hash      VARCHAR(255) UNIQUE NOT NULL,
    family          VARCHAR(255) NOT NULL,
    expires_at      TIMESTAMPTZ NOT NULL,
    revoked         BOOLEAN DEFAULT FALSE,
    created_at      TIMESTAMPTZ DEFAULT NOW()
);

CREATE INDEX idx_refresh_tokens_user ON refresh_tokens(user_id);

-- Posts
CREATE TABLE posts (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    author_id       UUID NOT NULL REFERENCES users(id),
    title           VARCHAR(200) NOT NULL,
    slug            VARCHAR(250) UNIQUE NOT NULL,
    excerpt         VARCHAR(500),
    content         JSONB NOT NULL,          -- Tiptap JSON
    content_md      TEXT,                     -- Markdown version for search
    cover_image_url VARCHAR(500),
    category        VARCHAR(100),
    tags            TEXT[] DEFAULT '{}',
    status          VARCHAR(20) DEFAULT 'draft', -- draft, scheduled, published, archived, deleted
    is_premium      BOOLEAN DEFAULT FALSE,
    word_count      INTEGER DEFAULT 0,
    reading_time    INTEGER DEFAULT 1,        -- minutes
    scheduled_at    TIMESTAMPTZ,
    published_at    TIMESTAMPTZ,
    created_at      TIMESTAMPTZ DEFAULT NOW(),
    updated_at      TIMESTAMPTZ DEFAULT NOW(),
    deleted_at      TIMESTAMPTZ
);

CREATE INDEX idx_posts_slug ON posts(slug) WHERE status = 'published';
CREATE INDEX idx_posts_author ON posts(author_id, created_at);
CREATE INDEX idx_posts_status ON posts(status, published_at DESC);
CREATE INDEX idx_posts_category ON posts(category, published_at DESC) WHERE status = 'published';
CREATE INDEX idx_posts_tags ON posts USING GIN(tags);

CREATE TABLE post_versions (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    post_id         UUID NOT NULL REFERENCES posts(id) ON DELETE CASCADE,
    title           VARCHAR(200),
    content         JSONB,
    content_md      TEXT,
    version         INTEGER NOT NULL,
    created_at      TIMESTAMPTZ DEFAULT NOW()
);

CREATE INDEX idx_post_versions_post ON post_versions(post_id, version DESC);

-- Comments
CREATE TABLE comments (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    post_id         UUID NOT NULL REFERENCES posts(id) ON DELETE CASCADE,
    author_id       UUID NOT NULL REFERENCES users(id),
    parent_id       UUID REFERENCES comments(id) ON DELETE CASCADE,
    root_id         UUID REFERENCES comments(id) ON DELETE CASCADE, -- top-level ancestor
    depth           SMALLINT NOT NULL DEFAULT 0, -- 0-4
    content         TEXT NOT NULL,
    content_html    TEXT,                        -- rendered markdown
    media_url       VARCHAR(500),
    is_pinned       BOOLEAN DEFAULT FALSE,
    is_spam         BOOLEAN DEFAULT FALSE,
    created_at      TIMESTAMPTZ DEFAULT NOW(),
    updated_at      TIMESTAMPTZ DEFAULT NOW(),
    deleted_at      TIMESTAMPTZ
);

CREATE INDEX idx_comments_post ON comments(post_id, created_at DESC) WHERE deleted_at IS NULL;
CREATE INDEX idx_comments_root ON comments(root_id, created_at) WHERE deleted_at IS NULL;
CREATE INDEX idx_comments_author ON comments(author_id, created_at);

-- Reactions
CREATE TABLE reactions (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         UUID NOT NULL REFERENCES users(id),
    target_type     VARCHAR(10) NOT NULL,   -- 'post' or 'comment'
    target_id       UUID NOT NULL,
    reaction        VARCHAR(10) NOT NULL,    -- emoji key
    created_at      TIMESTAMPTZ DEFAULT NOW(),
    UNIQUE(user_id, target_type, target_id, reaction)
);

CREATE INDEX idx_reactions_target ON reactions(target_type, target_id);

-- Bookmarks
CREATE TABLE bookmarks (
    user_id         UUID NOT NULL REFERENCES users(id),
    post_id         UUID NOT NULL REFERENCES posts(id) ON DELETE CASCADE,
    created_at      TIMESTAMPTZ DEFAULT NOW(),
    PRIMARY KEY(user_id, post_id)
);

-- Follows
CREATE TABLE follows (
    follower_id     UUID NOT NULL REFERENCES users(id),
    following_id    UUID NOT NULL REFERENCES users(id),
    created_at      TIMESTAMPTZ DEFAULT NOW(),
    PRIMARY KEY(follower_id, following_id),
    CHECK(follower_id != following_id)
);

-- Notifications
CREATE TABLE notifications (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         UUID NOT NULL REFERENCES users(id),
    type            VARCHAR(50) NOT NULL,
    title           VARCHAR(200) NOT NULL,
    body            TEXT,
    data            JSONB,
    is_read         BOOLEAN DEFAULT FALSE,
    created_at      TIMESTAMPTZ DEFAULT NOW()
);

CREATE INDEX idx_notifications_user ON notifications(user_id, created_at DESC);
CREATE INDEX idx_notifications_unread ON notifications(user_id, created_at DESC) WHERE NOT is_read;

-- Notification Preferences
CREATE TABLE notification_preferences (
    user_id         UUID NOT NULL REFERENCES users(id) PRIMARY KEY,
    comment_reply_web       BOOLEAN DEFAULT TRUE,
    comment_reply_email     BOOLEAN DEFAULT TRUE,
    comment_mention_web     BOOLEAN DEFAULT TRUE,
    comment_mention_email   BOOLEAN DEFAULT TRUE,
    post_reaction_web       BOOLEAN DEFAULT TRUE,
    new_follower_web        BOOLEAN DEFAULT TRUE,
    post_published_web      BOOLEAN DEFAULT TRUE,
    post_published_email    BOOLEAN DEFAULT TRUE,
    system_announcement_web BOOLEAN DEFAULT TRUE,
    system_announcement_email BOOLEAN DEFAULT TRUE
);

-- Subscriptions & Billing
CREATE TABLE subscription_plans (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name            VARCHAR(100) NOT NULL,
    slug            VARCHAR(50) UNIQUE NOT NULL,
    price_cents     INTEGER NOT NULL,
    currency        VARCHAR(3) DEFAULT 'USD',
    interval        VARCHAR(10) NOT NULL, -- 'monthly', 'yearly'
    features        JSONB,
    is_active       BOOLEAN DEFAULT TRUE
);

CREATE TABLE user_subscriptions (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         UUID NOT NULL REFERENCES users(id),
    plan_id         UUID NOT NULL REFERENCES subscription_plans(id),
    status          VARCHAR(20) DEFAULT 'active', -- active, cancelled, expired
    started_at      TIMESTAMPTZ DEFAULT NOW(),
    expires_at      TIMESTAMPTZ,
    cancelled_at    TIMESTAMPTZ
);

CREATE TABLE invoices (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         UUID NOT NULL REFERENCES users(id),
    subscription_id UUID REFERENCES user_subscriptions(id),
    amount_cents    INTEGER NOT NULL,
    currency        VARCHAR(3) DEFAULT 'USD',
    status          VARCHAR(20) DEFAULT 'paid',
    payment_method  VARCHAR(30),
    paid_at         TIMESTAMPTZ DEFAULT NOW()
);

-- Media
CREATE TABLE media (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         UUID NOT NULL REFERENCES users(id),
    filename        VARCHAR(255) NOT NULL,
    original_name   VARCHAR(255) NOT NULL,
    mime_type       VARCHAR(100) NOT NULL,
    size_bytes      BIGINT NOT NULL,
    width           INTEGER,
    height          INTEGER,
    variants        JSONB, -- { thumbnail: url, small: url, ... }
    created_at      TIMESTAMPTZ DEFAULT NOW()
);

CREATE INDEX idx_media_user ON media(user_id, created_at DESC);

-- Ads
CREATE TABLE ads (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    title           VARCHAR(200),
    image_url       VARCHAR(500),
    target_url      VARCHAR(1000),
    slot            VARCHAR(30), -- 'feed', 'sidebar', 'bottom_banner'
    category_filter VARCHAR(100),
    impressions     BIGINT DEFAULT 0,
    clicks          BIGINT DEFAULT 0,
    is_active       BOOLEAN DEFAULT TRUE,
    starts_at       TIMESTAMPTZ,
    ends_at         TIMESTAMPTZ,
    created_at      TIMESTAMPTZ DEFAULT NOW()
);

-- Analytics Events (time-series)
CREATE TABLE analytics_events (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    event_type      VARCHAR(50) NOT NULL,
    user_id         UUID,
    session_id      VARCHAR(100),
    properties      JSONB,
    created_at      TIMESTAMPTZ DEFAULT NOW()
);

CREATE INDEX idx_analytics_type_time ON analytics_events(event_type, created_at DESC);
CREATE INDEX idx_analytics_user ON analytics_events(user_id, created_at DESC);

-- Partition analytics by month
-- SELECT create_hypertable('analytics_events', 'created_at');  -- if using TimescaleDB
```

## 3. Event Catalog (NATS)

| Event | Publisher | Subscribers | Payload |
|-------|-----------|-------------|---------|
| `user.registered` | Auth Service | Notification, Analytics | `{ user_id, email, username }` |
| `user.login` | Auth Service | Analytics | `{ user_id, ip, user_agent }` |
| `post.published` | Post Service | Search (index), Notification (followers), Analytics | `{ post_id, author_id, title, tags, category }` |
| `post.updated` | Post Service | Search (reindex), Analytics | `{ post_id, author_id }` |
| `post.deleted` | Post Service | Search (deindex), Analytics | `{ post_id }` |
| `comment.created` | Comment Service | Notification (@mentions, reply), Analytics, WebSocket broadcast, Discover (scoring) | `{ comment_id, post_id, author_id, parent_id }` |
| `comment.deleted` | Comment Service | WebSocket broadcast, Discover (remove) | `{ comment_id, post_id }` |
| `reaction.added` | Reaction handler | WebSocket broadcast, Analytics | `{ target_type, target_id, user_id, reaction }` |
| `follow.created` | User Service | Notification, Analytics | `{ follower_id, following_id }` |
| `notification.created` | Notification Service | WebSocket/SSE delivery | `{ user_id, notification }` |
| `analytics.event` | Analytics Service | Analytics Service (ingest) | `{ event_type, user_id, properties }` |
| `subscription.created` | Billing Service | Notification, Analytics | `{ user_id, plan_id }` |
| `search.reindex` | Search Service | Search Service (internal) | `{ post_id }` |
| `message.sent` | Chat Service | WebSocket broadcast, Notification (if muted) | `{ message_id, chat_id, sender_id }` |
| `chat.created` | Chat Service | Notification (invited members) | `{ chat_id, creator_id, member_ids }` |

## 4. API Gateway Design

The API Gateway is the single entry point. It handles:
- **Authentication** (JWT validation, token refresh proxying)
- **Rate limiting** (per-IP and per-user, Redis-backed)
- **Request routing** to internal services via gRPC or HTTP
- **Response aggregation** where needed
- **CORS** headers
- **Request logging** (structured, to Loki)

### Internal Communication

Services communicate internally via:
- **gRPC** (with protobuf) for synchronous requests (e.g., Auth Service validates token for Post Service)
- **NATS** for asynchronous events

### Service Port Allocation

| Service | HTTP/gRPC Port |
|---------|---------------|
| API Gateway | 8000 (HTTP), 8001 (gRPC) |
| Auth Service | 8010, 8011 |
| User Service | 8020, 8021 |
| Post Service | 8030, 8031 |
| Comment Service | 8040, 8041 |
| Notification Service | 8050, 8051 |
| Search Service | 8060, 8061 |
| Media Service | 8070, 8071 |
| Analytics Service | 8080, 8081 |
| Chat Service | 8090, 8091 |

## 5. Web Frontend Architecture

### Next.js App Router Structure

```
src/
├── app/
│   ├── layout.tsx                  — RootLayout (providers, fonts)
│   ├── page.tsx                    — Homepage
│   ├── loading.tsx                 — Global loading state
│   ├── error.tsx                   — Global error boundary
│   ├── not-found.tsx               — 404 page
│   ├── (auth)/                     — Auth group
│   │   ├── login/page.tsx
│   │   └── register/page.tsx
│   ├── (dashboard)/                — Dashboard group (protected)
│   │   ├── layout.tsx              — Dashboard layout with sidebar
│   │   ├── dashboard/page.tsx
│   │   ├── dashboard/posts/page.tsx
│   │   ├── dashboard/analytics/page.tsx
│   │   ├── dashboard/settings/page.tsx
│   │   └── dashboard/billing/page.tsx
│   ├── write/page.tsx              — New post editor
│   ├── write/[id]/page.tsx         — Edit post
│   ├── search/page.tsx
│   ├── notifications/page.tsx
│   ├── bookmarks/page.tsx
│   ├── settings/page.tsx
│   ├── [username]/page.tsx         — Author profile
│   ├── [username]/[slug]/page.tsx  — Post detail
│   ├── category/[slug]/page.tsx
│   ├── tag/[slug]/page.tsx
│   ├── sitemap.ts                  — Dynamic sitemap
│   ├── robots.ts                   — robots.txt
│   └── opensearch.xml/route.ts     — OpenSearch descriptor
├── components/
│   ├── ui/                         — shadcn/ui components
│   ├── layout/
│   │   ├── navbar.tsx
│   │   ├── sidebar.tsx
│   │   ├── footer.tsx
│   │   └── mobile-nav.tsx
│   ├── post/
│   │   ├── post-card.tsx
│   │   ├── post-feed.tsx
│   │   ├── post-content.tsx
│   │   ├── post-header.tsx
│   │   ├── reaction-bar.tsx
│   │   └── related-posts.tsx
│   ├── editor/
│   │   ├── tiptap-editor.tsx
│   │   ├── metadata-sidebar.tsx
│   │   ├── publish-dialog.tsx
│   │   └── editor-toolbar.tsx
│   ├── comment/
│   │   ├── comment-section.tsx
│   │   ├── comment-item.tsx
│   │   ├── comment-form.tsx
│   │   └── comment-reactions.tsx
│   ├── search/
│   │   ├── search-input.tsx
│   │   ├── filter-bar.tsx
│   │   └── search-results.tsx
│   ├── user/
│   │   ├── user-avatar.tsx
│   │   ├── user-card.tsx
│   │   └── follow-button.tsx
│   └── shared/
│       ├── infinite-scroll.tsx
│       ├── skeleton.tsx
│       ├── empty-state.tsx
│       └── error-state.tsx
├── hooks/
│   ├── use-auth.ts
│   ├── use-posts.ts
│   ├── use-comments.ts
│   ├── use-websocket.ts
│   ├── use-media-upload.ts
│   └── use-debounce.ts
├── lib/
│   ├── api-client.ts              — Axios/fetch wrapper with interceptors
│   ├── auth.ts                    — JWT helpers, refresh logic
│   ├── websocket.ts               — WebSocket manager
│   └── utils.ts
├── stores/
│   ├── auth-store.ts
│   ├── ui-store.ts
│   ├── editor-store.ts
│   └── notification-store.ts
└── types/
    ├── post.ts
    ├── user.ts
    ├── comment.ts
    └── api.ts
```

### Data Flow

```
User Action → Zustand Action → TanStack Query Mutation
  → API Client → API Gateway → Microservice → Database
  → Response → TanStack Query Cache Update → React Re-render

Real-time:
  WebSocket Message → Zustand Store Update → React Re-render
```

## 6. Mobile App Architecture (Native Android)

The active and sole mobile client is the Kotlin application in `android/`. The Flutter tree under `mobile/` is a preserved legacy artifact; it is not an active architecture, implementation target, or completion signal. The detailed production requirements and phased acceptance gates are defined in `openspec/changes/android-native-production/`.

### 6.1 Native Android App Architecture (Kotlin)

The native Android application follows the same Clean Architecture layout but maps dependencies to Android platform utilities:

```
┌─────────────────────────────────┐
│      Presentation Layer          │
│  Compose UI, ViewModels          │
│  (StateFlow, Jetpack Compose)    │
├─────────────────────────────────┤
│        Domain Layer              │
│  Entities, Use Cases             │
│  (Pure Kotlin business logic)    │
├─────────────────────────────────┤
│         Data Layer               │
│  Room DB, Retrofit API, WS       │
│  (TokenManager, OkHttp WS)       │
└─────────────────────────────────┘
```

#### Tech Stack:
- **Dependency Injection**: Dagger Hilt (`@HiltAndroidApp`, `@AndroidEntryPoint`, `@Inject`)
- **Local DB**: Room Database (`XiloDatabase`, DAOs, Entities)
- **Networking**: Retrofit + OkHttp + kotlinx.serialization
- **WebSocket**: OkHttp WebSockets via `WebSocketManager`
- **UI State**: Compose StateFlow collection via `collectAsStateWithLifecycle()`
- **Paging and background sync**: Paging 3 + WorkManager durable outbox
- **Preferences and secrets**: DataStore + Android Keystore

---

## 7. Security Architecture

```
┌──────────────────────────────────────────┐
│              Security Layers              │
├──────────────────────────────────────────┤
│ Transport:   TLS 1.3 (Traefik auto-cert) │
│ Auth:        JWT (RS256) + Refresh Token  │
│ Password:    Argon2id (m=65536, t=3, p=4)│
│ Rate Limit:  Per-IP + Per-User (Redis)   │
│ Input:       Zod (web) / Validator (Go)  │
│ Headers:     CSP, HSTS, X-Frame-Options  │
│ CORS:        Whitelist origins only      │
│ Secrets:     K8s Secrets (never in code) │
│ Network:     K8s NetworkPolicy (zero-trust)│
└──────────────────────────────────────────┘
```

### JWT Token Structure

```json
{
  "sub": "user-uuid",
  "username": "alice",
  "role": "author",
  "iat": 1715000000,
  "exp": 1715000900,
  "jti": "token-uuid"
}
```

### Refresh Token Flow

```
1. Client sends POST /api/auth/refresh with refresh_token in body
2. Auth Service validates refresh token hash against DB
3. If valid and not revoked:
   a. Revoke old refresh token (rotation)
   b. Issue new access token + new refresh token
4. If revoked (reuse detected):
   a. Revoke entire token family
   b. Return 401 — force re-login
```

## 8. Storage Abstraction

The storage layer uses a driver/interface pattern so the underlying provider can be swapped without code changes.

```
Storage Interface (pkg/storage/storage.go)
├── MinIO Driver  (pkg/storage/minio/)   ← default, self-hosted
└── S3 Driver     (pkg/storage/s3/)      ← AWS / cloud provider
```

### Driver Selection

Configured via environment variables:

| Variable | Description | Default |
|----------|-------------|---------|
| `STORAGE_DRIVER` | Driver to use (`minio` or `s3`) | `minio` |
| `STORAGE_ENDPOINT` | Object storage endpoint | `localhost:9000` |
| `STORAGE_ACCESS_KEY` | Access key | `minioadmin` |
| `STORAGE_SECRET_KEY` | Secret key | `minioadmin` |
| `STORAGE_BUCKET` | Bucket name | `xilo-media` |
| `STORAGE_USE_SSL` | Use SSL/TLS | `false` |
| `STORAGE_REGION` | Region (S3 driver only) | `us-east-1` |

### Interface Contract

```go
type StorageDriver interface {
    Upload(ctx context.Context, key string, reader io.Reader, size int64, contentType string) (*UploadResult, error)
    Download(ctx context.Context, key string) (io.ReadCloser, error)
    Delete(ctx context.Context, key string) error
    GetURL(key string) string
}
```

All media operations go through this interface. The concrete driver is created at startup based on `STORAGE_DRIVER`.

---

## 9. Caching Strategy

```
Layer 1: Browser Cache
  - Static assets (JS, CSS, fonts): 1 year, immutable
  - Images (CDN): 1 year, immutable

Layer 2: CDN Cache (Cloudflare)
  - HTML pages: 60s (stale-while-revalidate)
  - API responses (public GET): 5min

Layer 3: ISR Cache (Next.js)
  - Homepage: 60s revalidate
  - Post pages: 300s revalidate (or on-demand on update)
  - Category/tag pages: 600s revalidate

Layer 4: Redis Cache
  - Post content: 5min TTL, invalidated on post update
  - User profiles: 10min TTL
  - Feed: 1min TTL (fan-out on write pattern)
  - Rate limit counters: sliding window

Layer 5: PostgreSQL (source of truth)
  - No caching; use appropriate indexes
```

## 11. Discover Feed Architecture

The Discover Feed surfaces high-quality comments as standalone tweet-like cards, enabling content discovery beyond the post context.

### 11.1 Content Sources

```typescript
enum DiscoverSource {
  TRENDING = 'trending',       // High-engagement recent comments
  RECOMMENDED = 'recommended', // Personalized based on user history
  TOPICS = 'topics'            // Topic/category-based filtering
}
```

### 11.2 Discover Card Layout

```
┌──────────────────────────────────┐
│ 👤 @alice                        │
│ ┌────────────────────────────┐   │
│ │ AI will replace most       │   │
│ │ junior dev jobs in 5 yrs.  │   │
│ └────────────────────────────┘   │
│ ❤️ 120   💬 34                   │
│                                  │
│ ↳ on post: "Future of AI devs"   │
│ by @john                         │
└──────────────────────────────────┘
```

### 11.3 Discover Scoring Algorithm

```
DiscoverScore = w₁R + w₂E + w₃Q + w₄P + w₅A

Where:
  R = Recency (exponential decay, λ=0.08)
  E = Engagement rate (likes + 2×replies + 3×shares) / (views + 1)
  Q = Quality score (text length, media presence, author reputation)
  P = Personalization (topic match, author affinity)
  A = Account age factor (anti-spam)

Weights:
  w₁ = 0.30 (recency)
  w₂ = 0.30 (engagement)
  w₃ = 0.15 (quality)
  w₄ = 0.15 (personalization)
  w₅ = 0.10 (account age)
```

### 11.4 Discover Pipeline

```
Comments DB
   │
   ▼
Candidate Generation (recent + trending + recommended)
   │
   ▼
Filtering (spam / low engagement / min likes threshold)
   │
   ▼
Feature Extraction (recency, engagement, quality, personalization)
   │
   ▼
Score Calculation (DiscoverScore)
   │
   ▼
Ranking + Diversity (max 2 consecutive from same author)
   │
   ▼
Redis Cache (precomputed every 2-5 minutes)
   │
   ▼
API Response (cursor-based pagination)
```

### 11.5 Anti-Spam Controls

- Minimum engagement threshold (X likes or replies) to enter Discover
- Account age factor in scoring
- Rate limiting on comment creation
- Duplicate text detection
- Shadow ban for spam accounts

---

## 12. Chat & Messaging Architecture

Real-time private messaging between users with Telegram-like UX.

### 12.1 Database Schema

```sql
-- Chats
CREATE TABLE chats (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    type            VARCHAR(10) NOT NULL DEFAULT 'direct', -- 'direct' or 'group'
    name            VARCHAR(100),                           -- group name only
    avatar_url      VARCHAR(500),
    created_at      TIMESTAMPTZ DEFAULT NOW(),
    updated_at      TIMESTAMPTZ DEFAULT NOW(),
    last_message_at TIMESTAMPTZ
);

-- Chat Members
CREATE TABLE chat_members (
    chat_id         UUID NOT NULL REFERENCES chats(id) ON DELETE CASCADE,
    user_id         UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    role            VARCHAR(10) DEFAULT 'member', -- 'admin', 'member'
    joined_at       TIMESTAMPTZ DEFAULT NOW(),
    last_read_at    TIMESTAMPTZ,
    is_muted        BOOLEAN DEFAULT FALSE,
    PRIMARY KEY(chat_id, user_id)
);

-- Messages
CREATE TABLE messages (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    chat_id         UUID NOT NULL REFERENCES chats(id) ON DELETE CASCADE,
    sender_id       UUID NOT NULL REFERENCES users(id),
    content         TEXT,
    media_url       VARCHAR(500),
    reply_to_id     UUID REFERENCES messages(id),
    is_edited       BOOLEAN DEFAULT FALSE,
    is_deleted      BOOLEAN DEFAULT FALSE,
    created_at      TIMESTAMPTZ DEFAULT NOW(),
    updated_at      TIMESTAMPTZ DEFAULT NOW(),
    deleted_at      TIMESTAMPTZ
);

CREATE INDEX idx_messages_chat ON messages(chat_id, created_at DESC);
CREATE INDEX idx_messages_sender ON messages(sender_id);

-- Message Read Status
CREATE TABLE message_reads (
    message_id      UUID NOT NULL REFERENCES messages(id) ON DELETE CASCADE,
    user_id         UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    read_at         TIMESTAMPTZ DEFAULT NOW(),
    PRIMARY KEY(message_id, user_id)
);

-- Message Reactions
CREATE TABLE message_reactions (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    message_id      UUID NOT NULL REFERENCES messages(id) ON DELETE CASCADE,
    user_id         UUID NOT NULL REFERENCES users(id),
    reaction        VARCHAR(10) NOT NULL,
    created_at      TIMESTAMPTZ DEFAULT NOW(),
    UNIQUE(message_id, user_id, reaction)
);
```

### 12.2 WebSocket Events

| Event | Direction | Payload |
|-------|-----------|---------|
| `chat.join` | Client→Server | `{ chatId }` |
| `chat.leave` | Client→Server | `{ chatId }` |
| `message.send` | Client→Server | `{ chatId, content, mediaUrl?, replyToId? }` |
| `message.receive` | Server→Client | `{ message }` |
| `message.edit` | Client→Server | `{ messageId, content }` |
| `message.delete` | Client→Server | `{ messageId }` |
| `message.read` | Client→Server | `{ messageId }` |
| `message.reaction` | Client→Server | `{ messageId, reaction }` |
| `user.typing` | Client→Server | `{ chatId }` |
| `user.typing` | Server→Client | `{ chatId, userId }` |
| `user.online` | Server→Client | `{ userId }` |
| `user.offline` | Server→Client | `{ userId }` |

### 12.3 API Endpoints

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| GET | `/api/chats` | Reader+ | List user's chats |
| POST | `/api/chats` | Reader+ | Create new chat |
| GET | `/api/chats/:id` | Member | Chat details |
| DELETE | `/api/chats/:id` | Member | Leave/delete chat |
| GET | `/api/chats/:id/messages` | Member | Message history |
| POST | `/api/chats/:id/messages` | Member | Send message |
| PATCH | `/api/messages/:id` | Sender | Edit message |
| DELETE | `/api/messages/:id` | Sender | Delete message |
| POST | `/api/messages/:id/read` | Member | Mark as read |
| POST | `/api/messages/:id/reactions` | Member | Toggle reaction |

---

## 13. Home Feed Algorithm

### 13.1 Content Sources

| Source | Weight | Description |
|--------|--------|-------------|
| Following | 60% | Posts from followed verified writers |
| Recommended | 25% | Posts from similar authors/topics |
| Trending | 15% | High-engagement posts in last 24h |

### 13.2 Scoring Formula

```
FeedScore = w₁R + w₂E + w₃A + w₄P + w₅F

Where:
  R = Recency (e^(-λt), λ=0.08, t=hours since publish)
  E = Engagement rate (likes + 2×comments + 3×shares) / (views + 1)
  A = Author authority (0.4×log(followers) + 0.4×avg_engagement + 0.2×verified_bonus)
  P = Personalization (+0.5 if liked author before, +0.3 if topic match, +0.2 if similar cluster)
  F = Following boost (1 if following author, 0 otherwise)

Weights:
  w₁ = 0.35 (recency)
  w₂ = 0.25 (engagement)
  w₃ = 0.15 (author authority)
  w₄ = 0.15 (personalization)
  w₅ = 0.10 (following boost)
```

### 13.3 Fanout Strategy

Hybrid approach:
- **Small accounts** (< 10K followers): fanout-on-write — push to followers' Redis feed on publish
- **Large accounts** (≥ 10K followers): fanout-on-read — fetch from author's post list at request time

```
Redis keys:
  feed:user:{userId}          — precomputed feed (small accounts)
  feed:author:{authorId}      — author's posts (large accounts)
  feed:trending               — trending posts (24h window)
```

### 13.4 Deduplication & Diversity

- Max 2 consecutive posts from same author
- Remove already-seen posts
- Respect muted authors/topics
- Cursor-based pagination: `GET /api/feed/home?cursor=post_8921&limit=20`

---

## 14. Visual Design System

Based on X/Twitter + Telegram hybrid aesthetic.

`openspec/changes/xilo-platform/specs/ui-ux-spec.md` is the normative visual authority. Values and component sketches in this design document are illustrative and SHALL defer to that specification when they differ.

### 14.1 Color Palette

```typescript
// Light Mode
const colors = {
  primary: '#1D9BF0',         // X blue
  background: '#FFFFFF',
  surface: '#F7F9FA',
  textPrimary: '#0F1419',
  textSecondary: '#536471',
  border: '#EFF3F4',
  error: '#F4212E',
  success: '#00BA7C',
  
  // Comment bubbles
  bubbleOwn: '#E8F5FE',       // Light blue (own comments)
  bubbleOthers: '#F7F9FA',    // Light gray (others)
  bubbleHighlighted: '#FFF9C4' // Yellow (mentioned)
};

// Dark Mode
const colorsDark = {
  primary: '#1D9BF0',
  background: '#15202B',
  surface: '#192734',
  textPrimary: '#E7E9EA',
  textSecondary: '#71767B',
  border: '#38444D',
  error: '#F4212E',
  success: '#00BA7C',
  
  bubbleOwn: '#1E3A5F',
  bubbleOthers: '#2C2C2E',
  bubbleHighlighted: '#3E3A2F'
};
```

### 14.2 Typography

| Element | Size | Weight | Font |
|---------|------|--------|------|
| H1 | 24px | 700 | Inter / Vazirmatn |
| H2 | 20px | 700 | Inter / Vazirmatn |
| Body | 15px | 400 | Inter / Vazirmatn |
| Caption | 13px | 400 | Inter / Vazirmatn |
| Small | 11px | 400 | Inter / Vazirmatn |

### 14.3 Spacing Scale

| Token | Value |
|-------|-------|
| xs | 4px |
| sm | 8px |
| md | 16px |
| lg | 24px |
| xl | 32px |
| 2xl | 48px |

### 14.4 Border Radius

| Element | Radius |
|---------|--------|
| Buttons/Inputs | 4px |
| Cards/Modals | 8px |
| Images/Media | 12-16px |
| Avatars/Pills | 50% (full) |
| Comment Bubbles | 14-16px |

### 14.5 Shadows

| Level | Value |
|-------|-------|
| Small | `0 1px 3px rgba(0,0,0,0.12)` |
| Medium | `0 4px 6px rgba(0,0,0,0.1)` |
| Large | `0 10px 20px rgba(0,0,0,0.15)` |

### 14.6 Animations

| Type | Duration | Easing |
|------|----------|--------|
| Fast | 200ms | cubic-bezier(0.4, 0, 0.2, 1) |
| Normal | 300ms | cubic-bezier(0.4, 0, 0.2, 1) |
| Slow | 500ms | cubic-bezier(0.4, 0, 0.2, 1) |

### 14.7 Profile Header Design

```
┌─────────────────────────────────────┐
│  ← [Logo] Xilo                  ⋯  │
│                                     │
│         ┌─────────────┐             │
│         │   Avatar    │             │
│         │   (120px)   │             │
│         └─────────────┘             │
│                                     │
│      Alex Morgan ✓                  │
│      @alexmorgan                    │
│                                     │
│  Building the future at the         │
│  intersection of technology…      │
│                                     │
│  1,248      256K       1,023        │
│  Posts    Followers   Following     │
│                                     │
│ [Follow] [Message] [Share Profile]  │
│                                     │
│ Posts | Replies | Media | Likes     │
└─────────────────────────────────────┘
```

### 14.8 Post Card Design

```
┌─────────────────────────────────────┐
│ 👤 Alex Morgan ✓  @alexmorgan · 2h │
│                                 ⋯   │
│ The best ideas come from            │
│ curiosity. Keep questioning,        │
│ keep building. 🚀                   │
│                                     │
│ ┌─────────────────────────────┐     │
│ │     [Image: Mountain]       │     │
│ │                             │     │
│ └─────────────────────────────┘     │
│                                     │
│ 💬 128  🔁 256  ❤️ 1.8K  ↗  📊     │
└─────────────────────────────────────┘
```

### 14.9 Comment Bubble Design (Telegram-Style)

```
┌─────────────────────────────────────┐
│ 👤 Sophia Lee ✓  @sophialee · 1h   │
│                                     │
│ ┌─────────────────────────────┐     │
│ │ Absolutely! Curiosity is    │     │
│ │ the fuel.                   │     │
│ └─────────────────────────────┘     │
│                                     │
│ 🔥 12   💯 3            9:15 AM     │
└─────────────────────────────────────┘
```

Thread visualization:
- Backend max nest depth: 4; UI shows 2 levels relative to focus root
- Twitter-style avatar-column thread line (≈2px)
- "N پاسخ" drill-down for replies beyond the visible window
- Back navigation pops focus to the previous root

---

## 15. Directory Structure (Repository)

```
xilo/
├── backend/
│   ├── cmd/
│   │   ├── api-gateway/main.go
│   │   ├── auth-service/main.go
│   │   ├── user-service/main.go
│   │   ├── post-service/main.go
│   │   ├── comment-service/main.go
│   │   ├── notification-service/main.go
│   │   ├── search-service/main.go
│   │   ├── media-service/main.go
│   │   ├── analytics-service/main.go
│   │   └── chat-service/main.go
│   ├── internal/
│   │   ├── auth/
│   │   ├── user/
│   │   ├── post/
│   │   ├── comment/
│   │   ├── notification/
│   │   ├── search/
│   │   ├── media/
│   │   ├── analytics/
│   │   └── chat/
│   ├── pkg/
│   │   ├── jwt/
│   │   ├── hash/
│   │   ├── validator/
│   │   ├── pagination/
│   │   ├── nats/
│   │   ├── redis/
│   │   └── storage/       — Storage interface + drivers
│   ├── proto/           — gRPC protobuf definitions
│   ├── migrations/      — SQL migration files
│   ├── go.mod
│   └── go.sum
├── web/
│   ├── src/             — Next.js source (see above)
│   ├── public/
│   ├── next.config.ts
│   ├── tailwind.config.ts
│   ├── package.json
│   └── tsconfig.json
├── android/             — active Kotlin/Jetpack Compose application
├── mobile/              — preserved legacy Flutter source; out of scope
├── infra/
│   ├── docker/
│   │   ├── Dockerfile.api-gateway
│   │   ├── Dockerfile.auth-service
│   │   ├── Dockerfile.web
│   │   └── ...
│   ├── docker-compose.yml
│   ├── docker-compose.prod.yml
│   ├── kubernetes/
│   │   ├── helm/
│   │   │   └── xilo/
│   │   │       ├── Chart.yaml
│   │   │       ├── values.yaml
│   │   │       └── templates/
│   │   └── manifest/    — Raw K8s manifests
│   └── terraform/       — Infrastructure as Code (optional)
├── openspec/
│   └── changes/
│       └── xilo-platform/
│           ├── proposal.md
│           ├── design.md
│           ├── tasks.md
│           └── specs/
├── .github/
│   └── workflows/
│       ├── backend-ci.yml
│       ├── web-ci.yml
│       └── android-ci.yml
├── .gitignore
├── LICENSE
├── README.md
└── requirements.md
```
