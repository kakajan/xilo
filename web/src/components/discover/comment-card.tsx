"use client";

import Link from "next/link";
import { Avatar, AvatarFallback, AvatarImage } from "@/components/ui/avatar";
import { getInitials } from "@/lib/utils";
import {
  AuthorHandleMeta,
  UsernameHandle,
} from "@/components/user/username-handle";
import { useFormatDate } from "@/hooks/use-format-date";
import { CommentActions } from "@/components/comment/comment-actions";
import {
  commentDislikeCount,
  commentLikeCount,
} from "@/lib/comment-reactions";
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
  liked?: boolean;
  disliked?: boolean;
  bookmarked?: boolean;
  onReply?: () => void;
  onLike?: () => void;
  onDislike?: () => void;
  onBookmark?: () => void;
  onReport?: () => void;
}

export function CommentCard({
  comment,
  liked,
  disliked,
  bookmarked,
  onReply,
  onLike,
  onDislike,
  onBookmark,
  onReport,
}: CommentCardProps) {
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
      : "#";
  const canOpenPost = Boolean(postSlug && postAuthor);
  const likes = commentLikeCount(comment.reactions, comment.like_count ?? 0);
  const dislikes = commentDislikeCount(comment.reactions);
  const replies = comment.reply_count ?? comment.replies?.length ?? 0;
  const text =
    comment.content.length > 280
      ? `${comment.content.slice(0, 280)}…`
      : comment.content;

  return (
    <article className="border-b border-border py-4">
      <div className="mb-2 flex items-start gap-3">
        {profileHref ? (
          <Link href={profileHref} className="shrink-0">
            <Avatar className="h-10 w-10">
              {comment.author?.avatar_url ? (
                <AvatarImage src={comment.author.avatar_url} alt="" />
              ) : null}
              <AvatarFallback>{getInitials(name)}</AvatarFallback>
            </Avatar>
          </Link>
        ) : (
          <Avatar className="h-10 w-10 shrink-0">
            {comment.author?.avatar_url ? (
              <AvatarImage src={comment.author.avatar_url} alt="" />
            ) : null}
            <AvatarFallback>{getInitials(name)}</AvatarFallback>
          </Avatar>
        )}

        <div className="min-w-0 flex-1">
          <div className="mb-1 flex min-w-0 flex-wrap items-center gap-1">
            {profileHref ? (
              <Link href={profileHref} className="min-w-0 font-semibold hover:underline">
                {name}
              </Link>
            ) : (
              <span className="min-w-0 font-semibold">{name}</span>
            )}
            <AuthorHandleMeta
              username={username}
              timeLabel={formatDate(comment.created_at)}
            />
          </div>

          {canOpenPost ? (
            <Link href={href} className="block">
              <p className="mb-2 whitespace-pre-wrap text-[15px] leading-relaxed">{text}</p>
              {comment.post_title ? (
                <p className="mb-2 text-sm text-primary">
                  روی پست: {comment.post_title}
                  {postAuthor ? (
                    <>
                      {" از "}
                      <UsernameHandle username={postAuthor} />
                    </>
                  ) : null}
                </p>
              ) : null}
            </Link>
          ) : (
            <p className="mb-2 whitespace-pre-wrap text-[15px] leading-relaxed">{text}</p>
          )}

          <CommentActions
            replyCount={replies}
            likeCount={likes}
            dislikeCount={dislikes}
            liked={liked}
            disliked={disliked}
            bookmarked={bookmarked}
            onReply={onReply}
            onLike={onLike}
            onDislike={onDislike}
            onBookmark={onBookmark}
            onReport={onReport}
          />
        </div>
      </div>
    </article>
  );
}
