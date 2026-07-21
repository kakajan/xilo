# Delta: Web — Group Chat

## ADDED Requirements

### Requirement: REQ-WEB-GCT-001 — New Group

Writer+ users SHALL create groups from `/chat` (or equivalent entry). Readers SHALL NOT see the create-group CTA.

#### Scenario: Create group
- GIVEN an author on chat
- WHEN they complete New Group with name and ≥1 member
- THEN they navigate to `/chat/:id` for the new group

### Requirement: REQ-WEB-GCT-002 — Group info parity

Web SHALL provide group info and member management with the same authorization rules as Android.

### Requirement: REQ-WEB-GCT-003 — Layer-2 parity

Web SHALL support pins, invite join/create/revoke (admins), and mention highlighting consistent with Android.
