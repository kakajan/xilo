"use client";

import { useFormatDate } from "@/hooks/use-format-date";

export function PostTimeLabel({
  publishedAt,
}: {
  publishedAt: string | null | undefined;
}) {
  const formatDate = useFormatDate();
  if (!publishedAt) return null;
  return <>{formatDate(publishedAt)}</>;
}
