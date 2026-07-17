"use client";

import { useMemo, useState } from "react";
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { apiFetch } from "@/lib/api-client";
import { Button } from "@/components/ui/button";
import { Avatar, AvatarFallback, AvatarImage } from "@/components/ui/avatar";
import { Skeleton } from "@/components/ui/skeleton";
import { getInitials } from "@/lib/utils";
import { useFormatDate } from "@/hooks/use-format-date";
import { useAuthStore } from "@/stores/auth-store";
import { cn } from "@/lib/utils";
import type { Comment, CommentListResponse } from "@/types/comment";

export function CommentSection({ postId }: { postId: string }) {
  const queryClient = useQueryClient();
  const user = useAuthStore((s) => s.user);
  const [newComment, setNewComment] = useState("");
  const [replyToId, setReplyToId] = useState<string | null>(null);
  const [replyDraft, setReplyDraft] = useState("");
  const [focusStack, setFocusStack] = useState<string[]>([]);

  const { data, isLoading } = useQuery({
    queryKey: ["comments", postId],
    queryFn: async () => {
      const res = await apiFetch<CommentListResponse>(
        `/api/posts/${postId}/comments?limit=50`
      );
      return res.data;
    },
  });

  const flatComments = useMemo(() => flattenComments(data ?? []), [data]);
  const replyParent = useMemo(
    () => (replyToId ? flatComments.find((c) => c.id === replyToId) ?? null : null),
    [flatComments, replyToId]
  );

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

  const handleSubmitTopLevel = (e: React.FormEvent) => {
    e.preventDefault();
    if (!newComment.trim()) return;
    createMutation.mutate({ content: newComment, parentId: null });
  };

  const handleSubmitReply = (e: React.FormEvent) => {
    e.preventDefault();
    if (!replyDraft.trim() || !replyToId) return;
    createMutation.mutate({ content: replyDraft, parentId: replyToId });
  };

  const handleDrillDown = (commentId: string) => {
    setFocusStack((stack) => [...stack, commentId]);
  };

  const handleFocusBack = () => {
    setFocusStack((stack) => stack.slice(0, -1));
  };

  return (
    <div>
      <div className="border-t border-border mb-4" />

      {replyParent ? (
        <TwitterStyleReplyCompose
          parent={replyParent}
          replyDraft={replyDraft}
          onReplyDraftChange={setReplyDraft}
          currentUserAvatar={user?.avatar_url}
          currentUserName={user?.display_name || user?.username || "You"}
          onCancel={() => {
            setReplyToId(null);
            setReplyDraft("");
          }}
          onSubmit={handleSubmitReply}
          isPending={createMutation.isPending}
        />
      ) : (
        <form onSubmit={handleSubmitTopLevel} className="mb-6">
          <textarea
            value={newComment}
            onChange={(e) => setNewComment(e.target.value)}
            placeholder="نوشتن نظر..."
            rows={3}
            className="w-full px-3 py-2 border rounded-lg bg-background resize-none text-sm"
          />
          <div className="flex justify-end mt-2">
            <Button type="submit" size="sm" disabled={createMutation.isPending}>
              {createMutation.isPending ? "در حال ارسال..." : "ارسال"}
            </Button>
          </div>
        </form>
      )}

      {focusCommentId ? (
        <div className="mb-3 flex items-center justify-between gap-2">
          <button
            type="button"
            onClick={handleFocusBack}
            className="text-sm text-primary hover:underline"
          >
            بازگشت
          </button>
          <span className="text-xs text-muted-foreground">نمایش شاخه پاسخ</span>
        </div>
      ) : null}

      {isLoading ? (
        <div className="space-y-4">
          {[1, 2, 3].map((i) => (
            <div key={i} className="flex gap-3">
              <Skeleton className="h-8 w-8 rounded-full" />
              <div className="flex-1">
                <Skeleton className="h-4 w-24 mb-1" />
                <Skeleton className="h-4 w-full" />
              </div>
            </div>
          ))}
        </div>
      ) : data?.length === 0 ? (
        <p className="text-sm text-muted-foreground">هنوز نظری ثبت نشده. اولین نفر باشید!</p>
      ) : (
        <div className="space-y-1">
          {visibleRoots.map((comment) => (
            <CommentThreadWindow
              key={comment.id}
              root={comment}
              onReply={(id) => {
                setReplyDraft("");
                setReplyToId(id);
              }}
              onDrillDown={handleDrillDown}
            />
          ))}
        </div>
      )}
    </div>
  );
}

/** Renders one focus-root comment + its direct replies (max 2 relative levels). */
function CommentThreadWindow({
  root,
  onReply,
  onDrillDown,
}: {
  root: Comment;
  onReply: (id: string) => void;
  onDrillDown: (id: string) => void;
}) {
  const replies = root.replies ?? [];
  const hasReplies = replies.length > 0;

  return (
    <div className="border-b border-border/40 pb-1 last:border-b-0">
      <CommentRow
        comment={root}
        showLineBelow={hasReplies}
        showLineAbove={false}
        onReply={onReply}
      />
      {replies.map((reply, index) => {
        const childCount = reply.replies?.length ?? 0;
        const isLast = index === replies.length - 1;
        return (
          <div key={reply.id}>
            <CommentRow
              comment={reply}
              showLineBelow={!isLast}
              showLineAbove
              onReply={onReply}
              drillDownCount={childCount > 0 ? childCount : undefined}
              onDrillDown={childCount > 0 ? () => onDrillDown(reply.id) : undefined}
            />
          </div>
        );
      })}
    </div>
  );
}

function CommentRow({
  comment,
  showLineBelow,
  showLineAbove,
  onReply,
  drillDownCount,
  onDrillDown,
}: {
  comment: Comment;
  showLineBelow: boolean;
  showLineAbove: boolean;
  onReply: (id: string) => void;
  drillDownCount?: number;
  onDrillDown?: () => void;
}) {
  const formatDate = useFormatDate();
  const authorName = comment.author?.display_name || comment.author?.username || "Unknown";
  const username = comment.author?.username;

  return (
    <div className="flex items-stretch gap-3 py-2">
      <div className="flex w-8 shrink-0 flex-col items-center self-stretch">
        {showLineAbove ? (
          <div className="mb-0.5 h-2 w-0.5 shrink-0 rounded-full bg-border" />
        ) : null}
        <Avatar className="h-8 w-8 shrink-0">
          {comment.author?.avatar_url ? (
            <AvatarImage src={comment.author.avatar_url} alt={authorName} />
          ) : null}
          <AvatarFallback>{getInitials(authorName)}</AvatarFallback>
        </Avatar>
        {showLineBelow ? (
          <div className="mt-0.5 w-0.5 min-h-[8px] flex-1 rounded-full bg-border" />
        ) : null}
      </div>
      <div className="min-w-0 flex-1">
        <div className="mb-0.5 flex flex-wrap items-center gap-2">
          <span className="text-sm font-medium">{authorName}</span>
          {username ? (
            <span className="text-xs text-muted-foreground">@{username}</span>
          ) : null}
          <span className="text-xs text-muted-foreground">{formatDate(comment.created_at)}</span>
        </div>
        <p className="text-sm whitespace-pre-wrap">{comment.content}</p>
        <div className="mt-1 flex flex-wrap items-center gap-3">
          <button
            type="button"
            onClick={() => onReply(comment.id)}
            className="text-xs text-muted-foreground hover:text-primary"
          >
            پاسخ
          </button>
          {onDrillDown && drillDownCount != null && drillDownCount > 0 ? (
            <button
              type="button"
              onClick={onDrillDown}
              className="text-xs font-medium text-primary hover:underline"
            >
              {drillDownCount} پاسخ
            </button>
          ) : null}
        </div>
      </div>
    </div>
  );
}

function TwitterStyleReplyCompose({
  parent,
  replyDraft,
  onReplyDraftChange,
  currentUserAvatar,
  currentUserName,
  onCancel,
  onSubmit,
  isPending,
}: {
  parent: Comment;
  replyDraft: string;
  onReplyDraftChange: (value: string) => void;
  currentUserAvatar?: string;
  currentUserName: string;
  onCancel: () => void;
  onSubmit: (e: React.FormEvent) => void;
  isPending: boolean;
}) {
  const formatDate = useFormatDate();
  const parentName = parent.author?.display_name || parent.author?.username || "Unknown";
  const parentUsername = parent.author?.username || "user";
  const parentInitials = getInitials(parentName);
  const meInitials = getInitials(currentUserName);

  return (
    <form
      onSubmit={onSubmit}
      className="mb-6 rounded-xl border border-border/60 bg-background p-4"
    >
      <div className="mb-3 flex items-center justify-between gap-2">
        <button
          type="button"
          onClick={onCancel}
          className="text-sm text-muted-foreground hover:text-foreground"
        >
          بستن
        </button>
        <Button type="submit" size="sm" disabled={isPending || !replyDraft.trim()}>
          {isPending ? "..." : "پاسخ"}
        </Button>
      </div>

      <div className="flex flex-col">
        <div className="flex gap-3">
          <div className="flex w-10 shrink-0 flex-col items-center">
            <Avatar className="h-10 w-10">
              {parent.author?.avatar_url ? (
                <AvatarImage src={parent.author.avatar_url} alt={parentName} />
              ) : null}
              <AvatarFallback>{parentInitials}</AvatarFallback>
            </Avatar>
            <div className="mt-1 w-0.5 min-h-full flex-1 rounded-full bg-border" />
          </div>
          <div className="min-w-0 flex-1 pb-3">
            <div className="flex flex-wrap items-center gap-2">
              <span className="text-sm font-semibold">{parentName}</span>
              <span className="text-xs text-muted-foreground">
                @{parentUsername} · {formatDate(parent.created_at)}
              </span>
            </div>
            <p className="mt-1 text-sm whitespace-pre-wrap">{parent.content}</p>
          </div>
        </div>

        <div className="flex gap-3">
          <div className="flex w-10 shrink-0 flex-col items-center">
            <div className="h-2 w-0.5 rounded-full bg-border" />
            <Avatar className="h-10 w-10">
              {currentUserAvatar ? (
                <AvatarImage src={currentUserAvatar} alt={currentUserName} />
              ) : null}
              <AvatarFallback>{meInitials}</AvatarFallback>
            </Avatar>
          </div>
          <div className="min-w-0 flex-1">
            <p className="text-sm text-muted-foreground">
              در حال پاسخ به <span className="text-primary">@{parentUsername}</span>
            </p>
            <textarea
              value={replyDraft}
              onChange={(e) => onReplyDraftChange(e.target.value)}
              placeholder="پاسخ خود را بنویسید"
              rows={3}
              autoFocus
              className={cn(
                "mt-1 w-full resize-none bg-transparent text-sm outline-none",
                "placeholder:text-muted-foreground"
              )}
            />
          </div>
        </div>
      </div>
    </form>
  );
}

function flattenComments(comments: Comment[]): Comment[] {
  const result: Comment[] = [];
  const walk = (items: Comment[]) => {
    for (const item of items) {
      result.push(item);
      if (item.replies?.length) walk(item.replies);
    }
  };
  walk(comments);
  return result;
}

function findCommentById(comments: Comment[], id: string): Comment | null {
  for (const comment of comments) {
    if (comment.id === id) return comment;
    if (comment.replies?.length) {
      const found = findCommentById(comment.replies, id);
      if (found) return found;
    }
  }
  return null;
}
