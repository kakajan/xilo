import type { Metadata } from "next";
import type { Post } from "@/types/post";

export async function generateSitemap() {
  const baseUrl = process.env.NEXT_PUBLIC_URL || "http://localhost:3000";

  try {
    const res = await fetch("http://localhost:8000/api/posts?limit=1000");
    const { data: posts } = (await res.json()) as { data: Post[] };

    const postEntries = posts.map((post) => ({
      url: `${baseUrl}/${post.author?.username || "unknown"}/${post.slug}`,
      lastModified: post.updated_at || post.published_at || new Date().toISOString(),
      changeFrequency: "weekly" as const,
      priority: 0.7,
    }));

    return [
      { url: baseUrl, lastModified: new Date().toISOString(), changeFrequency: "daily", priority: 1.0 },
      { url: `${baseUrl}/search`, lastModified: new Date().toISOString(), changeFrequency: "weekly", priority: 0.5 },
      ...postEntries,
    ];
  } catch {
    return [
      { url: baseUrl, lastModified: new Date().toISOString(), changeFrequency: "daily", priority: 1.0 },
    ];
  }
}

export function generateSitemapXml(entries: {
  url: string;
  lastModified: string;
  changeFrequency: string;
  priority: number;
}[]) {
  return `<?xml version="1.0" encoding="UTF-8"?>
<urlset xmlns="http://www.sitemaps.org/schemas/sitemap/0.9">
${entries
  .map(
    (entry) => `  <url>
    <loc>${entry.url}</loc>
    <lastmod>${new Date(entry.lastModified).toISOString()}</lastmod>
    <changefreq>${entry.changeFrequency}</changefreq>
    <priority>${entry.priority}</priority>
  </url>`
  )
  .join("\n")}
</urlset>`;
}

export default async function Sitemap() {
  const entries = await generateSitemap();
  return new Response(generateSitemapXml(entries), {
    headers: { "Content-Type": "application/xml" },
  });
}
