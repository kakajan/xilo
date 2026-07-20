# Design: Post-level Audio

## Data model

```sql
ALTER TABLE posts ADD COLUMN audio_url VARCHAR(500);
```

JSON field: `audio_url` (optional string). Mirrors `cover_image_url`.

## Upload

Reuse `POST /api/media/upload` (multipart field `file`).

| Kind | MIME | Max size |
|------|------|----------|
| Image | jpeg, png, webp, gif | 10MB |
| Audio | mpeg, mp4, aac, ogg, wav, webm | 50MB |

Audio uploads skip image processing; store `variants.original` only.

## Client flow

1. Author picks audio → upload → receive `url`
2. Create/update post with `audio_url`
3. Detail page reads `audio_url` → sticky player

## Player UX (web + Android)

- Scope: post detail only
- Slim sticky bar above reaction bar / above mobile bottom nav
- Controls: play/pause, seek, current/duration, rate 1× / 1.25× / 1.5×
- Web: custom UI over `<audio>`
- Android: platform `MediaPlayer` sticky bar (Media3 unavailable in this environment’s Maven mirror); release on dispose
