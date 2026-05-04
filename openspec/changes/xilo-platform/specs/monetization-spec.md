# Spec: Monetization System

## Overview
Flexible monetization supporting subscriptions, premium content, donations, ads, sponsored posts, and affiliate links. Payment processing via crypto, bank gateway, or manual invoices.

---

## Requirements

### REQ-MON-001: Subscription Plans

**Given** an admin in the billing panel  
**When** they configure subscription plans  
**Then** users can subscribe to:

| Plan | Features |
|------|----------|
| Free | Read posts, comment, react, basic search |
| Premium Monthly | Ad-free, early access, premium posts, custom domain |
| Premium Yearly | All Premium + 20% discount, priority support |

### REQ-MON-002: Premium Posts

**Given** an author  
**When** they mark a post as "premium"  
**Then** only subscribers can read the full content. Free users see a preview (first 200 words) with a "Subscribe to read more" CTA.

### REQ-MON-003: Donations

**Given** a reader viewing an author's profile or post  
**When** they click "Donate"  
**Then** they can send a one-time amount via crypto (BTC, ETH, USDT) or bank transfer.

**Author setup:** Authors connect their wallet address or bank details in settings.

### REQ-MON-004: Advertising

**Given** readers on the free plan  
**When** browsing posts  
**Then** non-intrusive ads appear:
- In-feed sponsored post (labeled "Sponsored")
- Between-post-list banner
- Bottom sticky banner (mobile)

**Ad management:** Admin dashboard for uploading creatives, setting targeting (by category), tracking impressions/clicks.

### REQ-MON-005: Affiliate Links

**Given** a post containing affiliate links  
**When** rendered  
**Then** links are automatically tagged with `rel="sponsored"` and tracking parameters appended.

### REQ-MON-006: Payment Processing

**Given** a user initiating payment  
**When** they choose a method  
**Then** the system supports:

| Method | Use Case | Settlement |
|--------|----------|------------|
| Crypto (BTC, ETH, USDT) | Subscriptions, donations | Instant on-chain |
| Bank Gateway | Subscriptions (Iran) | Through local gateway |
| Manual Invoice | Enterprise/custom | Manual admin approval |

### REQ-MON-007: Invoice Generation

**Given** a completed payment  
**When** the transaction succeeds  
**Then** a PDF invoice is generated and available in the user's billing history.

---

## API Endpoints

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| GET | `/api/billing/plans` | None | List subscription plans |
| POST | `/api/billing/subscribe` | Reader+ | Create subscription |
| GET | `/api/billing/subscription` | Reader+ | Get current subscription |
| DELETE | `/api/billing/subscription` | Reader+ | Cancel subscription |
| POST | `/api/billing/donate` | Reader+ | Process donation |
| GET | `/api/billing/invoices` | Reader+ | List invoices |
| GET | `/api/billing/invoices/:id` | Reader+ | Download invoice PDF |
| GET | `/api/billing/ads` | Admin | List ads |
| POST | `/api/billing/ads` | Admin | Create ad |
| PATCH | `/api/billing/ads/:id` | Admin | Update ad |
| DELETE | `/api/billing/ads/:id` | Admin | Delete ad |
