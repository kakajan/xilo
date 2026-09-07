"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import { FileText } from "lucide-react";
import { apiFetch } from "@/lib/api-client";
import { COMPOSE_KIND_LABELS, composeKindFromPost } from "@/lib/post-type";
import type { Post, PostListResponse } from "@/types/post";

export function DraftsList() {
  const [drafts, setDrafts] = useState<Post[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");

  useEffect(() => {
    let cancelled = false;

    async function load() {
      setLoading(true);
      setError("");
      try {
        const res = await apiFetch<PostListResponse>("/api/posts?status=draft&limit=20");
        if (!cancelled) setDrafts(res.data ?? []);
      } catch (err) {
        if (!cancelled) {
          setError((err as Error).message);
          setDrafts([]);
        }
      }
      if (!cancelled) setLoading(false);
    }

    void load();
    return () => {
      cancelled = true;
    };
  }, []);

  if (loading) {
    return (
      <section className="mb-6 rounded-xl border border-border bg-card p-4">
        <h2 className="mb-2 text-sm font-semibold">پیش‌نویس‌های من</h2>
        <p className="text-sm text-muted-foreground">در حال بارگذاری...</p>
      </section>
    );
  }

  if (error || drafts.length === 0) {
    return null;
  }

  return (
    <section className="mb-6 rounded-xl border border-border bg-card p-4">
      <h2 className="mb-3 text-sm font-semibold">پیش‌نویس‌های من</h2>
      <ul className="space-y-2">
        {drafts.map((draft) => {
          const kind = composeKindFromPost(draft);
          const label =
            draft.title?.trim() ||
            draft.content_md?.slice(0, 60) ||
            draft.excerpt?.slice(0, 60) ||
            COMPOSE_KIND_LABELS[kind];
          return (
            <li key={draft.id}>
              <Link
                href={`/write/${draft.id}`}
                className="flex items-center gap-3 rounded-lg px-2 py-2 text-sm transition-colors hover:bg-accent"
              >
                <FileText className="h-4 w-4 shrink-0 text-muted-foreground" aria-hidden />
                <span className="min-w-0 flex-1 truncate">{label}</span>
                <span className="shrink-0 text-xs text-muted-foreground">
                  {COMPOSE_KIND_LABELS[kind]}
                </span>
              </Link>
            </li>
          );
        })}
      </ul>
    </section>
  );
}
