---
name: openspec-propose
description: Use when the user asks to create a new feature proposal, plan a change, or add specifications. Creates OpenSpec change artifacts (proposal.md, specs/, design.md, tasks.md) following Xilo project conventions.
---

# OpenSpec Propose — Xilo

Create a new OpenSpec change folder with planning artifacts following the Xilo project conventions.

## Workflow

1. Read `requirements.md` and existing specs in `openspec/changes/xilo-platform/specs/` for context.
2. Read `openspec/changes/xilo-platform/design.md` for architecture patterns.
3. Create the change folder: `openspec/changes/<kebab-case-name>/`
4. Generate artifacts in dependency order:

### proposal.md
```markdown
# Proposal: <Change Name>

**Status:** Proposed
**Date:** YYYY-MM-DD

## Summary
One paragraph overview.

## Motivation
Why this change matters.

## Scope
| Domain | Deliverables |
|--------|-------------|

## Design Principles
1. ...
2. ...

## Success Criteria
- [ ] Criterion 1
- [ ] Criterion 2

## Risks & Mitigations
| Risk | Impact | Mitigation |
|------|--------|------------|
```

### specs/<domain>/spec.md
Delta specs describing added/modified/removed requirements:
```markdown
# Delta for <Domain>

## ADDED Requirements
### Requirement: <Name>
The system SHALL <behavior>.

#### Scenario: <Name>
- GIVEN <precondition>
- WHEN <action>
- THEN <expected outcome>
- AND <additional outcome>
```

### design.md
Technical approach, architecture decisions, data models, API contracts, file structure. Follow patterns from `openspec/changes/xilo-platform/design.md`.

### tasks.md
Implementation checklist with hierarchical numbering:
```markdown
## 1. <Phase Name>
- [ ] 1.1 <Task description>
  - Acceptance: <verifiable condition>
- [ ] 1.2 <Task description>
  - Acceptance: <verifiable condition>
```

## Xilo-Specific Rules

- **Backend** specs MUST reference: Go/Fiber, gRPC (internal), NATS (async), PostgreSQL, Redis
- **Frontend** specs MUST reference: Next.js 15 App Router, Tailwind CSS 4, shadcn/ui, Zustand, TanStack Query
- **Mobile** specs MUST reference: Flutter 3+, Clean Architecture, Riverpod, Dio, Hive
- **Infrastructure** specs MUST reference: Docker, Kubernetes, Traefik, Prometheus
- **API endpoints** follow `/api/<resource>` pattern with standard HTTP methods
- **Auth** always uses JWT + Refresh Token pattern
- **Caching** always multi-layer: CDN → Redis → ISR → Browser
- **Events** use NATS with `<resource>.<action>` naming
