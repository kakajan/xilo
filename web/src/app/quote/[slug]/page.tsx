"use client";

import { useMemo, useState } from "react";
import { useParams, useRouter } from "next/navigation";
import { useQuery } from "@tanstack/react-query";
import { X } from "lucide-react";
import { apiFetch } from "@/lib/api-client";
import { canCreatePost } from "@/lib/auth/permissions";
import { useRequireAuth } from "@/hooks/use-require-auth";
import { QuotedPostCard } from "@/components/post/quoted-post-card";
import { Avatar, AvatarFallback, AvatarImage } from "@/components/ui/avatar";
import { Button } from "@/components/ui/button";
import { Skeleton } from "@/components/ui/skeleton";
import { getInitials } from "@/lib/utils";
import type { Post, QuotedPostSummary } from "@/types/post";

function textToTipTapJSON(text: string) {
  const trimmed = text.trim();
  return JSON.stringify({
    type: "doc",
    content: [
      {
        type: "paragraph",
        content: trimmed ? [{ type: "text", text: trimmed }] : [],
      },
    ],
  });
}

function toQuoteSummary(post: Post): QuotedPostSummary {
  return {
    id: post.id,
    title: post.title,
    slug: post.slug,
    excerpt: post.excerpt || post.content_md?.slice(0, 200) || "",
    cover_image_url: post.cover_image_url || null,
    author: post.author,
    published_at: post.published_at,
  };
}

export default function QuoteComposePage() {
  const { slug } = useParams<{ slug: string }>();
  const router = useRouter();
  const { isAuthenticated, user, ready: authReady } = useRequireAuth({
    redirectToLogin: true,
  });
  const [text, setText] = useState("");
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState("");

  const { data: source, isLoading } = useQuery({
    queryKey: ["quote-source", slug],
    enabled: Boolean(slug) && isAuthenticated,
    queryFn: () => apiFetch<Post>(`/api/posts/${encodeURIComponent(slug)}`),
  });

  const quotePreview = useMemo(
    () => (source ? toQuoteSummary(source) : null),
    [source]
  );

  const submit = async () => {
    if (!source) return;
    const commentary = text.trim();
    if (!commentary) {
      setError("متن نقل‌قول را بنویسید");
      return;
    }
    setSaving(true);
    setError("");
    try {
      const post = await apiFetch<Post>("/api/posts", {
        method: "POST",
        body: JSON.stringify({
          title: commentary.slice(0, 80),
          excerpt: commentary.slice(0, 200),
          content: textToTipTapJSON(commentary),
          content_md: commentary,
          status: "published",
          quoted_post_id: source.id,
        }),
      });
      const username = post.author?.username || user?.username;
      if (username) router.replace(`/${username}/${post.slug}`);
      else router.replace("/");
    } catch (e) {
      setError((e as Error).message || "انتشار ناموفق بود");
    } finally {
      setSaving(false);
    }
  };

  if (!authReady || isLoading) {
    return <Skeleton className="mx-auto mt-8 h-80 w-full max-w-xl rounded-2xl" />;
  }

  if (!isAuthenticated || !canCreatePost(user?.role)) {
    return (
      <p className="py-20 text-center text-muted-foreground">
        برای نقل‌قول باید نویسنده باشید
      </p>
    );
  }

  if (!source || !quotePreview) {
    return (
      <p className="py-20 text-center text-muted-foreground">پست یافت نشد</p>
    );
  }

  const me = user?.display_name || user?.username || "من";

  return (
    <div className="mx-auto max-w-xl px-2 py-6">
      <div className="mb-4 flex items-center justify-between gap-3">
        <Button
          type="button"
          variant="ghost"
          size="icon"
          className="min-h-11 min-w-11"
          onClick={() => router.back()}
          aria-label="بستن"
        >
          <X className="h-5 w-5" />
        </Button>
        <h1 className="min-w-0 text-lg font-bold">نقل‌قول</h1>
        <Button
          type="button"
          className="min-h-11 rounded-full px-5"
          disabled={saving || !text.trim()}
          onClick={() => void submit()}
        >
          {saving ? "..." : "انتشار"}
        </Button>
      </div>

      <div className="rounded-2xl border bg-card p-4 shadow-sm">
        <div className="flex gap-3">
          <Avatar className="h-10 w-10 shrink-0">
            {user?.avatar_url ? <AvatarImage src={user.avatar_url} alt="" /> : null}
            <AvatarFallback>{getInitials(me)}</AvatarFallback>
          </Avatar>
          <div className="min-w-0 flex-1">
            <p className="mb-2 text-sm font-semibold">{me}</p>
            <textarea
              value={text}
              onChange={(e) => setText(e.target.value)}
              placeholder="نظرتان را اضافه کنید..."
              rows={4}
              className="w-full resize-none bg-transparent text-[17px] leading-relaxed outline-none placeholder:text-muted-foreground"
              autoFocus
            />
            <QuotedPostCard quote={quotePreview} className="mt-3 block rounded-2xl border border-border p-3" />
          </div>
        </div>
        {error ? <p className="mt-3 text-sm text-destructive">{error}</p> : null}
      </div>
    </div>
  );
}
