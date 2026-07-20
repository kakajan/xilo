# Delta: Media — Post Audio Upload

## REQ-MDA-AUDIO-001: Audio Upload

**Given** an authenticated author  
**When** they upload an audio file via `POST /api/media/upload`  
**Then** the file is accepted when MIME is one of `audio/mpeg`, `audio/mp4`, `audio/aac`, `audio/ogg`, `audio/wav`, `audio/webm` (or equivalent extension fallback) and size ≤ 50MB  
**And** image uploads remain limited to JPEG/PNG/WebP/GIF at 10MB  
**And** audio is stored without image variant generation.
