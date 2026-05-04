# Spec: Media & Storage System

## Overview
MinIO-powered object storage for images, avatars, attachments, and videos. Automatic image resizing, WebP conversion, and CDN-friendly URLs.

---

## Requirements

### REQ-MDA-001: Image Upload

**Given** an authenticated user with author+ role  
**When** they upload an image via the editor or profile settings  
**Then** the image is:
1. Validated (format: JPEG, PNG, WebP, GIF; max 10MB)
2. Sanitized (EXIF metadata stripped)
3. Resized to multiple sizes (thumbnail, small, medium, large, original)
4. Converted to WebP for all sizes except original
5. Stored in MinIO under `/{user_id}/{uuid}.{ext}`
6. Returned with public URL

**Generated sizes:**
| Name | Max Width | Max Height | Format |
|------|-----------|------------|--------|
| thumbnail | 150 | 150 | WebP |
| small | 480 | 480 | WebP |
| medium | 768 | 768 | WebP |
| large | 1280 | 1280 | WebP |
| original | — | — | Original |

### REQ-MDA-002: Avatar Upload

**Given** any authenticated user  
**When** they upload a profile avatar  
**Then** the image is cropped to square, resized to 256x256, and stored as WebP.

### REQ-MDA-003: Media Deletion

**Given** a media item  
**When** the owner deletes it (or their account is deleted)  
**Then** all size variants are removed from MinIO.

### REQ-MDA-004: Media in Posts

**Given** a post with embedded images  
**When** rendered  
**Then** images use `<picture>` with `srcset` for responsive loading and lazy loading (`loading="lazy"`).

### REQ-MDA-005: Upload Progress

**Given** a large file upload  
**When** uploading via the web or mobile app  
**Then** progress percentage is shown to the user.

### REQ-MDA-006: CDN & Caching

Media URLs are served via Traefik with:
- `Cache-Control: public, max-age=31536000, immutable`
- ETag-based conditional requests
- Optional Cloudflare CDN in front

---

## API Endpoints

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| POST | `/api/media/upload` | Author+ | Upload file(s) — multipart |
| DELETE | `/api/media/:id` | Owner | Delete media |
| GET | `/api/media/:id` | Owner | Get media metadata |
| GET | `/api/media` | Owner | List user's uploaded media |

## URL Pattern

```
https://cdn.xilo.dev/{user_id}/{uuid}/{size}.{ext}
https://cdn.xilo.dev/abc123/def456/medium.webp
```
