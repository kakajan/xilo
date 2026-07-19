# Delta for Android — Onboarding Interests + Contacts + Discover

## ADDED Requirements

### Requirement: Interest onboarding

The Android app SHALL fetch interests from `GET /api/interests`, let the user select them during onboarding, and persist via `PUT /api/users/me/interests` before completing the flow. Interests MUST NOT be hardcoded as the catalog source of truth.

#### Scenario: Save on complete
- GIVEN a logged-in user completing the interests step
- WHEN they continue
- THEN selected interest IDs are saved to the server

### Requirement: Contact suggestions

The app SHALL optionally request `READ_CONTACTS`, read phone/email from device contacts, SHA-256 hash normalized values on-device, call `POST /api/contacts/match`, and offer Follow via the real follow API. Denying or skipping permission MUST NOT block onboarding. Raw contact phones/emails MUST NEVER be uploaded.

#### Scenario: Skip contacts
- GIVEN the suggestions step
- WHEN the user denies or skips contacts
- THEN onboarding can still complete without matches

#### Scenario: Follow suggestion
- GIVEN a matched Xilo member
- WHEN the user taps Follow
- THEN `POST /api/users/{username}/follow` succeeds and UI reflects following state

### Requirement: Discover personalization UI

Discover SHALL show topic chips (from user/catalog interests) and refresh using interest-aware discover comments when online.

#### Scenario: Topic filter
- GIVEN the user has interests
- WHEN they select a topic chip
- THEN Discover shows comments filtered/boosted for that topic

### Requirement: Contacts hub screen

The app SHALL provide a Contacts screen (reachable from the Messages toolbar) that loads `GET /api/contacts`, shows a from-contacts badge when applicable, supports search, pull-to-refresh, optional address-book sync again via `POST /api/contacts/match`, and actions to open profile or start a direct chat.

#### Scenario: Open chat from contacts
- GIVEN the Contacts list shows a following user
- WHEN the user taps message
- THEN a direct chat is created/opened for that user

#### Scenario: Sync again
- GIVEN contacts permission is granted
- WHEN the user taps sync
- THEN match runs and the list refreshes with updated badges
