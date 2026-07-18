import { apiFetch } from "@/lib/api-client";
import type { Post, PostListResponse } from "@/types/post";

async function tryGetPost(key: string): Promise<Post | null> {
  if (!key) return null;
  try {
    return await apiFetch<Post>(`/api/posts/${encodeURIComponent(key)}`);
  } catch {
    return null;
  }
}

/**
 * Load a post for the editor. Production GET only resolves public slugs;
 * UUID paths 404, so we fall back to slug hints and a list lookup.
 */
export async function fetchPostForEdit(
  idOrSlug: string,
  slugHint?: string | null
): Promise<Post> {
  const candidates = [slugHint, idOrSlug].filter(
    (v): v is string => typeof v === "string" && v.length > 0
  );

  for (const key of candidates) {
    const post = await tryGetPost(key);
    if (post) return post;
  }

  const list = await apiFetch<PostListResponse>("/api/posts?limit=50");
  const hit = list.data?.find((p) => p.id === idOrSlug || p.slug === idOrSlug);
  if (hit?.slug) {
    const post = await tryGetPost(hit.slug);
    if (post) return post;
  }

  throw new Error("post not found");
}
