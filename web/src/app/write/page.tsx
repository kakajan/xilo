"use client";

import { useState, useCallback, useRef } from "react";
import { useRouter } from "next/navigation";
import { TiptapEditor } from "@/components/editor/tiptap-editor";
import { MetadataSidebar } from "@/components/editor/metadata-sidebar";
import { useEditorStore } from "@/stores/editor-store";
import { useDraftAutosave, useEditorDraftHydrated } from "@/hooks/use-draft-autosave";
import { useRequireAuth } from "@/hooks/use-require-auth";
import { canCreatePost } from "@/lib/auth/permissions";
import { apiFetch } from "@/lib/api-client";
import { extractTextFromTipTapJSON } from "@/lib/tiptap-content";
import { extractHashtags, mergeTags } from "@/lib/hashtag";
import { Button } from "@/components/ui/button";
import type { Post } from "@/types/post";

export default function WritePage() {
  const router = useRouter();
  const hydrated = useEditorDraftHydrated();
  const { isAuthenticated, user, ready: authReady } = useRequireAuth({
    redirectToLogin: false,
  });
  const {
    title,
    slug,
    excerpt,
    coverImageUrl,
    category,
    tags,
    status,
    isPremium,
    contentJson,
    setContentJson,
    reset,
  } = useEditorStore();

  const contentRef = useRef<{ html: string; json: string } | null>(null);
  const [json, setJson] = useState("");
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState("");

  const { schedule } = useDraftAutosave({
    persist: setContentJson,
    contentRef,
    enabled: hydrated && isAuthenticated,
  });

  const handleSave = useCallback(
    (_html: string, newJson: string) => {
      setJson(newJson);
      schedule(newJson);
    },
    [schedule]
  );

  const handleSubmit = async () => {
    if (!title.trim()) {
      setError("عنوان لازم است");
      return;
    }

    const payloadJson = contentRef.current?.json || json || contentJson;
    if (!payloadJson || payloadJson === "{}") {
      setError("متن پست خالی است");
      return;
    }

    setSaving(true);
    setError("");

    try {
      const contentMd = extractTextFromTipTapJSON(payloadJson);
      const mergedTags = mergeTags(extractHashtags(contentMd), tags);
      const post = await apiFetch<Post>("/api/posts", {
        method: "POST",
        body: JSON.stringify({
          title,
          slug: slug || undefined,
          excerpt: excerpt || undefined,
          content: payloadJson,
          content_md: contentMd,
          cover_image_url: coverImageUrl || undefined,
          category: category || undefined,
          tags: mergedTags.length > 0 ? mergedTags : undefined,
          status,
          is_premium: isPremium,
        }),
      });

      reset();
      router.push(`/${post.author?.username}/${post.slug}`);
    } catch (err) {
      setError((err as Error).message);
    }

    setSaving(false);
  };

  if (!authReady) {
    return (
      <div className="py-20 text-center text-muted-foreground">در حال بررسی ورود...</div>
    );
  }

  if (!isAuthenticated) {
    return (
      <div className="py-20 text-center">
        <p className="text-lg text-muted-foreground">برای نوشتن وارد شوید</p>
        <Button className="mt-4 min-h-11" onClick={() => router.push("/login")}>
          ورود
        </Button>
      </div>
    );
  }

  if (!canCreatePost(user?.role)) {
    return (
      <div className="py-20 text-center">
        <p className="text-lg text-muted-foreground">
          شما اجازهٔ ارسال پست ندارید. می‌توانید نظر بگذارید و از چت استفاده کنید.
        </p>
        <Button className="mt-4 min-h-11" onClick={() => router.push("/")}>
          بازگشت
        </Button>
      </div>
    );
  }

  if (!hydrated) {
    return (
      <div className="py-20 text-center text-muted-foreground">در حال بازیابی پیش‌نویس...</div>
    );
  }

  return (
    <div className="flex flex-col gap-6 md:flex-row md:items-start md:gap-8">
      <div className="min-w-0 flex-1">
        <div className="mb-4 flex items-center justify-between gap-3">
          <h1 className="min-w-0 text-xl font-bold">پست جدید</h1>
          <Button className="min-h-11 shrink-0" onClick={handleSubmit} disabled={saving}>
            {status === "published"
              ? saving
                ? "در حال انتشار..."
                : "انتشار"
              : saving
                ? "در حال ذخیره..."
                : "ذخیره پیش‌نویس"}
          </Button>
        </div>

        {error && <p className="mb-3 text-sm text-destructive">{error}</p>}

        <TiptapEditor
          content={contentJson || undefined}
          onSave={handleSave}
          contentRef={contentRef}
        />
      </div>

      <aside className="w-full shrink-0 md:sticky md:top-6 md:w-72 lg:w-80">
        <MetadataSidebar />
      </aside>
    </div>
  );
}
