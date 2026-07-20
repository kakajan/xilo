"use client";

import { useState, useCallback, useEffect, useRef } from "react";
import { useParams, useRouter, useSearchParams } from "next/navigation";
import { TiptapEditor } from "@/components/editor/tiptap-editor";
import { MetadataSidebar } from "@/components/editor/metadata-sidebar";
import { useEditorStore } from "@/stores/editor-store";
import { useDraftAutosave, useEditorDraftHydrated } from "@/hooks/use-draft-autosave";
import { useRequireAuth } from "@/hooks/use-require-auth";
import { apiFetch } from "@/lib/api-client";
import { fetchPostForEdit } from "@/lib/api/posts";
import { extractTextFromTipTapJSON } from "@/lib/tiptap-content";
import { extractHashtags, mergeTags } from "@/lib/hashtag";
import { Button } from "@/components/ui/button";
import { Skeleton } from "@/components/ui/skeleton";
import type { Post } from "@/types/post";

export default function EditPage() {
  const router = useRouter();
  const { id } = useParams<{ id: string }>();
  const searchParams = useSearchParams();
  const slugHint = searchParams.get("slug");
  const hydrated = useEditorDraftHydrated();
  const { isAuthenticated, ready: authReady } = useRequireAuth({
    redirectToLogin: false,
  });
  const store = useEditorStore();
  const contentRef = useRef<{ html: string; json: string } | null>(null);
  const [json, setJson] = useState("");
  const [postId, setPostId] = useState("");
  const [authorUsername, setAuthorUsername] = useState("");
  const [postSlug, setPostSlug] = useState("");
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(true);

  const { schedule } = useDraftAutosave({
    persist: (nextJson) => {
      if (!postId) return;
      store.setEditDraft(postId, nextJson);
    },
    contentRef,
    enabled: hydrated && isAuthenticated && !!postId,
  });

  useEffect(() => {
    if (!hydrated) return;

    async function load() {
      setLoading(true);
      setError("");
      try {
        const post = await fetchPostForEdit(id, slugHint);
        setPostId(post.id);
        store.setTitle(post.title);
        store.setSlug(post.slug);
        store.setExcerpt(post.excerpt || "");
        store.setCoverImageUrl(post.cover_image_url || "");
        store.setAudioUrl(post.audio_url || "");
        store.setCategory(post.category || "");
        store.setTags(post.tags || []);
        store.setStatus(post.status as "draft" | "published");
        store.setIsPremium(post.is_premium);

        const serverContent = post.content && post.content !== "{}" ? post.content : "";
        const snap = useEditorStore.getState();
        const localEdit =
          snap.editDraftId === post.id && snap.editContentJson && snap.editContentJson !== "{}"
            ? snap.editContentJson
            : "";
        const initial = localEdit || serverContent;
        setJson(initial);
        store.setHasUnsaved(Boolean(localEdit));
        setAuthorUsername(post.author?.username || "");
        setPostSlug(post.slug);
      } catch {
        setError("بارگذاری پست ناموفق بود");
      }
      setLoading(false);
    }
    void load();
    // eslint-disable-next-line react-hooks/exhaustive-deps -- load when route identity / hydration changes
  }, [id, slugHint, hydrated]);

  const handleSave = useCallback(
    (_html: string, newJson: string) => {
      setJson(newJson);
      schedule(newJson);
    },
    [schedule]
  );

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

    if (!postId) {
      setError("شناسه پست مشخص نیست");
      return;
    }

    setSaving(true);
    setError("");

    try {
      const contentMd = extractTextFromTipTapJSON(payloadJson);
      const mergedTags = mergeTags(extractHashtags(contentMd), store.tags);
      const post = await apiFetch<Post>(`/api/posts/${postId}`, {
        method: "PATCH",
        body: JSON.stringify({
          title: store.title,
          slug: store.slug || undefined,
          excerpt: store.excerpt || undefined,
          content: payloadJson,
          content_md: contentMd,
          cover_image_url: store.coverImageUrl || undefined,
          audio_url: store.audioUrl,
          category: store.category || undefined,
          tags: mergedTags,
          status: store.status,
          is_premium: store.isPremium,
        }),
      });

      store.clearEditDraft();
      store.reset();
      const username = post.author?.username || authorUsername;
      const slug = post.slug || postSlug || store.slug;
      router.push(username && slug ? `/${username}/${slug}` : "/");
    } catch (err) {
      setError((err as Error).message);
    }

    setSaving(false);
  };

  if (!authReady || !hydrated || loading) {
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
    <div className="flex flex-col gap-6 md:flex-row md:items-start md:gap-8">
      <div className="min-w-0 flex-1">
        <div className="mb-4 flex items-center justify-between gap-3">
          <h1 className="min-w-0 text-xl font-bold">ویرایش پست</h1>
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

      <aside className="w-full shrink-0 md:sticky md:top-6 md:w-72 lg:w-80">
        <MetadataSidebar />
      </aside>
    </div>
  );
}
