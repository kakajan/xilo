"use client";

import { useChromeVisibility } from "@/hooks/use-chrome-visibility";
import { StickyAudioPlayer } from "@/components/post/sticky-audio-player";
import { ReactionBar } from "@/components/post/reaction-bar";
import { cn } from "@/lib/utils";

export interface StickyPostFooterProps {
  postId: string;
  audioUrl?: string | null;
  title?: string;
  reactions?: Record<string, number>;
  viewerReactions?: string[];
}

export function StickyPostFooter({
  postId,
  audioUrl,
  title,
  reactions,
  viewerReactions,
}: StickyPostFooterProps) {
  const { visible } = useChromeVisibility();
  const hasAudio = Boolean(audioUrl?.trim());

  return (
    <div
      className={cn(
        "sticky z-40 -mx-4 border-t bg-background/95 backdrop-blur transition-[bottom] duration-300 ease-[cubic-bezier(0.4,0,0.2,1)]",
        visible ? "bottom-20 md:bottom-4" : "bottom-0 md:bottom-4"
      )}
    >
      {hasAudio && audioUrl ? (
        <div className="border-b border-border/50">
          <StickyAudioPlayer src={audioUrl} title={title} embedded />
        </div>
      ) : null}
      <div className="px-4 py-2">
        <ReactionBar
          targetType="post"
          targetId={postId}
          reactions={reactions}
          viewerReactions={viewerReactions}
        />
      </div>
    </div>
  );
}
