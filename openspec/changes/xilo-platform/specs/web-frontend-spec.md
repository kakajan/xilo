# Spec: Web Frontend

## Overview
Next.js 15+ App Router web application with SSR/SSG/ISR, Tailwind CSS 4, shadcn/ui components, Zustand state management, TanStack Query data fetching, Tiptap rich text editor, and Framer Motion animations. UX inspired by Telegram, Linear, Notion, and Medium.

---

## Requirements

### REQ-WEB-001: Pages & Routes

```
/                          Homepage (feed of latest posts)
/search                    Search page
/category/[slug]           Posts by category
/tag/[slug]                Posts by tag
/[username]                Author profile
/[username]/[slug]         Post detail
/write                     Post editor (authenticated)
/write/[id]                Edit existing post
/dashboard                 Author dashboard
/dashboard/posts            My posts
/dashboard/analytics        Author analytics
/dashboard/settings         Account settings
/dashboard/billing          Billing & subscriptions
/notifications              Notification center
/bookmarks                  Bookmarked posts
/settings                   Profile settings
/about                      About page
/login                      Login page
/register                   Register page
```

### REQ-WEB-002: Layout Shell

Global layout includes:
- **Top navbar**: Logo, search icon, write button, notifications bell, user avatar dropdown
- **Sidebar** (desktop): Categories, popular tags, trending posts
- **Footer**: Links, social, copyright
- **Mobile bottom nav**: Home, Search, Write, Notifications, Profile

### REQ-WEB-003: Homepage Feed

**Given** a visitor on the homepage  
**When** the page loads  
**Then** they see:
- Hero section with featured/pinned post
- Infinite-scroll feed of latest posts (cards)
- Each card: cover image, title, excerpt, author avatar+name, date, reading time, reaction count, comment count
- Skeleton loading placeholders during fetch

### REQ-WEB-004: Post Detail Page

**Given** a visitor reading a post  
**When** the page renders  
**Then** they see:
- Cover image (full width)
- Title, author info, published date, reading time
- Rich content rendered from Tiptap JSON
- Reaction bar (sticky on mobile)
- Comment section at bottom
- Share buttons (copy link, Twitter, Telegram, WhatsApp)
- Author card at bottom
- Related posts (3-5)

### REQ-WEB-005: Rich Text Editor (Tiptap)

**Given** an author in the post editor  
**When** they create/edit a post  
**Then** the editor supports:

| Feature | Implementation |
|---------|---------------|
| Bold, Italic, Underline, Strikethrough | Tiptap marks |
| Headings (H1-H4) | Tiptap nodes |
| Lists (ordered, unordered, task) | Tiptap nodes |
| Blockquotes | Tiptap nodes |
| Code blocks (syntax highlighted) | Tiptap + Shiki |
| Images (upload/drag-drop) | Tiptap extension |
| Embeds (YouTube, Twitter, CodePen) | Custom extension |
| Tables | Tiptap extension |
| Horizontal rule | Tiptap nodes |
| Markdown shortcuts | Tiptap markdown extension |
| Auto-save (drafts every 30s) | localStorage + API |
| Word count, reading time | Custom plugin |

**Metadata sidebar:** Title, slug, excerpt, cover image, tags, category, scheduled date, premium toggle.

### REQ-WEB-006: Dark Mode

System-default dark mode with manual toggle in navbar. Persisted in localStorage. Tailwind `dark:` variant used throughout.

### REQ-WEB-007: Responsive Design

- Mobile-first breakpoints: `sm(640)`, `md(768)`, `lg(1024)`, `xl(1280)`, `2xl(1536)`
- Fluid typography using `clamp()`
- Touch-friendly tap targets (min 44px)
- Swipe gestures where appropriate

### REQ-WEB-008: Loading States

All data-dependent components show:
- **Skeleton screens** for initial load
- **Spinner** for actions (submit, delete)
- **Optimistic updates** for reactions, bookmarks, follows
- **Error states** with retry button
- **Empty states** with helpful messaging

### REQ-WEB-009: Performance

- Route prefetching on hover/intent
- Code splitting per route (automatic with Next.js App Router)
- Image optimization via `next/image`
- Font optimization via `next/font`
- Bundle analyzer in CI to prevent regressions
- Target Lighthouse score: 95+ Performance, 100 Accessibility, 100 SEO

---

## Component Tree

```
<RootLayout>
  <ThemeProvider>
    <QueryClientProvider>
      <AuthProvider>
        <Navbar />
        <Sidebar />
        <main>{children}</main>
        <Footer />
        <MobileNav />
        <ToastProvider />
      </AuthProvider>
    </QueryClientProvider>
  </ThemeProvider>
</RootLayout>

Pages:
  HomePage → <Hero /> <PostFeed /> <TrendingSidebar />
  PostPage → <PostHeader /> <PostContent /> <ReactionBar /> <CommentSection /> <AuthorCard /> <RelatedPosts />
  EditorPage → <EditorNav /> <TiptapEditor /> <MetadataSidebar /> <PublishDialog />
  SearchPage → <SearchInput /> <FilterBar /> <SearchResults />
  Dashboard → <StatsCards /> <PostList /> <Charts />
```

## State Management (Zustand)

```typescript
// Stores
useAuthStore        — user, tokens, login/logout actions
useUIStore          — sidebar open, mobile nav, theme
useEditorStore      — draft content, metadata, auto-save
useNotificationStore — unread count, notification list
```
