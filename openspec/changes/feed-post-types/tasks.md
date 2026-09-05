## 1. Backend
- [ ] 1.1 Add `post_type` column and DTO validation
  - Acceptance: create/list/detail include `post_type`; unknown types 400
- [ ] 1.2 Media ids for photo/video; `link_url` for link
  - Acceptance: documented in OpenAPI / handler tests

## 2. Web
- [ ] 2.1 Composer type sheet and typed fields
  - Acceptance: cannot submit a type the API rejects
- [ ] 2.2 Feed cards per type
  - Acceptance: carousel / player / link preview

## 3. Android
- [ ] 3.1 FAB type sheet mapped to real API fields
  - Acceptance: no client-only fake types
- [ ] 3.2 Typed composer + cards
  - Acceptance: micro/photo/video/link after backend ships
