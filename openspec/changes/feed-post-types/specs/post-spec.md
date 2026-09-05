# Delta for Post

## ADDED Requirements

### Requirement: Typed feed posts
The system SHALL persist `post_type` of `article`, `micro`, `photo`, `video`, or `link` on every post.

#### Scenario: Default article
- GIVEN an author omits `post_type`
- WHEN they create a post with title and body
- THEN the post is stored as `article`

#### Scenario: Micro without title
- GIVEN an author creates `post_type=micro` with body ≤ 500 characters and no title
- WHEN they publish
- THEN the post is published and listed in the feed without requiring a title

#### Scenario: Photo gallery
- GIVEN an author creates `post_type=photo` with 1 to 10 uploaded image media ids
- WHEN they publish
- THEN the post stores those media ids in order and the card shows a carousel

#### Scenario: Video post
- GIVEN an author creates `post_type=video` with one video media id
- WHEN they publish
- THEN the post is playable on web and Android via the existing media pipeline

#### Scenario: Link post
- GIVEN an author creates `post_type=link` with a canonical HTTPS URL
- WHEN they publish
- THEN the post stores the URL and clients MAY show an Open Graph preview

#### Scenario: Unknown type rejected
- GIVEN a client sends an unsupported `post_type`
- WHEN they call `POST /api/posts`
- THEN the API returns `400`
