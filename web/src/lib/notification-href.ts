import type { Notification } from "@/types/notification";

function parseData(data: Notification["data"]): Record<string, unknown> {
  if (!data) return {};
  if (typeof data === "string") {
    try {
      return JSON.parse(data) as Record<string, unknown>;
    } catch {
      return {};
    }
  }
  return data;
}

function asString(value: unknown): string | null {
  return typeof value === "string" && value.trim() ? value.trim() : null;
}

/** Resolve in-app path for a notification (post comments, replies, chat, follows). */
export function hrefForNotification(n: Notification): string | null {
  const data = parseData(n.data);

  const link = asString(data.link);
  if (link) {
    if (link.startsWith("/") && !link.startsWith("//")) return link;
    if (typeof window !== "undefined") {
      try {
        const url = new URL(link);
        if (url.origin === window.location.origin) {
          return `${url.pathname}${url.search}${url.hash}`;
        }
      } catch {
        // ignore invalid URL
      }
    }
  }

  const slug = asString(data.post_slug) ?? asString(data.slug);
  const postAuthor =
    asString(data.post_author_username) ??
    asString(data.post_username) ??
    asString(data.username);
  const commentId = asString(data.comment_id);

  if (
    (n.type === "post_comment" || n.type === "comment_reply" || slug) &&
    slug &&
    postAuthor
  ) {
    const base = `/${postAuthor}/${slug}`;
    return commentId ? `${base}?reply=${encodeURIComponent(commentId)}` : base;
  }

  // Legacy: bare slug without author — prefer inbox over a broken `/{slug}` route.
  if (slug && !postAuthor && (n.type === "post_comment" || n.type === "comment_reply")) {
    return "/notifications";
  }

  const chatId = asString(data.chat_id);
  if (chatId) return `/chat/${chatId}`;

  const followerUsername = asString(data.follower_username) ?? asString(data.username);
  if (asString(data.follower_id) || n.type === "new_follower") {
    if (followerUsername) return `/${followerUsername}`;
  }

  return null;
}
