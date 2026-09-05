"use client";

import { ExternalLink } from "lucide-react";
import { cn } from "@/lib/utils";
import { linkHostname } from "@/lib/post-type";

interface PostLinkCardProps {
  url: string;
  className?: string;
}

export function PostLinkCard({ url, className }: PostLinkCardProps) {
  const hostname = linkHostname(url);

  return (
    <a
      href={url}
      target="_blank"
      rel="noopener noreferrer"
      className={cn(
        "flex items-center gap-3 rounded-xl border border-border bg-secondary/30 px-4 py-3 transition-colors hover:bg-secondary/50",
        className
      )}
      onClick={(e) => e.stopPropagation()}
    >
      <span className="flex h-10 w-10 shrink-0 items-center justify-center rounded-lg bg-primary/10 text-primary">
        <ExternalLink className="h-5 w-5" aria-hidden />
      </span>
      <span className="min-w-0 flex-1">
        <span className="block truncate text-sm font-semibold" dir="ltr">
          {hostname}
        </span>
        <span className="block truncate text-xs text-muted-foreground" dir="ltr">
          {url}
        </span>
      </span>
    </a>
  );
}
