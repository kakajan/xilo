import { PostBody } from "@/components/post/post-body";
import { PostLinkCard } from "@/components/post/post-link-card";
import { PostMediaCarousel } from "@/components/post/post-media-carousel";
import { HashtagText } from "@/components/post/hashtag-text";
import {
  postDisplayText,
  postMediaUrls,
  postPrimaryVideoUrl,
  resolvePostType,
} from "@/lib/post-type";
import type { Post } from "@/types/post";

interface PostTypeContentProps {
  post: Post;
}

export function PostTypeContent({ post }: PostTypeContentProps) {
  const postType = resolvePostType(post);
  const displayText = postDisplayText(post);

  if (postType === "article") {
    return (
      <>
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
        <PostBody content={post.content} content_md={post.content_md} excerpt={post.excerpt} />
      </>
    );
  }

  if (postType === "micro") {
    return (
      <div className="mb-8 whitespace-pre-wrap text-lg leading-relaxed">
        {displayText ? <HashtagText text={displayText} /> : null}
      </div>
    );
  }

  if (postType === "photo") {
    return (
      <div className="mb-8 space-y-4">
        <PostMediaCarousel urls={postMediaUrls(post)} imageClassName="max-h-[28rem]" />
        {displayText ? (
          <div className="prose dark:prose-invert max-w-none leading-relaxed">
            <HashtagText text={displayText} />
          </div>
        ) : null}
      </div>
    );
  }

  if (postType === "video") {
    const videoUrl = postPrimaryVideoUrl(post);
    return (
      <div className="mb-8 space-y-4">
        {videoUrl ? (
          <div className="overflow-hidden rounded-xl">
            <video src={videoUrl} controls className="max-h-[28rem] w-full bg-black" />
          </div>
        ) : null}
        {displayText ? (
          <div className="prose dark:prose-invert max-w-none leading-relaxed">
            <HashtagText text={displayText} />
          </div>
        ) : null}
      </div>
    );
  }

  if (postType === "link" && post.link_url) {
    return (
      <div className="mb-8 space-y-4">
        <PostLinkCard url={post.link_url} />
        {displayText ? (
          <div className="prose dark:prose-invert max-w-none leading-relaxed">
            <HashtagText text={displayText} />
          </div>
        ) : null}
      </div>
    );
  }

  return (
    <PostBody content={post.content} content_md={post.content_md} excerpt={post.excerpt} />
  );
}
