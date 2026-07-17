import type { MetadataRoute } from "next";
import type { Post } from "@/types/post";

export default async function sitemap(): Promise<MetadataRoute.Sitemap> {
  const baseUrl = process.env.NEXT_PUBLIC_URL || "https://aile.ir";

  const staticEntries: MetadataRoute.Sitemap = [
    { url: baseUrl, lastModified: new Date(), changeFrequency: "daily", priority: 1 },
    { url: `${baseUrl}/search`, lastModified: new Date(), changeFrequency: "weekly", priority: 0.5 },
  ];

  try {
    const apiBase = process.env.INTERNAL_API_URL || process.env.NEXT_PUBLIC_API_URL || "https://brain.aile.ir";
    const res = await fetch(`${apiBase}/api/posts?limit=1000`, {
      next: { revalidate: 3600 },
    });
    if (!res.ok) return staticEntries;
    const body = (await res.json()) as { data?: Post[] };
    const posts = Array.isArray(body.data) ? body.data : [];
    const postEntries: MetadataRoute.Sitemap = posts.map((post) => ({
      url: `${baseUrl}/${post.author?.username || "unknown"}/${post.slug}`,
      lastModified: new Date(post.updated_at || post.published_at || Date.now()),
      changeFrequency: "weekly",
      priority: 0.7,
    }));
    return [...staticEntries, ...postEntries];
  } catch {
    return staticEntries;
  }
}
