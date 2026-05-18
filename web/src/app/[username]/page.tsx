"use client";

import { useParams, useRouter } from "next/navigation";
import { useQuery } from "@tanstack/react-query";
import { apiFetch } from "@/lib/api-client";
import { PostCard } from "@/components/post/post-card";
import { Skeleton } from "@/components/ui/skeleton";
import { Avatar, AvatarFallback } from "@/components/ui/avatar";
import { Button } from "@/components/ui/button";
import { formatDate, getInitials } from "@/lib/utils";
import type { User } from "@/types/user";
import type { PostListResponse } from "@/types/post";

export default function AuthorProfilePage() {
  const { username } = useParams<{ username: string }>();
  const router = useRouter();

  const { data: author } = useQuery({
    queryKey: ["user", username],
    queryFn: () => apiFetch<User>(`/api/users/${username}`),
  });

  const { data: posts, isLoading } = useQuery({
    queryKey: ["posts", "author", username],
    queryFn: async () => {
      const res = await apiFetch<PostListResponse>(`/api/posts?author=${username}&limit=10`);
      return res.data;
    },
  });

  if (!author && !isLoading) {
    return (
      <div className="text-center py-20 text-muted-foreground">
        <p>User not found</p>
      </div>
    );
  }

  return (
    <div>
      <div className="flex flex-col items-center mb-8">
        <Avatar className="h-20 w-20 mb-4">
          <AvatarFallback className="text-2xl">
            {author ? getInitials(author.display_name || author.username) : ""}
          </AvatarFallback>
        </Avatar>
        <h1 className="text-2xl font-bold">{author?.display_name || author?.username}</h1>
        {author?.bio && <p className="text-muted-foreground mt-1">{author.bio}</p>}
        <p className="text-sm text-muted-foreground mt-1">
          Joined {author?.created_at ? formatDate(author.created_at) : ""}
        </p>
      </div>

      <h2 className="text-lg font-semibold mb-4">Posts</h2>

      {isLoading ? (
        <div className="space-y-8">
          {[1, 2, 3].map((i) => (
            <div key={i} className="space-y-3">
              <Skeleton className="h-4 w-24" />
              <Skeleton className="h-6 w-3/4" />
              <Skeleton className="h-4 w-full" />
            </div>
          ))}
        </div>
      ) : posts?.length === 0 ? (
        <p className="text-muted-foreground">No posts yet</p>
      ) : (
        <div className="space-y-6">
          {posts?.map((post) => <PostCard key={post.id} post={post} />)}
        </div>
      )}
    </div>
  );
}
