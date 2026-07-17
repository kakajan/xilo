"use client";

import { useQuery } from "@tanstack/react-query";
import { apiFetch } from "@/lib/api-client";
import { PostCard } from "./post-card";
import { Skeleton } from "@/components/ui/skeleton";
import type { PostListResponse } from "@/types/post";

export function PostFeed() {
  const { data, isLoading } = useQuery({
    queryKey: ["feed"],
    queryFn: async () => {
      const res = await apiFetch<PostListResponse>("/api/posts?limit=10");
      return res.data;
    },
  });

  if (isLoading) {
    return (
      <div className="space-y-8">
        {Array.from({ length: 5 }).map((_, i) => (
          <div key={i} className="space-y-3">
            <div className="flex items-center gap-3">
              <Skeleton className="h-8 w-8 rounded-full" />
              <div>
                <Skeleton className="h-4 w-24" />
                <Skeleton className="h-3 w-16 mt-1" />
              </div>
            </div>
            <Skeleton className="h-6 w-3/4" />
            <Skeleton className="h-4 w-full" />
            <Skeleton className="h-4 w-1/2" />
          </div>
        ))}
      </div>
    );
  }

  if (!data?.length) {
    return (
      <div className="py-20 text-center text-muted-foreground">
        <p className="text-lg font-medium">هنوز پستی نیست</p>
        <p className="mt-1 text-sm">بعداً دوباره سر بزنید</p>
      </div>
    );
  }

  return (
    <div className="space-y-6">
      {data.map((post) => (
        <PostCard key={post.id} post={post} />
      ))}
    </div>
  );
}
