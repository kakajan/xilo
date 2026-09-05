# Proposal: Feed post types

**Status:** Proposed
**Date:** 2026-09-05

## Summary
Add first-class feed post types beyond long-form articles: micro/status, photo/gallery, video, and link/embed. Keep article, quote, attached audio, and scheduled publishing as they exist today. Do not invent client-only types; backend, web, and Android share one contract.

## Motivation
The Android composer is article-shaped (title + body). Telegram/X-style feeds need shorter and media-first types so the home timeline stays alive without forcing every post through a blog title.

## Scope
| Domain | Deliverables |
|--------|-------------|
| Backend | `post_type` on posts, create/list/detail DTOs, media limits |
| Web | Composer type sheet + cards |
| Android | Type sheet + typed composer surfaces |

## Out of scope
Stories, polls, live, standalone voice posts (audio stays an attachment on article/micro).

## Success Criteria
- [ ] `POST /api/posts` accepts `post_type` with documented fields per type
- [ ] Feed cards render type-specific UI without fake local APIs
- [ ] Share URLs remain `https://aile.ir/{username}/{slug}` for all types

## Risks & Mitigations
| Risk | Impact | Mitigation |
|------|--------|------------|
| Clients ship types before API | Broken create | Spec + backend first |
| Large video uploads | Timeouts | Reuse media-spec chunked upload |
