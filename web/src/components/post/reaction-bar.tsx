"use client";

import { useState, useCallback } from "react";
import { Heart } from "lucide-react";
import { cn } from "@/lib/utils";
import { apiFetch } from "@/lib/api-client";

interface ReactionBarProps {
  targetType: "post" | "comment";
  targetId: string;
  reactions?: Record<string, number>;
  viewerReactions?: string[];
  className?: string;
}

function likeCountOf(reactions?: Record<string, number>) {
  return reactions?.like ?? reactions?.heart ?? 0;
}

function isLiked(viewerReactions?: string[]) {
  return (
    viewerReactions?.includes("like") === true ||
    viewerReactions?.includes("heart") === true
  );
}

/** Post like control — heart only (no thumbs-up / emoji reactions). */
export function ReactionBar({
  targetType,
  targetId,
  reactions,
  viewerReactions,
  className,
}: ReactionBarProps) {
  const [count, setCount] = useState(likeCountOf(reactions));
  const [liked, setLiked] = useState(isLiked(viewerReactions));
  const [animating, setAnimating] = useState(false);

  const toggleLike = useCallback(async () => {
    const prevLiked = liked;
    const prevCount = count;
    setLiked(!prevLiked);
    setCount((n) => (prevLiked ? Math.max(0, n - 1) : n + 1));
    setAnimating(true);

    try {
      await apiFetch(`/api/${targetType}/${targetId}/reactions`, {
        method: "POST",
        body: JSON.stringify({ reaction: "like" }),
      });
    } catch {
      setLiked(prevLiked);
      setCount(prevCount);
    }

    setTimeout(() => setAnimating(false), 300);
  }, [liked, count, targetType, targetId]);

  return (
    <div className={cn("flex items-center gap-1", className)}>
      <button
        type="button"
        onClick={() => void toggleLike()}
        className={cn(
          "relative inline-flex items-center gap-1.5 rounded-full px-3 py-1.5 text-xs transition-transform hover:bg-accent active:scale-110",
          liked && "text-[#F91880]"
        )}
        title="پسندیدن"
        aria-label="پسندیدن"
        aria-pressed={liked}
      >
        <Heart className={cn("h-4 w-4", liked && "fill-current")} />
        {count > 0 ? <span className={cn(!liked && "text-muted-foreground")}>{count}</span> : null}
        {animating && liked ? (
          <span className="pointer-events-none absolute -top-6 left-1/2 -translate-x-1/2 animate-pulse">
            <Heart className="h-4 w-4 fill-current text-[#F91880]" />
          </span>
        ) : null}
      </button>
    </div>
  );
}
