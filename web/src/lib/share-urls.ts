const DEFAULT_ORIGIN = "https://aile.ir";

export function publicSiteOrigin(): string {
  const fromEnv = process.env.NEXT_PUBLIC_URL?.trim().replace(/\/$/, "");
  if (fromEnv) return fromEnv;
  if (typeof window !== "undefined" && window.location?.origin) {
    return window.location.origin.replace(/\/$/, "");
  }
  return DEFAULT_ORIGIN;
}

function encSegment(value: string): string {
  return encodeURIComponent(value.trim());
}

export function postPath(username: string, slug: string): string {
  const s = slug.trim();
  if (!s) return "/";
  const u = username.trim();
  if (!u) return `/p/${encSegment(s)}`;
  return `/${encSegment(u)}/${encSegment(s)}`;
}

export function postShareUrl(username: string, slug: string): string {
  return `${publicSiteOrigin()}${postPath(username, slug)}`;
}

export function commentShareUrl(
  username: string,
  slug: string,
  commentId: string,
): string {
  const id = commentId.trim();
  return `${postShareUrl(username, slug)}?reply=${encSegment(id)}`;
}

export function profileShareUrl(username: string): string {
  const u = username.trim();
  if (!u) return publicSiteOrigin();
  return `${publicSiteOrigin()}/${encSegment(u)}`;
}

export async function shareOrCopy(
  url: string,
  title?: string,
): Promise<"shared" | "copied" | "failed"> {
  try {
    if (typeof navigator !== "undefined" && typeof navigator.share === "function") {
      await navigator.share({ url, title: title?.trim() || undefined });
      return "shared";
    }
  } catch (error) {
    if (error instanceof DOMException && error.name === "AbortError") {
      return "failed";
    }
  }
  try {
    if (typeof navigator !== "undefined" && navigator.clipboard?.writeText) {
      await navigator.clipboard.writeText(url);
      return "copied";
    }
  } catch {
    return "failed";
  }
  return "failed";
}
