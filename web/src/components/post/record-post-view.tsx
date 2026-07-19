"use client";

import { useEffect, useRef, useState } from "react";
import { recordPostView } from "@/lib/api/views";

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
      <bdi>{viewCount.toLocaleString("fa-IR")}</bdi> بازدید
    </span>
  );
}
