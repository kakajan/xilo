# Delta: Discover Spec — Reply Parent Context

## MODIFIED Requirements

### REQ-DIS-001: Discover Feed Display

Each Discover comment card SHALL include:

- Author avatar, name, comment text
- Engagement counts
- Link to parent post ("on post: [title] by @author")
- **When the comment has a non-null parent:** reply-to context for that parent comment (author handle and short content preview)

### REQ-DIS-008: Thread Navigation

**Given** a Discover comment card  
**When** the user clicks on it  
**Then** they navigate to the full comment thread within the parent post context  
**And** the client SHALL seed the thread focus path so the target comment is visible in the 2-level window relative to an appropriate focus root  
**And** the user can drill into deeper replies and navigate back through outer layers before leaving the post.

## ADDED Requirements

### REQ-DIS-011: Discover Parent Summary Payload

**Given** a Discover API comment with `parent_id` set and a non-deleted parent  
**When** the feed is returned  
**Then** the item includes `parent_id`, `root_id`, `depth`, and a `parent` object with `id`, `author_username`, `author_display_name`, and `content_preview`.

**Given** a top-level Discover comment (`parent_id` null)  
**When** the feed is returned  
**Then** `parent` is omitted and clients MUST NOT show a reply-to strip.
