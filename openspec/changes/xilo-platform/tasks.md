# Tasks: Xilo Platform Implementation

**Legend:** 🔴 Critical path | 🟡 Important | 🟢 Nice-to-have

---

## Phase 1 — Core MVP (Weeks 1–8)

### 1.1 Project Scaffolding & Infrastructure

- [ ] **T1.1.1** Validate active monorepo directory structure (`backend/`, `web/`, `android/`, `infra/`)
  - Acceptance: `ANP-1.1` verifies the in-place Android project; the legacy `mobile/` tree is preserved but not an active target.
- [x] **T1.1.2** Initialize Go module with Fiber dependency
  - Acceptance: `go mod tidy` succeeds, hello world endpoint runs
- [x] **T1.1.3** Initialize Next.js 15 project with TypeScript, Tailwind CSS 4, shadcn/ui
  - Acceptance: `npm run dev` starts, homepage renders
- [ ] **LEGACY-T1.1.4** Former Flutter initialization record — out of scope
  - Historical only; it is not Android completion evidence. See `android-native-production` Phase 1.
- [ ] **LEGACY-T1.2.7** Former Flutter auth-screen record — out of scope
  - Historical only; Android auth is `ANP-2.1`.
- [ ] **LEGACY-T1.5.5** Former Flutter comment-screen record — out of scope
  - Historical only; Android engagement work is `ANP-2.4`.
- [ ] **LEGACY-T1.6.5** Former Flutter search-screen record — out of scope
  - Historical only; Android feed/search work is `ANP-2.2`.

### 1.7 Visual Design System

- [x] **T1.7.1** Implement color palette (light/dark) with design tokens
  - Acceptance: All colors match ui-ux-spec (§2) — primary, backgrounds, text, semantic, bubble colors
- [x] **T1.7.2** Configure typography (Inter + Vazirmatn fonts)
  - Acceptance: Fonts load correctly; type scale matches spec (§3); RTL text renders properly
- [x] **T1.7.3** Implement spacing scale and border radius tokens
  - Acceptance: 4px grid spacing (§4); border radius tokens (§5) available in Tailwind config
- [ ] **T1.7.4** Configure shadow tokens
  - Acceptance: All shadow levels (§6) available as utility classes
- [ ] **T1.7.5** Set up responsive breakpoints
  - Acceptance: Breakpoints (§7) configured; layout behavior matches spec per breakpoint
- [x] **T1.7.6** Implement dark mode with system preference detection
  - Acceptance: Respects `prefers-color-scheme`; manual toggle persists; smooth 200ms transition
- [ ] **T1.7.7** Create skeleton loading components
  - Acceptance: Shimmer animation (§8.9) works for avatar, post, comment, stats skeletons
- [ ] **T1.7.8** Create empty state components
  - Acceptance: Illustration + title + description + CTA pattern (§8.10) reusable
- [ ] **T1.7.9** Create error state components
  - Acceptance: Error icon + message + retry pattern (§8.11) reusable
- [ ] **T1.7.10** Create toast/snackbar component
  - Acceptance: 4 types (success, error, warning, info); correct position per device (§8.12)
- [ ] **T1.7.11** Create modal/dialog component
  - Acceptance: Bottom sheet (mobile), center modal (desktop); correct animations (§8.13)
- [ ] **T1.7.12** Create dropdown/popover component
  - Acceptance: Fade + scale animation; keyboard accessible (§8.14)
- [ ] **T1.7.13** Implement animation system (CSS + Framer Motion)
  - Acceptance: Duration/easing tokens (§9.1-9.2); bubble entrance, like, thread expand animations work
- [ ] **T1.7.14** Implement focus states and accessibility
  - Acceptance: All interactive elements have visible focus (§11.2); color contrast passes AA (§11.1)
- [ ] **T1.7.15** Create profile header component (web + native Android)
  - Acceptance: Android proof is tracked by `ANP-2.2`; historical Flutter work is not completion evidence.
- [ ] **T1.7.16** Create post card component (web + native Android)
  - Acceptance: Android proof is tracked by `ANP-2.2`; historical Flutter work is not completion evidence.
- [x] **T1.7.17** Create button component variants
  - Acceptance: Primary, secondary, ghost, danger (§8.1) with correct states
- [ ] **T1.7.18** Create input field component
  - Acceptance: Default, focused, error, disabled states (§8.2); 44px touch-friendly height

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
- [ ] **T2.2.3** Implement Telegram-style comment bubble UI (web)
  - Acceptance: Bubbles with correct colors (own: #E8F5FE, others: #F7F9FA), 14-16px radius, entrance animation (§8.5)
- [ ] **T2.2.4** Implement Telegram-style comment bubble UI (mobile)
  - Acceptance: Matches web; swipe to reply/like; long-press menu (§10.1)
- [ ] **T2.2.5** Implement thread visualization with indentation and collapse
  - Acceptance: Twitter-style avatar-column thread line; max 2 visible levels relative to focus; "N پاسخ" drill-down for deeper replies
- [ ] **T2.2.6** Implement comment composer with mentions/hashtags autocomplete
  - Acceptance: @username and #hashtag suggestions while typing; image attachment; draft autosave
- [ ] **T2.2.7** Implement comment sorting UI (newest, oldest, most reacted, most replied)
  - Acceptance: Sort dropdown works; selection persists per session

### 2.2b Post hashtags (social)

- [x] **T2.2b.1** Backend hashtag extract/normalize + merge into `posts.tags` on Create/Update
  - Acceptance: `#خبر` / `#Xilo_App` stored normalized; URL fragments ignored; max 10; fixtures in `testdata/hashtags/`
- [x] **T2.2b.2** Tag suggest/trending APIs (`GET /api/tags/suggest`, `GET /api/tags/trending`)
  - Acceptance: Prefix autocomplete and 30-day trending counts from published posts
- [x] **T2.2b.3** Web: TipTap highlight + autocomplete, linkify in post body, `/tag/[slug]` feed
  - Acceptance: Inline `#tag` links to tag page; metadata tags merge with extracted hashtags
- [x] **T2.2b.4** Android: extract/send tags, HashtagAwareText, CreatePost suggestions, TagFeed screen
  - Acceptance: Tap hashtag opens TagFeed filtered by `GET /api/posts?tag=`

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

> **v1 delivery note:** Realtime uses Redis Pub/Sub + WebSocket (`BroadcastToUser`). NATS consumer/outbox is deferred (see `backend/pkg/realtime/README.md`). Email digest / SSE / Web Push are out of v1 scope.

- [x] **T2.4.1** Implement `notifications` table migration + notification producers (Redis/WS delivery; NATS deferred)
  - Acceptance: Events (comment reply, follow, post published, new message) create notifications and push via WS
- [x] **T2.4.2** Implement notification list endpoint with unread count
  - Acceptance: Returns paginated notifications; Redis-backed unread count
- [x] **T2.4.3** Add WebSocket push for new notifications
  - Acceptance: Bell icon updates in real-time; red badge shows unread count
- [x] **T2.4.4** Create notification center page and dropdown
  - Acceptance: List shows notifications; click marks as read; "Mark all read" works
- [x] **T2.4.5** Create notification preferences UI
  - Acceptance: User can toggle per-type Web/Email preferences

### 2.5 Bookmarks & Follows

- [x] **T2.5.1** Implement bookmark toggle endpoint
  - Acceptance: Bookmark added/removed; list endpoint returns bookmarked posts
- [x] **T2.5.2** Implement follow/unfollow endpoint
  - Acceptance: Follow relationship created; follow event emits NATS notification
- [x] **T2.5.3** Create bookmarks page in web and native Android
  - Acceptance: Android Saved hub (Messages tab chip + Messages/Posts/Comments segments) delivered; web bookmarks page still outstanding and tracked separately.
- [x] **T2.5.4** Add follow button to author profile and post author card
  - Acceptance: Button toggles follow/unfollow; count updates
- [x] **T2.5.5** Implement post repost toggle (API + web + Android)
  - Acceptance: `POST/DELETE /api/posts/:id/repost`; `repost_count`/`is_reposted` on list/detail; Android/web toggle with optimistic update

### 2.6 Mobile Real-time (Native Android)

- [ ] **T2.6.1** Implement Android OkHttp WebSocket manager with reconnection and Room reconciliation.
  - Acceptance: `ANP-3.2` and `REQ-AND-007` pass; Flutter code is not evidence.
- [ ] **T2.6.2** Wire Android comments and reactions to realtime events.
  - Acceptance: `ANP-2.4` passes on a supported Android test device.
- [x] **T2.6.3** Implement Android push registration and in-app notification handling.
  - Acceptance: `ANP-3.1` and `REQ-AND-006` pass.

### 2.7 Discover Feed

- [ ] **T2.7.1** Implement Discover scoring algorithm backend service
  - Acceptance: Comments scored correctly with recency, engagement, quality, personalization, account age
- [ ] **T2.7.2** Implement Discover candidate generation pipeline
  - Acceptance: Candidates sourced from trending, recent, recommended with correct ratios
- [ ] **T2.7.3** Implement anti-spam filtering for Discover
  - Acceptance: Spam comments excluded; account age, duplicate detection, min engagement enforced
- [ ] **T2.7.4** Implement Redis precomputed caching for Discover
  - Acceptance: Trending comments cached every 2-5 minutes; API reads from Redis
- [ ] **T2.7.5** Create Discover page in web (comment cards, topic filters, infinite scroll)
  - Acceptance: Page renders comment cards per spec (§8.4); topic filter works; pagination loads more
- [ ] **T2.7.6** Create Discover screen in mobile
  - Acceptance: Matches web behavior; pull-to-refresh; topic filter chips; empty state
- [ ] **T2.7.7** Implement Discover API endpoints
  - Acceptance: GET /api/discover, /discover/trending, /discover/recommended, /discover/topics, /discover/topic/:slug
- [ ] **T2.7.8** Implement Discover card click → thread navigation
  - Acceptance: Clicking card navigates to full comment thread in post context

### 2.8 Chat & Messaging

- [ ] **T2.8.1** Implement chat database schema (chats, chat_members, messages, message_reads, message_reactions)
  - Acceptance: Migrations run; foreign keys and indexes correct
- [ ] **T2.8.2** Implement chat CRUD endpoints
  - Acceptance: Create, list, get, update, delete chats work correctly
- [ ] **T2.8.3** Implement message CRUD endpoints
  - Acceptance: Send, edit, delete, mark-as-read messages work
- [ ] **T2.8.4** Implement WebSocket events for chat (send, receive, edit, delete, typing, presence)
  - Acceptance: Real-time message delivery; typing indicators; online status
- [ ] **T2.8.5** Create chat list page in web
  - Acceptance: Shows conversations sorted by last_message_at; unread badges; search; matches spec (§8.7)
- [ ] **T2.8.6** Create chat conversation page in web (Telegram-style bubbles)
  - Acceptance: Messages display as bubbles per spec (§8.6); composer works; reactions visible
- [ ] **T2.8.7** Create chat list screen in mobile
  - Acceptance: Matches web; swipe actions (archive, delete); unread badges
- [ ] **T2.8.8** Create chat conversation screen in mobile
  - Acceptance: Telegram-style bubbles; swipe to reply/like; long-press menu; matches spec (§8.6, §10.1)
- [ ] **T2.8.9** Implement read receipts and typing indicators
  - Acceptance: ✓/✓✓/blue indicators work; typing shows/hides correctly
- [x] **T2.8.10** Implement group chat functionality
  - Acceptance: Create group, add/remove members, admin controls
- [ ] **T2.8.11** Implement message composer with emoji picker and attachments
  - Acceptance: Text input, emoji picker, file attachment, send button; auto-expand to 120px
- [ ] **T2.8.12** Implement chat keyboard shortcuts (web)
  - Acceptance: R=reply, L=like, C=copy, ↑=edit own message

---

## Phase 3 — Scale & Microservices (Weeks 15–20)

### 3.1 Microservice Extraction

- [x] **T3.1.1** Extract Auth Service from monolith with gRPC interface
  - Acceptance: Auth endpoints respond via API Gateway → gRPC → Auth Service
- [x] **T3.1.2** Extract Post Service with gRPC interface
  - Acceptance: Post CRUD works via gRPC
- [x] **T3.1.3** Extract Comment Service with gRPC interface
  - Acceptance: Comments work via gRPC
- [x] **T3.1.4** Extract remaining services (Search, Media, Notification, Analytics, Chat)
  - Acceptance: All services independently deployable

### 3.2 NATS Integration

- [x] **T3.2.1** Implement NATS client wrapper (connect, publish, subscribe, JetStream)
  - Acceptance: Publish event; subscriber receives within 100ms
- [x] **T3.2.2** Wire `post_published` → Search Service reindex
  - Acceptance: Publishing a post triggers Meilisearch index update within 2s
- [x] **T3.2.3** Wire `comment_created` → Notification Service → WebSocket broadcast
  - Acceptance: End-to-end notification delivery under 500ms (Redis/WS; NATS deferred)
- [ ] **T3.2.4** Implement all events from Event Catalog (design.md §3)
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
- [ ] **T3.3.5** Implement hybrid fanout strategy (small accounts: fanout-on-write, large: fanout-on-read)
  - Acceptance: Accounts <10K followers use fanout-on-write; ≥10K use fanout-on-read
- [ ] **T3.3.6** Implement Home Feed scoring algorithm
  - Acceptance: Posts scored with recency (λ=0.08), engagement, author authority, personalization, following boost
- [ ] **T3.3.7** Implement feed deduplication and diversity rules
  - Acceptance: Max 2 consecutive from same author; seen posts excluded; muted filtered

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
- [x] **T4.3.4** Implement operational post view counting with 24h dedup
  - Acceptance: `POST /api/posts/:id/view` increments `view_count` once per user/session per 24h; clients display real counts
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

### 4.4 CI/CD & Quality

- [ ] **T4.4.1** Add translation validation to CI (check for missing keys)
  - Acceptance: CI fails if any locale is missing translation keys present in others
- [ ] **T4.4.2** Add design token validation to CI
  - Acceptance: CI fails if color contrast falls below AA threshold
- [ ] **T4.4.3** Add Lighthouse CI checks
  - Acceptance: PR blocked if Performance < 95, Accessibility < 100, SEO < 100

### 4.5 Onboarding & First-Run Experience

- [ ] **T4.5.5** Implement mobile onboarding flow (5 screens)
  - Acceptance: Welcome → Sign Up → Interests → Follow Suggestions → Home Feed per ui-ux-spec (§14)
- [ ] **T4.5.6** Implement web onboarding tooltips
  - Acceptance: First-visit tooltips on write button, discover tab, chat icon, profile
- [ ] **T4.5.7** Implement interest selection and follow suggestions
  - Acceptance: User selects interests; suggested writers shown; follow state persists

### 4.6 Accessibility & Polish

- [ ] **T4.6.1** Audit color contrast across all components
  - Acceptance: All text combinations pass WCAG AA (4.5:1 normal, 3:1 large)
- [ ] **T4.6.2** Implement keyboard navigation (web)
  - Acceptance: Tab navigation, focus states, keyboard shortcuts (§10.2) all work
- [ ] **T4.6.3** Implement gesture interactions (mobile)
  - Acceptance: All gestures from spec (§10.1) work — swipe, long-press, double-tap, pull-to-refresh
- [ ] **T4.6.4** Audit screen reader support
  - Acceptance: ARIA labels on icon buttons; live regions for real-time updates; semantic HTML
- [ ] **T4.6.5** Audit touch target sizes (mobile)
  - Acceptance: All interactive elements ≥ 44×44px; 8px spacing between targets

---

## Phase 5 — Future (Post-Launch)

- [ ] **T5.1** AI-powered content recommendations (collaborative filtering)
- [ ] **T5.2** Collaborative editing (CRDT-based, Yjs + Tiptap)
- [ ] **T5.3** Newsletter system (email subscriptions, automated digests)
- [ ] **T5.4** Community features (groups, forums)
- [ ] **T5.5** Native desktop app (Tauri, future and separate from Android mobile scope)
- [ ] **T5.6** Multi-language/i18n support (RTL, translations)
- [ ] **T5.7** White-label / custom domain support for authors
- [ ] **T5.8** API for third-party integrations (OAuth2 provider)

---

## Task Summary

| Phase | Weeks | Tasks | Focus |
|-------|-------|-------|-------|
| Phase 1 | 1–8 | 25 | Core MVP: auth, posts, comments, search, media, visual design system |
| Phase 2 | 9–14 | 48 | Real-time: WebSocket, live comments, reactions, notifications, discover, chat |
| Phase 3 | 15–20 | 19 | Scale: microservices, NATS, caching, K8s, feed algorithm |
| Phase 4 | 21–28 | 37 | Growth: SEO, monetization, analytics, onboarding, accessibility, CI/CD |
| Phase 5 | 29+ | 8 | Future: AI, collab, desktop, i18n |
| **Total** | | **137** | |
