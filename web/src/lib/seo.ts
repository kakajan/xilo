import type { Metadata } from "next";
import type { Post } from "@/types/post";
import type { PublicProfile } from "@/types/chat";
import {
  canonicalSiteOrigin,
  isValidPublicSlug,
  isValidPublicUsername,
  sitemapLastModified,
  uniqueSitemapUrls,
  type SitemapUrlInput,
} from "@/lib/public-url";
import { postShareUrl, profileShareUrl } from "@/lib/share-urls";

export const noIndexMetadata: Metadata = {
  robots: {
    index: false,
    follow: false,
    googleBot: { index: false, follow: false, noimageindex: true },
  },
};

export function siteNameFa(): string {
  return process.env.NEXT_PUBLIC_SITE_NAME_FA || "آیله";
}

export function publisherLogoUrl(origin = canonicalSiteOrigin()): string {
  return `${origin}/brand/aile/app-icon-512.png`;
}

export function jsonLdString(data: unknown): string {
  return JSON.stringify(data, (_key, value) =>
    value == null || value === "" ? undefined : value,
  );
}

export function noIndexCanonical(path: string, title?: string): Metadata {
  const origin = canonicalSiteOrigin();
  const normalized = path.startsWith("/") ? path : `/${path}`;
  const url = normalized === "/" ? `${origin}/` : `${origin}${normalized}`;
  return {
    ...noIndexMetadata,
    ...(title ? { title } : {}),
    alternates: { canonical: url },
    openGraph: { url, title, locale: "fa_IR" },
  };
}

function organizationJsonLd(origin: string) {
  return {
    "@type": "Organization",
    name: siteNameFa(),
    url: origin,
    logo: {
      "@type": "ImageObject",
      url: publisherLogoUrl(origin),
    },
  };
}

export function getWebsiteJsonLd() {
  const origin = canonicalSiteOrigin();
  return {
    "@context": "https://schema.org",
    "@type": "WebSite",
    name: siteNameFa(),
    url: origin,
    inLanguage: "fa-IR",
    publisher: organizationJsonLd(origin),
  };
}

export function getBlogJsonLd(posts: Post[]) {
  const origin = canonicalSiteOrigin();
  return {
    "@context": "https://schema.org",
    "@type": "Blog",
    name: siteNameFa(),
    url: origin,
    inLanguage: "fa-IR",
    publisher: organizationJsonLd(origin),
    blogPost: posts
      .filter(
        (post) =>
          isValidPublicUsername(post.author?.username) && isValidPublicSlug(post.slug),
      )
      .slice(0, 10)
      .map((post) => ({
        "@type": "BlogPosting",
        headline: post.title,
        url: postShareUrl(post.author?.username ?? "", post.slug),
        datePublished: post.published_at,
        dateModified: post.updated_at,
        author: {
          "@type": "Person",
          name: post.author?.display_name || post.author?.username,
        },
      })),
  };
}

export function getPersonJsonLd(profile: PublicProfile) {
  const url = profileShareUrl(profile.username);
  return {
    "@context": "https://schema.org",
    "@type": "Person",
    name: profile.display_name || profile.username,
    alternateName: `@${profile.username}`,
    url,
    identifier: profile.username,
    description: profile.bio,
    image: profile.avatar_url,
  };
}

export function getArticleJsonLd(post: Post, _baseUrl?: string) {
  const origin = canonicalSiteOrigin();
  const authorName = post.author?.username;
  const authorUrl = isValidPublicUsername(authorName)
    ? profileShareUrl(authorName)
    : origin;
  const pageUrl = postShareUrl(
    isValidPublicUsername(authorName) ? authorName : "",
    post.slug,
  );
  return {
    "@context": "https://schema.org",
    "@type": "Article",
    headline: post.title,
    description: post.excerpt,
    image: post.cover_image_url,
    datePublished: post.published_at,
    dateModified: post.updated_at,
    author: {
      "@type": "Person",
      name: post.author?.display_name || post.author?.username,
      url: authorUrl,
    },
    publisher: organizationJsonLd(origin),
    mainEntityOfPage: {
      "@type": "WebPage",
      "@id": pageUrl,
    },
    wordCount: post.word_count,
    timeRequired: post.reading_time ? `PT${post.reading_time}M` : undefined,
    url: pageUrl,
  };
}

export function sitemapPostEntry(post: {
  author?: { username?: string } | null;
  slug?: string | null;
  status?: string | null;
  updated_at?: string | null;
  published_at?: string | null;
}): SitemapUrlInput | null {
  if (post.status && post.status !== "published") return null;
  const slug = post.slug?.trim() ?? "";
  const username = post.author?.username?.trim() ?? "";
  if (!isValidPublicSlug(slug) || !isValidPublicUsername(username)) return null;
  return {
    url: postShareUrl(username, slug),
    lastModified: sitemapLastModified(post.updated_at, post.published_at),
    changeFrequency: "weekly",
    priority: 0.7,
  };
}

export function sitemapProfileEntry(
  username: string | undefined | null,
  lastModified?: Date,
): SitemapUrlInput | null {
  if (!isValidPublicUsername(username)) return null;
  return {
    url: profileShareUrl(username),
    lastModified,
    changeFrequency: "weekly",
    priority: 0.6,
  };
}

export function sitemapTagEntry(tag: string | undefined | null): SitemapUrlInput | null {
  const value = tag?.trim() ?? "";
  if (!value || value.length > 30) return null;
  const origin = canonicalSiteOrigin();
  return {
    url: `${origin}/tag/${encodeURIComponent(value)}`,
    changeFrequency: "daily",
    priority: 0.5,
  };
}

export function buildPublicSitemap(
  posts: Array<Parameters<typeof sitemapPostEntry>[0]>,
  tags: string[] = [],
): SitemapUrlInput[] {
  const origin = canonicalSiteOrigin();
  const now = new Date();
  const staticEntries: SitemapUrlInput[] = [
    { url: origin, lastModified: now, changeFrequency: "daily", priority: 1 },
    { url: `${origin}/discover`, lastModified: now, changeFrequency: "hourly", priority: 0.8 },
  ];
  const postEntries: SitemapUrlInput[] = [];
  const profiles = new Map<string, Date>();
  for (const post of posts) {
    const entry = sitemapPostEntry(post);
    if (!entry) continue;
    postEntries.push(entry);
    const username = post.author?.username?.trim();
    if (isValidPublicUsername(username)) {
      const stamp = sitemapLastModified(post.updated_at, post.published_at);
      const prev = profiles.get(username);
      if (!prev || stamp > prev) profiles.set(username, stamp);
    }
  }
  const profileEntries = [...profiles.entries()].map(([username, lastModified]) =>
    sitemapProfileEntry(username, lastModified),
  );
  const tagEntries = tags.map((tag) => sitemapTagEntry(tag));
  return uniqueSitemapUrls([...staticEntries, ...profileEntries, ...postEntries, ...tagEntries]);
}
