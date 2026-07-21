# Design: Telegram-like Group Chat

## Role model

| Concern | Rule |
|---------|------|
| Create group | Platform role in `author\|editor\|admin\|superadmin` |
| Membership / messaging | Any authenticated user |
| Group administration | `chat_members.role` = `admin` (not platform role) |

Do not conflate platform `admin` with group `member_role`.

## API additions

| Method | Path | Auth | Notes |
|--------|------|------|-------|
| POST | `/api/chats` | Auth; group requires writer+ | Min 1 other member for groups |
| PATCH | `/api/chats/:id/members/:userId` | Group admin | `{ "role": "admin"\|"member" }` |
| POST | `/api/chats/:id/media` | Active member | Chat-scoped upload |
| POST | `/api/chats/:id/pins` | Group admin | `{ "message_id" }` |
| DELETE | `/api/chats/:id/pins/:messageId` | Group admin | |
| GET | `/api/chats/:id/pins` | Member | |
| POST | `/api/chats/:id/invite-links` | Group admin | Returns token |
| DELETE | `/api/chats/:id/invite-links/:token` | Group admin | Revoke |
| POST | `/api/chats/join` | Auth | `{ "token" }` |

## Schema

- `messages.type` adds `system`
- `chat_pins (chat_id, message_id, pinned_by, pinned_at)`
- `chat_invite_links (chat_id, token, created_by, created_at, revoked_at, uses)`

## System messages

Inserted by server on group create / member add / remove / role change. `sender_id` = acting member. Clients render as centered system rows.

## Mentions

On text send, parse `@username` tokens; notify matching active members with type `chat_mention` (prefs reuse `new_message_*` until dedicated columns exist).
