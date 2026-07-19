"use client";

import { useEffect, useState } from "react";
import { useParams } from "next/navigation";
import Link from "next/link";
import { useTranslations } from "next-intl";
import { apiFetch } from "@/lib/api-client";
import { PostCard } from "@/components/post/post-card";
import { Skeleton } from "@/components/ui/skeleton";
import type { Post, PostListResponse } from "@/types/post";
import { normalizeTag } from "@/lib/hashtag";

export default function TagFeedPage() {
  const params = useParams();
  const raw = typeof params.slug === "string" ? params.slug : "";
  const tag = normalizeTag(decodeURIComponent(raw)) || decodeURIComponent(raw);
  const t = useTranslations("post");
  const tCommon = useTranslations("common");

  const [posts, setPosts] = useState<Post[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");

  useEffect(() => {
    if (!tag) return;
    let cancelled = false;
    (async () => {
      setLoading(true);
      setError("");
      try {
        const res = await apiFetch<PostListResponse>(
          `/api/posts?tag=${encodeURIComponent(tag)}&limit=20`
        );
        if (!cancelled) setPosts(res.data ?? []);
      } catch (e) {
        if (!cancelled) setError((e as Error).message || tCommon("errors.generic"));
      }
      if (!cancelled) setLoading(false);
    })();
    return () => {
      cancelled = true;
    };
  }, [tag, tCommon]);

  return (
    <div className="mx-auto max-w-2xl space-y-6 px-4 py-8">
      <header className="space-y-1">
        <p className="text-sm text-muted-foreground">{t("tagFeedLabel")}</p>
        <h1 className="flex items-center gap-2 text-2xl font-bold text-primary">
          <span className="shrink-0">#</span>
          <span className="min-w-0 break-all">{tag}</span>
        </h1>
      </header>

      {loading && (
        <div className="space-y-4">
          <Skeleton className="h-32 w-full" />
          <Skeleton className="h-32 w-full" />
        </div>
      )}

      {!loading && error && (
        <p className="text-destructive">{error}</p>
      )}

      {!loading && !error && posts.length === 0 && (
        <p className="text-muted-foreground">{t("tagFeedEmpty")}</p>
      )}

      {!loading &&
        posts.map((post) => (
          <PostCard key={post.id} post={post} />
        ))}

      <div className="pt-4">
        <Link href="/" className="text-sm text-primary hover:underline">
          {tCommon("actions.back")}
        </Link>
      </div>
    </div>
  );
}
