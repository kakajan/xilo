# Proposal: Telegram-like Group Chat

**Status:** Active  
**Date:** 2026-07-21

---

## Summary

Enable writer+ roles (`author`, `editor`, `admin`, `superadmin`) to create Telegram-style group chats. Any authenticated user may be a member and send messages. Group admins manage membership and metadata. Deliver full MVP parity on **Android and Web**, then layer-2 features (mentions, pins, invite links).

## Motivation

Direct chat exists, but group creation/admin UX is missing on clients. Platform writers need private collaboration spaces similar to Telegram groups.

## Scope

| Domain | Deliverables |
|--------|-------------|
| **OpenSpec** | Delta requirements for chat, notifications, Android, Web |
| **Backend** | Role gate for group create, promote/demote, system messages, chat media upload, mentions, pins, invite links |
| **Android** | New Group wizard, GroupInfo, member admin, media, layer-2 UX |
| **Web** | Same flows under `/chat` with RTL parity |

## Out of Scope

- Voice/video calls, forum topics, bots, public broadcast channels, polls
- Legacy Flutter `mobile/`

## Success Criteria

- [ ] Writer+ creates group (≥1 other member); reader gets `403` on create
- [ ] Group admin add/remove/promote/demote; last admin cannot be demoted
- [ ] Android + Web: create → chat → info → manage members → leave
- [ ] Chat members (any platform role) can upload chat media
- [ ] Mentions, pins, invite links work on both clients
