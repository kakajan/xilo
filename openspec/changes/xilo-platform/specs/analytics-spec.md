# Spec: Analytics System

## Overview
Self-hosted analytics tracking with PostHog or Umami as primary engine, plus custom event tracking for blog-specific metrics. All data stays on-premise.

---

## Requirements

### REQ-ALY-001: Page View Tracking

**Given** any visitor  
**When** they load a page  
**Then** a `page_view` event is recorded with:
- Page URL, referrer, user agent
- Session ID (anonymous or authenticated)
- Timestamp

### REQ-ALY-002: Custom Event Tracking

The following custom events are tracked:

| Event | Trigger | Properties |
|-------|---------|------------|
| `post_read` | User scrolls past 80% of post | post_id, reading_time_seconds |
| `scroll_depth` | Scroll reaches 25%, 50%, 75%, 100% | post_id, depth_percent |
| `comment_posted` | User submits a comment | post_id, comment_length |
| `reaction_added` | User reacts to post/comment | post_id, reaction_type |
| `share_clicked` | User clicks share button | post_id, platform |
| `search_performed` | User executes a search | query, result_count |
| `subscription_started` | User subscribes | plan, amount |
| `subscription_cancelled` | User cancels | plan, reason |

### REQ-ALY-003: Author Dashboard

**Given** an author viewing their dashboard  
**When** they see analytics  
**Then** they get:
- Post views over time (line chart, 7d/30d/90d/all)
- Total reads, unique readers
- Average reading time
- Comment count, reaction breakdown
- Top performing posts
- Reader geography (country level, if available)

### REQ-ALY-004: Admin Dashboard

**Given** an admin viewing the platform dashboard  
**When** they see global analytics  
**Then** they get:
- DAU/WAU/MAU
- New registrations over time
- Post publish rate
- Total comments/reactions
- Subscription revenue
- Top authors by views

### REQ-ALY-005: Data Privacy

- No PII in analytics events
- IP addresses anonymized (last octet zeroed)
- Data retention: raw events 90 days, aggregated forever
- GDPR: user can request deletion of their analytics data

---

## API Endpoints

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| GET | `/api/analytics/author/:authorId` | Author | Author dashboard data |
| GET | `/api/analytics/admin` | Admin | Admin dashboard data |
| POST | `/api/analytics/events` | None/Reader+ | Ingest custom event |
