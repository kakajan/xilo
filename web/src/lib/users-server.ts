import type { PublicProfile } from "@/types/chat";
import type { Post } from "@/types/post";
import { serverApiBase } from "@/lib/public-url";

export async function fetchPublicProfile(username: string): Promise<PublicProfile | null> {
  try {
    const res = await fetch(
      `${serverApiBase()}/api/users/${encodeURIComponent(username)}`,
      { next: { revalidate: 60 } },
    );
    if (!res.ok) return null;
    return res.json();
  } catch {
    return null;
  }
}

export async function fetchUserPublishedPosts(username: string): Promise<Post[]> {
  try {
    const res = await fetch(
      `${serverApiBase()}/api/users/${encodeURIComponent(username)}/posts?tab=posts&limit=20`,
      { next: { revalidate: 60 } },
    );
    if (!res.ok) return [];
    const body = (await res.json()) as { data?: Post[] };
    return Array.isArray(body.data) ? body.data : [];
  } catch {
    return [];
  }
}
