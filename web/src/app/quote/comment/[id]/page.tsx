"use client";

import { useState } from "react";
import { useParams, useRouter } from "next/navigation";
import { useQuery } from "@tanstack/react-query";
import { X } from "lucide-react";
import { apiFetch } from "@/lib/api-client";
import { canCreatePost } from "@/lib/auth/permissions";
import { useRequireAuth } from "@/hooks/use-require-auth";
import { QuotedCommentCard } from "@/components/post/quoted-comment-card";
import { Avatar, AvatarFallback, AvatarImage } from "@/components/ui/avatar";
import { Button } from "@/components/ui/button";
import { Skeleton } from "@/components/ui/skeleton";
import { getInitials } from "@/lib/utils";
import type { Comment } from "@/types/comment";
import type { Post, QuotedCommentSummary } from "@/types/post";

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

function toQuoteSummary(comment: Comment): QuotedCommentSummary {
  return {
    id: comment.id,
    content: comment.content,
    author: comment.author as QuotedCommentSummary["author"],
    post_id: comment.post_id,
    post_title: comment.post?.title || "",
    post_slug: comment.post?.slug || "",
    post_author_username: comment.post?.author_username || "",
    created_at: comment.created_at,
  };
}

export default function QuoteCommentComposePage() {
  const { id } = useParams<{ id: string }>();
  const router = useRouter();
  const { isAuthenticated, user, ready: authReady } = useRequireAuth({
    redirectToLogin: true,
  });
  const [text, setText] = useState("");
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState("");

  const { data: source, isLoading } = useQuery({
    queryKey: ["quote-comment-source", id],
    enabled: Boolean(id) && isAuthenticated,
    queryFn: () => apiFetch<Comment>(`/api/comments/${encodeURIComponent(id)}`),
  });

  const quotePreview = source ? toQuoteSummary(source) : null;

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
          quoted_comment_id: source.id,
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
        فقط نویسندگان می‌توانند نظر را نقل‌قول کنند.
      </p>
    );
  }

  if (!source || !quotePreview) {
    return (
      <p className="py-20 text-center text-muted-foreground">نظر یافت نشد.</p>
    );
  }

  return (
    <div className="mx-auto w-full max-w-xl px-4 py-6">
      <div className="mb-4 flex items-center justify-between gap-2">
        <h1 className="text-lg font-semibold">نقل‌قول نظر</h1>
        <Button variant="ghost" size="icon" onClick={() => router.back()} aria-label="بستن">
          <X className="h-5 w-5" />
        </Button>
      </div>

      <div className="rounded-2xl border border-border p-4">
        <div className="mb-3 flex items-center gap-3">
          <Avatar className="h-10 w-10 shrink-0">
            {user?.avatar_url ? <AvatarImage src={user.avatar_url} alt="" /> : null}
            <AvatarFallback>
              {getInitials(user?.display_name || user?.username || "?")}
            </AvatarFallback>
          </Avatar>
          <span className="min-w-0 font-medium">
            {user?.display_name || user?.username}
          </span>
        </div>

        <textarea
          value={text}
          onChange={(e) => setText(e.target.value)}
          placeholder="نظر خود را بنویسید…"
          className="min-h-[120px] w-full resize-none bg-transparent text-[15px] outline-none placeholder:text-muted-foreground"
          autoFocus
        />

        <QuotedCommentCard quote={quotePreview} />

        {error ? <p className="mt-3 text-sm text-destructive">{error}</p> : null}

        <div className="mt-4 flex justify-end">
          <Button onClick={submit} disabled={saving}>
            {saving ? "در حال انتشار…" : "انتشار"}
          </Button>
        </div>
      </div>
    </div>
  );
}
