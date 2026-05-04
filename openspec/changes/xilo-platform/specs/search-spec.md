# Spec: Search System

## Overview
Fast, typo-tolerant full-text search across posts and users powered by Meilisearch. Includes autocomplete suggestions, faceted filtering, and real-time index updates via NATS events.

---

## Requirements

### REQ-SRC-001: Full-Text Search

**Given** a visitor on the search page  
**When** they type a query (min 2 chars)  
**Then** matching posts are returned ranked by relevance.

**Searchable fields:** title, excerpt, content (stripped), tags, author name.

### REQ-SRC-002: Autocomplete / Typeahead

**Given** the search input focused  
**When** the user types at least 3 characters  
**Then** top 5 matching suggestions appear in a dropdown.

Suggestions include: post titles, author names, popular tags.

### REQ-SRC-003: Typo Tolerance

**Given** a search query with a typo (e.g., "fluter" instead of "flutter")  
**When** the search executes  
**Then** Meilisearch returns results for the correct term with typo tolerance of 1–2 characters.

### REQ-SRC-004: Faceted Search

**Given** search results  
**When** the user applies filters  
**Then** results narrow by:
- Category (multi-select)
- Tags (multi-select)
- Date range (published after/before)
- Author
- Reading time range

### REQ-SRC-005: Real-time Indexing

**Given** a post is created, updated, or deleted  
**When** the `post_published` / `post_updated` / `post_deleted` NATS event fires  
**Then** the Search Service updates the Meilisearch index within 2 seconds.

### REQ-SRC-006: Search Result Snippet

**Given** a search result  
**When** rendered on the results page  
**Then** a highlighted snippet shows the matching text with `<mark>` tags.

---

## API Endpoints

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| GET | `/api/search/posts?q=&category=&tag=&author=&after=&before=&page=` | None | Search posts |
| GET | `/api/search/suggest?q=` | None | Autocomplete suggestions |
| GET | `/api/search/users?q=` | None | Search users (for mentions) |

## Meilisearch Index Schema

```json
{
  "uid": "posts",
  "primaryKey": "id",
  "searchableAttributes": ["title", "excerpt", "content", "tags", "author_name"],
  "filterableAttributes": ["category", "tags", "author_id", "published_at", "reading_time"],
  "sortableAttributes": ["published_at", "reactions_count", "comments_count"],
  "rankingRules": ["words", "typo", "proximity", "attribute", "sort", "exactness"]
}
```
