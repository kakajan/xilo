"use client";

import { Suspense, useState } from "react";
import { useSearchParams, useRouter } from "next/navigation";
import { useQuery } from "@tanstack/react-query";
import { Search } from "lucide-react";
import { apiFetch } from "@/lib/api-client";
import { PostCard } from "@/components/post/post-card";
import { Skeleton } from "@/components/ui/skeleton";
import type { SearchResponse } from "@/types/search";

function SearchResults({ query }: { query: string }) {
  const router = useRouter();
  const searchParams = useSearchParams();
  const category = searchParams.get("category") || "";
  const tag = searchParams.get("tag") || "";

  const { data, isLoading } = useQuery({
    queryKey: ["search", query, category, tag],
    queryFn: async () => {
      const params = new URLSearchParams({ q: query, limit: "20" });
      if (category) params.set("category", category);
      if (tag) params.set("tag", tag);
      const res = await apiFetch<SearchResponse>(`/api/search/posts?${params}`);
      return res;
    },
    enabled: query.length >= 2,
  });

  if (!query || query.length < 2) {
    return <p className="text-center text-muted-foreground mt-20">Type at least 2 characters to search</p>;
  }

  if (isLoading) {
    return (
      <div className="space-y-8 mt-8">
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

  if (!data?.data?.length) {
    return <p className="text-center text-muted-foreground mt-20">No results found for &quot;{query}&quot;</p>;
  }

  return (
    <div>
      <p className="text-sm text-muted-foreground mb-4">{data.total} result{data.total !== 1 ? "s" : ""}</p>
      <div className="space-y-6">
        {data.data.map((result) => {
          const post = {
            id: result.id,
            author_id: "",
            title: result._formatted?.title || result.title,
            slug: result.slug,
            excerpt: result._formatted?.excerpt || result.excerpt,
            content: "",
            content_md: "",
            cover_image_url: result.cover_image_url,
            category: result.category,
            tags: result.tags,
            status: "published" as const,
            is_premium: false,
            word_count: result.word_count,
            reading_time: result.reading_time,
            scheduled_at: null,
            published_at: result.published_at,
            created_at: "",
            updated_at: "",
            author: {
              id: "",
              email: "",
              username: result.author_username,
              display_name: result.author_name,
              avatar_url: "",
              bio: "",
              role: "author" as const,
              email_verified: false,
              created_at: "",
              updated_at: "",
            },
          };
          return <PostCard key={result.id} post={post} />;
        })}
      </div>
    </div>
  );
}

export default function SearchPage() {
  const searchParams = useSearchParams();
  const router = useRouter();
  const [input, setInput] = useState(searchParams.get("q") || "");

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    if (input.trim().length >= 2) {
      router.push(`/search?q=${encodeURIComponent(input.trim())}`);
    }
  };

  return (
    <div className="max-w-2xl mx-auto">
      <form onSubmit={handleSubmit} className="mb-8">
        <div className="relative">
          <Search className="absolute left-4 top-1/2 -translate-y-1/2 h-5 w-5 text-muted-foreground" />
          <input
            type="text"
            value={input}
            onChange={(e) => setInput(e.target.value)}
            placeholder="Search posts..."
            className="w-full pl-12 pr-4 py-3 rounded-xl border bg-background text-lg"
            autoFocus
          />
        </div>
      </form>

      <Suspense fallback={<Skeleton className="h-40 w-full" />}>
        <SearchResults query={searchParams.get("q") || ""} />
      </Suspense>
    </div>
  );
}
