"use client";

import { ReactionBar } from "@/components/post/reaction-bar";

export function StickyReactionBar({
  postId,
  reactions,
  viewerReactions,
}: {
  postId: string;
  reactions?: Record<string, number>;
  viewerReactions?: string[];
}) {
  return (
    <div className="sticky bottom-20 z-40 -mx-4 border-t bg-background/95 px-4 py-2 backdrop-blur md:bottom-4">
      <ReactionBar
        targetType="post"
        targetId={postId}
        reactions={reactions}
        viewerReactions={viewerReactions}
      />
    </div>
  );
}
