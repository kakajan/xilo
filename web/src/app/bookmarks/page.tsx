"use client";

import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { Bookmark } from "lucide-react";
import { apiFetch } from "@/lib/api-client";
import { PostCard } from "@/components/post/post-card";
import { Skeleton } from "@/components/ui/skeleton";
import type { PostListResponse } from "@/types/post";

export default function BookmarksPage() {
  const queryClient = useQueryClient();

  const { data, isLoading } = useQuery({
    queryKey: ["bookmarks"],
    queryFn: async () => {
      const res = await apiFetch<PostListResponse>("/api/bookmarks");
      return res.data;
    },
  });

  if (isLoading) {
    return (
      <div className="space-y-8">
        {[1, 2, 3].map((i) => (
          <div key={i} className="space-y-3">
            <Skeleton className="h-4 w-24" />
            <Skeleton className="h-6 w-3/4" />
            <Skeleton className="h-4 w-full" />
          </div>
        ))}
      </div>
    );
  }

  if (!data?.length) {
    return (
      <div className="text-center py-20 text-muted-foreground">
        <Bookmark className="h-12 w-12 mx-auto mb-4 opacity-50" />
        <p className="text-lg font-medium">No bookmarks yet</p>
        <p className="text-sm mt-1">Save posts for later reading</p>
      </div>
    );
  }

  return (
    <div>
      <h1 className="text-2xl font-bold mb-6">Bookmarks</h1>
      <div className="space-y-6">
        {data.map((post) => (
          <PostCard key={post.id} post={post} />
        ))}
      </div>
    </div>
  );
}
