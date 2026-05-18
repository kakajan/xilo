"use client";

import Link from "next/link";
import { formatDate, readingTimeText } from "@/lib/utils";
import { Avatar, AvatarFallback } from "@/components/ui/avatar";
import { getInitials } from "@/lib/utils";
import type { Post } from "@/types/post";

export function PostCard({ post }: { post: Post }) {
  const authorName = post.author?.display_name || post.author?.username || "Unknown";

  return (
    <article className="group cursor-pointer border-b pb-6">
      <div className="flex items-center gap-3 mb-3">
        <Link href={`/${post.author?.username}`}>
          <Avatar className="h-8 w-8">
            <AvatarFallback>{getInitials(authorName)}</AvatarFallback>
          </Avatar>
        </Link>
        <div className="text-sm">
          <Link href={`/${post.author?.username}`} className="font-medium hover:underline">
            {authorName}
          </Link>
          <p className="text-muted-foreground text-xs">
            {post.published_at ? formatDate(post.published_at) : ""}
            {post.reading_time ? ` · ${readingTimeText(post.reading_time)}` : ""}
          </p>
        </div>
      </div>

      <Link href={`/${post.author?.username}/${post.slug}`}>
        <h2 className="text-xl font-bold mb-2 group-hover:text-primary transition-colors">
          {post.title}
        </h2>
        {post.excerpt && (
          <p className="text-muted-foreground line-clamp-2 mb-3">{post.excerpt}</p>
        )}
      </Link>

      <div className="flex items-center gap-2 flex-wrap">
        {post.tags?.slice(0, 3).map((tag) => (
          <Link
            key={tag}
            href={`/tag/${tag}`}
            className="text-xs bg-secondary text-secondary-foreground px-2 py-1 rounded-full hover:bg-primary/10"
          >
            {tag}
          </Link>
        ))}
      </div>
    </article>
  );
}
