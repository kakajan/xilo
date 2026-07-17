# Spec: Post System

## Overview
Full CRUD for blog posts/articles with rich text editing, media embedding, version history, scheduling, and SEO metadata.

---

## Requirements

### REQ-POST-001: Create Post

**Given** an authenticated author  
**When** they submit a post with title, slug, content (Tiptap JSON/Markdown), excerpt, cover image, tags, and category  
**Then** the post is saved as `draft` and can be published.

**Validation:**
- Title: 1–200 chars
- Slug: auto-generated from title, unique, editable
- Content: max 500KB (JSON) / 200KB (Markdown)
- Tags: 0–10 tags, each 1–30 chars
- Cover image: max 5MB, auto-resized to multiple sizes via MinIO

### REQ-POST-002: Read Post

**Given** any visitor  
**When** they request a published post by slug  
**Then** post content with author info, metadata, and comments are returned.

**Performance:** cached in Redis for 5min; cache-busted on update.

### REQ-POST-003: Update Post

**Given** the post author or editor/admin  
**When** they modify content, title, or metadata  
**Then** the post is updated and `updated_at` is set. Previous version saved in `post_versions` table.

### REQ-POST-004: Delete Post

**Given** the post author or admin  
**When** they delete a post  
**Then** post is soft-deleted (archived). Hard delete only by superadmin after 30 days.

### REQ-POST-005: Post States

A post can be in one of these states:
- `draft` — visible only to author
- `scheduled` — will auto-publish at `scheduled_at`
- `published` — publicly visible
- `archived` — hidden from public, retained for author
- `deleted` — marked for hard deletion

### REQ-POST-006: List Posts

**Given** any visitor  
**When** they browse the blog  
**Then** paginated list of published posts sorted by `published_at DESC`.

**Supports:**
- Cursor-based pagination (keyset)
- Filter by: category, tag, author, date range
- Search integration (delegated to Meilisearch)
- Default page size: 10, max: 50

### REQ-POST-007: Post Reactions

**Given** an authenticated user  
**When** they react to a post with an emoji  
**Then** the reaction is persisted in real-time via WebSocket broadcast.

**Supported reactions:** 👍, ❤️, 😄, 😮, 😢, 😡, 👏, 🎉, 💡, 🔥

### REQ-POST-008: Post Bookmarks

**Given** an authenticated user  
**When** they bookmark a post  
**Then** it appears in their bookmarks list for later reading.

### REQ-POST-011: Post Reposts

**Given** an authenticated user  
**When** they repost a published post  
**Then** the repost is persisted, `repost_count` increments, and `is_reposted` is true for that viewer.

**Given** an authenticated user who already reposted a post  
**When** they remove the repost  
**Then** the repost row is deleted and `repost_count` decrements.

**Notes:**
- One repost per user per post (unique on `user_id`, `post_id`)
- Repost is distinct from external share (`share_clicked` analytics)

### REQ-POST-009: Reading Time

Posts display estimated reading time calculated as: `word_count / 200` words-per-minute, with minimum 1 minute.

### REQ-POST-010: Scheduled Publishing

**Given** an author  
**When** they set `scheduled_at` to a future timestamp  
**Then** a background job publishes the post at that time via NATS scheduled event.

---

## API Endpoints

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| POST | `/api/posts` | Author+ | Create post |
| GET | `/api/posts` | None | List published posts |
| GET | `/api/posts/:slug` | None | Get post by slug |
| PATCH | `/api/posts/:id` | Author/Editor+ | Update post |
| DELETE | `/api/posts/:id` | Author/Admin+ | Delete post |
| POST | `/api/posts/:id/reactions` | Reader+ | Toggle reaction |
| POST | `/api/posts/:id/bookmark` | Reader+ | Toggle bookmark |
| DELETE | `/api/posts/:id/bookmark` | Reader+ | Remove bookmark |
| POST | `/api/posts/:id/repost` | Reader+ | Repost a post |
| DELETE | `/api/posts/:id/repost` | Reader+ | Remove repost |
| GET | `/api/posts/feed` | Reader+ | Personalized feed |
| GET | `/api/posts/my/drafts` | Author+ | List own drafts |
