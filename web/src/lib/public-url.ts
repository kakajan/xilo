/** Edge-safe public URL helpers for canonicals, sitemap, robots, and middleware. */

const DEFAULT_ORIGIN = "https://aile.ir";
const API_HOSTS = new Set(["brain.aile.ir"]);

/** Matches backend `ValidateUsername`: 3–32 latin letters, digits, underscore. */
export const PUBLIC_USERNAME_RE = /^[a-zA-Z0-9_]{3,32}$/;

/** Matches backend `ValidateSlug`. */
export const PUBLIC_SLUG_RE = /^[a-z0-9]+(?:-[a-z0-9]+)*$/;

export const RESERVED_ROOT_SEGMENTS = new Set([
  "api",
  "dashboard",
  "write",
  "login",
  "register",
  "settings",
  "chat",
  "contacts",
  "notifications",
  "bookmarks",
  "saved",
  "quote",
  "search",
  "discover",
  "tag",
  "p",
  "robots.txt",
  "sitemap.xml",
  "opensearch.xml",
  "favicon.ico",
  "brand",
]);

const NOINDEX_PREFIXES = [
  "/api",
  "/dashboard",
  "/write",
  "/login",
  "/register",
  "/settings",
  "/chat",
  "/contacts",
  "/notifications",
  "/bookmarks",
  "/saved",
  "/quote",
  "/search",
] as const;

export function isValidPublicUsername(username: string | undefined | null): username is string {
  const u = username?.trim() ?? "";
  return PUBLIC_USERNAME_RE.test(u);
}

export function isValidPublicSlug(slug: string | undefined | null): boolean {
  const s = slug?.trim() ?? "";
  return s.length > 0 && s.length <= 250 && PUBLIC_SLUG_RE.test(s);
}

export function decodePathSegment(value: string): string {
  const raw = value.trim();
  if (!raw) return "";
  try {
    return decodeURIComponent(raw).trim();
  } catch {
    return raw;
  }
}

export function isNoIndexPath(pathname: string): boolean {
  const path = normalizePathname(pathname);
  if (path === "/") return false;
  return NOINDEX_PREFIXES.some((prefix) => path === prefix || path.startsWith(`${prefix}/`));
}

/** Paths like `/$` that Google indexed as fake usernames. */
export function isCrawlerJunkPath(pathname: string): boolean {
  const path = normalizePathname(pathname);
  if (path === "/") return false;
  const first = decodePathSegment(path.split("/").filter(Boolean)[0] ?? "");
  if (!first) return false;
  if (RESERVED_ROOT_SEGMENTS.has(first.toLowerCase())) return false;
  if (isNoIndexPath(path)) return false;
  return !isValidPublicUsername(first);
}

export function normalizePathname(pathname: string): string {
  const raw = pathname.split("?")[0].split("#")[0].trim();
  if (!raw || raw === "/") return "/";
  const withSlash = raw.startsWith("/") ? raw : `/${raw}`;
  return withSlash.replace(/\/+$/, "") || "/";
}

/**
 * Public site origin for crawlers and share links.
 * Never points at the API host (brain.aile.ir). Always HTTPS for aile.ir.
 */
export function canonicalSiteOrigin(
  raw = process.env.NEXT_PUBLIC_URL || DEFAULT_ORIGIN,
): string {
  const trimmed = raw.trim().replace(/\/$/, "");
  const candidate = trimmed || DEFAULT_ORIGIN;
  try {
    const url = new URL(candidate.includes("://") ? candidate : `https://${candidate}`);
    let host = url.hostname.toLowerCase();
    if (API_HOSTS.has(host) || host.endsWith(".brain.aile.ir")) {
      return DEFAULT_ORIGIN;
    }
    host = host.replace(/^www\./, "");
    const isLocal =
      host === "localhost" || host === "127.0.0.1" || host.endsWith(".local");
    const protocol = isLocal ? url.protocol || "http:" : "https:";
    const port = isLocal && url.port ? `:${url.port}` : "";
    return `${protocol}//${host}${port}`;
  } catch {
    return DEFAULT_ORIGIN;
  }
}

export function serverApiBase(): string {
  const raw = (
    process.env.INTERNAL_API_URL ||
    process.env.NEXT_PUBLIC_API_URL ||
    "http://localhost:8000"
  ).trim();
  return raw.replace(/\/$/, "") || "http://localhost:8000";
}

export function sitemapLastModified(...values: Array<string | number | Date | null | undefined>): Date {
  for (const value of values) {
    if (value == null || value === "") continue;
    const date = value instanceof Date ? value : new Date(value);
    if (!Number.isNaN(date.getTime())) return date;
  }
  return new Date();
}

export type SitemapUrlInput = {
  url: string;
  lastModified?: Date;
  changeFrequency?: "always" | "hourly" | "daily" | "weekly" | "monthly" | "yearly" | "never";
  priority?: number;
};

export function uniqueSitemapUrls(entries: Array<SitemapUrlInput | null | undefined>): SitemapUrlInput[] {
  const seen = new Set<string>();
  const out: SitemapUrlInput[] = [];
  for (const entry of entries) {
    if (!entry?.url) continue;
    let parsed: URL;
    try {
      parsed = new URL(entry.url);
    } catch {
      continue;
    }
    if (parsed.protocol !== "https:" && parsed.protocol !== "http:") continue;
    const host = parsed.hostname.toLowerCase().replace(/^www\./, "");
    if (API_HOSTS.has(host)) continue;
    parsed.hostname = host;
    if (host === "aile.ir" || host.endsWith(".aile.ir")) {
      parsed.protocol = "https:";
    }
    parsed.hash = "";
    parsed.pathname = parsed.pathname.replace(/\/+$/, "") || "/";
    const junkSegment = parsed.pathname.split("/").some((seg) => {
      if (!seg) return false;
      try {
        const decoded = decodeURIComponent(seg);
        return decoded === "$" || decoded === "*";
      } catch {
        return true;
      }
    });
    if (junkSegment) continue;
    const key = parsed.toString();
    if (seen.has(key)) continue;
    seen.add(key);
    out.push({ ...entry, url: key });
  }
  return out;
}
