import { Suspense } from "react";
import { notFound } from "next/navigation";
import Link from "next/link";
import type { Post } from "@/types/post";
import { apiFetch } from "@/lib/api-client";
import { formatDate, readingTimeText, getInitials } from "@/lib/utils";
import { Avatar, AvatarFallback } from "@/components/ui/avatar";
import { Skeleton } from "@/components/ui/skeleton";
import { CommentSection } from "@/components/comment/comment-section";

async function getPost(username: string, slug: string): Promise<Post | null> {
  try {
    const res = await fetch(`http://localhost:8000/api/posts/${slug}`, {
      next: { revalidate: 60 },
    });
    if (!res.ok) return null;
    return res.json();
  } catch {
    return null;
  }
}

export default async function PostPage({
  params,
}: {
  params: { username: string; slug: string };
}) {
  const post = await getPost(params.username, params.slug);
  if (!post) notFound();

  const authorName = post.author?.display_name || post.author?.username || "Unknown";

  return (
    <article className="max-w-3xl mx-auto">
      <header className="mb-8">
        <h1 className="text-4xl font-bold mb-4">{post.title}</h1>

        <div className="flex items-center gap-3 mb-4">
          <Link href={`/${post.author?.username}`}>
            <Avatar className="h-10 w-10">
              <AvatarFallback>{getInitials(authorName)}</AvatarFallback>
            </Avatar>
          </Link>
          <div>
            <Link href={`/${post.author?.username}`} className="font-medium hover:underline">
              {authorName}
            </Link>
            <p className="text-sm text-muted-foreground">
              {post.published_at ? formatDate(post.published_at) : ""}
              {post.reading_time ? ` · ${readingTimeText(post.reading_time)}` : ""}
              {post.category ? ` · ${post.category}` : ""}
            </p>
          </div>
        </div>

        {post.tags?.length > 0 && (
          <div className="flex gap-2 flex-wrap">
            {post.tags.map((tag) => (
              <Link
                key={tag}
                href={`/tag/${tag}`}
                className="text-xs bg-secondary text-secondary-foreground px-2 py-1 rounded-full"
              >
                {tag}
              </Link>
            ))}
          </div>
        )}
      </header>

      <div className="prose dark:prose-invert max-w-none mb-12">
        <p>{post.content_md || post.excerpt}</p>
      </div>

      <div className="border-t pt-8">
        <Suspense fallback={<Skeleton className="h-40 w-full" />}>
          <CommentSection postId={post.id} />
        </Suspense>
      </div>
    </article>
  );
}
