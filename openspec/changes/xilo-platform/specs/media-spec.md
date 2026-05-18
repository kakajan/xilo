# Spec: Media & Storage System

## Overview

Pluggable object storage for images, avatars, attachments, and videos. Automatic image resizing, WebP conversion, and CDN-friendly URLs.

The storage layer uses a **driver/interface pattern** — the system ships with a self-hosted MinIO driver by default, but any S3-compatible or cloud provider (AWS S3, Cloudflare R2, DigitalOcean Spaces) can be swapped in via configuration without code changes.

```
Storage Interface (pkg/storage/storage.go)
├── MinIO Driver  (pkg/storage/minio/)   ← default, self-hosted
└── S3 Driver     (pkg/storage/s3/)      ← AWS / cloud provider
```

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

### REQ-MDA-007: Storage Driver Abstraction

**Given** the media service needs to store files  
**When** it writes or reads a file  
**Then** it MUST use the `StorageDriver` interface — never call MinIO/S3 directly.

**Interface contract:**

```go
type StorageDriver interface {
    Upload(ctx context.Context, key string, reader io.Reader, opts UploadOptions) (*UploadResult, error)
    Download(ctx context.Context, key string) (io.ReadCloser, error)
    Delete(ctx context.Context, key string) error
    GetURL(key string) string
    GetSignedURL(key string, duration time.Duration) (string, error)
}
```

**Constraints:**
- The driver MUST be selected via `STORAGE_DRIVER` environment variable (`minio` | `s3`)
- The MinIO driver SHALL be the default when `STORAGE_DRIVER` is unset
- Storage connection config SHALL come from environment variables (`STORAGE_ENDPOINT`, `STORAGE_ACCESS_KEY`, `STORAGE_SECRET_KEY`, `STORAGE_BUCKET`, `STORAGE_USE_SSL`)
- Switching drivers SHALL NOT require code changes — only config change + restart
- Each driver MUST implement the full `StorageDriver` interface
- The media service SHALL ONLY depend on the interface, never on concrete driver types

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
