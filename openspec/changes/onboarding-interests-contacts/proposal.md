# Proposal: Onboarding Interests + Contact Sync

**Status:** Active  
**Date:** 2026-07-19

---

## Summary

Replace demo Android onboarding with admin-managed interests (CRUD + multilingual labels) that persist on the user profile and personalize Discover, plus real contact-sync suggestions that match device phone/email hashes to Xilo members without uploading raw PII.

## Motivation

- Onboarding currently hardcodes interest chips and three demo users; nothing is saved or used.
- Discover ignores user preferences.
- Contact suggestions need a privacy-preserving match path (client SHA-256 → server HMAC) and real follow API calls.

## Scope

| Domain | Deliverables |
|--------|-------------|
| **Backend interests** | `interests` + `user_interests` tables, seed ~18 topics, public list + user save + admin CRUD/reorder |
| **Backend contacts** | `phone_hash`/`email_hash` on users, backfill, `POST /api/contacts/match`, rate limit |
| **Backend Discover** | Interest-aware discover comments (boost/filter by category/tags vs interest slugs) |
| **Web admin** | `/dashboard/interests` CRUD + nav |
| **Android** | OnboardingViewModel, fetch/save interests, ContactsReader + permission, match + follow, Discover chips |

## Out of Scope

- Full DiscoverScore algorithm (deferred)
- SMS contact invite / non-member invites
- Flutter `mobile/` legacy app
- Uploading raw phone/email to the server

## Design Principles

1. Admin owns the interest catalog; clients never hardcode the catalog as source of truth.
2. Contact matching uses one-way hashes only; pepper stays server-side.
3. Contact permission is skippable; denying must not block onboarding.
4. Discover personalization this phase = boost/filter by interest slug vs post category/tags.

## Success Criteria

- [ ] Admin can CRUD/reorder/deactivate interests; Android shows them
- [ ] Onboarding saves interests; Discover shows more relevant comments or topic filter works
- [ ] With contacts permission, matched Xilo members are suggested; Follow persists
- [ ] Deny/skip contacts does not block onboarding
- [ ] Raw contact phones/emails never leave the device

## Risks & Mitigations

| Risk | Impact | Mitigation |
|------|--------|------------|
| Hash rainbow / enumeration | Privacy | Server HMAC with `CONTACT_MATCH_PEPPER`; rate limit; max 500 ids/request |
| Empty interest catalog | Bad UX | Seed 18 diverse interests; admin can add more |
| Discover over-filtering | Empty feed | Soft boost + optional topic chip filter; fall back to recent |
