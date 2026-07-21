"use client";

import type { ReactNode } from "react";
import {
  Bookmark,
  Flag,
  MessageCircle,
  Pin,
  Share2,
  ThumbsDown,
  ThumbsUp,
} from "lucide-react";
import { cn } from "@/lib/utils";
import { CommentRepostButton } from "@/components/comment/comment-repost-button";

interface CommentActionsProps {
  commentId?: string;
  replyCount?: number;
  likeCount?: number;
  dislikeCount?: number;
  repostCount?: number;
  liked?: boolean;
  disliked?: boolean;
  bookmarked?: boolean;
  reposted?: boolean;
  pinned?: boolean;
  onReply?: () => void;
  onLike?: () => void;
  onDislike?: () => void;
  onBookmark?: () => void;
  onReport?: () => void;
  onShare?: () => void;
  onPin?: () => void;
  className?: string;
  /** Hide bookmark/report (e.g. nested reply composer context). */
  showSecondary?: boolean;
}

function ActionButton({
  label,
  count,
  active,
  activeClass,
  onClick,
  children,
}: {
  label: string;
  count?: number | string | null;
  active?: boolean;
  activeClass?: string;
  onClick?: () => void;
  children: ReactNode;
}) {
  return (
    <button
      type="button"
      onClick={(e) => {
        e.preventDefault();
        e.stopPropagation();
        onClick?.();
      }}
      disabled={!onClick}
      className={cn(
        "inline-flex items-center gap-1.5 rounded-lg px-1 py-1.5 text-muted-foreground transition-colors hover:bg-accent disabled:pointer-events-none",
        active && activeClass
      )}
      aria-label={label}
      title={label}
    >
      {children}
      {count != null ? <span className="text-xs tabular-nums">{count}</span> : null}
    </button>
  );
}

/** Android CommentCard action row: reply · like · dislike · amplify | share · pin · bookmark · report */
export function CommentActions({
  commentId,
  replyCount = 0,
  likeCount = 0,
  dislikeCount = 0,
  repostCount = 0,
  liked = false,
  disliked = false,
  bookmarked = false,
  reposted = false,
  pinned = false,
  onReply,
  onLike,
  onDislike,
  onBookmark,
  onReport,
  onShare,
  onPin,
  className,
  showSecondary = true,
}: CommentActionsProps) {
  return (
    <div
      className={cn(
        "flex w-full items-center justify-between gap-2 text-muted-foreground",
        className
      )}
    >
      <div className="flex items-center gap-3">
        <ActionButton label="پاسخ" count={replyCount} onClick={onReply}>
          <MessageCircle className="h-4 w-4 shrink-0" />
        </ActionButton>
        <ActionButton
          label="پسندیدن"
          count={likeCount}
          active={liked}
          activeClass="text-[#00BA7C]"
          onClick={onLike}
        >
          <ThumbsUp className={cn("h-4 w-4 shrink-0", liked && "fill-current")} />
        </ActionButton>
        <ActionButton
          label="نپسندیدن"
          count={dislikeCount}
          active={disliked}
          activeClass="text-[#F4212E]"
          onClick={onDislike}
        >
          <ThumbsDown className={cn("h-4 w-4 shrink-0", disliked && "fill-current")} />
        </ActionButton>
        {commentId ? (
          <CommentRepostButton
            commentId={commentId}
            repostCount={repostCount}
            reposted={reposted}
          />
        ) : null}
      </div>

      {showSecondary ? (
        <div className="flex items-center gap-1">
          <ActionButton label="اشتراک‌گذاری" onClick={onShare}>
            <Share2 className="h-4 w-4 shrink-0" />
          </ActionButton>
          {onPin ? (
            <ActionButton
              label={pinned ? "برداشتن پین" : "پین نظر"}
              active={pinned}
              activeClass="text-primary"
              onClick={onPin}
            >
              <Pin className={cn("h-4 w-4 shrink-0", pinned && "fill-current")} />
            </ActionButton>
          ) : null}
          <ActionButton
            label={bookmarked ? "حذف از ذخیره‌ها" : "ذخیره نظر"}
            active={bookmarked}
            activeClass="text-primary"
            onClick={onBookmark}
          >
            <Bookmark className={cn("h-4 w-4 shrink-0", bookmarked && "fill-current")} />
          </ActionButton>
          <ActionButton label="گزارش" onClick={onReport}>
            <Flag className="h-4 w-4 shrink-0" />
          </ActionButton>
        </div>
      ) : null}
    </div>
  );
}
