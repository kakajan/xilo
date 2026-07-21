# Delta: Discover Spec — Shares from Comment Reposts

## MODIFIED Requirements

### REQ-DIS-002: Discover Scoring Algorithm

Engagement component SHALL include comment amplifications:

```
E raw = likes + 2×replies + 3×min(repost_count, 20)
```

`repost_count` is the denormalized Author+ amplify count on the comment (plain reposts + quote-recorded reposts).

## ADDED Requirements

### REQ-DIS-010: Amplify Affordance on Discover Cards

**Given** an Author+ viewer on Discover  
**When** a comment card is shown  
**Then** a single repost control offers plain repost and quote.

**Given** any viewer  
**When** `repost_count > 0`  
**Then** the card shows the amplify count (and optional badge).
