"use client";

import Link from "next/link";
import { Avatar, AvatarFallback, AvatarImage } from "@/components/ui/avatar";
import { UsernameHandle } from "@/components/user/username-handle";
import { getInitials } from "@/lib/utils";
import type { QuotedPostSummary } from "@/types/post";

export function QuotedPostCard({
  quote,
  className,
}: {
  quote: QuotedPostSummary;
  className?: string;
}) {
  const name = quote.author?.display_name || quote.author?.username || "کاربر";
  const href =
    quote.author?.username && quote.slug
      ? `/${quote.author.username}/${quote.slug}`
      : quote.slug
        ? `/${quote.slug}`
        : "#";

  return (
    <Link
      href={href}
      onClick={(e) => e.stopPropagation()}
      className={
        className ??
        "mt-3 block rounded-2xl border border-border bg-background p-3 transition-colors hover:bg-muted/40"
      }
    >
      <div className="mb-1.5 flex items-center gap-2">
        <Avatar className="h-5 w-5 shrink-0">
          {quote.author?.avatar_url ? (
            <AvatarImage src={quote.author.avatar_url} alt="" />
          ) : null}
          <AvatarFallback className="text-[10px]">{getInitials(name)}</AvatarFallback>
        </Avatar>
        <span className="min-w-0 truncate text-sm font-semibold">{name}</span>
        {quote.author?.username ? (
          <UsernameHandle
            username={quote.author.username}
            className="truncate text-xs text-muted-foreground"
          />
        ) : null}
      </div>
      {quote.title ? (
        <p className="text-[15px] font-semibold leading-snug">{quote.title}</p>
      ) : null}
      {quote.excerpt ? (
        <p className="mt-1 line-clamp-3 text-sm text-muted-foreground">{quote.excerpt}</p>
      ) : null}
      {quote.cover_image_url ? (
        // eslint-disable-next-line @next/next/no-img-element
        <img
          src={quote.cover_image_url}
          alt=""
          className="mt-2 max-h-40 w-full rounded-xl object-cover"
        />
      ) : null}
    </Link>
  );
}
