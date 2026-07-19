"use client";

import Link from "next/link";
import { segmentHashtags } from "@/lib/hashtag";

interface HashtagTextProps {
  text: string;
  className?: string;
}

/** Renders plain text with clickable #hashtag links. */
export function HashtagText({ text, className }: HashtagTextProps) {
  const segments = segmentHashtags(text);
  return (
    <span className={className}>
      {segments.map((seg, i) =>
        seg.type === "hashtag" ? (
          <Link
            key={`${seg.tag}-${i}`}
            href={`/tag/${encodeURIComponent(seg.tag)}`}
            className="text-primary font-medium hover:underline"
            onClick={(e) => e.stopPropagation()}
          >
            {seg.value}
          </Link>
        ) : (
          <span key={i}>{seg.value}</span>
        )
      )}
    </span>
  );
}
