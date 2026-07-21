"use client";

import { useCallback, useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import { Search, X } from "lucide-react";
import { useQuery } from "@tanstack/react-query";
import { apiFetch } from "@/lib/api-client";
import { CommentCard, type DiscoverComment } from "@/components/discover/comment-card";
import { PostCard } from "@/components/post/post-card";
import { Skeleton } from "@/components/ui/skeleton";
import { Button } from "@/components/ui/button";
import { bookmarkComment, unbookmarkComment } from "@/lib/api/bookmarks";
import {
  commentDislikeCount,
  commentLikeCount,
  hasViewerReaction,
  nextVoteState,
  toggleCommentVote,
  type CommentVote,
} from "@/lib/comment-reactions";
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
  const likes = commentLikeCount(c.reactions, api.like_count ?? 0);
  const dislikes = commentDislikeCount(c.reactions);
  return {
    ...c,
    post_slug: extras?.post_slug ?? api.post?.slug ?? extras?.post_slug,
    post_title: extras?.post_title ?? api.post?.title ?? extras?.post_title,
    post_author_username: extras?.post_author_username,
    author_username: c.author?.username,
    reply_count: api.reply_count ?? c.replies?.length ?? 0,
    reactions: {
      ...(c.reactions ?? {}),
      like: likes,
      dislike: dislikes,
    },
    viewer_reactions: c.viewer_reactions,
    is_bookmarked: c.is_bookmarked ?? false,
  };
}

function postHref(comment: DiscoverComment) {
  const postSlug = comment.post_slug?.trim() || "";
  const postAuthor = (comment.post_author_username || "").trim();
  if (!postSlug || !postAuthor) return null;
  return `/${postAuthor}/${postSlug}?reply=${encodeURIComponent(comment.id)}`;
}

type InterestItem = { id: string; slug: string; name_fa?: string; name?: string };

export default function DiscoverPage() {
  const router = useRouter();
  const [searchActive, setSearchActive] = useState(false);
  const [query, setQuery] = useState("");
  const debounced = useDebouncedValue(query, 300);
  const [comments, setComments] = useState<DiscoverComment[]>([]);
  const [loading, setLoading] = useState(true);
  const [info, setInfo] = useState<string | null>(null);
  const [interest, setInterest] = useState<string>("");

  const { data: interests = [] } = useQuery({
    queryKey: ["discover-interests"],
    queryFn: async () => {
      try {
        const mine = await apiFetch<{ interests?: InterestItem[] } | InterestItem[]>(
          "/api/users/me/interests"
        );
        const list = Array.isArray(mine) ? mine : mine.interests ?? [];
        if (list.length) return list;
      } catch {
        // fall through
      }
      const all = await apiFetch<{ interests?: InterestItem[] } | InterestItem[]>(
        "/api/interests"
      );
      return Array.isArray(all) ? all : all.interests ?? [];
    },
  });

  const refresh = useCallback(async () => {
    setLoading(true);
    try {
      try {
        const qs = new URLSearchParams({ limit: "50" });
        if (interest) qs.set("interest", interest);
        const discoverRes = await apiFetch<{ data?: DiscoverApiComment[] }>(
          `/api/discover/comments?${qs.toString()}`
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
  }, [interest]);

  useEffect(() => {
    void refresh();
  }, [refresh]);

  useEffect(() => {
    if (!info) return;
    const t = setTimeout(() => setInfo(null), 2500);
    return () => clearTimeout(t);
  }, [info]);

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

  const patchComment = (id: string, patch: Partial<DiscoverComment>) => {
    setComments((list) => list.map((c) => (c.id === id ? { ...c, ...patch } : c)));
  };

  const toggleVote = async (comment: DiscoverComment, reaction: CommentVote) => {
    const liked = hasViewerReaction(comment.viewer_reactions, "like");
    const disliked = hasViewerReaction(comment.viewer_reactions, "dislike");
    const currentlyActive = reaction === "like" ? liked : disliked;
    const oppositeActive = reaction === "like" ? disliked : liked;
    const likeCount = commentLikeCount(comment.reactions, comment.like_count ?? 0);
    const dislikeCount = commentDislikeCount(comment.reactions);
    const next = nextVoteState(
      reaction,
      currentlyActive,
      oppositeActive,
      likeCount,
      dislikeCount
    );
    const prev = comment;
    patchComment(comment.id, {
      reactions: { ...comment.reactions, like: next.likeCount, dislike: next.dislikeCount },
      like_count: next.likeCount,
      viewer_reactions: [
        ...(next.liked ? (["like"] as const) : []),
        ...(next.disliked ? (["dislike"] as const) : []),
      ],
    });
    try {
      await toggleCommentVote(comment.id, reaction, currentlyActive, oppositeActive);
    } catch {
      patchComment(prev.id, prev);
    }
  };

  const toggleBookmark = async (comment: DiscoverComment) => {
    const prev = comment.is_bookmarked ?? false;
    patchComment(comment.id, { is_bookmarked: !prev });
    try {
      if (prev) await unbookmarkComment(comment.id);
      else await bookmarkComment(comment.id);
    } catch {
      patchComment(comment.id, { is_bookmarked: prev });
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

      {info ? (
        <p className="mb-3 rounded-xl bg-muted px-3 py-2 text-sm text-muted-foreground">{info}</p>
      ) : null}

      {!searchActive && interests.length > 0 ? (
        <div className="mb-4 flex flex-wrap gap-2">
          <button
            type="button"
            onClick={() => setInterest("")}
            className={`min-h-9 rounded-full px-3 text-xs font-medium ${
              !interest
                ? "bg-primary text-primary-foreground"
                : "bg-muted text-muted-foreground hover:bg-accent"
            }`}
          >
            همه
          </button>
          {interests.map((item) => (
            <button
              key={item.id || item.slug}
              type="button"
              onClick={() => setInterest(item.slug)}
              className={`min-h-9 rounded-full px-3 text-xs font-medium ${
                interest === item.slug
                  ? "bg-primary text-primary-foreground"
                  : "bg-muted text-muted-foreground hover:bg-accent"
              }`}
            >
              {item.name_fa || item.name || item.slug}
            </button>
          ))}
        </div>
      ) : null}

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
          {comments.map((c) => {
            const href = postHref(c);
            return (
              <CommentCard
                key={c.id}
                comment={c}
                liked={hasViewerReaction(c.viewer_reactions, "like")}
                disliked={hasViewerReaction(c.viewer_reactions, "dislike")}
                bookmarked={c.is_bookmarked}
                onReply={href ? () => router.push(href) : undefined}
                onLike={() => void toggleVote(c, "like")}
                onDislike={() => void toggleVote(c, "dislike")}
                onBookmark={() => void toggleBookmark(c)}
                onReport={() => setInfo("گزارش ثبت شد")}
              />
            );
          })}
        </div>
      )}
    </div>
  );
}
