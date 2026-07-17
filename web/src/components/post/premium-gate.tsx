"use client";

import { useEffect, useState, useCallback } from "react";
import Link from "next/link";
import { useQuery } from "@tanstack/react-query";
import { apiFetch } from "@/lib/api-client";

interface PostContent {
  id: string;
  title: string;
  slug: string;
  content_md: string;
  is_premium: boolean;
}

export function PremiumGate({
  children,
  post,
}: {
  children: React.ReactNode;
  post: PostContent;
}) {
  const [hasAccess, setHasAccess] = useState(!post.is_premium);

  const { data: sub } = useQuery({
    queryKey: ["my-subscription"],
    queryFn: () => apiFetch<{ active: boolean }>("/api/billing/my-subscription"),
    enabled: post.is_premium,
  });

  useEffect(() => {
    if (sub?.active || !post.is_premium) {
      setHasAccess(true);
    }
  }, [sub, post.is_premium]);

  if (hasAccess) return <>{children}</>;

  const words = post.content_md?.split(/\s+/).slice(0, 50).join(" ") || "";

  return (
    <div>
      <div className="prose dark:prose-invert max-w-none mb-8">
        <p>{words}...</p>
      </div>
      <div className="border rounded-xl p-8 text-center bg-muted/30">
        <h3 className="text-lg font-semibold mb-2">Premium Content</h3>
        <p className="text-muted-foreground mb-4">
          Subscribe to read this full article and support the author.
        </p>
        <Link
          href="/dashboard/billing"
          className="inline-flex h-10 items-center justify-center rounded-lg bg-primary px-6 text-sm font-medium text-primary-foreground hover:bg-primary/90"
        >
          Subscribe Now
        </Link>
      </div>
    </div>
  );
}
