# Tasks: Web–Android Parity

## Phase 0 — OpenSpec
- [x] **T0.1** Create proposal, design (page matrix), tasks, delta spec

## Phase 1 — Design system & shell
- [x] **T1.1** Vazirmatn + Inter, fa/RTL consumer layout
- [x] **T1.2** Bubble/chat CSS tokens
- [x] **T1.3** FloatingBottomNav, ChromeVisibility, OfflineBanner, FAB
- [x] **T1.4** AppShell wired for consumer vs dashboard/write

## Phase 2 — Feed / post / comments
- [x] **T2.1** PostCard action bar parity
- [x] **T2.2** Telegram comment bubbles + sort
- [x] **T2.3** Sticky reaction bar on post detail

## Phase 3 — Discover
- [x] **T3.1** `/discover` page + CommentCard feed
- [x] **T3.2** Search overlay + navigate to thread

## Phase 4 — Chat
- [x] **T4.1** Types + API helpers + chat store
- [x] **T4.2** Chat list + conversation pages
- [x] **T4.3** Saved hub + chat folders UI

## Phase 5 — Profile
- [x] **T5.1** Collapsing header, stats, tabs
- [x] **T5.2** Followers/following pages
- [x] **T5.3** Follow / Message / Share actions

## Phase 6 — Settings / auth / onboarding
- [x] **T6.1** Settings menu parity
- [x] **T6.2** Devices sessions
- [x] **T6.3** Auth Persian + OTP
- [x] **T6.4** Onboarding gate

## Phase 7 — Polish
- [x] **T7.1** Motion + a11y touch targets
- [x] **T7.2** Persian copy pass
- [x] **T7.3** typecheck / lint / build gate

## Phase 8 — Author gate + Aile brand
- [x] **T8.1** Backend: only `author+` may `POST /api/posts`; admin user role API
- [x] **T8.2** Platform brand in `platform_settings` (default `آیله | aile`)
- [x] **T8.3** Web: hide write FAB/nav for non-authors; `/dashboard/users` + brand settings
- [x] **T8.4** Android: hide create-post FAB for non-authors; launcher/UI brand آیله
- [x] **T8.5** Seed default Aile superadmin (`faslolkhitab@gmail.com`) via migration 000018
