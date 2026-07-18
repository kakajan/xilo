"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { useQuery } from "@tanstack/react-query";
import { getBookmarks, getCommentBookmarks } from "@/lib/api/bookmarks";
import { useAuthStore } from "@/stores/auth-store";
import { PostCard } from "@/components/post/post-card";
import { Skeleton } from "@/components/ui/skeleton";
import { cn } from "@/lib/utils";

type Tab = "posts" | "comments";

export default function SavedHubPage() {
  const router = useRouter();
  const { isAuthenticated, isLoading: authLoading, authChecked } = useAuthStore();
  const [tab, setTab] = useState<Tab>("posts");

  useEffect(() => {
    if (authChecked && !isAuthenticated) router.replace("/login");
  }, [authChecked, isAuthenticated, router]);

  const postsQ = useQuery({
    queryKey: ["bookmarks-posts"],
    enabled: isAuthenticated,
    queryFn: async () => (await getBookmarks()).data ?? [],
  });

  const commentsQ = useQuery({
    queryKey: ["bookmarks-comments"],
    enabled: isAuthenticated,
    queryFn: async () => (await getCommentBookmarks()).data ?? [],
  });

  if (authLoading || !isAuthenticated) {
    return <Skeleton className="h-40 w-full" />;
  }

  return (
    <div>
      <h1 className="mb-4 text-2xl font-bold">ذخیره‌ها</h1>
      <div className="mb-6 flex gap-2">
        {(
          [
            ["posts", "پست‌ها"],
            ["comments", "نظرات"],
          ] as const
        ).map(([key, label]) => (
          <button
            key={key}
            type="button"
            onClick={() => setTab(key)}
            className={cn(
              "min-h-11 rounded-full px-4 text-sm font-medium",
              tab === key
                ? "bg-primary text-primary-foreground"
                : "bg-muted text-muted-foreground"
            )}
          >
            {label}
          </button>
        ))}
      </div>

      {tab === "posts" ? (
        postsQ.isLoading ? (
          <Skeleton className="h-32 w-full" />
        ) : (postsQ.data?.length ?? 0) === 0 ? (
          <p className="py-12 text-center text-muted-foreground">پست ذخیره‌شده‌ای نیست</p>
        ) : (
          <div className="space-y-4">
            {postsQ.data!.map((p) => (
              <PostCard key={p.id} post={p} />
            ))}
          </div>
        )
      ) : commentsQ.isLoading ? (
        <Skeleton className="h-32 w-full" />
      ) : (commentsQ.data?.length ?? 0) === 0 ? (
        <p className="py-12 text-center text-muted-foreground">نظر ذخیره‌شده‌ای نیست</p>
      ) : (
        <ul className="divide-y divide-border">
          {commentsQ.data!.map((c) => {
            const slug = c.post_slug;
            const author = c.author_username || c.author?.username;
            const href =
              slug && author ? `/${author}/${slug}?reply=${c.comment_id || c.id}` : "/";
            return (
              <li key={c.id} className="py-4">
                <Link href={href} className="block hover:bg-muted/40 rounded-xl px-2 py-2">
                  <p className="mb-1 text-sm font-medium text-primary">
                    {c.post_title || "پست"}
                  </p>
                  <p className="whitespace-pre-wrap text-[15px]">{c.content}</p>
                </Link>
              </li>
            );
          })}
        </ul>
      )}
    </div>
  );
}
