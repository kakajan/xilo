# Delta: Post Spec — Quote Comment

## ADDED Requirements

### REQ-POST-012: Quote Comment as Post

**Given** an authenticated Author+ user  
**When** they create a post with `quoted_comment_id` referencing a non-deleted comment on a published post  
**Then** the post is created with `quoted_comment` summary enriched, a comment repost is recorded for the author, and the comment author receives a `comment_quoted` notification.

**Given** a create-post request with both `quoted_post_id` and `quoted_comment_id`  
**When** validated  
**Then** the request is rejected.

**Given** a post with `quoted_comment_id`  
**When** returned in feed/detail  
**Then** `quoted_comment` includes comment text, comment author, and parent post title/slug/author for navigation.
