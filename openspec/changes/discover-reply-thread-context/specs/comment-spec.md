# Delta: Comment Spec — Discover Entry into 2-Level Window

## MODIFIED Requirements

### REQ-CMT-009: Telegram-Style Bubble Design

Unchanged visual rules. Clarification for Discover entry:

**Given** a deep-link or Discover navigation targeting a nested comment  
**When** the post thread loads  
**Then** the client SHALL initialize the focus stack from the ancestor path so the target appears inside the max-2-level window  
**And** deeper replies remain behind “View N replies” / drill-down as already specified.
