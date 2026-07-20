"use client";

import Link from "next/link";
import { Heart, MessageCircle } from "lucide-react";
import { Avatar, AvatarFallback, AvatarImage } from "@/components/ui/avatar";
import { getInitials, cn } from "@/lib/utils";
import {
  AuthorHandleMeta,
  UsernameHandle,
} from "@/components/user/username-handle";
import { useFormatDate } from "@/hooks/use-format-date";
import type { Comment } from "@/types/comment";

export interface DiscoverComment extends Comment {
  post_slug?: string;
  post_title?: string;
  author_username?: string;
  post_author_username?: string;
  reply_count?: number;
}

interface CommentCardProps {
  comment: DiscoverComment;
  onLike?: () => void;
  liked?: boolean;
}

export function CommentCard({ comment, onLike, liked }: CommentCardProps) {
  const formatDate = useFormatDate();
  const name = comment.author?.display_name || comment.author?.username || "کاربر";
  const username = (comment.author?.username || comment.author_username || "").trim();
  const postSlug = comment.post_slug?.trim() || "";
  // Never fall back to the comment author — post URLs are /{postAuthor}/{slug}.
  const postAuthor = (comment.post_author_username || "").trim();
  const profileHref = username ? `/${username}` : null;
  const href =
    postSlug && postAuthor
      ? `/${postAuthor}/${postSlug}?reply=${encodeURIComponent(comment.id)}`
      : postSlug
        ? `#`
        : "#";
  const canOpenPost = Boolean(postSlug && postAuthor);
  const likes = comment.reactions?.like ?? comment.reactions?.heart ?? comment.like_count ?? 0;
  const replies = comment.reply_count ?? comment.replies?.length ?? 0;
  const text =
    comment.content.length > 280
      ? `${comment.content.slice(0, 280)}…`
      : comment.content;

  return (
    <article className="border-b border-border py-4">
      <div className="mb-2 flex items-center gap-2">
        {profileHref ? (
          <Link href={profileHref}>
            <Avatar className="h-10 w-10">
              {comment.author?.avatar_url ? (
                <AvatarImage src={comment.author.avatar_url} alt="" />
              ) : null}
              <AvatarFallback>{getInitials(name)}</AvatarFallback>
            </Avatar>
          </Link>
        ) : (
          <Avatar className="h-10 w-10">
            {comment.author?.avatar_url ? (
              <AvatarImage src={comment.author.avatar_url} alt="" />
            ) : null}
            <AvatarFallback>{getInitials(name)}</AvatarFallback>
          </Avatar>
        )}
        <div className="min-w-0">
          {profileHref ? (
            <Link href={profileHref} className="font-semibold hover:underline">
              {name}
            </Link>
          ) : (
            <span className="font-semibold">{name}</span>
          )}
          <AuthorHandleMeta
            username={username}
            timeLabel={formatDate(comment.created_at)}
          />
        </div>
      </div>

      {canOpenPost ? (
        <Link href={href} className="block">
          <p className="mb-2 whitespace-pre-wrap text-[15px] leading-relaxed">{text}</p>
          {comment.post_title && (
            <p className="mb-2 text-sm text-primary">
              روی پست: {comment.post_title}
              {postAuthor ? (
                <>
                  {" از "}
                  <UsernameHandle username={postAuthor} />
                </>
              ) : null}
            </p>
          )}
        </Link>
      ) : (
        <div className="block">
          <p className="mb-2 whitespace-pre-wrap text-[15px] leading-relaxed">{text}</p>
        </div>
      )}

      <div className="flex items-center gap-1 text-muted-foreground">
        <button
          type="button"
          onClick={onLike}
          className={cn(
            "inline-flex min-h-11 min-w-11 items-center justify-center gap-1 rounded-full px-2 hover:bg-accent",
            liked && "text-[#F91880]"
          )}
        >
          <Heart className={cn("h-4 w-4", liked && "fill-current")} />
          <span className="text-xs">{likes}</span>
        </button>
        {canOpenPost ? (
          <Link
            href={href}
            className="inline-flex min-h-11 min-w-11 items-center justify-center gap-1 rounded-full px-2 hover:bg-accent hover:text-primary"
          >
            <MessageCircle className="h-4 w-4" />
            <span className="text-xs">{replies}</span>
          </Link>
        ) : (
          <span className="inline-flex min-h-11 min-w-11 items-center justify-center gap-1 rounded-full px-2">
            <MessageCircle className="h-4 w-4" />
            <span className="text-xs">{replies}</span>
          </span>
        )}
      </div>
    </article>
  );
}
