"use client";

import { useCallback, useEffect, useState } from "react";
import { Search, X } from "lucide-react";
import { useQuery } from "@tanstack/react-query";
import { apiFetch } from "@/lib/api-client";
import { CommentCard, type DiscoverComment } from "@/components/discover/comment-card";
import { PostCard } from "@/components/post/post-card";
import { Skeleton } from "@/components/ui/skeleton";
import { Button } from "@/components/ui/button";
import type { Comment, CommentListResponse } from "@/types/comment";
import type { Post, PostListResponse } from "@/types/post";
import { useDebouncedValue } from "@/hooks/use-debounced-value";

type DiscoverApiComment = Comment & {
  like_count?: number;
  reply_count?: number;
  post?: {
    id?: string;
    title?: string;
    slug?: string;
    author_username?: string;
  };
};

function flattenTree(comments: Comment[]): Comment[] {
  const out: Comment[] = [];
  const walk = (list: Comment[]) => {
    for (const c of list) {
      out.push({ ...c, replies: undefined });
      if (c.replies?.length) walk(c.replies);
    }
  };
  walk(comments);
  return out;
}

function toDiscoverComment(
  c: Comment | DiscoverApiComment,
  extras?: Partial<DiscoverComment>
): DiscoverComment {
  const api = c as DiscoverApiComment;
  return {
    ...c,
    post_slug: extras?.post_slug ?? api.post?.slug ?? extras?.post_slug,
    post_title: extras?.post_title ?? api.post?.title ?? extras?.post_title,
    post_author_username: extras?.post_author_username,
    author_username: c.author?.username,
    reply_count: api.reply_count ?? c.replies?.length ?? 0,
    reactions: c.reactions ?? (api.like_count != null ? { like: api.like_count } : undefined),
  };
}

export default function DiscoverPage() {
  const [searchActive, setSearchActive] = useState(false);
  const [query, setQuery] = useState("");
  const debounced = useDebouncedValue(query, 300);
  const [comments, setComments] = useState<DiscoverComment[]>([]);
  const [loading, setLoading] = useState(true);
  const [liked, setLiked] = useState<Record<string, boolean>>({});

  const refresh = useCallback(async () => {
    setLoading(true);
    try {
      try {
        const discoverRes = await apiFetch<{ data?: DiscoverApiComment[] }>(
          "/api/discover/comments?limit=50"
        );
        const rows = discoverRes.data ?? [];
        if (rows.length > 0) {
          setComments(
            rows.map((c) =>
              toDiscoverComment(c, {
                post_slug: c.post?.slug,
                post_title: c.post?.title,
                post_author_username: c.post?.author_username,
              })
            )
          );
          return;
        }
      } catch {
        // Fall through to per-post comments.
      }

      const postsRes = await apiFetch<PostListResponse>("/api/posts?limit=10");
      const posts = postsRes.data ?? [];
      const nested = await Promise.all(
        posts.slice(0, 8).map(async (post) => {
          try {
            const res = await apiFetch<CommentListResponse>(
              `/api/posts/${post.id}/comments?limit=5&sort=newest`
            );
            return flattenTree(res.data ?? []).map((c) =>
              toDiscoverComment(c, {
                post_slug: post.slug,
                post_title: post.title,
                post_author_username: post.author?.username,
              })
            );
          } catch {
            return [] as DiscoverComment[];
          }
        })
      );
      const flat = nested.flat().sort((a, b) => {
        return new Date(b.created_at).getTime() - new Date(a.created_at).getTime();
      });
      setComments(flat.slice(0, 50));
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void refresh();
  }, [refresh]);

  const { data: searchPosts, isFetching: searching } = useQuery({
    queryKey: ["discover-search", debounced],
    enabled: searchActive && debounced.trim().length > 0,
    queryFn: async () => {
      const res = await apiFetch<{ data?: Post[] } | PostListResponse>(
        `/api/search?q=${encodeURIComponent(debounced.trim())}&limit=20`
      );
      if (Array.isArray((res as PostListResponse).data)) {
        return (res as PostListResponse).data;
      }
      const postsRes = await apiFetch<PostListResponse>(`/api/posts?limit=20`);
      const q = debounced.trim().toLowerCase();
      return (postsRes.data ?? []).filter(
        (p) =>
          p.title.toLowerCase().includes(q) ||
          p.excerpt?.toLowerCase().includes(q) ||
          p.tags?.some((t) => t.toLowerCase().includes(q))
      );
    },
  });

  const toggleLike = async (comment: DiscoverComment) => {
    const prev = liked[comment.id] ?? false;
    setLiked((m) => ({ ...m, [comment.id]: !prev }));
    try {
      await apiFetch(`/api/comment/${comment.id}/reactions`, {
        method: "POST",
        body: JSON.stringify({ reaction: "like" }),
      });
    } catch {
      setLiked((m) => ({ ...m, [comment.id]: prev }));
    }
  };

  return (
    <div>
      <div className="mb-4 flex items-center justify-between gap-3">
        <h1 className="text-2xl font-bold">اکتشاف</h1>
        <Button
          variant="ghost"
          size="icon"
          className="min-h-11 min-w-11"
          onClick={() => {
            setSearchActive((v) => !v);
            if (searchActive) setQuery("");
          }}
          aria-label={searchActive ? "بستن جستجو" : "جستجو"}
        >
          {searchActive ? <X className="h-5 w-5" /> : <Search className="h-5 w-5" />}
        </Button>
      </div>

      {searchActive && (
        <div className="relative mb-4">
          <Search className="absolute start-3 top-1/2 h-4 w-4 -translate-y-1/2 text-muted-foreground" />
          <input
            autoFocus
            value={query}
            onChange={(e) => setQuery(e.target.value)}
            placeholder="جستجوی پست‌ها..."
            className="w-full min-h-11 rounded-xl border bg-background py-2 pe-4 ps-9 text-sm"
          />
        </div>
      )}

      {searchActive && debounced.trim() ? (
        <div className="space-y-4">
          {searching && <Skeleton className="h-24 w-full" />}
          {!searching && (searchPosts?.length ?? 0) === 0 && (
            <p className="py-12 text-center text-muted-foreground">نتیجه‌ای یافت نشد</p>
          )}
          {searchPosts?.map((post) => (
            <PostCard key={post.id} post={post} />
          ))}
        </div>
      ) : loading ? (
        <div className="space-y-4">
          {[1, 2, 3, 4].map((i) => (
            <Skeleton key={i} className="h-28 w-full rounded-xl" />
          ))}
        </div>
      ) : comments.length === 0 ? (
        <p className="py-16 text-center text-muted-foreground">هنوز نظری برای اکتشاف نیست</p>
      ) : (
        <div>
          {comments.map((c) => (
            <CommentCard
              key={c.id}
              comment={c}
              liked={liked[c.id]}
              onLike={() => void toggleLike(c)}
            />
          ))}
        </div>
      )}
    </div>
  );
}
