# Delta: Web Frontend Spec — Discover Reply Context

## ADDED Requirements

### REQ-WEB-DIS-REPLY-001: Reply-To Strip on Discover Cards

**Given** a Discover comment with `parent` / `parent_id`  
**When** the Discover card is rendered  
**Then** it shows a reply-to line (handle + optional preview) in addition to the parent post line  
**And** the card still deep-links to `/{username}/{slug}?reply={commentId}`.

### REQ-WEB-DIS-REPLY-002: Hard 2-Level Comment Window

**Given** the post comment section  
**When** rendering relative to the current focus root  
**Then** only the focus root and its direct children are inlined  
**And** nodes with grandchildren show “N پاسخ — مشاهده رشته” to push focus  
**And** `?reply=` continues to seed the focus stack from `pathToComment`.
