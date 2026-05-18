"use client";

import { useState, useCallback } from "react";
import { useRouter } from "next/navigation";
import { TiptapEditor } from "@/components/editor/tiptap-editor";
import { MetadataSidebar } from "@/components/editor/metadata-sidebar";
import { useEditorStore } from "@/stores/editor-store";
import { useAuthStore } from "@/stores/auth-store";
import { apiFetch } from "@/lib/api-client";
import { Button } from "@/components/ui/button";
import type { Post } from "@/types/post";

export default function WritePage() {
  const router = useRouter();
  const { isAuthenticated } = useAuthStore();
  const { title, slug, excerpt, coverImageUrl, category, tags, status, isPremium, reset } =
    useEditorStore();

  const [html, setHtml] = useState("");
  const [json, setJson] = useState("");
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState("");

  if (!isAuthenticated) {
    return (
      <div className="text-center py-20">
        <p className="text-lg text-muted-foreground">Sign in to write</p>
        <Button className="mt-4" onClick={() => router.push("/login")}>
          Sign in
        </Button>
      </div>
    );
  }

  const handleSave = useCallback((newHtml: string, newJson: string) => {
    setHtml(newHtml);
    setJson(newJson);
  }, []);

  const handleSubmit = async () => {
    if (!title.trim()) {
      setError("Title is required");
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

  return (
    <div className="lg:flex lg:gap-8">
      <div className="flex-1 min-w-0">
        <div className="flex items-center justify-between mb-4">
          <h1 className="text-xl font-bold">New Post</h1>
          <Button onClick={handleSubmit} disabled={saving}>
            {status === "published"
              ? saving ? "Publishing..." : "Publish"
              : saving ? "Saving..." : "Save Draft"}
          </Button>
        </div>

        {error && (
          <p className="text-sm text-destructive mb-3">{error}</p>
        )}

        <TiptapEditor onSave={handleSave} />
      </div>

      <aside className="w-64 shrink-0 mt-8 lg:mt-0">
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
