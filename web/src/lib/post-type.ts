import type { CreatePostRequest, Post, PostMedia, PostType } from "@/types/post";
import { extractTextFromTipTapJSON, isEmptyTipTapJSON } from "@/lib/tiptap-content";

export const EDITOR_POST_KINDS = [
  "article",
  "micro",
  "photo",
  "video",
  "link",
  "quote",
  "audio",
] as const;

export type EditorPostKind = (typeof EDITOR_POST_KINDS)[number];

export const POST_TYPE_LABELS: Record<PostType, string> = {
  article: "مقاله",
  micro: "متن کوتاه",
  photo: "عکس",
  video: "ویدیو",
  link: "لینک",
};

export const COMPOSE_KIND_LABELS: Record<EditorPostKind, string> = {
  ...POST_TYPE_LABELS,
  quote: "نقل‌قول",
  audio: "صوت",
};

export const MICRO_MAX_CHARS = 500;

export function resolvePostType(post: Pick<Post, "post_type">): PostType {
  return post.post_type ?? "article";
}

export function apiPostType(kind: EditorPostKind): PostType {
  if (kind === "audio" || kind === "quote") return "article";
  return kind;
}

export function isArticleLike(kind: EditorPostKind): boolean {
  return kind === "article" || kind === "audio";
}

export function composeKindFromPost(post: Post): EditorPostKind {
  if (post.quoted_post_id || post.quoted_comment_id || post.quoted_post || post.quoted_comment) {
    return "quote";
  }
  const type = resolvePostType(post);
  if (type === "article" && post.audio_url) return "audio";
  return type;
}

export function linkHostname(url: string): string {
  try {
    return new URL(url).hostname.replace(/^www\./, "");
  } catch {
    return url;
  }
}

export function isValidHttpsUrl(raw: string): boolean {
  try {
    const parsed = new URL(raw.trim());
    return parsed.protocol === "https:" && parsed.hostname.length > 0;
  } catch {
    return false;
  }
}

export function postMediaUrls(post: Post): string[] {
  if (post.media?.length) {
    return post.media.map((m) => m.url).filter(Boolean);
  }
  if (post.cover_image_url) {
    return [post.cover_image_url];
  }
  return [];
}

export function postPrimaryVideoUrl(post: Post): string | null {
  const fromMedia = post.media?.find((m) => m.url)?.url;
  return fromMedia ?? null;
}

export function postDisplayText(post: Post): string {
  const md = post.content_md?.trim();
  if (md) return md;
  return post.excerpt?.trim() ?? "";
}

export interface BuildPostPayloadInput {
  postType: EditorPostKind;
  title: string;
  slug?: string;
  excerpt?: string;
  contentJson: string;
  coverImageUrl?: string;
  audioUrl?: string;
  category?: string;
  tags?: string[];
  status: "draft" | "published";
  isPremium: boolean;
  linkUrl?: string;
  mediaIds?: string[];
  quotedPostId?: string;
  quotedCommentId?: string;
  clearQuote?: boolean;
}

export function validatePostPayload(input: BuildPostPayloadInput): string | null {
  const { postType, title, contentJson, linkUrl, mediaIds, audioUrl, quotedPostId, quotedCommentId } =
    input;

  switch (postType) {
    case "article":
      if (!title.trim()) return "عنوان لازم است";
      if (isEmptyTipTapJSON(contentJson)) return "متن پست خالی است";
      return null;
    case "audio":
      if (!title.trim()) return "عنوان لازم است";
      if (isEmptyTipTapJSON(contentJson)) return "متن پست خالی است";
      if (!audioUrl?.trim()) return "فایل صوتی لازم است";
      return null;
    case "quote": {
      const body = extractMicroBody(contentJson);
      if (!body) return "متن نقل‌قول را بنویسید";
      if (!quotedPostId?.trim() && !quotedCommentId?.trim()) return "پست مورد نقل‌قول را انتخاب کنید";
      return null;
    }
    case "micro": {
      const body = extractMicroBody(contentJson);
      if (!body) return "متن کوتاه خالی است";
      if (body.length > MICRO_MAX_CHARS) {
        return `متن کوتاه نباید بیشتر از ${MICRO_MAX_CHARS} نویسه باشد`;
      }
      return null;
    }
    case "photo": {
      const count = mediaIds?.length ?? 0;
      if (count < 1 || count > 10) return "بین ۱ تا ۱۰ عکس انتخاب کنید";
      return null;
    }
    case "video":
      if ((mediaIds?.length ?? 0) !== 1) return "یک فایل ویدیو انتخاب کنید";
      return null;
    case "link":
      if (!linkUrl?.trim()) return "آدرس لینک لازم است";
      if (!isValidHttpsUrl(linkUrl)) return "لینک باید با https شروع شود";
      return null;
    default:
      return null;
  }
}

function extractMicroBody(contentJson: string): string {
  if (!contentJson || contentJson === "{}") return "";
  const fromTipTap = extractTextFromTipTapJSON(contentJson).trim();
  if (fromTipTap) return fromTipTap;
  return contentJson.trim();
}

export function buildCreatePostPayload(input: BuildPostPayloadInput): CreatePostRequest {
  const contentMd = extractMicroBody(input.contentJson);
  const hasEditorContent = !isEmptyTipTapJSON(input.contentJson);
  const apiType = apiPostType(input.postType);

  const base: CreatePostRequest = {
    title: input.title.trim(),
    slug: input.slug || undefined,
    excerpt: input.excerpt || undefined,
    content: hasEditorContent ? input.contentJson : "{}",
    content_md: contentMd || undefined,
    cover_image_url: input.coverImageUrl || undefined,
    audio_url: input.audioUrl ?? "",
    category: input.category || undefined,
    tags: input.tags?.length ? input.tags : undefined,
    status: input.status,
    is_premium: input.isPremium,
    post_type: apiType,
  };

  if (apiType === "link") {
    base.link_url = input.linkUrl?.trim();
  }

  if (apiType === "photo" || apiType === "video") {
    base.media_ids = input.mediaIds;
  }

  if (input.postType === "quote") {
    if (input.quotedCommentId?.trim()) {
      base.quoted_comment_id = input.quotedCommentId.trim();
    } else if (input.quotedPostId?.trim()) {
      base.quoted_post_id = input.quotedPostId.trim();
    }
    if (!base.title) {
      base.title = contentMd.slice(0, 80) || "نقل‌قول";
    }
  } else if (input.clearQuote) {
    base.quoted_post_id = "";
    base.quoted_comment_id = "";
  }

  if ((input.postType === "micro" || input.postType === "quote") && !base.title) {
    base.title = contentMd.slice(0, 80) || (input.postType === "quote" ? "نقل‌قول" : "متن کوتاه");
  }

  if (input.postType === "audio" && !base.title) {
    base.title = contentMd.slice(0, 80) || "صوت";
  }

  return base;
}

export function mediaItemFromUpload(res: { id: string; url: string; mime_type?: string }): PostMedia {
  return { id: res.id, url: res.url, mime_type: res.mime_type };
}
