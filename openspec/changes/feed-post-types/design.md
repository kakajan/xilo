# Design: Feed post types

## Types

| `post_type` | Required | Optional | Card |
|-------------|----------|----------|------|
| `article` (default) | title, body | cover, audio, tags, schedule | current article card |
| `micro` | body ≤ 500 chars | tags | compact text, no title row |
| `photo` | 1–10 images | caption | carousel |
| `video` | 1 video | caption, poster | Media3 |
| `link` | canonical URL | comment | OG preview |

`quoted_post_id` / `quoted_comment_id` remain orthogonal (a quote of any type).

## API

`POST /api/posts` and `PATCH /api/posts/:id` add:

```json
{
  "post_type": "article|micro|photo|video|link",
  "media_ids": ["uuid"],
  "link_url": "https://..."
}
```

Unknown `post_type` → `400`. Readers still cannot create posts.

Existing article fields stay valid when `post_type` is omitted (`article`).

## Storage
Add `posts.post_type VARCHAR(20) NOT NULL DEFAULT 'article'`. Media rows already exist via media-spec; gallery/video reference `media_ids`.

## Clients
Do not render a type the API does not return. Android FAB sheet lists types only after this change is implemented on the gateway.
