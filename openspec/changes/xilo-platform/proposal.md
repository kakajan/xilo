# Proposal: Xilo Blog Platform

**Status:** Proposed  
**Date:** 2026-05-05  
**Author:** Xilo Team

---

## Summary

Build **Xilo** — a modern, Telegram-inspired blogging platform with a web frontend (Next.js) and a native Android app (Kotlin/Jetpack Compose). The system is fully self-hosted with zero dependency on paid SaaS services, designed to scale to 1M+ concurrent users through a microservices architecture backed by Go, PostgreSQL, Redis, NATS, Meilisearch, and MinIO. The preserved `mobile/` Flutter project is legacy and out of scope for active product development.

---

## Motivation

- **Ownership**: Full control over data, infrastructure, and user experience without vendor lock-in.
- **Performance**: Telegram-like instant loading, real-time interactions, and buttery-smooth animations.
- **Scale**: Architecture designed from day one for high concurrency (1M users).
- **Cost**: Zero SaaS dependency — every component is open-source and self-hostable.
- **Cross-platform**: Unified experience across web and mobile with a single backend.

---

## Scope

### In Scope

| Domain | Deliverables |
|--------|-------------|
| **Backend** | Go/Fiber microservices: API Gateway, Auth, User, Post, Comment, Notification, Search, Media, Analytics, Chat |
| **Database** | PostgreSQL 16+ (primary), Redis (cache/pub/sub), Meilisearch (search), MinIO (object storage) |
| **Message Queue** | NATS for event-driven inter-service communication |
| **Web Frontend** | Next.js 15+ App Router, Tailwind CSS 4, shadcn/ui, Zustand, TanStack Query, Tiptap editor, Framer Motion |
| **Mobile App** | Native Android in `android/`: Kotlin, Jetpack Compose, Hilt, Room, Retrofit, OkHttp, Paging 3, WorkManager, DataStore, Keystore |
| **Real-time** | WebSocket with Redis Pub/Sub clustering for live comments, reactions, notifications, chat |
| **Auth** | JWT + Refresh Tokens, Argon2 hashing, OAuth2 (optional) |
| **SEO** | SSR/SSG/ISR, JSON-LD structured data, dynamic sitemap, Core Web Vitals targets |
| **Analytics** | Self-hosted (PostHog/Umami) with custom event tracking |
| **Monetization** | Subscriptions, premium posts, donations, ads, sponsored content, affiliate links |
| **DevOps** | Docker, Kubernetes, Traefik, Prometheus + Grafana + Loki, Sentry (self-hosted) |
| **Comment System** | Telegram-style chat bubbles, nested threading (max depth 4), emoji reactions, @mentions, real-time updates, markdown |
| **Discover Feed** | Comments displayed as tweet-like cards with ranking algorithm, trending, recommended, topic-based filtering |
| **Chat/Messaging** | Private real-time chat between users, typing indicators, read receipts, online presence, media sharing |
| **Feed System** | Home feed with hybrid fanout strategy, scoring algorithm (recency, engagement, author authority, personalization, following boost), Redis caching |

### User Roles

| Role | Permissions |
|------|-------------|
| **Admin** | Full platform control, user management, content moderation, system settings |
| **Verified Writer** | Create/publish posts, access analytics, manage own content, receive payments |
| **Regular User** | Read posts, comment, react, follow, chat, bookmark, discover |

### Out of Scope (Future)

- AI-powered content recommendations (Phase 5+)
- Collaborative editing (Phase 5+)
- Native desktop apps (Electron/Tauri)
- Video streaming / live broadcasting
- Marketplace / e-commerce integration

---

## Design Principles

1. **Fluid not rigid** — iterate on specs and implementation; no waterfall gates.
2. **Event-driven first** — every meaningful action emits an event via NATS.
3. **Cache aggressively** — multi-layer caching (CDN → Redis → ISR → Browser).
4. **Mobile-first UX** — design for mobile, enhance for desktop.
5. **Self-hosted everything** — no external SaaS dependencies.
6. **API-first** — mobile and web consume the same REST + WebSocket APIs.

---

## Success Criteria

- [ ] Web LCP < 2.5s, CLS < 0.1, TTFB < 200ms
- [ ] Mobile app launch < 1.5s cold start
- [ ] Real-time comment delivery < 500ms P95
- [ ] Horizontal scaling to handle 1M concurrent users
- [ ] 100% self-hosted deployable via `docker compose up` (dev) and Helm chart (prod)
- [ ] Full offline support in mobile app for reading cached posts

---

## Risks & Mitigations

| Risk | Impact | Mitigation |
|------|--------|------------|
| Microservice complexity early on | High dev overhead | Start with modular monolith, extract services in Phase 3 |
| Android + Go contract drift | Slower velocity | Shared OpenAPI/API contracts, Android integration tests, and `android-native-production` acceptance gates |
| Self-hosting operational burden | Maintenance cost | Comprehensive Docker Compose and Helm charts; runbooks |
| Real-time at scale | Infrastructure cost | Redis Pub/Sub clustering; horizontal WebSocket scaling |
| SEO with SPA-like interactions | Search ranking | Next.js SSR + ISR for all public pages; server-rendered metadata |
