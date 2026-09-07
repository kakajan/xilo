"use client";

import { useEffect, useState } from "react";
import { X } from "lucide-react";
import { apiFetch } from "@/lib/api-client";
import { QuotedPostCard } from "@/components/post/quoted-post-card";
import { QuotedCommentCard } from "@/components/post/quoted-comment-card";
import { useEditorStore } from "@/stores/editor-store";
import type { QuotedPostSummary } from "@/types/post";
import type { SearchResponse } from "@/types/search";

export function QuoteSourcePicker() {
  const { quotedPost, quotedComment, setQuotedPost } = useEditorStore();
  const [query, setQuery] = useState("");
  const [results, setResults] = useState<QuotedPostSummary[]>([]);
  const [searching, setSearching] = useState(false);
  const [error, setError] = useState("");

  useEffect(() => {
    const q = query.trim();
    if (q.length < 2) {
      setResults([]);
      setSearching(false);
      return;
    }
    let cancelled = false;
    setSearching(true);
    const handle = window.setTimeout(() => {
      void (async () => {
        try {
          const res = await apiFetch<SearchResponse>(
            `/api/search/posts?q=${encodeURIComponent(q)}&limit=8`
          );
          if (cancelled) return;
          setResults(
            (res.data ?? []).map((hit) => ({
              id: hit.id,
              title: hit._formatted?.title || hit.title,
              slug: hit.slug,
              excerpt: hit._formatted?.excerpt || hit.excerpt || "",
              cover_image_url: hit.cover_image_url || null,
              author: {
                id: "",
                email: "",
                username: hit.author_username,
                display_name: hit.author_name,
                avatar_url: "",
                bio: "",
                role: "reader",
                email_verified: false,
                created_at: "",
                updated_at: "",
              },
              published_at: hit.published_at,
            }))
          );
          setError("");
        } catch {
          if (!cancelled) {
            setError("جستجو ناموفق بود");
            setResults([]);
          }
        }
        if (!cancelled) setSearching(false);
      })();
    }, 250);
    return () => {
      cancelled = true;
      window.clearTimeout(handle);
    };
  }, [query]);

  return (
    <div className="space-y-3">
      {quotedComment ? (
        <QuotedCommentCard quote={quotedComment} />
      ) : quotedPost ? (
        <div className="relative">
          <QuotedPostCard quote={quotedPost} className="block rounded-2xl border border-border p-3" />
          <button
            type="button"
            onClick={() => setQuotedPost(null)}
            className="absolute top-2 end-2 rounded-full bg-background/90 p-1"
            aria-label="حذف نقل‌قول"
          >
            <X className="h-4 w-4" />
          </button>
        </div>
      ) : (
        <>
          <label className="block text-sm font-medium">پست مورد نقل‌قول</label>
          <input
            type="search"
            value={query}
            onChange={(e) => setQuery(e.target.value)}
            placeholder="جستجوی عنوان یا متن پست…"
            className="w-full rounded-xl border bg-background px-4 py-3 text-sm"
          />
          {searching ? (
            <p className="text-xs text-muted-foreground">در حال جستجو...</p>
          ) : null}
          {error ? (
            <p className="text-sm text-destructive" role="alert">
              {error}
            </p>
          ) : null}
          {results.length > 0 ? (
            <ul className="divide-y overflow-hidden rounded-xl border">
              {results.map((post) => (
                <li key={post.id}>
                  <button
                    type="button"
                    className="flex w-full flex-col items-start gap-0.5 px-4 py-3 text-start hover:bg-accent"
                    onClick={() => {
                      setQuotedPost(post);
                      setQuery("");
                      setResults([]);
                    }}
                  >
                    <span className="text-sm font-medium">{post.title}</span>
                    <span className="text-xs text-muted-foreground">
                      {post.author?.display_name || post.author?.username || post.slug}
                    </span>
                  </button>
                </li>
              ))}
            </ul>
          ) : null}
        </>
      )}
    </div>
  );
}
