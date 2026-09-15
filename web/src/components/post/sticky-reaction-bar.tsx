"use client";

import { useChromeVisibility } from "@/hooks/use-chrome-visibility";
import { ReactionBar } from "@/components/post/reaction-bar";
import { cn } from "@/lib/utils";

export function StickyReactionBar({
  postId,
  reactions,
  viewerReactions,
  embedded = false,
  className,
}: {
  postId: string;
  reactions?: Record<string, number>;
  viewerReactions?: string[];
  embedded?: boolean;
  className?: string;
}) {
  const { visible } = useChromeVisibility();

  if (embedded) {
    return (
      <ReactionBar
        targetType="post"
        targetId={postId}
        reactions={reactions}
        viewerReactions={viewerReactions}
        className={className}
      />
    );
  }

  return (
    <div
      className={cn(
        "sticky z-40 -mx-4 border-t bg-background/95 px-4 py-2 backdrop-blur transition-[bottom] duration-300 ease-[cubic-bezier(0.4,0,0.2,1)]",
        visible ? "bottom-20 md:bottom-4" : "bottom-0 md:bottom-4",
        className
      )}
    >
      <ReactionBar
        targetType="post"
        targetId={postId}
        reactions={reactions}
        viewerReactions={viewerReactions}
      />
    </div>
  );
}
