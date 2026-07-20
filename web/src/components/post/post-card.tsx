"use client";

import Link from "next/link";
import { useState } from "react";
import { Bookmark, Eye, Heart, MessageCircle, Share2 } from "lucide-react";
import { cn, getInitials } from "@/lib/utils";
import { Avatar, AvatarFallback, AvatarImage } from "@/components/ui/avatar";
import { RepostButton } from "@/components/post/repost-button";
import { QuotedPostCard } from "@/components/post/quoted-post-card";
import { PostOwnerMenu } from "@/components/post/post-owner-menu";
import { HashtagText } from "@/components/post/hashtag-text";
import { AuthorHandleMeta, TimeLabel } from "@/components/user/username-handle";
import { useFormatDate } from "@/hooks/use-format-date";
import { bookmarkPost, unbookmarkPost } from "@/lib/api/bookmarks";
import { apiFetch } from "@/lib/api-client";
import { useAuthStore } from "@/stores/auth-store";
import type { Post } from "@/types/post";

export function PostCard({ post, onRemoved }: { post: Post; onRemoved?: () => void }) {
  const formatDate = useFormatDate();
  const user = useAuthStore((s) => s.user);
  const isOwner =
    !!user &&
    (user.id === post.author_id ||
      (!!post.author?.username && user.username === post.author.username));
  const authorName = post.author?.display_name || post.author?.username || "ناشناس";
  const likeCount = post.reactions?.like ?? post.reactions?.heart ?? 0;
  const [liked, setLiked] = useState(post.viewer_reactions?.includes("like") ?? false);
  const [likes, setLikes] = useState(likeCount);
  const [bookmarked, setBookmarked] = useState(post.is_bookmarked ?? false);
  const [hidden, setHidden] = useState(false);

  if (hidden) return null;

  const href = post.author?.username
    ? `/${post.author.username}/${post.slug}`
    : `/${post.slug}`;

  const toggleLike = async (e: React.MouseEvent) => {
    e.preventDefault();
    e.stopPropagation();
    const prev = liked;
    setLiked(!prev);
    setLikes((n) => (prev ? Math.max(0, n - 1) : n + 1));
    try {
      await apiFetch(`/api/post/${post.id}/reactions`, {
        method: "POST",
        body: JSON.stringify({ reaction: "like" }),
      });
    } catch {
      setLiked(prev);
      setLikes(likeCount);
    }
  };

  const toggleBookmark = async (e: React.MouseEvent) => {
    e.preventDefault();
    e.stopPropagation();
    const prev = bookmarked;
    setBookmarked(!prev);
    try {
      if (prev) await unbookmarkPost(post.id);
      else await bookmarkPost(post.id);
    } catch {
      setBookmarked(prev);
    }
  };

  const share = async (e: React.MouseEvent) => {
    e.preventDefault();
    e.stopPropagation();
    const url = typeof window !== "undefined" ? `${window.location.origin}${href}` : href;
    try {
      await navigator.clipboard.writeText(url);
    } catch {
      /* ignore */
    }
  };

  return (
    <article className="group border-b border-border pb-5">
      <div className="mb-3 flex items-center gap-3">
        <Link href={post.author?.username ? `/${post.author.username}` : "#"}>
          <Avatar className="h-10 w-10">
            {post.author?.avatar_url ? (
              <AvatarImage src={post.author.avatar_url} alt="" />
            ) : null}
            <AvatarFallback>{getInitials(authorName)}</AvatarFallback>
          </Avatar>
        </Link>
        <div className="min-w-0 flex-1 text-sm">
          <Link
            href={post.author?.username ? `/${post.author.username}` : "#"}
            className="font-semibold hover:underline"
          >
            {authorName}
          </Link>
          <AuthorHandleMeta
            username={post.author?.username}
            timeLabel={post.published_at ? formatDate(post.published_at) : null}
            trailing={
              post.reading_time ? (
                <>
                  <span aria-hidden>·</span>
                  <TimeLabel>
                    <bdi>{post.reading_time}</bdi> دقیقه مطالعه
                  </TimeLabel>
                </>
              ) : null
            }
          />
        </div>
        {isOwner ? (
          <PostOwnerMenu
            post={post}
            onRemoved={() => {
              setHidden(true);
              onRemoved?.();
            }}
          />
        ) : null}
      </div>

      <Link href={href} className="block">
        <h2 className="mb-2 text-xl font-bold transition-colors group-hover:text-primary">
          {post.title}
        </h2>
      </Link>
      {post.excerpt && (
        <p className="mb-3 line-clamp-2 text-muted-foreground">
          <HashtagText text={post.excerpt} />
        </p>
      )}
      {post.quoted_post ? <QuotedPostCard quote={post.quoted_post} /> : null}

      <div className="flex flex-wrap items-center justify-between gap-2">
        <div className="flex flex-wrap items-center gap-1">
          {post.tags?.slice(0, 3).map((tag) => (
            <Link
              key={tag}
              href={`/tag/${encodeURIComponent(tag)}`}
              className="rounded-full bg-secondary px-2 py-1 text-xs text-secondary-foreground hover:bg-primary/10"
            >
              #{tag}
            </Link>
          ))}
        </div>
        <div className="flex items-center gap-0.5 text-muted-foreground">
          <span
            className="inline-flex min-h-11 min-w-11 items-center justify-center gap-1 rounded-full px-2"
            aria-label="بازدید"
            title="بازدید"
          >
            <Eye className="h-4 w-4 shrink-0" />
            <span className="min-w-0 text-xs">{post.view_count ?? 0}</span>
          </span>
          <Link
            href={href}
            className="inline-flex min-h-11 min-w-11 items-center justify-center gap-1 rounded-full px-2 hover:bg-accent hover:text-primary"
            aria-label="نظرات"
          >
            <MessageCircle className="h-4 w-4" />
            <span className="text-xs">{post.comment_count ?? 0}</span>
          </Link>
          <div onClick={(e) => e.stopPropagation()}>
            <RepostButton
              postId={post.id}
              postSlug={post.slug}
              repostCount={post.repost_count ?? 0}
              reposted={post.is_reposted ?? false}
            />
          </div>
          <button
            type="button"
            onClick={toggleLike}
            className={cn(
              "inline-flex min-h-11 min-w-11 items-center justify-center gap-1 rounded-full px-2 hover:bg-accent",
              liked && "text-[#F91880]"
            )}
            aria-label="پسندیدن"
          >
            <Heart className={cn("h-4 w-4", liked && "fill-current")} />
            <span className="text-xs">{likes}</span>
          </button>
          <button
            type="button"
            onClick={toggleBookmark}
            className={cn(
              "inline-flex min-h-11 min-w-11 items-center justify-center rounded-full px-2 hover:bg-accent",
              bookmarked && "text-primary"
            )}
            aria-label="نشان‌کردن"
          >
            <Bookmark className={cn("h-4 w-4", bookmarked && "fill-current")} />
          </button>
          <button
            type="button"
            onClick={share}
            className="inline-flex min-h-11 min-w-11 items-center justify-center rounded-full px-2 hover:bg-accent"
            aria-label="اشتراک"
          >
            <Share2 className="h-4 w-4" />
          </button>
        </div>
      </div>
    </article>
  );
}
