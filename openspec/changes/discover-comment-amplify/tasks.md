# Tasks: Discover Comment Amplify

## Spec

- [x] **DCA-0.1** Proposal, design, delta specs

## Backend (MVP)

- [x] **DCA-1.1** Migration: `comment_reposts`, `comments.repost_count`, `posts.quoted_comment_id`
- [x] **DCA-1.2** Toggle comment repost API (Author+) + denormalized count
- [x] **DCA-1.3** Create post with `quoted_comment_id` + enrich `quoted_comment`
- [x] **DCA-1.4** Notifications `comment_reposted` / `comment_quoted`
- [x] **DCA-1.5** Discover score uses shares (`repost_count`); expose fields on Discover/comment DTOs
- [x] **DCA-1.6** Unit tests for role gate, toggle, quote enrich

## Web (MVP)

- [x] **DCA-2.1** Types + repost/quote APIs for comments
- [x] **DCA-2.2** Repost menu on Discover comment card + thread actions
- [x] **DCA-2.3** Quote compose route for comments + embed card
- [x] **DCA-2.4** Share deep-link + amplify badge

## Android (MVP)

- [x] **DCA-3.1** DTO/repo comment repost + create with `quotedCommentId`
- [x] **DCA-3.2** Repost menu on CommentCard / Discover
- [x] **DCA-3.3** CreatePost quote-from-comment flow + embed
- [x] **DCA-3.4** Share deep-link + amplify badge

## Phase 2

- [x] **DCA-4.1** Comment `@mention` parsing + notify
- [x] **DCA-4.2** Web Discover topic/interest chips + filter
- [x] **DCA-4.3** Pin UI for post author/mod on comments
- [x] **DCA-4.4** Backend comment list sort implementation
