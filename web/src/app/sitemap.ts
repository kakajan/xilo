import type { MetadataRoute } from "next";
import type { Post } from "@/types/post";
import { serverApiBase } from "@/lib/public-url";
import { buildPublicSitemap } from "@/lib/seo";
import { fetchTrendingTags } from "@/lib/posts-server";

const PAGE_SIZE = 50;
const MAX_PAGES = 40;

export default async function sitemap(): Promise<MetadataRoute.Sitemap> {
  const posts = await loadPublishedPosts();
  const tags = await fetchTrendingTags(30);
  return buildPublicSitemap(posts, tags);
}

async function loadPublishedPosts(): Promise<Post[]> {
  const base = serverApiBase();
  const posts: Post[] = [];
  let cursor = "";
  try {
    for (let page = 0; page < MAX_PAGES; page++) {
      const params = new URLSearchParams({
        limit: String(PAGE_SIZE),
        status: "published",
      });
      if (cursor) params.set("cursor", cursor);
      const res = await fetch(`${base}/api/posts?${params}`, {
        next: { revalidate: 3600 },
      });
      if (!res.ok) break;
      const body = (await res.json()) as {
        data?: Post[];
        next_cursor?: string;
        has_more?: boolean;
      };
      const batch = Array.isArray(body.data) ? body.data : [];
      posts.push(...batch);
      if (!body.has_more || !body.next_cursor || batch.length === 0) break;
      cursor = body.next_cursor;
    }
  } catch {
    return posts;
  }
  return posts;
}
