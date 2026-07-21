# Delta: Android Native Spec — Discover Reply Context

## ADDED Requirements

### REQ-AND-DIS-REPLY-001: Reply-To Strip on Discover Cards

**Given** a Discover comment with parent summary  
**When** the card is rendered  
**Then** it shows a reply-to line (handle + optional preview) in addition to the parent post line.

### REQ-AND-DIS-REPLY-002: Seed Focus Stack from Reply Target

**Given** `PostDetailKey.replyToCommentId` is set  
**When** comments load  
**Then** the screen builds the ancestor path via `parentId` and sets `focusStack` to that path without the target  
**And** back pops focus layers before exiting the screen.
