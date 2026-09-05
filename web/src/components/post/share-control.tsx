"use client";

import { useState } from "react";
import { Share2 } from "lucide-react";
import { shareOrCopy } from "@/lib/share-urls";

export function ShareControl({
  url,
  title,
  className,
}: {
  url: string;
  title?: string;
  className?: string;
}) {
  const [copied, setCopied] = useState(false);

  return (
    <button
      type="button"
      onClick={async (e) => {
        e.preventDefault();
        e.stopPropagation();
        const result = await shareOrCopy(url, title);
        if (result === "copied") {
          setCopied(true);
          window.setTimeout(() => setCopied(false), 1500);
        }
      }}
      className={
        className ||
        "inline-flex min-h-11 min-w-11 items-center justify-center rounded-full px-2 hover:bg-accent"
      }
      aria-label="اشتراک"
    >
      <Share2 className="h-4 w-4" />
      {copied ? (
        <span className="sr-only" aria-live="polite">
          لینک کپی شد
        </span>
      ) : null}
    </button>
  );
}
