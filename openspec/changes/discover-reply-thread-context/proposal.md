# Proposal: Discover Reply Thread Context

**Status:** Active  
**Date:** 2026-07-21  
**Author:** Xilo Team

---

## Summary

Discover comment cards SHALL show which comment a hit replies to (in addition to the parent post), and opening a card SHALL enter the post’s 2-level nested reply window with a seeded focus stack so users can browse inner/outer layers like a timeline.

## Motivation

- Discover already links to the parent post, but reply hits look like top-level tweets with no parent-comment context.
- Post detail already has Twitter-style 2-level windowing + drill-down; Discover deep-links do not seed that focus path on Android, so nested hits are easy to miss.
- Readers need a clear story: medium (post) → parent comment → this reply → deeper replies.

## Scope

| Domain | Deliverables |
|--------|-------------|
| **Backend** | Discover DTO: `parent_id`, `root_id`, `depth`, optional `parent` summary |
| **Web** | Reply-to strip on Discover cards; hard 2-level window in post CommentSection |
| **Android** | Reply-to strip on Discover CommentCard; seed `focusStack` from `replyToCommentId` |
| **OpenSpec** | Delta specs for discover, comment UX, web, android |

## Out of Scope

- Inline multi-level nesting inside the Discover feed itself
- Discover scoring / amplify (see `discover-comment-amplify`)
- Changing max backend nesting depth (still 4)

## Risks

- Parent deleted → omit `parent` summary; still expose `parent_id` when known
- Deep replies outside the unfocused 2-level window → must seed focus path

## Success Criteria

- [x] Discover reply cards show parent comment author (+ preview) and post medium
- [x] Opening a nested Discover hit shows the target in the 2-level window
- [x] Users can drill deeper and back out through focus layers on web and Android
- [x] Root Discover cards omit the reply-to strip
