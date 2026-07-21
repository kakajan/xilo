# Tasks: Discover Reply Thread Context

## Spec

- [x] **DRT-0.1** Proposal, design, delta specs

## Backend

- [x] **DRT-1.1** Discover model/query: `parent_id`, `root_id`, `depth`, `parent` summary
- [x] **DRT-1.2** Unit tests for root vs reply parent enrichment

## Android

- [x] **DRT-2.1** Map parent summary; reply-to strip on Discover CommentCard
- [x] **DRT-2.2** Seed `focusStack` from `replyToCommentId` path on PostDetail
- [x] **DRT-2.3** Unit tests for path helper / focus window

## Web

- [x] **DRT-3.1** Types + reply-to strip on Discover comment card
- [x] **DRT-3.2** Hard 2-level window in CommentSection (keep deep-link path seeding)

## Verify

- [x] **DRT-4.1** Root vs nested Discover → thread drill/back parity (web + Android)
