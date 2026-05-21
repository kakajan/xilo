# Spec: Discover Feed

## Overview

The Discover Feed surfaces high-quality comments as standalone tweet-like cards, enabling content discovery beyond the post context. Comments are ranked by a scoring algorithm considering recency, engagement, quality, personalization, and account age.

---

## Requirements

### REQ-DIS-001: Discover Feed Display

**Given** a user on the Discover page  
**When** the page loads  
**Then** they see a feed of comment cards ranked by DiscoverScore.

**Card contents:**
- Comment author avatar, username, verified badge
- Comment text (truncated if > 280 chars)
- Engagement counts (likes, replies)
- Link to parent post ("on post: [title] by @author")
- Timestamp (relative)

### REQ-DIS-002: Discover Scoring Algorithm

**Given** a comment candidate for Discover  
**When** the scoring pipeline runs  
**Then** each comment receives a DiscoverScore:

```
DiscoverScore = w₁R + w₂E + w₃Q + w₄P + w₅A

R = Recency: e^(-λt), λ=0.08, t=hours since creation
E = Engagement: (likes + 2×replies + 3×shares) / (views + 1), normalized to [0,1]
Q = Quality: text length factor + media bonus + author reputation
P = Personalization: topic match + author affinity + engagement cluster
A = Account age: log(days_since_registration) / 100, capped at 1.0

Weights: w₁=0.30, w₂=0.30, w₃=0.15, w₄=0.15, w₅=0.10
```

### REQ-DIS-003: Candidate Generation

**Given** the Discover pipeline runs  
**When** selecting candidates  
**Then** comments are sourced from:
- **Trending**: High-engagement comments in last 24 hours
- **Recent**: Comments from last 6 hours with minimum engagement
- **Recommended**: Comments matching user's topic/author history

**Ratio:** 40% trending, 30% recent, 30% recommended

### REQ-DIS-004: Anti-Spam Filtering

**Given** a comment candidate  
**When** evaluated for Discover  
**Then** it is excluded if:
- Author account age < 7 days
- Comment flagged as spam
- Author is shadow-banned
- Duplicate text detected (cosine similarity > 0.9)
- Engagement below minimum threshold (3 likes or 1 reply)

### REQ-DIS-005: Diversity Rules

**Given** a ranked Discover feed  
**When** rendering  
**Then**:
- Maximum 2 consecutive comments from same author
- Maximum 3 consecutive comments from same parent post
- Already-seen comments are excluded
- Muted authors/topics are filtered

### REQ-DIS-006: Topic Filtering

**Given** a user on Discover  
**When** they select a topic filter  
**Then** results are filtered by topic/category.

**Topics** are derived from post categories and trending hashtags.

### REQ-DIS-007: Precomputed Caching

**Given** the Discover pipeline  
**When** running on schedule  
**Then** trending comments are precomputed every 2-5 minutes and stored in Redis:

```
ZADD discover:trending score commentId
ZADD discover:recommended:{userId} score commentId
```

**API reads from Redis:** `ZRANGE discover:trending 0 50`

### REQ-DIS-008: Thread Navigation

**Given** a Discover comment card  
**When** the user clicks on it  
**Then** they navigate to the full comment thread within the parent post context.

### REQ-DIS-009: Pagination

**Given** a Discover feed  
**When** the user scrolls  
**Then** cursor-based pagination loads more comments.

```
GET /api/discover?cursor=comment_8921&limit=20&topic=tech
```

**Response:**
```json
{
  "comments": [...],
  "nextCursor": "comment_9011"
}
```

---

## API Endpoints

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| GET | `/api/discover` | None | Discover feed (paginated) |
| GET | `/api/discover/trending` | None | Trending comments |
| GET | `/api/discover/recommended` | Reader+ | Personalized recommendations |
| GET | `/api/discover/topics` | None | Available topic filters |
| GET | `/api/discover/topic/:slug` | None | Comments by topic |

---

## Meilisearch Index (Comments)

```json
{
  "uid": "comments",
  "primaryKey": "id",
  "searchableAttributes": ["content", "author_name"],
  "filterableAttributes": ["post_id", "author_id", "created_at", "likes_count", "topic"],
  "sortableAttributes": ["created_at", "likes_count", "replies_count", "discover_score"],
  "rankingRules": ["discover_score", "words", "typo", "proximity", "sort"]
}