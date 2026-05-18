"use client";

import { useState } from "react";
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { apiFetch } from "@/lib/api-client";
import { Button } from "@/components/ui/button";
import { Avatar, AvatarFallback } from "@/components/ui/avatar";
import { Skeleton } from "@/components/ui/skeleton";
import { formatDate, getInitials } from "@/lib/utils";
import type { Comment, CommentListResponse } from "@/types/comment";

export function CommentSection({ postId }: { postId: string }) {
  const queryClient = useQueryClient();
  const [newComment, setNewComment] = useState("");
  const [replyTo, setReplyTo] = useState<string | null>(null);

  const { data, isLoading } = useQuery({
    queryKey: ["comments", postId],
    queryFn: async () => {
      const res = await apiFetch<CommentListResponse>(
        `/api/posts/${postId}/comments?limit=50`
      );
      return res.data;
    },
  });

  const createMutation = useMutation({
    mutationFn: (content: string) =>
      apiFetch<Comment>(`/api/posts/${postId}/comments`, {
        method: "POST",
        body: JSON.stringify({ content, parent_id: replyTo }),
      }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["comments", postId] });
      setNewComment("");
      setReplyTo(null);
    },
  });

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    if (!newComment.trim()) return;
    createMutation.mutate(newComment);
  };

  return (
    <div>
      <h3 className="text-lg font-semibold mb-4">Comments</h3>

      <form onSubmit={handleSubmit} className="mb-6">
        {replyTo && (
          <p className="text-sm text-muted-foreground mb-2">
            Replying to a comment{" "}
            <button type="button" onClick={() => setReplyTo(null)} className="text-primary underline">
              Cancel
            </button>
          </p>
        )}
        <textarea
          value={newComment}
          onChange={(e) => setNewComment(e.target.value)}
          placeholder="Write a comment..."
          rows={3}
          className="w-full px-3 py-2 border rounded-lg bg-background resize-none text-sm"
        />
        <div className="flex justify-end mt-2">
          <Button type="submit" size="sm" disabled={createMutation.isPending}>
            {createMutation.isPending ? "Posting..." : "Post"}
          </Button>
        </div>
      </form>

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
        <p className="text-sm text-muted-foreground">No comments yet. Be the first!</p>
      ) : (
        <div className="space-y-4">
          {data?.map((comment) => (
            <CommentItem key={comment.id} comment={comment} onReply={setReplyTo} />
          ))}
        </div>
      )}
    </div>
  );
}

function CommentItem({
  comment,
  onReply,
}: {
  comment: Comment;
  onReply: (id: string) => void;
}) {
  const [collapsed, setCollapsed] = useState(false);
  const authorName = comment.author?.display_name || comment.author?.username || "Unknown";

  return (
    <div className={`${comment.depth > 0 ? "ml-6 pl-4 border-l" : ""}`}>
      <div className="flex gap-3 py-2">
        <Avatar className="h-8 w-8 mt-0.5">
          <AvatarFallback>{getInitials(authorName)}</AvatarFallback>
        </Avatar>
        <div className="flex-1 min-w-0">
          <div className="flex items-center gap-2 mb-0.5">
            <span className="text-sm font-medium">{authorName}</span>
            <span className="text-xs text-muted-foreground">{formatDate(comment.created_at)}</span>
          </div>
          <p className="text-sm">{comment.content}</p>
          <div className="flex gap-3 mt-1">
            <button onClick={() => onReply(comment.id)} className="text-xs text-muted-foreground hover:text-primary">
              Reply
            </button>
            {comment.replies && comment.replies.length > 0 && (
              <button
                onClick={() => setCollapsed(!collapsed)}
                className="text-xs text-muted-foreground hover:text-primary"
              >
                {collapsed ? `Show ${comment.replies.length} replies` : "Hide replies"}
              </button>
            )}
          </div>

          {comment.replies && !collapsed && (
            <div className="mt-2">
              {comment.replies.map((reply) => (
                <CommentItem key={reply.id} comment={reply} onReply={onReply} />
              ))}
            </div>
          )}
        </div>
      </div>
    </div>
  );
}
