import type { Post } from "@/types/post";
import { serverApiBase } from "@/lib/public-url";

export async function fetchPublishedPost(slug: string): Promise<Post | null> {
  try {
    const res = await fetch(`${serverApiBase()}/api/posts/${encodeURIComponent(slug)}`, {
      next: { revalidate: 60 },
    });
    if (!res.ok) return null;
    return res.json();
  } catch {
    return null;
  }
}

export async function fetchPublishedPosts(limit = 10): Promise<Post[]> {
  try {
    const params = new URLSearchParams({
      limit: String(Math.min(Math.max(limit, 1), 50)),
      status: "published",
    });
    const res = await fetch(`${serverApiBase()}/api/posts?${params}`, {
      next: { revalidate: 60 },
    });
    if (!res.ok) return [];
    const body = (await res.json()) as { data?: Post[] };
    return Array.isArray(body.data) ? body.data : [];
  } catch {
    return [];
  }
}

export async function fetchTrendingTags(limit = 20): Promise<string[]> {
  try {
    const res = await fetch(
      `${serverApiBase()}/api/tags/trending?limit=${Math.min(Math.max(limit, 1), 50)}`,
      { next: { revalidate: 3600 } },
    );
    if (!res.ok) return [];
    const body = (await res.json()) as { data?: Array<{ tag?: string }> };
    return (body.data ?? []).map((item) => item.tag?.trim() ?? "").filter(Boolean);
  } catch {
    return [];
  }
}
