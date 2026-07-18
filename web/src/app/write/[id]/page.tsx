"use client";

import { useState, useCallback, useEffect, useRef } from "react";
import { useParams, useRouter } from "next/navigation";
import { TiptapEditor } from "@/components/editor/tiptap-editor";
import { MetadataSidebar } from "@/components/editor/metadata-sidebar";
import { useEditorStore } from "@/stores/editor-store";
import { useAuthStore } from "@/stores/auth-store";
import { apiFetch } from "@/lib/api-client";
import { extractTextFromTipTapJSON } from "@/lib/tiptap-content";
import { Button } from "@/components/ui/button";
import { Skeleton } from "@/components/ui/skeleton";
import { BackButton } from "@/components/shared/back-button";
import type { Post } from "@/types/post";

export default function EditPage() {
  const router = useRouter();
  const { id } = useParams<{ id: string }>();
  const { isAuthenticated } = useAuthStore();
  const store = useEditorStore();
  const contentRef = useRef<{ html: string; json: string } | null>(null);
  const [json, setJson] = useState("");
  const [authorUsername, setAuthorUsername] = useState("");
  const [postSlug, setPostSlug] = useState("");
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    async function load() {
      try {
        const post = await apiFetch<Post>(`/api/posts/${id}`);
        store.setTitle(post.title);
        store.setSlug(post.slug);
        store.setExcerpt(post.excerpt || "");
        store.setCoverImageUrl(post.cover_image_url || "");
        store.setCategory(post.category || "");
        store.setTags(post.tags || []);
        store.setStatus(post.status as "draft" | "published");
        store.setIsPremium(post.is_premium);
        store.setHasUnsaved(false);
        setJson(post.content && post.content !== "{}" ? post.content : "");
        setAuthorUsername(post.author?.username || "");
        setPostSlug(post.slug);
      } catch {
        setError("بارگذاری پست ناموفق بود");
      }
      setLoading(false);
    }
    load();
    // eslint-disable-next-line react-hooks/exhaustive-deps -- load once per id
  }, [id]);

  const handleSave = useCallback((newHtml: string, newJson: string) => {
    setJson(newJson);
  }, []);

  const handleSubmit = async () => {
    if (!store.title.trim()) {
      setError("عنوان لازم است");
      return;
    }

    const payloadJson = contentRef.current?.json || json;
    if (!payloadJson || payloadJson === "{}") {
      setError("متن پست خالی است");
      return;
    }

    setSaving(true);
    setError("");

    try {
      const post = await apiFetch<Post>(`/api/posts/${id}`, {
        method: "PATCH",
        body: JSON.stringify({
          title: store.title,
          slug: store.slug || undefined,
          excerpt: store.excerpt || undefined,
          content: payloadJson,
          content_md: extractTextFromTipTapJSON(payloadJson),
          cover_image_url: store.coverImageUrl || undefined,
          category: store.category || undefined,
          tags: store.tags.length > 0 ? store.tags : undefined,
          status: store.status,
          is_premium: store.isPremium,
        }),
      });

      store.reset();
      const username = post.author?.username || authorUsername;
      const slug = post.slug || postSlug || store.slug;
      router.push(username && slug ? `/${username}/${slug}` : "/");
    } catch (err) {
      setError((err as Error).message);
    }

    setSaving(false);
  };

  if (loading) {
    return (
      <div className="space-y-4">
        <Skeleton className="h-10 w-48" />
        <Skeleton className="h-[400px] w-full" />
      </div>
    );
  }

  if (!isAuthenticated) {
    return (
      <div className="py-20 text-center">
        <p className="text-lg text-muted-foreground">برای ویرایش وارد شوید</p>
        <Button className="mt-4" onClick={() => router.push("/login")}>
          ورود
        </Button>
      </div>
    );
  }

  return (
    <div className="lg:flex lg:gap-8">
      <div className="min-w-0 flex-1">
        <div className="mb-4 flex items-center justify-between gap-3">
          <div className="flex min-w-0 items-center gap-2">
            <BackButton fallbackHref="/" />
            <h1 className="min-w-0 text-xl font-bold">ویرایش پست</h1>
          </div>
          <Button className="shrink-0" onClick={handleSubmit} disabled={saving}>
            {store.status === "published"
              ? saving
                ? "در حال انتشار..."
                : "انتشار مجدد"
              : saving
                ? "در حال ذخیره..."
                : "ذخیره پیش‌نویس"}
          </Button>
        </div>

        {error && <p className="mb-3 text-sm text-destructive">{error}</p>}

        <TiptapEditor content={json} onSave={handleSave} contentRef={contentRef} />
      </div>

      <aside className="mt-8 w-64 shrink-0 lg:mt-0">
        <MetadataSidebar />
      </aside>
    </div>
  );
}
