# Tasks: Xilo Platform Implementation

**Legend:** 🔴 Critical path | 🟡 Important | 🟢 Nice-to-have

---

## Phase 1 — Core MVP (Weeks 1–8)

### 1.1 Project Scaffolding & Infrastructure

- [ ] **T1.1.1** Create monorepo directory structure (`backend/`, `web/`, `mobile/`, `infra/`)
  - Acceptance: Directory tree matches design doc
- [ ] **T1.1.2** Initialize Go module with Fiber dependency
  - Acceptance: `go mod tidy` succeeds, hello world endpoint runs
- [ ] **T1.1.3** Initialize Next.js 15 project with TypeScript, Tailwind CSS 4, shadcn/ui
  - Acceptance: `npm run dev` starts, homepage renders
- [ ] **T1.1.4** Initialize Flutter project with Clean Architecture folder structure
  - Acceptance: `flutter run` launches app on emulator
- [ ] **T1.1.5** Create `docker-compose.yml` with PostgreSQL, Redis, NATS, Meilisearch, MinIO
  - Acceptance: `docker compose up` starts all services healthy
- [ ] **T1.1.6** Set up CI/CD skeletons (GitHub Actions: lint, test, build for each stack)
  - Acceptance: PR checks run and pass

### 1.2 Authentication System

- [ ] **T1.2.1** Implement `users` table migration
  - Acceptance: Migration runs, indexes created
- [ ] **T1.2.2** Implement registration endpoint (`POST /api/auth/register`) with validation, Argon2 hashing, JWT generation
  - Acceptance: cURL test — register, receive tokens, verify JWT claims
- [ ] **T1.2.3** Implement login endpoint (`POST /api/auth/login`) with rate limiting
  - Acceptance: Successful login returns tokens; 6th attempt in 1min returns 429
- [ ] **T1.2.4** Implement token refresh with rotation and reuse detection
  - Acceptance: Refresh returns new tokens; reusing old refresh returns 401
- [ ] **T1.2.5** Implement auth middleware for Fiber (JWT validation, role checking)
  - Acceptance: Protected routes return 401 without token; 403 if wrong role
- [ ] **T1.2.6** Create web login/register pages with React Hook Form + Zod validation
  - Acceptance: Forms validate client-side, submit to API, redirect on success
- [ ] **T1.2.7** Create mobile login/register screens with Riverpod
  - Acceptance: Screens match web behavior; tokens stored in Hive
- [ ] **T1.2.8** Implement Zustand `useAuthStore` with token persistence, auto-refresh
  - Acceptance: App survives page refresh; tokens refresh silently before expiry

### 1.3 Post System (CRUD)

- [ ] **T1.3.1** Implement `posts` and `post_versions` table migrations
  - Acceptance: Tables created with indexes
- [ ] **T1.3.2** Implement create post endpoint (`POST /api/posts`) with slug generation, tag validation
  - Acceptance: Post saved as draft; slug auto-generated; tags stored as array
- [ ] **T1.3.3** Implement read post endpoint (`GET /api/posts/:slug`) with Redis caching
  - Acceptance: First request hits DB; subsequent requests hit cache (verified via logs)
- [ ] **T1.3.4** Implement update post endpoint (`PATCH /api/posts/:id`) with version history
  - Acceptance: Previous version saved in `post_versions`; author/editor can edit
- [ ] **T1.3.5** Implement delete post (soft delete) endpoint
  - Acceptance: Post status becomes `deleted`; public endpoint returns 404
- [ ] **T1.3.6** Implement list posts endpoint (`GET /api/posts`) with cursor pagination, filtering
  - Acceptance: Returns 10 posts per page; supports `?category=&tag=&author=&cursor=`
- [ ] **T1.3.7** Implement Tiptap editor component with toolbar, image upload, markdown shortcuts
  - Acceptance: Author can write formatted posts; images upload inline; auto-save to localStorage
- [ ] **T1.3.8** Create post detail page with SSR (Next.js)
  - Acceptance: Post renders server-side with full metadata; view source shows content
- [ ] **T1.3.9** Create homepage feed with infinite scroll
  - Acceptance: Posts load 10 at a time; scroll to bottom loads more; loading skeleton
- [ ] **T1.3.10** Create post card component (cover image, title, excerpt, author, meta)
  - Acceptance: Cards render in feed and search results; responsive layout
- [ ] **T1.3.11** Create author profile page (`/[username]`)
  - Acceptance: Shows author info, post list; follow button
- [ ] **T1.3.12** Create write/edit page with metadata sidebar (title, slug, tags, cover)
  - Acceptance: Full editor with publish/schedule/draft actions
- [ ] **T1.3.13** Implement mobile post detail screen with Hero animation
  - Acceptance: Post opens with cover image transition; content scrolls smoothly

### 1.4 Media System

- [ ] **T1.4.1** Configure MinIO bucket and IAM policy
  - Acceptance: Bucket created; upload/download works with SDK
- [ ] **T1.4.2** Implement image upload endpoint with resizing (thumbnail, small, medium, large, original → WebP)
  - Acceptance: Single upload returns URLs for all sizes; images accessible via URL
- [ ] **T1.4.3** Implement avatar upload with square crop and 256x256 resize
  - Acceptance: Avatar updates immediately; old variants cleaned up
- [ ] **T1.4.4** Wire Tiptap image extension to media upload API
  - Acceptance: Drag-drop image → uploads → inserts at cursor position
- [ ] **T1.4.5** Implement Next.js `<Image>` with blur placeholder for all post images
  - Acceptance: Images lazy-load with blur-up effect; CLS = 0 per Lighthouse

### 1.5 Comments (Basic)

- [ ] **T1.5.1** Implement `comments` table migration
  - Acceptance: Table created with parent_id, root_id, depth columns
- [ ] **T1.5.2** Implement create comment endpoint (`POST /api/posts/:postId/comments`)
  - Acceptance: Comment saved; nested replies work up to depth 4
- [ ] **T1.5.3** Implement list comments endpoint with cursor pagination, sorting
  - Acceptance: Returns nested comment tree; sorts by newest/oldest/popular
- [ ] **T1.5.4** Create comment section component (threaded view, collapse, form)
  - Acceptance: Comments render nested; collapse toggles children; reply form at each level
- [ ] **T1.5.5** Create mobile comment screen with reply and nested view
  - Acceptance: Matches web behavior; smooth scroll to reply

### 1.6 Search (Phase 1)

- [ ] **T1.6.1** Configure Meilisearch index with schema (searchable, filterable, sortable attributes)
  - Acceptance: Index created; test document searchable
- [ ] **T1.6.2** Implement search endpoint (`GET /api/search/posts`) with facet filtering
  - Acceptance: Search returns relevant results; typo tolerance works
- [ ] **T1.6.3** Implement autocomplete endpoint (`GET /api/search/suggest`)
  - Acceptance: Returns top 5 suggestions for partial query
- [ ] **T1.6.4** Create search page UI with input, filter bar, results grid
  - Acceptance: Search works in real-time (debounced); filters narrow results
- [ ] **T1.6.5** Create mobile search screen with autocomplete dropdown
  - Acceptance: Matches web search behavior

---

## Phase 2 — Real-time & Engagement (Weeks 9–14)

### 2.1 WebSocket Infrastructure

- [ ] **T2.1.1** Implement WebSocket server with gorilla/websocket in API Gateway
  - Acceptance: Client connects; ping/pong keepalive works
- [ ] **T2.1.2** Implement Redis Pub/Sub for WebSocket clustering
  - Acceptance: Two API Gateway instances; message published on one broadcasts on both
- [ ] **T2.1.3** Implement WebSocket auth (JWT via connection query param)
  - Acceptance: Unauthenticated connections rejected; valid JWT accepted
- [ ] **T2.1.4** Create `useWebSocket` hook in Next.js with auto-reconnect, exponential backoff
  - Acceptance: Survives network drops; reconnects with backoff; event callbacks work

### 2.2 Real-time Comments

- [ ] **T2.2.1** Add WebSocket broadcast on comment create/update/delete
  - Acceptance: New comment appears on all connected clients within 500ms
- [ ] **T2.2.2** Update comment section to consume WebSocket events
  - Acceptance: Comments update live without page refresh; optimistic insertion

### 2.3 Reactions

- [ ] **T2.3.1** Implement `reactions` table migration
  - Acceptance: Table created with unique constraint on (user, target, reaction)
- [ ] **T2.3.2** Implement reaction toggle endpoint for posts and comments
  - Acceptance: Toggle adds/removes reaction; returns updated count
- [ ] **T2.3.3** Add WebSocket broadcast for reaction events
  - Acceptance: Reaction count updates live on all clients
- [ ] **T2.3.4** Create reaction bar component (emoji picker, animated toggles)
  - Acceptance: Click emoji → animation → count updates; optimistic update
- [ ] **T2.3.5** Add reaction bar to post detail page (sticky on mobile)
  - Acceptance: Bar is visible while reading; reactions update live

### 2.4 Notifications

- [ ] **T2.4.1** Implement `notifications` table migration + NATS consumer
  - Acceptance: Events (comment reply, mention, follow) create notifications
- [ ] **T2.4.2** Implement notification list endpoint with unread count
  - Acceptance: Returns paginated notifications; Redis-backed unread count
- [ ] **T2.4.3** Add WebSocket push for new notifications
  - Acceptance: Bell icon updates in real-time; red badge shows unread count
- [ ] **T2.4.4** Create notification center page and dropdown
  - Acceptance: List shows notifications; click marks as read; "Mark all read" works
- [ ] **T2.4.5** Create notification preferences UI
  - Acceptance: User can toggle per-type Web/Email preferences

### 2.5 Bookmarks & Follows

- [ ] **T2.5.1** Implement bookmark toggle endpoint
  - Acceptance: Bookmark added/removed; list endpoint returns bookmarked posts
- [ ] **T2.5.2** Implement follow/unfollow endpoint
  - Acceptance: Follow relationship created; follow event emits NATS notification
- [ ] **T2.5.3** Create bookmarks page in web and mobile
  - Acceptance: Shows bookmarked posts list
- [ ] **T2.5.4** Add follow button to author profile and post author card
  - Acceptance: Button toggles follow/unfollow; count updates

### 2.6 Mobile Real-time

- [ ] **T2.6.1** Implement WebSocket manager in Flutter with reconnection
  - Acceptance: Connects on app start; reconnects on network change
- [ ] **T2.6.2** Wire mobile comment screen to WebSocket events
  - Acceptance: Live comment updates in mobile app
- [ ] **T2.6.3** Implement mobile notification with local push when in-app
  - Acceptance: In-app notification banner when new notification arrives

---

## Phase 3 — Scale & Microservices (Weeks 15–20)

### 3.1 Microservice Extraction

- [ ] **T3.1.1** Extract Auth Service from monolith with gRPC interface
  - Acceptance: Auth endpoints respond via API Gateway → gRPC → Auth Service
- [ ] **T3.1.2** Extract Post Service with gRPC interface
  - Acceptance: Post CRUD works via gRPC
- [ ] **T3.1.3** Extract Comment Service with gRPC interface
  - Acceptance: Comments work via gRPC
- [ ] **T3.1.4** Extract remaining services (Search, Media, Notification, Analytics)
  - Acceptance: All services independently deployable

### 3.2 NATS Integration

- [ ] **T3.2.1** Implement NATS client wrapper (connect, publish, subscribe, JetStream)
  - Acceptance: Publish event; subscriber receives within 100ms
- [ ] **T3.2.2** Wire `post_published` → Search Service reindex
  - Acceptance: Publishing a post triggers Meilisearch index update within 2s
- [ ] **T3.2.3** Wire `comment_created` → Notification Service → WebSocket broadcast
  - Acceptance: End-to-end notification delivery under 500ms
- [ ] **T3.2.4** Implement all events from Event Catalog (design.md §3)
  - Acceptance: Every event type flows from publisher to subscriber

### 3.3 Caching Layer

- [ ] **T3.3.1** Implement Redis caching middleware for all GET endpoints
  - Acceptance: Cache hit returns data from Redis; cached responses include `X-Cache: HIT` header
- [ ] **T3.3.2** Implement cache invalidation on mutations (write-through)
  - Acceptance: Updating a post invalidates its cache key; next GET fetches fresh data
- [ ] **T3.3.3** Implement feed fan-out on write pattern
  - Acceptance: New post pushed to followers' feed caches in Redis
- [ ] **T3.3.4** Implement ISR on-demand revalidation for post pages
  - Acceptance: Post update triggers Next.js revalidation; next request gets fresh SSR

### 3.4 Database Optimization

- [ ] **T3.4.1** Add PostgreSQL connection pooling (pgbouncer or built-in)
  - Acceptance: Connection pool limits enforced; no connection exhaustion under load
- [ ] **T3.4.2** Implement read replicas for search/list queries
  - Acceptance: Read queries route to replica; write queries to primary
- [ ] **T3.4.3** Add missing indexes based on query patterns (from slow query log)
  - Acceptance: Slow queries (>100ms) eliminated; EXPLAIN ANALYZE confirms index usage
- [ ] **T3.4.4** Implement table partitioning for `analytics_events` (by month)
  - Acceptance: Monthly partitions auto-created; queries prune irrelevant partitions

### 3.5 Kubernetes Deployment

- [ ] **T3.5.1** Write Dockerfiles (multi-stage) for all Go services
  - Acceptance: Images build; <20MB final size per service
- [ ] **T3.5.2** Write Helm chart for Xilo platform
  - Acceptance: `helm install xilo ./infra/kubernetes/helm/xilo` deploys all services
- [ ] **T3.5.3** Configure HPA for API Gateway and Post Service
  - Acceptance: Load test triggers scale-up; pods auto-scale down when idle
- [ ] **T3.5.4** Configure Traefik Ingress with Let's Encrypt
  - Acceptance: HTTPS works; auto-cert renewal active
- [ ] **T3.5.5** Write NetworkPolicy for inter-service isolation
  - Acceptance: API Gateway cannot directly access databases (only through services)

---

## Phase 4 — Growth & Monetization (Weeks 21–28)

### 4.1 SEO & Performance Optimization

- [ ] **T4.1.1** Implement JSON-LD structured data for articles and blog
  - Acceptance: Google Rich Results Test validates markup
- [ ] **T4.1.2** Implement dynamic sitemap (`/sitemap.xml`) with all published posts
  - Acceptance: Sitemap returns XML with correct `lastmod` and `changefreq`
- [ ] **T4.1.3** Implement `robots.txt` and OpenSearch descriptor
  - Acceptance: Robots blocks `/api/` and `/dashboard/`; OpenSearch XML valid
- [ ] **T4.1.4** Performance audit: Lighthouse 95+ Performance, 100 Accessibility, 100 SEO
  - Acceptance: Lighthouse report shows target scores for homepage and post page
- [ ] **T4.1.5** Implement responsive images with `srcset` and AVIF support
  - Acceptance: Images deliver correct size per viewport; AVIF for capable browsers

### 4.2 Monetization

- [ ] **T4.2.1** Implement subscription plans CRUD (admin)
  - Acceptance: Admin can create/edit/deactivate plans
- [ ] **T4.2.2** Implement subscription purchase flow with payment integration
  - Acceptance: User subscribes → payment processed → subscription active → premium features unlocked
- [ ] **T4.2.3** Implement premium post gating (preview for free users, full for subscribers)
  - Acceptance: Free user sees first 200 words + "Subscribe" CTA; subscriber sees full content
- [ ] **T4.2.4** Implement donation flow with crypto wallet address configuration
  - Acceptance: Author sets wallet; reader donates; transaction recorded
- [ ] **T4.2.5** Implement ad management (admin CRUD) and ad serving (client)
  - Acceptance: Admin uploads creative; ads appear in feed/sidebar; impressions/clicks tracked
- [ ] **T4.2.6** Implement invoice PDF generation
  - Acceptance: Invoice generated on payment; downloadable from billing history

### 4.3 Analytics

- [ ] **T4.3.1** Implement analytics event ingestion endpoint
  - Acceptance: Events ingested; stored in `analytics_events` table
- [ ] **T4.3.2** Implement author dashboard with views, reads, reactions charts
  - Acceptance: Author sees 7d/30d/90d charts for their posts
- [ ] **T4.3.3** Implement admin dashboard with DAU/WAU/MAU, revenue, top posts
  - Acceptance: Admin sees platform-wide metrics with time range selector
- [ ] **T4.3.4** Integrate PostHog/Umami (self-hosted) for session recording (optional)
  - Acceptance: Self-hosted analytics deployed; page views tracked
- [ ] **T4.3.5** Implement custom event tracking (post_read, scroll_depth, search_performed)
  - Acceptance: Events fire and appear in analytics dashboard

### 4.4 Mobile App Polish

- [ ] **T4.4.1** Implement offline reading (cache last 50 posts in Hive)
  - Acceptance: Posts readable without internet; stale data indicator shown
- [ ] **T4.4.2** Implement push notifications via FCM (or self-hosted alternative)
  - Acceptance: Notification appears when app is in background
- [ ] **T4.4.3** Implement shimmer loading states for all list views
  - Acceptance: Every list shows shimmer skeleton during load
- [ ] **T4.4.4** Implement pull-to-refresh and infinite scroll everywhere
  - Acceptance: Pull down refreshes; scroll to bottom loads more
- [ ] **T4.4.5** Performance: cold start <1.5s, 60fps scrolling
  - Acceptance: Profiler confirms; no jank in DevTools timeline
- [ ] **T4.4.6** Prepare app store listings (screenshots, description, privacy policy)
  - Acceptance: App ready for App Store and Google Play submission

### 4.5 Monitoring & Observability

- [ ] **T4.5.1** Deploy Prometheus + Grafana with service dashboards
  - Acceptance: Dashboards show HTTP latency, error rates, DB connections, cache hit ratio
- [ ] **T4.5.2** Configure Loki for log aggregation with structured JSON logging
  - Acceptance: Logs searchable in Grafana; correlation with metrics
- [ ] **T4.5.3** Deploy self-hosted Sentry for error tracking
  - Acceptance: Unhandled errors appear in Sentry with stack traces
- [ ] **T4.5.4** Configure alerting rules (error rate >1%, latency P95 >500ms, DB down)
  - Acceptance: Alerts fire to Slack/email/Discord

---

## Phase 5 — Future (Post-Launch)

- [ ] **T5.1** AI-powered content recommendations (collaborative filtering)
- [ ] **T5.2** Collaborative editing (CRDT-based, Yjs + Tiptap)
- [ ] **T5.3** Newsletter system (email subscriptions, automated digests)
- [ ] **T5.4** Community features (groups, forums)
- [ ] **T5.5** Native desktop app (Tauri or Flutter desktop)
- [ ] **T5.6** Multi-language/i18n support (RTL, translations)
- [ ] **T5.7** White-label / custom domain support for authors
- [ ] **T5.8** API for third-party integrations (OAuth2 provider)

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
