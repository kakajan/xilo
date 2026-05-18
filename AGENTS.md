# Xilo — AI Agent Guide

## Project Overview

Xilo is a self-hosted, Telegram-inspired modern blog platform with:
- **Backend**: Go 1.22+ with Fiber v2, Clean Architecture, microservices, pluggable storage drivers (MinIO default, S3 optional)
- **Web Frontend**: Next.js 15+ (App Router), React 19, TypeScript, Tailwind CSS 4, shadcn/ui
- **Mobile App**: Flutter 3+, Dart 3+, Clean Architecture, Riverpod
- **Infrastructure**: Docker, Kubernetes, PostgreSQL, Redis, NATS, Meilisearch, MinIO (self-hosted), S3-compatible cloud storage (optional)
- **Monorepo**: `backend/`, `web/`, `mobile/`, `infra/`

## OpenSpec Workflow

This project uses [OpenSpec](https://github.com/Fission-AI/OpenSpec) for spec-driven development. All planning artifacts live under `openspec/`.

### Directory Structure

```
openspec/
├── specs/                    # Source of truth — current system behavior
│   └── <domain>/
│       └── spec.md           # Requirements + scenarios (GIVEN/WHEN/THEN)
└── changes/
    ├── xilo-platform/        # Active change: initial platform build
    │   ├── proposal.md       # Why this change, scope, risks
    │   ├── design.md         # Architecture, data models, API contracts
    │   ├── tasks.md          # Implementation checklist (phases + checkboxes)
    │   └── specs/            # Delta specs for this change
    │       ├── auth-spec.md
    │       ├── post-spec.md
    │       ├── comment-spec.md
    │       ├── search-spec.md
    │       ├── notification-spec.md
    │       ├── media-spec.md
    │       ├── analytics-spec.md
    │       ├── monetization-spec.md
    │       ├── seo-spec.md
    │       ├── web-frontend-spec.md
    │       ├── mobile-app-spec.md
    │       └── infrastructure-spec.md
    └── archive/              # Completed changes (date-prefixed)
```

### How to Use OpenSpec in This Project

1. **Read specs before coding.** Always check `openspec/specs/` for existing requirements relevant to the task.
2. **Follow tasks.md.** Implementation tasks are in `openspec/changes/<name>/tasks.md`. Check them off as completed.
3. **Check design.md.** Architecture decisions, DB schemas, and API contracts are in `design.md`.
4. **Create new changes** for significant features using `/opsx:propose` or by creating change folders manually.
5. **Spec format**: Requirements use SHALL/MUST/SHOULD. Scenarios use GIVEN/WHEN/THEN/AND.

### Available Skills

| Skill | Purpose |
|-------|---------|
| `openspec-propose` | Create new change proposals following Xilo conventions |
| `openspec-apply` | Implement tasks from an OpenSpec change |
| `openspec-verify` | Verify implementation matches specs |
| `xilo-backend` | Go/Fiber backend development conventions |
| `xilo-frontend` | Next.js/React frontend development conventions |
| `xilo-mobile` | Flutter/Dart mobile development conventions |
| `xilo-infra` | Docker/K8s infrastructure conventions |

## Development Conventions

### General

- **Language**: All code comments in English. UI text in Persian (Farsi) or English (TBD).
- **Testing**: Every service MUST have unit tests. Integration tests for critical paths.
- **Linting**: `golangci-lint` (Go), ESLint (TS/TSX), `dart analyze` (Flutter).
- **Commits**: Conventional commits — `type(scope): message`. Example: `feat(auth): add JWT refresh token rotation`.
- **Secrets**: NEVER hardcode secrets. Use environment variables / Kubernetes Secrets.

### Backend (Go)

- Framework: Fiber v2
- Architecture: Clean Architecture + DDD
- Service communication: gRPC (internal), NATS (async events)
- Database: PostgreSQL 16+, Redis 7+, Meilisearch, MinIO
- File naming: lowercase with underscores (`auth_service.go`)
- Package naming: short, lowercase, no underscores
- Error handling: Always return structured errors, never panic in HTTP handlers
- Migrations: Use `golang-migrate` or similar. Store in `backend/migrations/`

### Frontend (Next.js)

- Framework: Next.js 15 App Router, React 19, TypeScript
- Styling: Tailwind CSS 4, shadcn/ui components
- State: Zustand stores (client), TanStack Query (server cache)
- Forms: React Hook Form + Zod
- Editor: Tiptap (rich text)
- Animation: Framer Motion
- Components: Server Components by default, `'use client'` only when needed
- File naming: kebab-case (`post-card.tsx`), hooks: `use-*.ts`
- Routes: App Router file conventions (`page.tsx`, `layout.tsx`, `loading.tsx`)

### Mobile (Flutter)

- Architecture: Clean Architecture (presentation → domain → data)
- State: Riverpod 2.x
- DI: GetIt + Injectable
- HTTP: Dio + Retrofit
- Local: Hive
- Routes: GoRouter
- Offline: Cache last 50 posts in Hive

### Infrastructure

- Dev: `docker compose up` from `infra/` directory
- Prod: Kubernetes with Helm chart
- Monitoring: Prometheus + Grafana + Loki
- CI/CD: GitHub Actions (`.github/workflows/`)

## Tech Stack Quick Reference

```
Backend:    Go 1.22+  → Fiber v2  → gRPC + NATS  → PostgreSQL 16 / Redis 7
Web:        Next.js 15 → React 19 → Tailwind 4   → shadcn/ui / Zustand
Mobile:     Flutter 3  → Dart 3   → Riverpod      → Dio / Hive
Search:     Meilisearch
Storage:    MinIO (self-hosted, default) / S3 (cloud, swappable via driver)
Queue:      NATS
Cache:      Redis
DB:         PostgreSQL 16+
Proxy:      Traefik / NGINX
Monitor:    Prometheus + Grafana + Loki
```

## Task Triage

When asked to implement something:

1. Check `openspec/changes/xilo-platform/tasks.md` for the relevant phase.
2. Read the corresponding spec in `openspec/changes/xilo-platform/specs/`.
3. Read `openspec/changes/xilo-platform/design.md` for architecture decisions.
4. Follow the conventions listed above for the relevant stack.
5. Write tests alongside implementation.
6. Mark tasks as `[x]` when complete.
