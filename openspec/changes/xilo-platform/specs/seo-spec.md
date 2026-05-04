# Spec: SEO & Performance

## Overview
SEO-optimized web frontend leveraging Next.js SSR/SSG/ISR capabilities, structured data, dynamic sitemaps, and Core Web Vitals performance targets.

---

## Requirements

### REQ-SEO-001: Server-Side Rendering

**Given** a request for any public post or page  
**When** the server processes the request  
**Then** fully rendered HTML is returned with all metadata, content, and structured data.

**Edge case:** Crawler bots get SSR; real users get hydrated SPA for interactivity.

### REQ-SEO-002: Static Generation with ISR

**Given** high-traffic pages (homepage, category pages, popular posts)  
**When** generated statically  
**Then** they are revalidated via ISR every 60 seconds (configurable per page type).

### REQ-SEO-003: Metadata API

**Given** every page  
**When** rendered  
**Then** the following meta tags are present:

| Meta Tag | Source |
|----------|--------|
| `<title>` | Post title + " — Xilo" |
| `<meta name="description">` | Post excerpt (max 160 chars) |
| `<meta property="og:title">` | Post title |
| `<meta property="og:description">` | Post excerpt |
| `<meta property="og:image">` | Post cover image (1200x630) |
| `<meta property="og:url">` | Canonical URL |
| `<meta property="og:type">` | "article" |
| `<meta name="twitter:card">` | "summary_large_image" |
| `<link rel="canonical">` | Canonical URL |
| `<meta name="robots">` | "index, follow" (published) / "noindex" (draft) |

### REQ-SEO-004: Structured Data (JSON-LD)

All post pages output `Article` schema.org JSON-LD:
```json
{
  "@context": "https://schema.org",
  "@type": "Article",
  "headline": "...",
  "description": "...",
  "image": "...",
  "datePublished": "...",
  "dateModified": "...",
  "author": { "@type": "Person", "name": "..." },
  "publisher": { "@type": "Organization", "name": "Xilo" }
}
```

Blog homepage outputs `Blog` schema with `BlogPosting` items.

### REQ-SEO-005: Dynamic Sitemap

**Given** the sitemap endpoint  
**When** requested at `/sitemap.xml`  
**Then** it lists:
- All published posts with `lastmod` and `changefreq="weekly"`
- All category pages with `changefreq="daily"`
- Static pages (about, contact) with `changefreq="monthly"`

### REQ-SEO-006: robots.txt

```
User-agent: *
Allow: /
Disallow: /api/
Disallow: /dashboard/
Sitemap: https://xilo.dev/sitemap.xml
```

### REQ-SEO-007: Core Web Vitals Targets

| Metric | Target | Measurement |
|--------|--------|-------------|
| TTFB | < 200ms | Server response time |
| LCP | < 2.5s | Largest Contentful Paint |
| FID | < 100ms | First Input Delay |
| CLS | < 0.1 | Cumulative Layout Shift |
| INP | < 200ms | Interaction to Next Paint |

### REQ-SEO-008: Image Optimization

- Next.js `<Image>` component for automatic WebP/AVIF conversion
- Responsive `srcset` generation
- Lazy loading for below-fold images
- Blur placeholder (base64 hash) while loading
- Explicit width/height to prevent CLS

### REQ-SEO-009: OpenSearch

Provide OpenSearch descriptor at `/opensearch.xml` so browsers can add Xilo as a search engine.

---

## Performance Strategy

### Caching Layers

```
Browser Cache (immutable assets)
  → CDN Cache (Cloudflare free tier or Varnish)
    → ISR Cache (Next.js incremental static regeneration)
      → Redis Cache (API responses, 5min TTL)
        → PostgreSQL (source of truth)
```

### Pagination

- Cursor-based (keyset) pagination for all list endpoints
- No `OFFSET` — use `WHERE id > :cursor ORDER BY id LIMIT :n`
