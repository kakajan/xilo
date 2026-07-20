# Spec: Notification System

## Overview
Real-time push notifications delivered via WebSocket for in-app notifications, with optional email digests and SSE as longer-term channels. Domain fanout persists to PostgreSQL and delivers over Redis Pub/Sub → WebSocket; NATS outbox relay is deferred until transactional outbox exists.

### v1 channels (implemented scope)

| Channel | v1 status |
|---------|-----------|
| PostgreSQL inbox + REST list/mark-read/prefs | Required |
| WebSocket `notification.new` / `notification.count` via Redis `ws:user:*` | Required |
| Redis unread badge counter | Required |
| Android FCM (env-gated) | Required when Firebase credentials configured |
| Email digests / SSE `/stream` / Web Push | Deferred (spec remains authoritative for later phases) |
| NATS `notification.created` consumer | Deferred (outbox gate) |

**v1 event types produced:** `comment_reply`, `new_follower`, `post_published`, `new_message`.

---

## Requirements

### REQ-NTF-001: Notification Types

The system supports these notification types:

| Type | Trigger | Delivery |
|------|---------|----------|
| `comment_reply` | Someone replies to your comment | WS + Email digest |
| `comment_mention` | Someone @mentions you | WS + Email |
| `post_reaction` | Someone reacts to your post | WS |
| `new_follower` | Someone follows you | WS |
| `post_published` | Author you follow publishes | WS + Email digest |
| `system_announcement` | Admin broadcast | WS + Email |
| `moderation_action` | Your comment/post was moderated | WS + Email |
| `subscription_expiry` | Subscription ending soon | Email |
| `new_message` | Someone sends you a chat message | WS + Push (if muted: skip) |
| `chat_mention` | Someone @mentions you in group chat | WS + Push |

### REQ-NTF-002: Real-time Delivery

**Given** an authenticated user with an active WebSocket connection  
**When** a notification event fires  
**Then** the notification appears on their client within 500ms.

### REQ-NTF-003: Notification Persistence

**Given** any notification event  
**When** the user is offline  
**Then** the notification is persisted in PostgreSQL and delivered on next WebSocket connect.

Unread badge count is maintained in Redis for fast lookup.

### REQ-NTF-004: Notification Preferences

**Given** a user in settings  
**When** they toggle notification channels per type  
**Then** notifications respect their preferences.

**Per-type settings:** Enable/disable for Web push and Email independently.

### REQ-NTF-005: Mark as Read

**Given** notifications in the notification panel  
**When** the user opens the panel or clicks a notification  
**Then** it's marked as read.

**Batch actions:** "Mark all as read" button.

### REQ-NTF-006: Email Digests

**Given** users who opt into email digests  
**When** a daily/weekly cron job runs  
**Then** a summarized email is sent with top activity.

---

## API Endpoints

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| GET | `/api/notifications` | Reader+ | List notifications (paginated, cursor) |
| GET | `/api/notifications/unread-count` | Reader+ | Get unread count |
| PATCH | `/api/notifications/:id/read` | Reader+ | Mark one as read |
| PATCH | `/api/notifications/read-all` | Reader+ | Mark all as read |
| PUT | `/api/notifications/preferences` | Reader+ | Update preferences |

## WebSocket Events

| Event | Direction | Payload |
|-------|-----------|---------|
| `notification.new` | Server→Client | `{ id, type, title, body, data, created_at }` |
| `notification.count` | Server→Client | `{ unread: number }` |

## SSE Endpoint

`GET /api/notifications/stream` — SSE stream for fallback when WebSocket is unavailable (proxies, firewalls).
