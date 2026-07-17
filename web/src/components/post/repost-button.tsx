"use client";

import { useState } from "react";
import { useMutation } from "@tanstack/react-query";
import { Repeat2 } from "lucide-react";
import { apiFetch } from "@/lib/api-client";
import { Button } from "@/components/ui/button";
import { cn } from "@/lib/utils";

interface RepostButtonProps {
  postId: string;
  repostCount?: number;
  reposted?: boolean;
  className?: string;
}

export function RepostButton({
  postId,
  repostCount: initialCount = 0,
  reposted: initialReposted = false,
  className,
}: RepostButtonProps) {
  const [reposted, setReposted] = useState(initialReposted);
  const [count, setCount] = useState(initialCount);

  const mutation = useMutation({
    mutationFn: () =>
      apiFetch<{ reposted: boolean; repost_count: number }>(
        `/api/posts/${postId}/repost`,
        { method: reposted ? "DELETE" : "POST" }
      ),
    onMutate: () => {
      const previous = { reposted, count };
      const next = !reposted;
      setReposted(next);
      setCount((c) => Math.max(0, c + (next ? 1 : -1)));
      return previous;
    },
    onSuccess: (data) => {
      setReposted(data.reposted);
      setCount(data.repost_count);
      // Keep local engagement state only — do not refetch/reorder the feed.
    },
    onError: (_err, _vars, previous) => {
      if (!previous) return;
      setReposted(previous.reposted);
      setCount(previous.count);
    },
  });

  return (
    <Button
      variant="ghost"
      size="sm"
      onClick={() => mutation.mutate()}
      disabled={mutation.isPending}
      className={cn(
        "gap-1.5 text-muted-foreground",
        reposted && "text-emerald-600",
        className
      )}
      aria-label="Repost"
      aria-pressed={reposted}
    >
      <span className="tabular-nums text-sm">{count}</span>
      <Repeat2 className="h-4 w-4 shrink-0" />
    </Button>
  );
}
