# Tasks: Xilo Platform Implementation

**Legend:** 🔴 Critical path | 🟡 Important | 🟢 Nice-to-have

---

## Phase 1 — Core MVP (Weeks 1–8)

### 1.1 Project Scaffolding & Infrastructure

- [x] **T1.1.1** Create monorepo directory structure (`backend/`, `web/`, `mobile/`, `infra/`)
  - Acceptance: Directory tree matches design doc
- [x] **T1.1.2** Initialize Go module with Fiber dependency
  - Acceptance: `go mod tidy` succeeds, hello world endpoint runs
- [x] **T1.1.3** Initialize Next.js 15 project with TypeScript, Tailwind CSS 4, shadcn/ui
  - Acceptance: `npm run dev` starts, homepage renders
- [x] **T1.1.4** Initialize Flutter project with Clean Architecture folder structure
  - Acceptance: `flutter run` launches app on emulator
- [x] **T1.2.7** Create mobile login/register screens with Riverpod
  - Acceptance: Screens match web behavior; tokens stored in Hive
- [x] **T1.5.5** Create mobile comment screen with reply and nested view
  - Acceptance: Matches web behavior; smooth scroll to reply
- [x] **T1.6.5** Create mobile search screen with autocomplete dropdown
  - Acceptance: Matches web search behavior

---

## Phase 2 — Real-time & Engagement (Weeks 9–14)

### 2.1 WebSocket Infrastructure

- [x] **T2.1.1** Implement WebSocket server with gorilla/websocket in API Gateway
  - Acceptance: Client connects; ping/pong keepalive works
- [x] **T2.1.2** Implement Redis Pub/Sub for WebSocket clustering
  - Acceptance: Two API Gateway instances; message published on one broadcasts on both
- [x] **T2.1.3** Implement WebSocket auth (JWT via connection query param)
  - Acceptance: Unauthenticated connections rejected; valid JWT accepted
- [x] **T2.1.4** Create `useWebSocket` hook in Next.js with auto-reconnect, exponential backoff
  - Acceptance: Survives network drops; reconnects with backoff; event callbacks work

### 2.2 Real-time Comments

- [x] **T2.2.1** Add WebSocket broadcast on comment create/update/delete
  - Acceptance: New comment appears on all connected clients within 500ms
- [x] **T2.2.2** Update comment section to consume WebSocket events
  - Acceptance: Comments update live without page refresh; optimistic insertion

### 2.3 Reactions

- [x] **T2.3.1** Implement `reactions` table migration
  - Acceptance: Table created with unique constraint on (user, target, reaction)
- [x] **T2.3.2** Implement reaction toggle endpoint for posts and comments
  - Acceptance: Toggle adds/removes reaction; returns updated count
- [x] **T2.3.3** Add WebSocket broadcast for reaction events
  - Acceptance: Reaction count updates live on all clients
- [x] **T2.3.4** Create reaction bar component (emoji picker, animated toggles)
  - Acceptance: Click emoji → animation → count updates; optimistic update
- [ ] **T2.3.5** Add reaction bar to post detail page (sticky on mobile)
  - Acceptance: Bar is visible while reading; reactions update live

### 2.4 Notifications

- [x] **T2.4.1** Implement `notifications` table migration + NATS consumer
  - Acceptance: Events (comment reply, mention, follow) create notifications
- [x] **T2.4.2** Implement notification list endpoint with unread count
  - Acceptance: Returns paginated notifications; Redis-backed unread count
- [x] **T2.4.3** Add WebSocket push for new notifications
  - Acceptance: Bell icon updates in real-time; red badge shows unread count
- [x] **T2.4.4** Create notification center page and dropdown
  - Acceptance: List shows notifications; click marks as read; "Mark all read" works
- [ ] **T2.4.5** Create notification preferences UI
  - Acceptance: User can toggle per-type Web/Email preferences

### 2.5 Bookmarks & Follows

- [x] **T2.5.1** Implement bookmark toggle endpoint
  - Acceptance: Bookmark added/removed; list endpoint returns bookmarked posts
- [x] **T2.5.2** Implement follow/unfollow endpoint
  - Acceptance: Follow relationship created; follow event emits NATS notification
- [x] **T2.5.3** Create bookmarks page in web and mobile
  - Acceptance: Shows bookmarked posts list
- [x] **T2.5.4** Add follow button to author profile and post author card
  - Acceptance: Button toggles follow/unfollow; count updates

### 2.6 Mobile Real-time

- [x] **T2.6.1** Implement WebSocket manager in Flutter with reconnection
  - Acceptance: Connects on app start; reconnects on network change
- [x] **T2.6.2** Wire mobile comment screen to WebSocket events
  - Acceptance: Live comment updates in mobile app
- [x] **T2.6.3** Implement mobile notification with local push when in-app
  - Acceptance: In-app notification banner when new notification arrives

---

## Phase 3 — Scale & Microservices (Weeks 15–20)

### 3.1 Microservice Extraction

- [x] **T3.1.1** Extract Auth Service from monolith with gRPC interface
  - Acceptance: Auth endpoints respond via API Gateway → gRPC → Auth Service
- [x] **T3.1.2** Extract Post Service with gRPC interface
  - Acceptance: Post CRUD works via gRPC
- [x] **T3.1.3** Extract Comment Service with gRPC interface
  - Acceptance: Comments work via gRPC
- [x] **T3.1.4** Extract remaining services (Search, Media, Notification, Analytics)
  - Acceptance: All services independently deployable

### 3.2 NATS Integration

- [x] **T3.2.1** Implement NATS client wrapper (connect, publish, subscribe, JetStream)
  - Acceptance: Publish event; subscriber receives within 100ms
- [x] **T3.2.2** Wire `post_published` → Search Service reindex
  - Acceptance: Publishing a post triggers Meilisearch index update within 2s
- [x] **T3.2.3** Wire `comment_created` → Notification Service → WebSocket broadcast
  - Acceptance: End-to-end notification delivery under 500ms
- [x] **T3.2.4** Implement all events from Event Catalog (design.md §3)
  - Acceptance: Every event type flows from publisher to subscriber

### 3.3 Caching Layer

- [x] **T3.3.1** Implement Redis caching middleware for all GET endpoints
  - Acceptance: Cache hit returns data from Redis; cached responses include `X-Cache: HIT` header
- [x] **T3.3.2** Implement cache invalidation on mutations (write-through)
  - Acceptance: Updating a post invalidates its cache key; next GET fetches fresh data
- [x] **T3.3.3** Implement feed fan-out on write pattern
  - Acceptance: New post pushed to followers' feed caches in Redis
- [x] **T3.3.4** Implement ISR on-demand revalidation for post pages
  - Acceptance: Post update triggers Next.js revalidation; next request gets fresh SSR

### 3.4 Database Optimization

- [x] **T3.4.1** Add PostgreSQL connection pooling (pgbouncer or built-in)
  - Acceptance: Connection pool limits enforced; no connection exhaustion under load
- [ ] **T3.4.2** Implement read replicas for search/list queries
  - Acceptance: Read queries route to replica; write queries to primary
- [ ] **T3.4.3** Add missing indexes based on query patterns (from slow query log)
  - Acceptance: Slow queries (>100ms) eliminated; EXPLAIN ANALYZE confirms index usage
- [ ] **T3.4.4** Implement table partitioning for `analytics_events` (by month)
  - Acceptance: Monthly partitions auto-created; queries prune irrelevant partitions

### 3.5 Kubernetes Deployment

- [x] **T3.5.1** Write Dockerfiles (multi-stage) for all Go services
  - Acceptance: Images build; <20MB final size per service
- [x] **T3.5.2** Write Helm chart for Xilo platform
  - Acceptance: `helm install xilo ./infra/kubernetes/helm/xilo` deploys all services
- [x] **T3.5.3** Configure HPA for API Gateway and Post Service
  - Acceptance: Load test triggers scale-up; pods auto-scale down when idle
- [x] **T3.5.4** Configure Traefik Ingress with Let's Encrypt
  - Acceptance: HTTPS works; auto-cert renewal active
- [x] **T3.5.5** Write NetworkPolicy for inter-service isolation
  - Acceptance: API Gateway cannot directly access databases (only through services)

---

## Phase 4 — Growth & Monetization (Weeks 21–28)

### 4.1 SEO & Performance Optimization

- [x] **T4.1.1** Implement JSON-LD structured data for articles and blog
  - Acceptance: Google Rich Results Test validates markup
- [x] **T4.1.2** Implement dynamic sitemap (`/sitemap.xml`) with all published posts
  - Acceptance: Sitemap returns XML with correct `lastmod` and `changefreq`
- [x] **T4.1.3** Implement `robots.txt` and OpenSearch descriptor
  - Acceptance: Robots blocks `/api/` and `/dashboard/`; OpenSearch XML valid
- [x] **T4.1.4** Performance audit: Lighthouse 95+ Performance, 100 Accessibility, 100 SEO
  - Acceptance: Lighthouse report shows target scores for homepage and post page
- [x] **T4.1.5** Implement responsive images with `srcset` and AVIF support
  - Acceptance: Images deliver correct size per viewport; AVIF for capable browsers

### 4.2 Monetization

- [x] **T4.2.1** Implement subscription plans CRUD (admin)
  - Acceptance: Admin can create/edit/deactivate plans
- [x] **T4.2.2** Implement subscription purchase flow with payment integration
  - Acceptance: User subscribes → payment processed → subscription active → premium features unlocked
- [x] **T4.2.3** Implement premium post gating (preview for free users, full for subscribers)
  - Acceptance: Free user sees first 200 words + "Subscribe" CTA; subscriber sees full content
- [x] **T4.2.4** Implement donation flow with crypto wallet address configuration
  - Acceptance: Author sets wallet; reader donates; transaction recorded
- [x] **T4.2.5** Implement ad management (admin CRUD) and ad serving (client)
  - Acceptance: Admin uploads creative; ads appear in feed/sidebar; impressions/clicks tracked
- [x] **T4.2.6** Implement invoice PDF generation
  - Acceptance: Invoice generated on payment; downloadable from billing history

### 4.3 Analytics

- [x] **T4.3.1** Implement analytics event ingestion endpoint
  - Acceptance: Events ingested; stored in `analytics_events` table
- [x] **T4.3.2** Implement author dashboard with views, reads, reactions charts
  - Acceptance: Author sees 7d/30d/90d charts for their posts
- [x] **T4.3.3** Implement admin dashboard with DAU/WAU/MAU, revenue, top posts
  - Acceptance: Admin sees platform-wide metrics with time range selector
- [x] **T4.3.5** Implement custom event tracking (post_read, scroll_depth, search_performed)
  - Acceptance: Events fire and appear in analytics dashboard
- [x] **T4.5.1** Deploy Prometheus + Grafana with service dashboards
  - Acceptance: Dashboards show HTTP latency, error rates, DB connections, cache hit ratio
- [x] **T4.5.2** Configure Loki for log aggregation with structured JSON logging
  - Acceptance: Logs searchable in Grafana; correlation with metrics
- [x] **T4.5.3** Deploy self-hosted Sentry for error tracking
  - Acceptance: Unhandled errors appear in Sentry with stack traces
- [x] **T4.5.4** Configure alerting rules (error rate >1%, latency P95 >500ms, DB down)
  - Acceptance: Alerts fire to Slack/email/Discord

---

## Phase 5 — Future (Post-Launch)

- [x] **T5.1** AI-powered content recommendations (collaborative filtering)
- [x] **T5.2** Collaborative editing (CRDT-based, Yjs + Tiptap)
- [x] **T5.3** Newsletter system (email subscriptions, automated digests)
- [x] **T5.4** Community features (groups, forums)
- [x] **T5.5** Native desktop app (Tauri or Flutter desktop)
- [x] **T5.6** Multi-language/i18n support (RTL, translations)
- [x] **T5.7** White-label / custom domain support for authors
- [x] **T5.8** API for third-party integrations (OAuth2 provider)

---

## Task Summary

| Phase | Weeks | Tasks | Focus |
|-------|-------|-------|-------|
| Phase 1 | 1–8 | 38 | Core MVP: auth, posts, comments, search, media |
| Phase 2 | 9–14 | 18 | Real-time: WebSocket, live comments, reactions, notifications |
| Phase 3 | 15–20 | 17 | Scale: microservices, NATS, caching, K8s |
| Phase 4 | 21–28 | 24 | Growth: SEO, monetization, analytics, polish |
| Phase 5 | 29+ | 8 | Future: AI, collab, desktop, i18n |
| **Total** | | **105** | |
