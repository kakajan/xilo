# Spec: Post System

## Overview
Full CRUD for blog posts/articles with rich text editing, media embedding, version history, scheduling, and SEO metadata.

---

## Requirements

### REQ-POST-001: Create Post

**Given** an authenticated user with role `author`, `editor`, `admin`, or `superadmin`  
**When** they submit a post with title, slug, content (Tiptap JSON/Markdown), excerpt, cover image, tags, and category  
**Then** the post is saved as `draft` and can be published.

**Given** an authenticated `reader`  
**When** they call `POST /api/posts`  
**Then** the API returns `403 Forbidden`.

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

**Given** an authenticated user with role Author+ (`author`, `editor`, `admin`, or `superadmin`)  
**When** they repost a published post  
**Then** the repost is persisted, `repost_count` increments, and `is_reposted` is true for that viewer.

**Given** an authenticated Author+ user who already reposted a post  
**When** they remove the repost  
**Then** the repost row is deleted and `repost_count` decrements.

**Given** an authenticated Reader (no create-post permission)  
**When** they view a post or feed card  
**Then** the repost control is hidden, and `POST/DELETE /api/posts/:id/repost` returns forbidden.

**Notes:**
- One repost per user per post (unique on `user_id`, `post_id`)
- Repost is limited to the same roles that can publish posts (Author+)
- Repost is distinct from external share (`share_clicked` analytics)

### REQ-POST-009: Reading Time

Posts display estimated reading time calculated as: `word_count / 200` words-per-minute, with minimum 1 minute.

### REQ-POST-010: Scheduled Publishing

**Given** an author  
**When** they set `scheduled_at` to a future timestamp  
**Then** a background job publishes the post at that time via NATS scheduled event.

### REQ-POST-012: Inline Hashtags

**Given** an author creates or updates a post whose body contains Instagram/X-style `#hashtags`  
**When** the server processes Create/Update  
**Then** hashtags are extracted from plain text (`content_md` or TipTap text), normalized (NFC, Latin lowercase, no `#`), merged with explicit `tags` (extract first), deduped case-insensitively, capped at 10, and stored in `posts.tags`.

**Rules:**
- Body pattern: `#` + 1–30 letters/digits/`_`/`-` (Latin + Arabic/Persian scripts)
- Reject digits-only tags (e.g. `#123`) and matches inside URLs
- Clients MAY send tags for UX; server extraction is the source of truth

**Given** a visitor views a post or feed card  
**When** the body/excerpt contains `#tag`  
**Then** each hashtag is rendered as a link to the tag feed (`/tag/{tag}` on web; TagFeed on Android).

**Given** a visitor opens a tag feed  
**When** they request posts for that tag  
**Then** `GET /api/posts?tag=` returns published posts containing that tag.

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
| POST | `/api/posts/:id/repost` | Author+ | Repost a post |
| DELETE | `/api/posts/:id/repost` | Author+ | Remove repost |
| GET | `/api/posts/feed` | Reader+ | Personalized feed |
| GET | `/api/posts/my/drafts` | Author+ | List own drafts |
| GET | `/api/tags/suggest?q=` | None | Hashtag autocomplete |
| GET | `/api/tags/trending` | None | Trending hashtags |
