# Delta: Comment Spec — Amplify

## ADDED Requirements

### REQ-CMT-012: Comment Repost (Author+)

**Given** an authenticated user with role Author+ (`author`, `editor`, `admin`, or `superadmin`)  
**When** they repost a non-deleted comment  
**Then** a `comment_reposts` row is created, `repost_count` increments, and `is_reposted` is true for that viewer.

**Given** an Author+ user who already reposted a comment  
**When** they remove the repost  
**Then** the row is deleted and `repost_count` decrements.

**Given** a Reader (no create-post permission)  
**When** they view a comment card  
**Then** the repost control is hidden, and `POST/DELETE /api/comments/:id/repost` returns forbidden.

**Notes:**
- One repost per user per comment
- Plain repost does **not** create a post or Home feed item
- Distinct from external share / deep-link copy

### REQ-CMT-013: Comment Amplify Badge

**Given** a comment with `repost_count > 0`  
**When** it is rendered on Discover or in a thread  
**Then** clients MAY show an amplify badge/count visible to all viewers.

### REQ-CMT-014: Comment Deep Link Share

**Given** a comment on a published post  
**When** a user shares or copies the comment link  
**Then** the URL opens the parent post with `?reply={commentId}` focusing that thread.

## MODIFIED Requirements

### REQ-CMT-011: Discover Feed Eligibility

Discover cards SHALL include `repost_count` (and `is_reposted` for Author+ viewers) in addition to likes/replies.

## API Additions

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| POST | `/api/comments/:id/repost` | Author+ | Repost a comment |
| DELETE | `/api/comments/:id/repost` | Author+ | Remove comment repost |
