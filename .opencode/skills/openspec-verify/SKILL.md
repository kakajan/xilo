---
name: openspec-verify
description: Use when the user asks to verify, review, or validate implementation against specs. Checks completeness, correctness, and coherence of implementation vs OpenSpec artifacts.
---

# OpenSpec Verify — Xilo

Verify that implementation matches OpenSpec artifacts for a given change.

## Verification Dimensions

### 1. Completeness
- Are all tasks in `tasks.md` checked `[x]`?
- Is every requirement from `specs/` implemented?
- Are all scenarios (GIVEN/WHEN/THEN) covered?

### 2. Correctness
- Does implementation match the spec intent?
- Are edge cases from scenarios handled?
- Do error states match spec definitions?
- Are validation rules enforced?

### 3. Coherence
- Do design decisions from `design.md` reflect in code?
- Are naming conventions consistent?
- Do API contracts match between backend and frontend?
- Are events published/subscribed as catalogued?

## Verification Checklist

```
[ ] Tasks: All checkboxes marked in tasks.md
[ ] Specs: Every requirement has corresponding code
[ ] Design: Architecture matches design.md
[ ] Tests: Scenarios have test coverage
[ ] Linting: All linters pass (golangci-lint, eslint, dart analyze)
[ ] Build: All services compile without errors
[ ] Secrets: No hardcoded credentials
[ ] API: Endpoints match spec paths and methods
[ ] Events: NATS events match event catalog
[ ] Database: Schema matches design.md table definitions
[ ] Documentation: AGENTS.md updated if conventions changed
```

## Reporting

Report findings as:

| Level | Indicator | Meaning |
|-------|-----------|---------|
| CRITICAL | ❌ | Missing requirement, wrong implementation, security issue |
| WARNING | ⚠️ | Deviation from design, missing test, style inconsistency |
| SUGGESTION | 💡 | Improvement opportunity, optimization |

## Xilo-Specific Verification Points

- **Auth**: JWT expiration, refresh token rotation, Argon2 hashing, rate limiting
- **Posts**: Cursor pagination, Redis caching, version history, soft delete
- **Comments**: Max depth 4, WebSocket broadcast, reaction toggle
- **Search**: Meilisearch index updates via NATS, typo tolerance
- **SEO**: SSR for public pages, JSON-LD structured data, robots.txt, sitemap
- **Performance**: Next.js `<Image>`, lazy loading, multi-layer caching
- **Infrastructure**: Non-root containers, resource limits, health checks
