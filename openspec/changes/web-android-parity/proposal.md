# Proposal: Web–Android Feature & Visual Parity

**Status:** Active  
**Date:** 2026-07-18

---

## Summary

Bring the Next.js web consumer experience to full parity with the native Android app: four-tab shell (Feed / Discover / Chat / Profile), Telegram-style UI, chat, saved hub, settings (devices, folders), profile tabs, and Persian RTL defaults—while keeping web author tools (`/write`, dashboard).

## Motivation

Android already delivers the primary consumer UX. Web still uses a blog-style navbar and lacks Discover, Chat, and most settings surfaces. Users expect the same product on both clients.

## Scope

| Domain | Deliverables |
|--------|-------------|
| **OpenSpec** | Page matrix, delta web-frontend requirements, task checklist |
| **Web shell** | Floating bottom nav, FAB, chrome hide-on-scroll, Vazirmatn + RTL |
| **Web features** | Discover, Chat, Saved Hub, Profile tabs, Follow lists, Settings parity, Onboarding |
| **Web polish** | Comment bubbles, motion, a11y, Persian copy |

## Out of Scope

- Backend Discover scoring pipeline (use Android’s recent-comments + search approach)
- Full offline Room-equivalent on web
- Wallet, Stories, advanced group chat beyond existing APIs
- Changes to `mobile/` (legacy Flutter) or Android unless shared API bugs

## Success Criteria

- [x] Four main tabs match Android destinations
- [x] Routes: `/discover`, `/chat`, `/chat/[id]`, `/saved`, settings devices/folders, follow lists
- [x] Chat send/receive against existing `/api/chats`
- [x] `npm run typecheck && npm run lint && npm run build` pass in `web/`
