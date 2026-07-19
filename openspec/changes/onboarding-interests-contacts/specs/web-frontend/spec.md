# Delta for Web Frontend — Admin Interests

## ADDED Requirements

### Requirement: Admin interests dashboard

The admin dashboard SHALL provide `/dashboard/interests` for listing, creating, editing, reordering, and deactivating interests, using the same auth/`apiFetch` patterns as the users page. The dashboard layout SHALL include a nav item for Interests.

#### Scenario: Admin manages catalog
- GIVEN an admin session
- WHEN they open Interests and create or reorder items
- THEN changes persist via admin APIs and appear in the list

### Requirement: Contacts hub page

The web app SHALL provide `/contacts` listing the authenticated user's followings from `GET /api/contacts`, with a from-contacts badge, search, message (create direct chat), and profile links. Side and bottom nav SHALL include Contacts. Web MUST NOT upload raw address-book PII; sync badge comes from prior Android/onboarding match persistence.

#### Scenario: Message from contacts
- GIVEN the user is authenticated and follows someone
- WHEN they open Contacts and tap message
- THEN a direct chat is created and they navigate to `/chat/{id}`
