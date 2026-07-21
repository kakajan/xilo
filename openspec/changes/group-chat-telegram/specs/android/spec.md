# Delta: Android — Group Chat

## ADDED Requirements

### Requirement: REQ-AND-GCT-001 — New Group

Writer+ users SHALL access a New Group flow from chat entry points. Readers SHALL NOT see the create-group CTA.

#### Scenario: Create group
- GIVEN an author on New Chat
- WHEN they select New Group, pick ≥1 contact, set a name, and confirm
- THEN a group chat opens and appears in the chat list

### Requirement: REQ-AND-GCT-002 — Group info

Members SHALL open Group Info to view members, mute, and leave. Group admins SHALL edit name/avatar, add/remove members, and promote/demote.

### Requirement: REQ-AND-GCT-003 — Layer-2 surfaces

The client SHALL support viewing pinned messages, sharing/revoking invite links (admins), and highlighting `@mentions` in bubbles when present.
