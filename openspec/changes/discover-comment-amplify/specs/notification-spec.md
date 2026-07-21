# Delta: Notification Spec — Comment Amplify

## MODIFIED Requirements

### REQ-NTF-001: Notification Types

Add:

| Type | Trigger | Delivery |
|------|---------|----------|
| `comment_reposted` | Author+ reposts your comment | WS |
| `comment_quoted` | Author+ publishes a post quoting your comment | WS |

## ADDED Requirements

### REQ-NTF-007: Comment Amplify Notifications

**Given** a user owns a comment  
**When** another Author+ user reposts or quotes it  
**Then** they receive the corresponding notification with deep-link data to the comment (and quote post id when applicable).

Self-amplify (repost/quote own comment) SHALL NOT notify.
