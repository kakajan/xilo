---
name: xilo-frontend
description: Use when writing Next.js/React frontend code for the Xilo platform. Covers App Router conventions, component patterns, Tailwind CSS 4, shadcn/ui, Zustand stores, TanStack Query, Tiptap editor, and Framer Motion.
---

# Xilo Frontend Development

Next.js 15+ App Router, React 19, TypeScript, Tailwind CSS 4, shadcn/ui, Zustand, TanStack Query, Tiptap, Framer Motion.

## Project Structure

```
web/
├── src/
│   ├── app/                        # App Router pages
│   │   ├── layout.tsx              # Root layout (providers, fonts, metadata)
│   │   ├── page.tsx                # Homepage
│   │   ├── loading.tsx             # Global loading
│   │   ├── error.tsx               # Global error boundary
│   │   ├── not-found.tsx           # 404
│   │   ├── (auth)/login/page.tsx
│   │   ├── (dashboard)/dashboard/page.tsx
│   │   ├── search/page.tsx
│   │   ├── write/page.tsx
│   │   ├── [username]/page.tsx     # Author profile
│   │   ├── [username]/[slug]/page.tsx  # Post detail
│   │   ├── sitemap.ts
│   │   └── robots.ts
│   ├── components/
│   │   ├── ui/                     # shadcn/ui primitives
│   │   ├── layout/                 # navbar, sidebar, footer, mobile-nav
│   │   ├── post/                   # post-card, post-feed, post-content
│   │   ├── editor/                 # tiptap-editor, metadata-sidebar
│   │   ├── comment/                # comment-section, comment-item
│   │   └── shared/                 # skeleton, empty-state, error-state
│   ├── hooks/                      # use-auth, use-posts, use-comments, use-websocket
│   ├── lib/                        # api-client, auth, websocket, utils
│   ├── stores/                     # auth-store, ui-store, editor-store
│   └── types/                      # post, user, comment, api
├── public/
├── next.config.ts
├── tailwind.config.ts
├── tsconfig.json
└── package.json
```

## Component Patterns

### Server Component (default)
```tsx
// app/[username]/[slug]/page.tsx
import { Metadata } from "next"
import { getPost } from "@/lib/api-client"

export async function generateMetadata({ params }): Promise<Metadata> {
  const post = await getPost(params.slug)
  return {
    title: `${post.title} — Xilo`,
    description: post.excerpt,
    openGraph: { /* ... */ }
  }
}

export default async function PostPage({ params }) {
  const post = await getPost(params.slug)
  return <PostContent post={post} />
}
```

### Client Component (only when needed)
```tsx
"use client"

import { useQuery } from "@tanstack/react-query"
import { usePostStore } from "@/stores/post-store"

export function PostFeed() {
  const { data, fetchNextPage, hasNextPage } = useInfiniteQuery({
    queryKey: ["posts"],
    queryFn: ({ pageParam }) => fetchPosts(pageParam),
    getNextPageParam: (lastPage) => lastPage.nextCursor,
    initialPageParam: "",
  })
  
  return (
    <div>
      {data?.pages.map((page) =>
        page.posts.map((post) => <PostCard key={post.id} post={post} />)
      )}
      <InfiniteScroll onLoadMore={fetchNextPage} hasMore={hasNextPage} />
    </div>
  )
}
```

## Zustand Stores

```tsx
// stores/auth-store.ts
import { create } from "zustand"
import { persist } from "zustand/middleware"

interface AuthState {
  user: User | null
  accessToken: string | null
  refreshToken: string | null
  login: (email: string, password: string) => Promise<void>
  logout: () => void
}

export const useAuthStore = create<AuthState>()(
  persist(
    (set) => ({
      user: null,
      accessToken: null,
      refreshToken: null,
      login: async (email, password) => {
        const res = await api.post("/api/auth/login", { email, password })
        set({ user: res.user, accessToken: res.accessToken, refreshToken: res.refreshToken })
      },
      logout: () => set({ user: null, accessToken: null, refreshToken: null }),
    }),
    { name: "xilo-auth" }
  )
)
```

## TanStack Query Setup

```tsx
// app/layout.tsx — wrap with QueryClientProvider
import { QueryClient, QueryClientProvider } from "@tanstack/react-query"

const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      staleTime: 60 * 1000,       // 1 min
      gcTime: 5 * 60 * 1000,      // 5 min garbage collection
      retry: 2,
      refetchOnWindowFocus: false,
    },
  },
})
```

## API Client

```tsx
// lib/api-client.ts
const client = axios.create({ baseURL: process.env.NEXT_PUBLIC_API_URL })

client.interceptors.request.use((config) => {
  const token = useAuthStore.getState().accessToken
  if (token) config.headers.Authorization = `Bearer ${token}`
  return config
})

client.interceptors.response.use(
  (res) => res,
  async (error) => {
    if (error.response?.status === 401) {
      await refreshAccessToken()
      // Retry original request
    }
    return Promise.reject(error)
  }
)
```

## Tiptap Editor

```tsx
"use client"

import { useEditor, EditorContent } from "@tiptap/react"
import StarterKit from "@tiptap/starter-kit"
import Image from "@tiptap/extension-image"
import Underline from "@tiptap/extension-underline"
import Link from "@tiptap/extension-link"

export function TiptapEditor({ content, onChange }) {
  const editor = useEditor({
    extensions: [StarterKit, Image, Underline, Link],
    content,
    onUpdate: ({ editor }) => onChange(editor.getJSON()),
  })

  return (
    <div className="prose dark:prose-invert max-w-none">
      <EditorToolbar editor={editor} />
      <EditorContent editor={editor} />
    </div>
  )
}
```

## Tailwind CSS 4 Conventions

- Use `dark:` variants for dark mode
- Use CSS variables via `@theme` for design tokens
- Mobile-first: start with mobile styles, add `sm:`, `md:`, `lg:`, `xl:`, `2xl:` breakpoints
- Use `shadcn/ui` components rather than custom CSS when possible
- Animation: use Tailwind's built-in `transition-*` utilities or Framer Motion for complex animations

## Image Handling

```tsx
import Image from "next/image"

<Image
  src={post.coverImageUrl}
  alt={post.title}
  width={1200}
  height={630}
  priority         // for above-fold images
  placeholder="blur"
  blurDataURL={post.blurHash}
  className="object-cover rounded-lg"
/>
```

## Key Rules

- Prefer Server Components (`async` functions, no `"use client"`)
- `"use client"` only for: event handlers, hooks, state, browser APIs
- NEVER fetch in Client Components — do it in Server Components or via TanStack Query
- ALWAYS use `next/image` for images (never plain `<img>`)
- ALWAYS provide `loading.tsx` and `error.tsx` per route group
- Generate metadata with `generateMetadata()` for SEO pages
- Use Zod for form validation, React Hook Form for form state
- Use Framer Motion `AnimatePresence` for exit animations
- Target Lighthouse: 95+ Performance, 100 Accessibility, 100 SEO
