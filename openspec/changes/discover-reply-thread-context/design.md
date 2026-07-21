# Design: Discover Reply Thread Context

## API — Discover comment enrichment

`GET /api/discover/comments` items gain:

```json
{
  "parent_id": "uuid|null",
  "root_id": "uuid|null",
  "depth": 0,
  "parent": {
    "id": "uuid",
    "author_username": "alice",
    "author_display_name": "Alice",
    "content_preview": "short text…"
  }
}
```

- `parent` is present only when `parent_id` is set and the parent row is not soft-deleted.
- `content_preview` is truncated (≈140 chars) server-side.
- Existing `post` context remains required for the medium line.

## Client UX

### Discover card (flat feed)

1. Body of the hit comment
2. If `parent` present: “در پاسخ به @user — preview”
3. Always when available: “روی پست: {title}”

Tap / reply still navigates to `/{postAuthor}/{slug}?reply={commentId}` (web) or `PostDetailKey(replyToCommentId=…)` (Android).

### Post thread entry

Reuse the existing 2-level window relative to focus root (REQ-CMT-009 / ui-ux-spec §8.5):

1. Build ancestor path from flat `parent_id` (Android) or tree (web).
2. `focusStack = path without the target` so the target appears as a direct child of the focused node (or at root level when depth ≤ 1).
3. Scroll to the target; back pops focus before leaving the screen.
4. “N پاسخ” drills into deeper layers.

Web CommentSection SHALL stop recursively rendering all descendants and match Android’s hard 2-level window.

## Data flow

```
Discover API (+ parent summary)
        → Discover card (reply-to + post)
        → Post detail deep-link
        → Seed focusStack from path
        → buildVisibleCommentThread / 2-level window
```
