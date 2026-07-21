# Design: Discover Comment Amplify

## Product model

| Action | Who | Result |
|--------|-----|--------|
| **Repost** | Author+ | Toggle row in `comment_reposts`; increment `comments.repost_count`; boost Discover via shares; **no** new post / feed item |
| **Quote** | Author+ | New published post with `quoted_comment_id`; auto-record comment repost; notify comment author; appears in Home feed |

Mutual exclusion on a post: at most one of `quoted_post_id` / `quoted_comment_id`.

## Data model

```sql
ALTER TABLE comments ADD COLUMN repost_count INT NOT NULL DEFAULT 0;

CREATE TABLE comment_reposts (
  user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  comment_id UUID NOT NULL REFERENCES comments(id) ON DELETE CASCADE,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  PRIMARY KEY (user_id, comment_id)
);

ALTER TABLE posts
  ADD COLUMN quoted_comment_id UUID REFERENCES comments(id) ON DELETE SET NULL;
```

## API

| Method | Path | Auth | Behavior |
|--------|------|------|----------|
| POST | `/api/comments/:id/repost` | Author+ | Insert repost; return `{ reposted, repost_count }` |
| DELETE | `/api/comments/:id/repost` | Author+ | Remove; return counts |
| POST | `/api/posts` | Author+ | Accept `quoted_comment_id`; enrich `quoted_comment` |

Comment list / Discover payloads include `repost_count` and viewer `is_reposted` (Author+).

### QuotedCommentSummary

```json
{
  "id": "…",
  "content": "…",
  "author": { "id", "username", "display_name", "avatar_url" },
  "post_id": "…",
  "post_title": "…",
  "post_slug": "…",
  "post_author_username": "…"
}
```

## Notifications

| Type | Trigger |
|------|---------|
| `comment_reposted` | Author+ toggles plain repost on your comment |
| `comment_quoted` | Author+ publishes a post quoting your comment |

Respect notification preferences when present; default deliver via existing WS path.

## Discover scoring

Engagement term includes shares:

`raw = likes + 2×replies + 3×min(repost_count, shareCap)` with `shareCap = 20`.

## UI

- Reuse post `RepostMenuButton` / `RepostButton` pattern on comment cards.
- Control visible only when `canRepost` / Author+.
- Badge when `repost_count > 0`: “تقویت‌شده” / amplified.
- Share copies deep link `/{username}/{slug}?reply={commentId}`.

## Mentions (phase 2)

On comment create/update, parse `@username`, notify matching users with `comment_mention` (existing type).
