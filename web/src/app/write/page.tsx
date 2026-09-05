"use client";

import { useState, useCallback, useRef, useEffect } from "react";
import { useRouter } from "next/navigation";
import { TiptapEditor } from "@/components/editor/tiptap-editor";
import { MetadataSidebar } from "@/components/editor/metadata-sidebar";
import { DraftsList } from "@/components/editor/drafts-list";
import { PostTypeBadge, PostTypePicker } from "@/components/editor/post-type-picker";
import { TypedPostFields } from "@/components/editor/typed-post-fields";
import { useEditorStore } from "@/stores/editor-store";
import { useDraftAutosave, useEditorDraftHydrated } from "@/hooks/use-draft-autosave";
import { useRequireAuth } from "@/hooks/use-require-auth";
import { canCreatePost } from "@/lib/auth/permissions";
import { apiFetch } from "@/lib/api-client";
import { extractTextFromTipTapJSON } from "@/lib/tiptap-content";
import { extractHashtags, mergeTags } from "@/lib/hashtag";
import { buildCreatePostPayload, validatePostPayload } from "@/lib/post-type";
import { Button } from "@/components/ui/button";
import type { Post, PostType } from "@/types/post";

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
    audioUrl,
    category,
    tags,
    status,
    isPremium,
    postType,
    linkUrl,
    mediaIds,
    contentJson,
    setContentJson,
    setPostType,
    reset,
  } = useEditorStore();

  const contentRef = useRef<{ html: string; json: string } | null>(null);
  const [json, setJson] = useState("");
  const [microText, setMicroText] = useState("");
  const [typePickerOpen, setTypePickerOpen] = useState(false);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState("");

  useEffect(() => {
    if (postType === "micro" && contentJson && contentJson !== "{}") {
      const plain = extractTextFromTipTapJSON(contentJson);
      if (plain) setMicroText(plain);
    }
  }, [postType, contentJson]);

  const { schedule } = useDraftAutosave({
    persist: setContentJson,
    contentRef,
    enabled: hydrated && isAuthenticated && postType !== "micro",
  });

  const handleSave = useCallback(
    (_html: string, newJson: string) => {
      setJson(newJson);
      schedule(newJson);
    },
    [schedule]
  );

  const handlePostTypeChange = (next: PostType) => {
    setPostType(next);
    setError("");
  };

  const resolveContentJson = () => {
    if (postType === "micro") {
      if (!microText.trim()) return "{}";
      return JSON.stringify({
        type: "doc",
        content: [
          {
            type: "paragraph",
            content: [{ type: "text", text: microText }],
          },
        ],
      });
    }
    return contentRef.current?.json || json || contentJson;
  };

  const handleSubmit = async () => {
    const payloadJson = resolveContentJson();
    const validationError = validatePostPayload({
      postType,
      title,
      slug,
      excerpt,
      contentJson: payloadJson,
      coverImageUrl,
      audioUrl,
      category,
      tags,
      status,
      isPremium,
      linkUrl,
      mediaIds,
    });

    if (validationError) {
      setError(validationError);
      return;
    }

    setSaving(true);
    setError("");

    try {
      const contentMd =
        postType === "micro"
          ? microText.trim()
          : extractTextFromTipTapJSON(payloadJson);
      const mergedTags = mergeTags(extractHashtags(contentMd), tags);
      const body = buildCreatePostPayload({
        postType,
        title,
        slug,
        excerpt,
        contentJson: payloadJson,
        coverImageUrl,
        audioUrl,
        category,
        tags: mergedTags,
        status,
        isPremium,
        linkUrl,
        mediaIds,
      });

      const post = await apiFetch<Post>("/api/posts", {
        method: "POST",
        body: JSON.stringify(body),
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

  const showTiptap = postType === "article" || postType === "photo" || postType === "link";

  return (
    <>
      <DraftsList />

      <div className="flex flex-col gap-6 md:flex-row md:items-start md:gap-8">
        <div className="min-w-0 flex-1">
          <div className="mb-4 flex flex-wrap items-center justify-between gap-3">
            <div className="flex min-w-0 items-center gap-3">
              <h1 className="min-w-0 text-xl font-bold">پست جدید</h1>
              <PostTypeBadge value={postType} onClick={() => setTypePickerOpen(true)} />
            </div>
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

          <div className="mb-4 space-y-4">
            <TypedPostFields
              postType={postType}
              microText={microText}
              onMicroTextChange={(text) => {
                setMicroText(text);
                useEditorStore.setState({ hasUnsaved: true });
              }}
            />
          </div>

          {showTiptap ? (
            <TiptapEditor
              content={contentJson || undefined}
              onSave={handleSave}
              contentRef={contentRef}
            />
          ) : null}
        </div>

        <aside className="w-full shrink-0 md:sticky md:top-6 md:w-72 lg:w-80">
          <MetadataSidebar />
        </aside>
      </div>

      <PostTypePicker
        value={postType}
        onChange={handlePostTypeChange}
        open={typePickerOpen}
        onOpenChange={setTypePickerOpen}
      />
    </>
  );
}
