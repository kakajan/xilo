# Proposal: Post-level Audio Upload + Sticky Player

**Status:** Active  
**Date:** 2026-07-20

---

## Summary

Allow authors to attach **one optional audio file** to a post when creating or editing. Readers see a **narrow sticky player** on the post detail page (web and Android) with modern playback controls. Feed cards remain text/cover-only.

## Motivation

- Authors want to publish posts as listen-along content (narration, podcast-style audio) without embedding third-party players.
- Existing media upload is image-only; posts only store `cover_image_url`.
- Sticky chrome already exists for reactions; a matching slim audio bar fits the current design language.

## Scope

| Domain | Deliverables |
|--------|-------------|
| **Backend** | Audio MIME allowlist (50MB); `posts.audio_url`; create/update/get wiring |
| **Web** | Editor audio upload/remove; sticky player on post detail |
| **Android** | Create/edit audio pick+upload; Media3 sticky player on post detail |
| **OpenSpec** | Delta specs for media, post, web, Android |

## Out of Scope

- Multiple audio tracks per post
- Waveform visualization
- Offline download / background playback across app navigation
- Chat voice messages
- Audio mini-player on feed cards
- Global Spotify-style player

## Success Criteria

- [x] `POST /api/media/upload` accepts allowed audio types up to 50MB; images stay 10MB
- [x] Posts expose optional `audio_url` on create/update/response (clearable)
- [x] Web write UI can attach/replace/remove audio
- [x] Web + Android post detail show sticky slim player when `audio_url` is set
