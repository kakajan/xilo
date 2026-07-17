# Spec: Chat & Messaging

## Overview

Real-time private messaging between users with Telegram-like UX. Supports direct messages, group chats, media sharing, message reactions, typing indicators, read receipts, and online presence.

---

## Requirements

### REQ-CHAT-001: Create Chat

**Given** an authenticated user  
**When** they start a new chat with another user  
**Then** a direct chat is created (or existing one is reused).

**Validation:**
- Cannot create chat with self
- Cannot create duplicate direct chat with same user
- Group chats require name and at least 2 members

### REQ-CHAT-002: Send Message

**Given** a user in a chat  
**When** they send a message  
**Then** the message is delivered in real-time to all chat members via WebSocket.

**Message types:**
- Text (max 10,000 chars)
- Image (auto-resized, max 10MB)
- Video (max 100MB)
- File (max 50MB)
- Reply to existing message

### REQ-CHAT-003: Message History

**Given** a user opens a chat  
**When** the chat loads  
**Then** the last 50 messages are fetched, with infinite scroll for older messages.

**Performance:** cursor-based pagination, messages cached in Redis for 1 hour.

### REQ-CHAT-004: Read Receipts

**Given** a message in a chat  
**When** a member views the message  
**Then** a read receipt is sent and displayed to the sender.

**Indicators:**
- ✓ Sent (delivered to server)
- ✓✓ Delivered (received by recipient)
- ✓✓ Blue Read (viewed by recipient)

### REQ-CHAT-005: Typing Indicators

**Given** a user typing in a chat  
**When** they are actively composing  
**Then** other members see "typing..." indicator.

**Behavior:**
- Debounced: sent every 3 seconds while typing
- Cleared after 5 seconds of inactivity
- Not sent if user is offline

### REQ-CHAT-006: Online Presence

**Given** a user's chat list  
**When** viewing chat members  
**Then** online status is shown:
- Green dot for online (last seen < 2 minutes)
- "Last seen X minutes/hours ago" for offline
- Nothing if user has hidden their last seen

### REQ-CHAT-007: Message Reactions

**Given** any message  
**When** a user adds a reaction  
**Then** the reaction is displayed in real-time.

**Supported reactions:** 👍, ❤️, 😄, 😮, 😢, 😡, 👏, 🎉, 💡, 🔥

Multiple reactions per user per message allowed.

### REQ-CHAT-008: Edit & Delete Messages

**Given** a message sender  
**When** they edit their message  
**Then** the message is updated and marked as "edited".

**When** they delete their message  
**Then** the message is soft-deleted (shows "deleted message" to others).

**Constraints:**
- Edit allowed within 48 hours of sending
- Delete allowed anytime (soft delete)

### REQ-CHAT-009: Chat List

**Given** a user  
**When** they view their chat list  
**Then** chats are sorted by `last_message_at DESC` with:
- Last message preview
- Unread count badge
- Online status indicator
- Muted indicator

### REQ-CHAT-010: Group Chats

**Given** a user  
**When** they create or join a group chat  
**Then** they can:
- Send/receive messages
- Add/remove members (admins only)
- Change group name/avatar (admins only)
- Mute/unmute the chat
- Leave the group

### REQ-CHAT-011: Chat Notifications

**Given** a user with a muted chat  
**When** a new message arrives  
**Then** no notification is shown.

**Given** a user with an unmuted chat  
**When** a new message arrives  
**Then**:
- In-app: notification banner + sound
- Background: push notification (FCM/self-hosted)

### REQ-CHAT-012: Message Search

**Given** a user in a chat  
**When** they search within the chat  
**Then** matching messages are highlighted.

**Searchable:** text content, sender name, date range.

### REQ-CHAT-013: Saved Messages

**Given** an authenticated user  
**When** they open Saved Messages  
**Then** the system get-or-creates a private chat of type `saved` owned solely by that user (one per user).

**Android UX:**
- Messages tab `ذخیره‌شده‌ها` chip filters in-place to saved chat messages only.
- Settings → «پیام‌های ذخیره‌شده» opens a dedicated Saved hub with Messages / Posts / Comments segments.

### REQ-CHAT-014: Chat Folders

**Given** an authenticated user  
**When** they create, rename, delete, or assign chats to a folder  
**Then** folders are scoped to that user and may only contain chats where the user is an active member.

**Given** a chat list with folders  
**When** the user selects a folder filter  
**Then** only chats assigned to that folder are shown (plus an All filter).

---

## API Endpoints

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| GET | `/api/chats` | Reader+ | List user's chats |
| POST | `/api/chats` | Reader+ | Create new chat |
| GET | `/api/chats/saved` | Reader+ | Get or create Saved Messages chat |
| GET | `/api/chats/:id` | Member | Chat details |
| PATCH | `/api/chats/:id` | Admin | Update chat (group) |
| DELETE | `/api/chats/:id` | Member | Leave chat |
| POST | `/api/chats/:id/members` | Admin | Add members |
| DELETE | `/api/chats/:id/members/:userId` | Admin/Member | Remove member/leave |
| GET | `/api/chats/:id/messages` | Member | Message history |
| POST | `/api/chats/:id/messages` | Member | Send message |
| PATCH | `/api/messages/:id` | Sender | Edit message |
| DELETE | `/api/messages/:id` | Sender | Delete message |
| POST | `/api/messages/:id/read` | Member | Mark as read |
| POST | `/api/messages/:id/reactions` | Member | Toggle reaction |
| GET | `/api/chats/:id/search?q=` | Member | Search messages |
| GET | `/api/chat-folders` | Reader+ | List folders |
| POST | `/api/chat-folders` | Reader+ | Create folder |
| PATCH | `/api/chat-folders/:id` | Owner | Rename / reorder folder |
| DELETE | `/api/chat-folders/:id` | Owner | Delete folder |
| PUT | `/api/chat-folders/:id/chats` | Owner | Replace folder chat membership |

## WebSocket Events

| Event | Direction | Payload |
|-------|-----------|---------|
| `chat.join` | Client→Server | `{ chatId }` |
| `chat.leave` | Client→Server | `{ chatId }` |
| `message.send` | Client→Server | `{ chatId, content, mediaUrl?, replyToId? }` |
| `message.receive` | Server→Client | `{ message }` |
| `message.edit` | Server→Client | `{ messageId, content, editedAt }` |
| `message.delete` | Server→Client | `{ messageId }` |
| `message.read` | Client→Server | `{ messageId }` |
| `message.read` | Server→Client | `{ messageId, userId }` |
| `message.reaction` | Client→Server | `{ messageId, reaction }` |
| `message.reaction` | Server→Client | `{ messageId, reaction, userId }` |
| `user.typing` | Client→Server | `{ chatId }` |
| `user.typing` | Server→Client | `{ chatId, userId }` |
| `user.online` | Server→Client | `{ userId }` |
| `user.offline` | Server→Client | `{ userId }` |
| `chat.created` | Server→Client | `{ chat }` |
| `chat.updated` | Server→Client | `{ chat }` |
| `chat.member_added` | Server→Client | `{ chatId, userId }` |
| `chat.member_removed` | Server→Client | `{ chatId, userId }` |