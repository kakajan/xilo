import { Suspense } from "react";
import { notFound } from "next/navigation";
import Link from "next/link";
import type { Metadata } from "next";
import { formatDate, readingTimeText, getInitials } from "@/lib/utils";
import { Avatar, AvatarFallback, AvatarImage } from "@/components/ui/avatar";
import { Skeleton } from "@/components/ui/skeleton";
import { CommentSection } from "@/components/comment/comment-section";
import { StickyReactionBar } from "@/components/post/sticky-reaction-bar";
import { StickyAudioPlayer } from "@/components/post/sticky-audio-player";
import { PostBody } from "@/components/post/post-body";
import { QuotedPostCard } from "@/components/post/quoted-post-card";
import { QuotedCommentCard } from "@/components/post/quoted-comment-card";
import { RecordPostView } from "@/components/post/record-post-view";
import { ShareControl } from "@/components/post/share-control";
import {
  AuthorHandleMeta,
  TimeLabel,
} from "@/components/user/username-handle";
import { fetchPublishedPost } from "@/lib/posts-server";
import { postShareUrl, publicSiteOrigin } from "@/lib/share-urls";
import { getArticleJsonLd } from "@/lib/seo";

export async function generateMetadata({
  params,
}: {
  params: Promise<{ username: string; slug: string }>;
}): Promise<Metadata> {
  const { username, slug } = await params;
  const post = await fetchPublishedPost(slug);
  if (!post) return { title: "پست پیدا نشد" };
  const url = postShareUrl(post.author?.username || username, post.slug);
  const title = post.title;
  const description = post.excerpt || undefined;
  return {
    title,
    description,
    alternates: { canonical: url },
    openGraph: {
      title,
      description,
      url,
      type: "article",
      images: post.cover_image_url ? [post.cover_image_url] : undefined,
    },
    twitter: {
      card: post.cover_image_url ? "summary_large_image" : "summary",
      title,
      description,
    },
  };
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
  const post = await fetchPublishedPost(slug);
  if (!post) notFound();

  const authorName = post.author?.display_name || post.author?.username || "ناشناس";
  const shareUrl = postShareUrl(post.author?.username || username, post.slug);
  const jsonLd = getArticleJsonLd(post, publicSiteOrigin());

  return (
    <article className="mx-auto max-w-3xl">
      <script
        type="application/ld+json"
        dangerouslySetInnerHTML={{ __html: JSON.stringify(jsonLd) }}
      />
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
          <div className="min-w-0 flex-1">
            <Link
              href={`/${post.author?.username || username}`}
              className="font-medium hover:underline"
            >
              {authorName}
            </Link>
            <AuthorHandleMeta
              className="text-sm"
              username={post.author?.username || username}
              timeLabel={post.published_at ? formatDate(post.published_at) : null}
              trailing={
                <>
                  {post.reading_time ? (
                    <>
                      <span aria-hidden>·</span>
                      <TimeLabel>{readingTimeText(post.reading_time)}</TimeLabel>
                    </>
                  ) : null}
                  {post.category ? (
                    <>
                      <span aria-hidden>·</span>
                      <span>{post.category}</span>
                    </>
                  ) : null}
                  <span aria-hidden>·</span>
                  <RecordPostView
                    postId={post.id}
                    initialViewCount={post.view_count ?? 0}
                  />
                </>
              }
            />
          </div>
          <ShareControl url={shareUrl} title={post.title} />
        </div>

        {post.tags?.length > 0 && (
          <div className="flex flex-wrap gap-2">
            {post.tags.map((tag) => (
              <Link
                key={tag}
                href={`/tag/${encodeURIComponent(tag)}`}
                className="rounded-full bg-secondary px-2 py-1 text-xs text-secondary-foreground hover:bg-primary/10"
              >
                #{tag}
              </Link>
            ))}
          </div>
        )}

        {post.cover_image_url ? (
          <div className="mt-6 overflow-hidden rounded-xl">
            {/* eslint-disable-next-line @next/next/no-img-element */}
            <img
              src={post.cover_image_url}
              alt=""
              className="max-h-[28rem] w-full object-cover"
            />
          </div>
        ) : null}
      </header>

      <PostBody
        content={post.content}
        content_md={post.content_md}
        excerpt={post.excerpt}
      />

      {post.quoted_post ? (
        <div className="mt-6">
          <QuotedPostCard quote={post.quoted_post} />
        </div>
      ) : null}

      {post.quoted_comment ? (
        <div className="mt-6">
          <QuotedCommentCard quote={post.quoted_comment} />
        </div>
      ) : null}

      {post.audio_url ? (
        <StickyAudioPlayer src={post.audio_url} title={post.title} />
      ) : null}

      <StickyReactionBar
        postId={post.id}
        reactions={post.reactions}
        viewerReactions={post.viewer_reactions}
      />

      <div className="border-t pt-8">
        <Suspense fallback={<Skeleton className="h-40 w-full" />}>
          <CommentSection
            postId={post.id}
            initialReplyTo={reply}
            postAuthorUsername={post.author?.username || username}
            postSlug={post.slug}
            postAuthorId={post.author_id}
          />
        </Suspense>
      </div>
    </article>
  );
}
