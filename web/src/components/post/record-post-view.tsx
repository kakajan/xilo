"use client";

import { useEffect, useRef, useState } from "react";
import { recordPostView } from "@/lib/api/views";

const FA_DIGITS = "۰۱۲۳۴۵۶۷۸۹";

function toFaDigits(n: number): string {
  return String(Math.max(0, Math.floor(n))).replace(/\d/g, (d) => FA_DIGITS[Number(d)]!);
}

export function RecordPostView({
  postId,
  initialViewCount = 0,
}: {
  postId: string;
  initialViewCount?: number;
}) {
  const sent = useRef(false);
  const [viewCount, setViewCount] = useState(initialViewCount);

  useEffect(() => {
    if (!postId || sent.current) return;
    sent.current = true;
    recordPostView(postId)
      .then((res) => setViewCount(res.view_count))
      .catch(() => {
        /* non-blocking */
      });
  }, [postId]);

  return (
    <span className="text-sm text-muted-foreground">
      <bdi>{toFaDigits(viewCount)}</bdi> بازدید
    </span>
  );
}
