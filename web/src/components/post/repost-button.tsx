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

interface RepostButtonProps {
  postId: string;
  postSlug?: string;
  repostCount?: number;
  reposted?: boolean;
  className?: string;
}

export function RepostButton({
  postId,
  postSlug,
  repostCount: initialCount = 0,
  reposted: initialReposted = false,
  className,
}: RepostButtonProps) {
  const router = useRouter();
  const role = useAuthStore((s) => s.user?.role);
  const [reposted, setReposted] = useState(initialReposted);
  const [count, setCount] = useState(initialCount);
  const [open, setOpen] = useState(false);

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
    },
    onError: (_err, _vars, previous) => {
      if (!previous) return;
      setReposted(previous.reposted);
      setCount(previous.count);
    },
  });

  if (!canRepost(role)) return null;

  const quoteTarget = postSlug || postId;

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
          aria-label="بازنشر"
          aria-pressed={reposted}
        >
          <span className="tabular-nums text-sm">{count}</span>
          <Repeat2 className="h-4 w-4 shrink-0" />
        </Button>
      </DropdownMenuTrigger>
      <DropdownMenuContent align="end" className="min-w-[12rem]">
        <DropdownMenuItem
          onSelect={() => {
            mutation.mutate();
          }}
        >
          <Repeat2 className="h-4 w-4 shrink-0" />
          <span className="min-w-0">{reposted ? "لغو بازنشر" : "بازنشر"}</span>
        </DropdownMenuItem>
        <DropdownMenuItem
          onSelect={() => {
            router.push(`/quote/${encodeURIComponent(quoteTarget)}`);
          }}
        >
          <PencilLine className="h-4 w-4 shrink-0" />
          <span className="min-w-0">نقل‌قول</span>
        </DropdownMenuItem>
      </DropdownMenuContent>
    </DropdownMenu>
  );
}
