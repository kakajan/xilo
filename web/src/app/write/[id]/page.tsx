"use client";

import { useState, useCallback, useEffect } from "react";
import { useParams, useRouter } from "next/navigation";
import { TiptapEditor } from "@/components/editor/tiptap-editor";
import { MetadataSidebar } from "@/components/editor/metadata-sidebar";
import { useEditorStore } from "@/stores/editor-store";
import { useAuthStore } from "@/stores/auth-store";
import { apiFetch } from "@/lib/api-client";
import { Button } from "@/components/ui/button";
import { Skeleton } from "@/components/ui/skeleton";
import type { Post } from "@/types/post";

export default function EditPage() {
  const router = useRouter();
  const { id } = useParams<{ id: string }>();
  const { isAuthenticated } = useAuthStore();
  const store = useEditorStore();
  const [html, setHtml] = useState("");
  const [json, setJson] = useState("");
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
      } catch {
        setError("Failed to load post");
      }
      setLoading(false);
    }
    load();
  }, [id]);

  const handleSave = useCallback((newHtml: string, newJson: string) => {
    setHtml(newHtml);
    setJson(newJson);
  }, []);

  const handleSubmit = async () => {
    if (!store.title.trim()) {
      setError("Title is required");
      return;
    }

    setSaving(true);
    setError("");

    try {
      await apiFetch(`/api/posts/${id}`, {
        method: "PATCH",
        body: JSON.stringify({
          title: store.title,
          slug: store.slug || undefined,
          excerpt: store.excerpt || undefined,
          content: json,
          content_md: extractText(json),
          cover_image_url: store.coverImageUrl || undefined,
          category: store.category || undefined,
          tags: store.tags.length > 0 ? store.tags : undefined,
          status: store.status,
          is_premium: store.isPremium,
        }),
      });

      store.reset();
      router.push("/");
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
      <div className="text-center py-20">
        <p className="text-lg text-muted-foreground">Sign in to edit</p>
        <Button className="mt-4" onClick={() => router.push("/login")}>
          Sign in
        </Button>
      </div>
    );
  }

  return (
    <div className="lg:flex lg:gap-8">
      <div className="flex-1 min-w-0">
        <div className="flex items-center justify-between mb-4">
          <h1 className="text-xl font-bold">Edit Post</h1>
          <Button onClick={handleSubmit} disabled={saving}>
            {store.status === "published"
              ? saving ? "Republishing..." : "Republish"
              : saving ? "Saving..." : "Save Draft"}
          </Button>
        </div>

        {error && <p className="text-sm text-destructive mb-3">{error}</p>}

        <TiptapEditor content={json} onSave={handleSave} />
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
