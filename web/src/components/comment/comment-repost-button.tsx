"use client";

import { useState } from "react";
import { useRouter } from "next/navigation";
import { useMutation } from "@tanstack/react-query";
import { PencilLine, Repeat2 } from "lucide-react";
import { apiFetch } from "@/lib/api-client";
import { canRepost } from "@/lib/auth/permissions";
import { Button } from "@/components/ui/button";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";
import { cn } from "@/lib/utils";
import { useAuthStore } from "@/stores/auth-store";

interface CommentRepostButtonProps {
  commentId: string;
  repostCount?: number;
  reposted?: boolean;
  className?: string;
  /** When false, show count-only (readers). */
  interactive?: boolean;
}

export function CommentRepostButton({
  commentId,
  repostCount: initialCount = 0,
  reposted: initialReposted = false,
  className,
  interactive = true,
}: CommentRepostButtonProps) {
  const router = useRouter();
  const role = useAuthStore((s) => s.user?.role);
  const [reposted, setReposted] = useState(initialReposted);
  const [count, setCount] = useState(initialCount);
  const [open, setOpen] = useState(false);
  const can = canRepost(role) && interactive;

  const mutation = useMutation({
    mutationFn: () =>
      apiFetch<{ reposted: boolean; repost_count: number }>(
        `/api/comments/${commentId}/repost`,
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
    },
    onError: (_err, _vars, previous) => {
      if (!previous) return;
      setReposted(previous.reposted);
      setCount(previous.count);
    },
  });

  if (!can && count <= 0) return null;

  if (!can) {
    return (
      <span
        className={cn(
          "inline-flex items-center gap-1.5 px-1 py-1.5 text-xs tabular-nums text-muted-foreground",
          className
        )}
        title="تقویت‌شده"
      >
        <Repeat2 className="h-4 w-4 shrink-0" />
        {count}
      </span>
    );
  }

  return (
    <DropdownMenu open={open} onOpenChange={setOpen}>
      <DropdownMenuTrigger asChild>
        <Button
          variant="ghost"
          size="sm"
          disabled={mutation.isPending}
          className={cn(
            "gap-1.5 text-muted-foreground",
            reposted && "text-emerald-600",
            className
          )}
          aria-label="تقویت نظر"
          aria-pressed={reposted}
          onClick={(e) => {
            e.preventDefault();
            e.stopPropagation();
          }}
        >
          <Repeat2 className="h-4 w-4 shrink-0" />
          <span className="tabular-nums text-sm">{count}</span>
        </Button>
      </DropdownMenuTrigger>
      <DropdownMenuContent align="end" className="min-w-[12rem]">
        <DropdownMenuItem
          onSelect={() => {
            mutation.mutate();
          }}
        >
          <Repeat2 className="h-4 w-4 shrink-0" />
          <span className="min-w-0">{reposted ? "لغو تقویت" : "تقویت"}</span>
        </DropdownMenuItem>
        <DropdownMenuItem
          onSelect={() => {
            router.push(`/quote/comment/${encodeURIComponent(commentId)}`);
          }}
        >
          <PencilLine className="h-4 w-4 shrink-0" />
          <span className="min-w-0">نقل‌قول</span>
        </DropdownMenuItem>
      </DropdownMenuContent>
    </DropdownMenu>
  );
}
