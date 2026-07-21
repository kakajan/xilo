# Proposal: Discover Comment Amplify (Repost / Quote)

**Status:** Active  
**Date:** 2026-07-21  
**Author:** Xilo Team

---

## Summary

Make Discover comments feel Twitter-like for audience conversation, and let **Author+** users amplify audience comments via a single **repost** control that offers plain repost or quote. Quote creates a new post embedding the comment; plain repost is a lightweight amplification signal (no new feed item).

## Motivation

- Discover already surfaces comments as tweet-like cards, but authors cannot promote strong audience voices into their own publishing surface.
- Post-level repost/quote already exists; comment-level amplify closes the loop between conversation and publishing.
- A single author-only icon keeps Reader UX clean while giving writers a clear amplification path.

## Scope

| Domain | Deliverables |
|--------|-------------|
| **Backend** | `comment_reposts`, `comments.repost_count`, `posts.quoted_comment_id`, toggle API, quote create, notifications, Discover score uses shares |
| **Web** | Repost menu on Discover + thread comment cards; quote compose; share deep-link; amplify badge |
| **Android** | Same parity on CommentCard / Discover / CreatePost |
| **OpenSpec** | Delta specs for comment, post, discover, notification, web, android |

### Phase 2 (same change, after MVP)

- Wire `@mentions` notifications for comments
- Topic chips on web Discover + interest filter
- Pin UI for post author/mod
- Backend comment sort (`newest` / `oldest` / `most_reacted` / `most_replied`)

## Out of Scope

- Full Redis Discover precompute pipeline
- Plain repost creating Home feed items
- Reader-visible repost control
- Profile “highlighted comments” (Could / phase 3)

## Risks

- Amplify spam → rate-limit toggles; cap shares contribution in scoring
- Quote without context → require `quoted_comment` embed + parent post link
- Role confusion → reuse exact Author+ gate as post repost

## Success Criteria

- [ ] Author+ can repost/quote comments from Discover and post threads
- [ ] Readers never see the repost control; they see count/badge when > 0
- [ ] Quote publishes a post with `quoted_comment` embed and notifies comment author
- [ ] Discover scoring includes `repost_count` as shares
- [ ] Web and Android parity for MVP amplify flows
