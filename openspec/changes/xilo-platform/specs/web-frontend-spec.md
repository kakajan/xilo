# Spec: Web Frontend

## Overview
Next.js 15+ App Router web application with SSR/SSG/ISR, Tailwind CSS 4, shadcn/ui components, Zustand state management, TanStack Query data fetching, Tiptap rich text editor, and Framer Motion animations. UX inspired by Telegram, Linear, Notion, and Medium.

---

## Requirements

### REQ-WEB-001: Pages & Routes

```
/                          Homepage (feed of latest posts)
/discover                  Discover feed (comments as tweets)
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
/chat                      Chat list
/chat/[id]                 Chat conversation
/about                      About page
/login                      Login page
/register                   Register page
```

### REQ-WEB-002: Layout Shell

Global layout includes:
- **Top navbar**: Logo, search icon, write button, notifications bell, chat icon, user avatar dropdown
- **Sidebar** (desktop): Categories, popular tags, trending posts
- **Footer**: Links, social, copyright
- **Mobile bottom nav**: Home, Discover, Write, Chat, Notifications, Profile

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
- Comment section at bottom (Telegram-style bubbles)
- Share buttons (copy link, Twitter, Telegram, WhatsApp)
- Author card at bottom
- Related posts (3-5)

### REQ-WEB-004B: Discover Page

**Given** a user on the Discover page  
**When** the page loads  
**Then** they see:
- Infinite-scroll feed of comment cards
- Each card: author avatar+name, comment text, engagement counts, link to parent post
- Topic filter bar at top
- Skeleton loading placeholders during fetch
- Empty state with "No comments yet" message

### REQ-WEB-004C: Chat Page

**Given** a user on the Chat page  
**When** the page loads  
**Then** they see:
- **Chat list view**: List of conversations sorted by last message time
  - Each item: avatar, name, last message preview, unread badge, timestamp
  - Search bar to filter chats
- **Chat conversation view**: Telegram-style message bubbles
  - Own messages: blue bubble, right-aligned
  - Others' messages: gray bubble, left-aligned
  - Message input bar with emoji picker, file attachment, send button
  - Typing indicator, online status
  - Message reactions displayed below bubbles

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
  DiscoverPage → <TopicFilterBar /> <DiscoverFeed /> <DiscoverCard />
  PostPage → <PostHeader /> <PostContent /> <ReactionBar /> <CommentSection /> <AuthorCard /> <RelatedPosts />
  EditorPage → <EditorNav /> <TiptapEditor /> <MetadataSidebar /> <PublishDialog />
  SearchPage → <SearchInput /> <FilterBar /> <SearchResults />
  ChatListPage → <ChatSearch /> <ChatList /> <ChatListItem />
  ChatPage → <ChatHeader /> <MessageList /> <MessageBubble /> <MessageComposer />
  Dashboard → <StatsCards /> <PostList /> <Charts />
```

## State Management (Zustand)

```typescript
// Stores
useAuthStore        — user, tokens, login/logout actions
useUIStore          — sidebar open, mobile nav, theme
useEditorStore      — draft content, metadata, auto-save
useNotificationStore — unread count, notification list
useChatStore        — active chat, typing users, online status
```

## Visual Design Tokens

The normative visual authority is `openspec/changes/xilo-platform/specs/ui-ux-spec.md`.

### Color Palette (Light Mode)
```typescript
const colors = {
  primary: '#1D9BF0',
  background: '#FFFFFF',
  surface: '#F7F9FA',
  textPrimary: '#0F1419',
  textSecondary: '#536471',
  border: '#EFF3F4',
  bubbleOwn: '#E8F5FE',
  bubbleOthers: '#F7F9FA'
};
```

### Typography
- Font family: Inter (English), Vazirmatn (Persian)
- Body: 15px / 400
- Heading 1: 24px / 700
- Heading 2: 20px / 700
- Caption: 13px / 400

### Spacing Scale
- xs: 4px, sm: 8px, md: 16px, lg: 24px, xl: 32px, 2xl: 48px

### Border Radius
- Buttons/Inputs: 4px
- Cards: 8px
- Images/Media: 12-16px
- Comment Bubbles: 14-16px
- Avatars: 50%
