# Spec: Comment System

## Overview
Hybrid Twitter + Telegram-style comment system with nested threading (max depth 4), emoji reactions, @mentions, real-time updates via WebSocket, and markdown support.

---

## Requirements

### REQ-CMT-001: Create Comment

**Given** an authenticated user viewing a published post  
**When** they submit a comment with text (markdown) and optional media attachment  
**Then** the comment is created and broadcast to all WebSocket clients viewing the post.

**Validation:**
- Text: 1–5000 chars
- Media: single image (max 5MB, auto-resized)
- Rate limit: 10 comments/min per user

### REQ-CMT-002: Nested Replies

**Given** an existing comment  
**When** a user replies to it  
**Then** the reply is nested under the parent, up to max depth 4.

At depth 4, replies are flattened (no further nesting). Sort order: newest first within each level.

### REQ-CMT-003: Real-time Updates

**Given** a user viewing a post page  
**When** another user adds, edits, or deletes a comment or reaction  
**Then** the viewing user sees the change within 500ms via WebSocket.

**Events emitted:**
- `comment.created`
- `comment.updated`
- `comment.deleted`
- `comment.reaction_added`
- `comment.reaction_removed`

### REQ-CMT-004: Emoji Reactions

**Given** any comment  
**When** a user clicks an emoji reaction  
**Then** the reaction is toggled (add/remove) and broadcast in real-time.

Multiple reactions per user per comment allowed (like Telegram).

### REQ-CMT-005: @Mentions

**Given** comment text containing `@username`  
**When** the comment is posted  
**Then** the mentioned user receives a notification.

Auto-complete suggestions while typing based on participants in the thread.

### REQ-CMT-006: Comment Moderation

**Given** a post author, editor, or admin  
**When** they view a comment  
**Then** they can:
- Delete any comment on their post
- Pin a comment to top
- Mark comment as spam (hides with "show" toggle)

### REQ-CMT-007: Thread Collapse

**Given** a nested comment thread  
**When** the user clicks "collapse"  
**Then** child replies are hidden with a "Show N replies" button.

Collapse state is UI-only (not persisted).

### REQ-CMT-008: Comment Sorting

Readers can sort comments by:
- **Newest** (default)
- **Oldest**
- **Most reacted**
- **Most replied** (hot)

---

## API Endpoints

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| GET | `/api/posts/:postId/comments` | None | List comments (paginated) |
| POST | `/api/posts/:postId/comments` | Reader+ | Create comment |
| PATCH | `/api/comments/:id` | Owner | Edit comment |
| DELETE | `/api/comments/:id` | Owner/Mod | Delete comment |
| POST | `/api/comments/:id/reactions` | Reader+ | Toggle reaction |
| POST | `/api/comments/:id/pin` | Author/Mod | Pin/unpin comment |
| POST | `/api/comments/:id/report` | Reader+ | Report comment |

## WebSocket Events

| Event | Direction | Payload |
|-------|-----------|---------|
| `subscribe:post` | Client→Server | `{ postId }` |
| `unsubscribe:post` | Client→Server | `{ postId }` |
| `comment.created` | Server→Client | `{ comment }` |
| `comment.updated` | Server→Client | `{ comment }` |
| `comment.deleted` | Server→Client | `{ commentId }` |
| `comment.reaction` | Server→Client | `{ commentId, reaction, count }` |
