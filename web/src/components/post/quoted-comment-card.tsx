"use client";

import Link from "next/link";
import { Avatar, AvatarFallback, AvatarImage } from "@/components/ui/avatar";
import { getInitials } from "@/lib/utils";
import type { QuotedCommentSummary } from "@/types/post";

interface QuotedCommentCardProps {
  quote: QuotedCommentSummary;
}

export function QuotedCommentCard({ quote }: QuotedCommentCardProps) {
  const name = quote.author?.display_name || quote.author?.username || "کاربر";
  const username = quote.author?.username || "";
  const href =
    quote.post_author_username && quote.post_slug
      ? `/${quote.post_author_username}/${quote.post_slug}?reply=${encodeURIComponent(quote.id)}`
      : "#";
  const text =
    quote.content.length > 280
      ? `${quote.content.slice(0, 280)}…`
      : quote.content;

  return (
    <Link
      href={href}
      className="mt-3 block rounded-xl border border-border bg-muted/30 p-3 transition-colors hover:bg-muted/50"
    >
      <div className="mb-2 flex items-center gap-2">
        <Avatar className="h-7 w-7 shrink-0">
          {quote.author?.avatar_url ? (
            <AvatarImage src={quote.author.avatar_url} alt="" />
          ) : null}
          <AvatarFallback className="text-xs">{getInitials(name)}</AvatarFallback>
        </Avatar>
        <div className="min-w-0 flex items-center gap-1.5 text-sm">
          <span className="truncate font-medium">{name}</span>
          {username ? (
            <span className="truncate text-muted-foreground">@{username}</span>
          ) : null}
        </div>
      </div>
      <p className="whitespace-pre-wrap text-sm leading-relaxed">{text}</p>
      {quote.post_title ? (
        <p className="mt-2 text-xs text-muted-foreground">
          روی پست: {quote.post_title}
        </p>
      ) : null}
    </Link>
  );
}
