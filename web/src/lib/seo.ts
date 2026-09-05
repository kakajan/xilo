import type { Post } from "@/types/post";
import { postShareUrl, profileShareUrl } from "@/lib/share-urls";

export function getArticleJsonLd(post: Post, baseUrl: string) {
  const origin = baseUrl.replace(/\/$/, "");
  const authorUrl = post.author?.username
    ? profileShareUrl(post.author.username)
    : origin;
  const pageUrl = postShareUrl(post.author?.username || "", post.slug);
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
    publisher: {
      "@type": "Organization",
      name:
        process.env.NEXT_PUBLIC_SITE_DISPLAY ||
        process.env.NEXT_PUBLIC_SITE_NAME_EN ||
        "aile",
    },
    mainEntityOfPage: {
      "@type": "WebPage",
      "@id": pageUrl,
    },
    wordCount: post.word_count,
    timeRequired: `PT${post.reading_time}M`,
  };
}
