"use client";

import { useState, useCallback } from "react";
import { useRouter } from "next/navigation";
import { TiptapEditor } from "@/components/editor/tiptap-editor";
import { MetadataSidebar } from "@/components/editor/metadata-sidebar";
import { useEditorStore } from "@/stores/editor-store";
import { canCreatePost } from "@/lib/auth/permissions";
import { useAuthStore } from "@/stores/auth-store";
import { apiFetch } from "@/lib/api-client";
import { Button } from "@/components/ui/button";
import type { Post } from "@/types/post";

export default function WritePage() {
  const router = useRouter();
  const { isAuthenticated, user } = useAuthStore();
  const { title, slug, excerpt, coverImageUrl, category, tags, status, isPremium, reset } =
    useEditorStore();

  const [, setHtml] = useState("");
  const [json, setJson] = useState("");
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState("");

  const handleSave = useCallback((newHtml: string, newJson: string) => {
    setHtml(newHtml);
    setJson(newJson);
  }, []);

  const handleSubmit = async () => {
    if (!title.trim()) {
      setError("عنوان لازم است");
      return;
    }

    setSaving(true);
    setError("");

    try {
      const post = await apiFetch<Post>("/api/posts", {
        method: "POST",
        body: JSON.stringify({
          title,
          slug: slug || undefined,
          excerpt: excerpt || undefined,
          content: json,
          content_md: extractText(json),
          cover_image_url: coverImageUrl || undefined,
          category: category || undefined,
          tags: tags.length > 0 ? tags : undefined,
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

  return (
    <div className="lg:flex lg:gap-8">
      <div className="min-w-0 flex-1">
        <div className="mb-4 flex items-center justify-between">
          <h1 className="text-xl font-bold">پست جدید</h1>
          <Button className="min-h-11" onClick={handleSubmit} disabled={saving}>
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

        <TiptapEditor onSave={handleSave} />
      </div>

      <aside className="mt-8 w-64 shrink-0 lg:mt-0">
        <MetadataSidebar />
      </aside>
    </div>
  );
}

function extractText(json: string): string {
  try {
    const obj = JSON.parse(json);
    const texts: string[] = [];
    const walk = (node: Record<string, unknown>) => {
      if (node.text) texts.push(node.text as string);
      if (node.content && Array.isArray(node.content)) {
        (node.content as Record<string, unknown>[]).forEach(walk);
      }
    };
    walk(obj);
    return texts.join(" ");
  } catch {
    return "";
  }
}
