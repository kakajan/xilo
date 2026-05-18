import type { Post } from "@/types/post";

export function getArticleJsonLd(post: Post, baseUrl: string) {
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
      url: `${baseUrl}/${post.author?.username}`,
    },
    publisher: {
      "@type": "Organization",
      name: "Xilo",
    },
    mainEntityOfPage: {
      "@type": "WebPage",
      "@id": `${baseUrl}/${post.author?.username}/${post.slug}`,
    },
    wordCount: post.word_count,
    timeRequired: `PT${post.reading_time}M`,
  };
}
