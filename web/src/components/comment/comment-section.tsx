"use client";

import { useEffect, useMemo, useState } from "react";
import Link from "next/link";
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { apiFetch } from "@/lib/api-client";
import { Button } from "@/components/ui/button";
import { Avatar, AvatarFallback, AvatarImage } from "@/components/ui/avatar";
import { Skeleton } from "@/components/ui/skeleton";
import { getInitials, cn } from "@/lib/utils";
import { TimeLabel, UsernameHandle } from "@/components/user/username-handle";
import { useFormatDate } from "@/hooks/use-format-date";
import { useAuthStore } from "@/stores/auth-store";
import type { Comment, CommentListResponse } from "@/types/comment";

type SortKey = "newest" | "oldest" | "most_reacted" | "most_replied";

const SORT_LABELS: { key: SortKey; label: string }[] = [
  { key: "newest", label: "جدیدترین" },
  { key: "oldest", label: "قدیمی‌ترین" },
  { key: "most_reacted", label: "بیشترین واکنش" },
  { key: "most_replied", label: "بیشترین پاسخ" },
];

export function CommentSection({
  postId,
  initialReplyTo,
}: {
  postId: string;
  initialReplyTo?: string | null;
}) {
  const queryClient = useQueryClient();
  const user = useAuthStore((s) => s.user);
  const [newComment, setNewComment] = useState("");
  const [replyToId, setReplyToId] = useState<string | null>(initialReplyTo ?? null);
  const [replyDraft, setReplyDraft] = useState("");
  const [sort, setSort] = useState<SortKey>("newest");
  const [focusStack, setFocusStack] = useState<string[]>([]);

  const { data, isLoading, isError, refetch } = useQuery({
    queryKey: ["comments", postId, sort],
    queryFn: async () => {
      const res = await apiFetch<CommentListResponse>(
        `/api/posts/${postId}/comments?limit=50&sort=${sort}`
      );
      return res.data ?? [];
    },
  });

  const flatComments = useMemo(() => flattenComments(data ?? []), [data]);
  const replyParent = useMemo(
    () => (replyToId ? flatComments.find((c) => c.id === replyToId) ?? null : null),
    [flatComments, replyToId]
  );

  useEffect(() => {
    if (!initialReplyTo) return;
    setReplyToId(initialReplyTo);
  }, [initialReplyTo]);

  // When deep-linking to a nested reply, open its branch so the composer is visible.
  useEffect(() => {
    if (!initialReplyTo || !data?.length) return;
    const path = pathToComment(data, initialReplyTo);
    if (path.length > 1) {
      setFocusStack(path.slice(0, -1));
    }
  }, [initialReplyTo, data]);

  const focusCommentId = focusStack[focusStack.length - 1] ?? null;
  const visibleRoots = useMemo(() => {
    const tree = data ?? [];
    if (!focusCommentId) return tree;
    const focused = findCommentById(tree, focusCommentId);
    return focused ? [focused] : tree;
  }, [data, focusCommentId]);

  const createMutation = useMutation({
    mutationFn: ({ content, parentId }: { content: string; parentId?: string | null }) =>
      apiFetch<Comment>(`/api/posts/${postId}/comments`, {
        method: "POST",
        body: JSON.stringify({ content, parent_id: parentId ?? undefined }),
      }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["comments", postId] });
      setNewComment("");
      setReplyDraft("");
      setReplyToId(null);
    },
  });

  if (isLoading) {
    return (
      <div className="space-y-4">
        {[1, 2, 3].map((i) => (
          <Skeleton key={i} className="h-20 w-full rounded-2xl" />
        ))}
      </div>
    );
  }

  if (isError) {
    return (
      <div className="space-y-3 py-8 text-center">
        <p className="text-muted-foreground">خطا در بارگذاری نظرات</p>
        <Button type="button" variant="outline" onClick={() => void refetch()}>
          تلاش مجدد
        </Button>
      </div>
    );
  }

  return (
    <section className="space-y-4">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <h2 className="flex items-center gap-2 text-lg font-bold">
          <span className="min-w-0">نظرات</span>
        </h2>
        <div className="flex flex-wrap gap-1">
          {SORT_LABELS.map((s) => (
            <button
              key={s.key}
              type="button"
              onClick={() => setSort(s.key)}
              className={cn(
                "min-h-9 rounded-full px-3 text-xs font-medium",
                sort === s.key
                  ? "bg-primary text-primary-foreground"
                  : "bg-muted text-muted-foreground hover:bg-accent"
              )}
            >
              {s.label}
            </button>
          ))}
        </div>
      </div>

      {focusCommentId && (
        <Button variant="ghost" size="sm" onClick={() => setFocusStack((s) => s.slice(0, -1))}>
          ← بازگشت به رشته
        </Button>
      )}

      {user ? (
        <form
          onSubmit={(e) => {
            e.preventDefault();
            if (!newComment.trim()) return;
            createMutation.mutate({ content: newComment, parentId: null });
          }}
          className="flex gap-2"
        >
          <input
            value={newComment}
            onChange={(e) => setNewComment(e.target.value)}
            placeholder="نظر خود را بنویسید..."
            className="min-h-11 flex-1 rounded-2xl border bg-background px-4 text-sm"
          />
          <Button type="submit" className="min-h-11" disabled={createMutation.isPending}>
            ارسال
          </Button>
        </form>
      ) : (
        <p className="text-sm text-muted-foreground">برای نظر دادن وارد شوید.</p>
      )}

      <div className="relative space-y-3 ps-4">
        <div className="absolute bottom-2 start-0 top-2 w-px bg-border" aria-hidden />
        {visibleRoots.length === 0 ? (
          <p className="py-8 text-center text-muted-foreground">هنوز نظری نیست</p>
        ) : (
          visibleRoots.map((c) => (
            <CommentBubble
              key={c.id}
              comment={c}
              currentUserId={user?.id}
              replyToId={replyToId}
              replyDraft={replyDraft}
              onReplyDraft={setReplyDraft}
              onReply={(id) => setReplyToId(id)}
              onCancelReply={() => {
                setReplyToId(null);
                setReplyDraft("");
              }}
              onSubmitReply={() => {
                if (!replyDraft.trim() || !replyToId) return;
                createMutation.mutate({ content: replyDraft, parentId: replyToId });
              }}
              onDrill={(id) => setFocusStack((s) => [...s, id])}
              depth={0}
            />
          ))
        )}
      </div>

      {replyToId && !replyParent && (
        <div className="rounded-2xl border bg-muted/40 p-3">
          <p className="mb-2 text-xs text-muted-foreground">پاسخ به نظر</p>
          <form
            className="flex gap-2"
            onSubmit={(e) => {
              e.preventDefault();
              if (!replyDraft.trim()) return;
              createMutation.mutate({ content: replyDraft, parentId: replyToId });
            }}
          >
            <input
              value={replyDraft}
              onChange={(e) => setReplyDraft(e.target.value)}
              className="min-h-11 flex-1 rounded-2xl border bg-background px-3 text-sm"
              placeholder="پاسخ..."
              autoFocus
            />
            <Button type="submit" size="sm" className="min-h-11">
              ارسال
            </Button>
            <Button
              type="button"
              size="sm"
              variant="ghost"
              className="min-h-11"
              onClick={() => {
                setReplyToId(null);
                setReplyDraft("");
              }}
            >
              لغو
            </Button>
          </form>
        </div>
      )}

      {replyParent && (
        <p className="text-xs text-muted-foreground">
          پاسخ به {replyParent.author?.display_name || replyParent.author?.username}
        </p>
      )}
    </section>
  );
}

function CommentBubble({
  comment,
  currentUserId,
  replyToId,
  replyDraft,
  onReplyDraft,
  onReply,
  onCancelReply,
  onSubmitReply,
  onDrill,
  depth,
}: {
  comment: Comment;
  currentUserId?: string;
  replyToId: string | null;
  replyDraft: string;
  onReplyDraft: (v: string) => void;
  onReply: (id: string) => void;
  onCancelReply: () => void;
  onSubmitReply: () => void;
  onDrill: (id: string) => void;
  depth: number;
}) {
  const formatDate = useFormatDate();
  const isOwn = currentUserId && comment.author_id === currentUserId;
  const name = comment.author?.display_name || comment.author?.username || "کاربر";
  const username = comment.author?.username?.trim() || "";
  const profileHref = username ? `/${username}` : null;
  const reactionEntries = Object.entries(comment.reactions ?? {}).filter(([, n]) => n > 0);

  return (
    <div className={cn("relative", depth > 0 && "ms-4")} id={`comment-${comment.id}`}>
      <div className="mb-1 flex items-center gap-2">
        {profileHref ? (
          <Link href={profileHref} className="shrink-0">
            <Avatar className="h-8 w-8">
              {comment.author?.avatar_url ? (
                <AvatarImage src={comment.author.avatar_url} alt="" />
              ) : null}
              <AvatarFallback className="text-xs">{getInitials(name)}</AvatarFallback>
            </Avatar>
          </Link>
        ) : (
          <Avatar className="h-8 w-8 shrink-0">
            {comment.author?.avatar_url ? (
              <AvatarImage src={comment.author.avatar_url} alt="" />
            ) : null}
            <AvatarFallback className="text-xs">{getInitials(name)}</AvatarFallback>
          </Avatar>
        )}
        {profileHref ? (
          <Link href={profileHref} className="min-w-0 text-sm font-semibold hover:underline">
            {name}
          </Link>
        ) : (
          <span className="min-w-0 text-sm font-semibold">{name}</span>
        )}
        {username ? (
          profileHref ? (
            <Link href={profileHref} className="text-xs text-muted-foreground hover:underline">
              <UsernameHandle username={username} />
            </Link>
          ) : (
            <UsernameHandle username={username} className="text-xs text-muted-foreground" />
          )
        ) : null}
        <TimeLabel className="text-xs text-muted-foreground">
          {formatDate(comment.created_at)}
        </TimeLabel>
      </div>

      <div
        className={cn(
          "rounded-[1rem] px-3.5 py-3 text-[15px] leading-relaxed",
          isOwn ? "bg-bubble-own" : "bg-bubble-others"
        )}
      >
        <p className="whitespace-pre-wrap">{comment.content}</p>
        <div className="mt-2 flex flex-wrap items-end justify-between gap-2">
          <div className="flex flex-wrap gap-1">
            {reactionEntries.map(([emoji, count]) => (
              <span
                key={emoji}
                className="rounded-full bg-background/60 px-2 py-0.5 text-xs"
              >
                {emoji === "like" || emoji === "heart" ? "❤️" : emoji === "flame" ? "🔥" : "👍"}{" "}
                {count}
              </span>
            ))}
          </div>
          <div className="flex gap-2 text-xs text-muted-foreground">
            <button type="button" className="min-h-8 hover:text-primary" onClick={() => onReply(comment.id)}>
              پاسخ
            </button>
            {(comment.replies?.length ?? 0) > 0 && (
              <button
                type="button"
                className="min-h-8 hover:text-primary"
                onClick={() => onDrill(comment.id)}
              >
                {comment.replies!.length} پاسخ
              </button>
            )}
          </div>
        </div>
      </div>

      {replyToId === comment.id && (
        <form
          className="mt-2 flex gap-2"
          onSubmit={(e) => {
            e.preventDefault();
            onSubmitReply();
          }}
        >
          <input
            value={replyDraft}
            onChange={(e) => onReplyDraft(e.target.value)}
            className="min-h-11 flex-1 rounded-2xl border bg-background px-3 text-sm"
            placeholder="پاسخ..."
            autoFocus
          />
          <Button type="submit" size="sm" className="min-h-11">
            ارسال
          </Button>
          <Button type="button" size="sm" variant="ghost" className="min-h-11" onClick={onCancelReply}>
            لغو
          </Button>
        </form>
      )}

      {comment.replies?.map((r) => (
        <div key={r.id} className="mt-3">
          <CommentBubble
            comment={r}
            currentUserId={currentUserId}
            replyToId={replyToId}
            replyDraft={replyDraft}
            onReplyDraft={onReplyDraft}
            onReply={onReply}
            onCancelReply={onCancelReply}
            onSubmitReply={onSubmitReply}
            onDrill={onDrill}
            depth={depth + 1}
          />
        </div>
      ))}
    </div>
  );
}

function flattenComments(comments: Comment[]): Comment[] {
  const out: Comment[] = [];
  const walk = (list: Comment[]) => {
    for (const c of list) {
      out.push(c);
      if (c.replies?.length) walk(c.replies);
    }
  };
  walk(comments);
  return out;
}

function findCommentById(comments: Comment[], id: string): Comment | null {
  for (const c of comments) {
    if (c.id === id) return c;
    if (c.replies?.length) {
      const found = findCommentById(c.replies, id);
      if (found) return found;
    }
  }
  return null;
}

/** Ancestor ids from root to target (inclusive). Empty if not found. */
function pathToComment(comments: Comment[], id: string, trail: string[] = []): string[] {
  for (const c of comments) {
    const next = [...trail, c.id];
    if (c.id === id) return next;
    if (c.replies?.length) {
      const found = pathToComment(c.replies, id, next);
      if (found.length) return found;
    }
  }
  return [];
}
