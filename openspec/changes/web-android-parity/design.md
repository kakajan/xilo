# Design: Web–Android Parity

## Page matrix (Android → Web)

| Android screen | Web route | Primary components |
|----------------|-----------|-------------------|
| Main / Feed tab | `/` | `AppShell`, `PostFeed`, `FloatingBottomNav`, FAB |
| Discover tab | `/discover` | `DiscoverFeed`, `CommentCard`, search overlay |
| Chat list tab | `/chat` | `ChatList`, folder chips |
| Chat conversation | `/chat/[id]` | `MessageList`, `MessageComposer` |
| Profile tab / ProfileKey | `/[username]` | collapsing header, tabs, follow/message |
| FollowList | `/[username]/followers`, `/following` | `FollowList` |
| PostDetail | `/[username]/[slug]` | post + Telegram comments |
| CreatePost | `/write` | existing Tiptap (no bottom nav) |
| Settings | `/settings` | menu like Android |
| Devices | `/settings/devices` | session list |
| ChatFolders | `/settings/chat-folders` | folder CRUD |
| SavedHub | `/saved` | bookmarked posts + comments |
| Auth | `/login`, `/register` | Persian + OTP |
| Onboarding | overlay / gate in shell | localStorage flag |
| Dashboard | `/dashboard/*` | unchanged, no consumer bottom nav |

## Shell architecture

- Consumer routes use `AppShell` (top chrome + floating bottom nav on `md` down; desktop side/top links to same four destinations).
- `(dashboard)` and `/write` use a minimal layout without floating nav.
- Default `lang=fa` + `dir=rtl` for consumer UI; fonts: Vazirmatn + Inter.
- Create-post FAB / write entry points are shown only for roles `author|editor|admin|superadmin` (readers keep comments + chat).
- Deploy brand (this edge: **آیله | aile**) is loaded from `GET /api/platform/settings` → `brand`; Xilo remains the infrastructure/codebase name.

## API modules

Typed helpers under `web/src/lib/api/` wrapping `apiFetch`:

- `chats.ts`, `chat-folders.ts`, `sessions.ts`, `bookmarks.ts`, `users.ts`, `posts.ts`, `comments.ts`

## File ownership

| Area | Owner paths |
|------|-------------|
| Shell / tokens | `web/src/app/layout.tsx`, `globals.css`, `components/layout/*`, `lib/theme.ts` |
| Feed / comments | `components/post/*`, `components/comment/*` |
| Discover | `app/discover/*`, `components/discover/*` |
| Chat | `app/chat/*`, `components/chat/*`, `stores/chat-store.ts`, `types/chat.ts` |
| Profile | `app/[username]/*`, `components/user/*` |
| Settings / auth | `app/settings/*`, `app/(auth)/*`, `components/onboarding/*` |
