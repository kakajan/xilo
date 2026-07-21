# Delta: Chat — Group Chat Telegram

## ADDED Requirements

### Requirement: REQ-CHAT-015 — Writer+ creates groups

Only users with platform role `author`, `editor`, `admin`, or `superadmin` SHALL create chats with `type=group`. Direct and saved chats remain available to any authenticated user. Group create SHALL require a non-empty name and at least one other member.

#### Scenario: Reader cannot create group
- GIVEN an authenticated reader
- WHEN they `POST /api/chats` with `type=group`
- THEN the server responds `403` with code `forbidden`

#### Scenario: Author creates two-person group
- GIVEN an authenticated author
- WHEN they create a group with name and one other member
- THEN the chat is created and the creator has `current_role=admin`

### Requirement: REQ-CHAT-016 — Promote and demote group admins

Group admins SHALL change another active member's `role` between `admin` and `member` via `PATCH /api/chats/:id/members/:userId`. The last remaining group admin SHALL NOT be demoted or removed without a replacement.

#### Scenario: Promote member
- GIVEN a group admin and a member
- WHEN the admin sets the member role to `admin`
- THEN the member's role is `admin`

#### Scenario: Last admin demote rejected
- GIVEN a group with a single admin
- WHEN that admin is demoted
- THEN the server rejects the change

### Requirement: REQ-CHAT-017 — System messages

The server SHALL insert `type=system` messages for group create, member add/remove, and role changes. Clients SHALL render them as non-editable system rows.

### Requirement: REQ-CHAT-018 — Mentions

Text messages MAY include `@username` mentions. Matching active members SHALL receive a `chat_mention` notification when unmuted.

### Requirement: REQ-CHAT-019 — Chat media for members

Active chat members SHALL upload media via `POST /api/chats/:id/media` regardless of platform writer role. Size limits follow existing chat message media rules.

### Requirement: REQ-CHAT-020 — Pins and invite links

Group admins SHALL pin/unpin messages and create/revoke invite links. Any authenticated user with a valid non-revoked token SHALL join via `POST /api/chats/join`.
