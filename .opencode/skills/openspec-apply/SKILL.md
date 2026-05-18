---
name: openspec-apply
description: Use when the user asks to implement a feature, work through tasks, or build code for a change. Reads OpenSpec artifacts and implements tasks following Xilo conventions.
---

# OpenSpec Apply — Xilo

Implement tasks from an active OpenSpec change following Xilo project conventions.

## Workflow

1. **Identify the change**: Read `openspec/changes/` directory. If multiple, ask the user which to implement.
2. **Read all artifacts** before writing code:
   - `proposal.md` — understand the intent
   - `specs/` — know the acceptance criteria (GIVEN/WHEN/THEN scenarios)
   - `design.md` — follow architecture decisions, DB schemas, API contracts
   - `tasks.md` — work through incomplete tasks
3. **Implement tasks** in order from `tasks.md`:
   - Mark each task `[x]` when verified complete
   - Write tests alongside implementation
   - Follow relevant stack conventions (see below)

## Stack-Specific Implementation Guidelines

### Backend (Go/Fiber)
```
Layer structure (per service):
  cmd/<service>/main.go       — Entry point
  internal/<service>/
    handler/                   — HTTP/gRPC handlers
    service/                   — Business logic
    repository/                — Data access
    model/                     — Domain entities
    middleware/                 — Auth, logging, rate limit
```

- Run `go mod tidy` after adding dependencies
- Use `context.Context` for cancellation
- Log with structured logging (JSON)
- Export Prometheus metrics on `/metrics`
- Validate input before processing
- Handle errors with proper HTTP status codes

### Frontend (Next.js/React)
```
src/
  app/<route>/page.tsx         — Server Component (preferred)
  components/<domain>/         — Client Components when needed
  hooks/use-*.ts               — Custom hooks
  lib/api-client.ts            — API calls
  stores/*.ts                  — Zustand stores
```

- Prefer Server Components; `'use client'` only for interactivity
- Use `<Image>` from `next/image` for all images
- Generate metadata with `generateMetadata()`
- Use React Hook Form + Zod for forms
- Use TanStack Query `useQuery`/`useMutation` for data fetching
- Use Tailwind classes, NOT inline styles

### Mobile (Flutter/Dart)
```
lib/
  features/<feature>/
    data/                     — DataSources, RepositoriesImpl
    domain/                    — Entities, UseCases, Repository interfaces
    presentation/              — Pages, Providers, Widgets
  core/
    di/                        — GetIt registration
    network/                   — Dio + interceptors
    storage/                   — Hive boxes
    router/                    — GoRouter
```

- Use Freezed for models
- Use Riverpod for state
- Use Dio interceptors for auth token injection
- Offline-first: read from Hive, refresh from API
- Android: `minSdkVersion 23`, iOS: `minimumDeploymentTarget 15.0`

### Infrastructure
- Docker: multi-stage builds, non-root user, <100MB images
- K8s: use Helm chart from `infra/kubernetes/helm/xilo/`
- Add resource limits to all deployments
- Add Prometheus annotations for service discovery

## Task Completion Criteria

A task is complete when:
- [x] Code compiles / builds without errors
- [x] Unit tests pass
- [x] Acceptance criteria from spec are met
- [x] Linting passes
- [x] No hardcoded secrets or credentials
