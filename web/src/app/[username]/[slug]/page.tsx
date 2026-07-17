import { Suspense } from "react";
import { notFound } from "next/navigation";
import Link from "next/link";
import type { Post } from "@/types/post";
import { formatDate, readingTimeText, getInitials } from "@/lib/utils";
import { Avatar, AvatarFallback, AvatarImage } from "@/components/ui/avatar";
import { Skeleton } from "@/components/ui/skeleton";
import { CommentSection } from "@/components/comment/comment-section";
import { StickyReactionBar } from "@/components/post/sticky-reaction-bar";

async function getPost(slug: string): Promise<Post | null> {
  try {
    const base = process.env.NEXT_PUBLIC_API_URL || "http://localhost:8000";
    const res = await fetch(`${base}/api/posts/${slug}`, {
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
  searchParams,
}: {
  params: Promise<{ username: string; slug: string }>;
  searchParams: Promise<{ reply?: string }>;
}) {
  const { username, slug } = await params;
  const { reply } = await searchParams;
  const post = await getPost(slug);
  if (!post) notFound();

  const authorName = post.author?.display_name || post.author?.username || "ناشناس";

  return (
    <article className="mx-auto max-w-3xl">
      <header className="mb-8">
        <h1 className="mb-4 text-3xl font-bold md:text-4xl">{post.title}</h1>

        <div className="mb-4 flex items-center gap-3">
          <Link href={`/${post.author?.username || username}`}>
            <Avatar className="h-10 w-10">
              {post.author?.avatar_url ? (
                <AvatarImage src={post.author.avatar_url} alt="" />
              ) : null}
              <AvatarFallback>{getInitials(authorName)}</AvatarFallback>
            </Avatar>
          </Link>
          <div className="min-w-0">
            <Link
              href={`/${post.author?.username || username}`}
              className="font-medium hover:underline"
            >
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
          <div className="flex flex-wrap gap-2">
            {post.tags.map((tag) => (
              <Link
                key={tag}
                href={`/tag/${tag}`}
                className="rounded-full bg-secondary px-2 py-1 text-xs text-secondary-foreground"
              >
                {tag}
              </Link>
            ))}
          </div>
        )}
      </header>

      <div className="prose dark:prose-invert mb-8 max-w-none">
        <p className="whitespace-pre-wrap leading-relaxed">
          {post.content_md || post.content || post.excerpt}
        </p>
      </div>

      <StickyReactionBar postId={post.id} reactions={post.reactions} />

      <div className="border-t pt-8">
        <Suspense fallback={<Skeleton className="h-40 w-full" />}>
          <CommentSection postId={post.id} initialReplyTo={reply} />
        </Suspense>
      </div>
    </article>
  );
}
