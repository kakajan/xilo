# Tasks: Onboarding Interests + Contact Sync

## 0. OpenSpec

- [x] 0.1 Create `openspec/changes/onboarding-interests-contacts/` (proposal, design, tasks, delta specs)

## 1. Backend interests

- [x] 1.1 Migration `000020_interests` + seed ~18 interests (fa+en labels)
- [x] 1.2 Interest repository + public `GET /api/interests`
- [x] 1.3 `GET/PUT /api/users/me/interests`
- [x] 1.4 Admin CRUD + reorder `/api/admin/interests`
- [x] 1.5 Wire api-gateway routes
- [x] 1.6 Unit tests for interest handlers/repo

## 2. Backend contacts

- [x] 2.1 Migration `000021_contact_hashes` + backfill phone_hash/email_hash
- [x] 2.2 Hash util (normalize + HMAC); update on register/profile phone/email change
- [x] 2.3 `POST /api/contacts/match` (JWT, rate limit, max 500)
- [x] 2.4 Wire gateway; unit tests for hash + match

## 3. Backend Discover

- [x] 3.1 `GET /api/discover/comments` with interest boost/filter
- [x] 3.2 Wire gateway; unit/integration test for boost ordering

## 4. Web admin

- [x] 4.1 `/dashboard/interests` page (CRUD, reorder, deactivate)
- [x] 4.2 Nav link in `dashboard/layout.tsx`

## 5. Android onboarding

- [x] 5.1 API DTOs + XiloApiService endpoints
- [x] 5.2 OnboardingViewModel: fetch/save interests
- [x] 5.3 ContactsReader + READ_CONTACTS + match + skippable UX
- [x] 5.4 Real follow on suggestions step
- [x] 5.5 Replace mesh/sync jargon strings (fa+en+)

## 6. Android Discover

- [x] 6.1 Topic chips + personalized refresh via discover API

## 7. Verify

- [x] 7.1 `go test` relevant packages (`go test ./...` — all ok)
- [x] 7.2 Android: `app-debug.apk` produced; `compileDebugKotlin` ok; emulator install attempted (concurrent Gradle `--stop` contention may interrupt full rebuilds)
- [x] 7.3 Check off completed tasks above

## 8. Contacts hub (followings + sync badge)

- [x] 8.1 Migration `000022_contact_matches` + upsert on `POST /api/contacts/match`
- [x] 8.2 `GET /api/contacts` + handler tests
- [x] 8.3 Web `/contacts` page, nav, i18n, chat/profile actions
- [x] 8.4 Android `ContactsScreen` + sync-again + nav from Messages
- [x] 8.5 OpenSpec delta requirements for hub
