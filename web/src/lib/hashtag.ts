/** Instagram/X-style hashtag helpers (mirrors backend/pkg/hashtag). */

export const MAX_TAGS = 10;
export const MAX_TAG_LEN = 30;

const TAG_BODY = /^[\p{L}\p{N}_-]{1,30}$/u;
const URL_SPAN = /https?:\/\/[^\s<>"']+|www\.[^\s<>"']+/gi;

export function normalizeTag(raw: string): string {
  let s = raw.trim().replace(/^#/, "");
  try {
    s = s.normalize("NFC");
  } catch {
    /* ignore */
  }
  if (!s || [...s].length > MAX_TAG_LEN) return "";
  if (!TAG_BODY.test(s)) return "";
  if (/^\d+$/.test(s)) return "";
  return foldLatin(s);
}

function foldLatin(s: string): string {
  return [...s]
    .map((ch) => (/[A-Za-z]/.test(ch) ? ch.toLowerCase() : ch))
    .join("");
}

function dedupeKey(tag: string): string {
  return tag.toLowerCase();
}

function isBoundary(ch: string | undefined): boolean {
  if (ch === undefined || ch === "") return true;
  if (/\s/u.test(ch)) return true;
  return /[\p{P}\p{S}]/u.test(ch);
}

function isTagChar(ch: string): boolean {
  return /[\p{L}\p{N}_-]/u.test(ch);
}

function maskURLs(text: string): string {
  return text.replace(URL_SPAN, (u) => " ".repeat([...u].length));
}

/** Unique normalized tags in first-seen order (capped). */
export function extractHashtags(text: string): string[] {
  if (!text) return [];
  const masked = maskURLs(text);
  const chars = [...masked];
  const out: string[] = [];
  const seen = new Set<string>();

  for (let i = 0; i < chars.length; i++) {
    if (chars[i] !== "#") continue;
    if (i > 0 && !isBoundary(chars[i - 1])) continue;
    let j = i + 1;
    while (j < chars.length && isTagChar(chars[j]!)) j++;
    if (j === i + 1) continue;
    const norm = normalizeTag(chars.slice(i + 1, j).join(""));
    if (!norm) continue;
    const key = dedupeKey(norm);
    if (seen.has(key)) continue;
    seen.add(key);
    out.push(norm);
    if (out.length >= MAX_TAGS) break;
    i = j - 1;
  }
  return out;
}

export function mergeTags(extracted: string[], explicit: string[]): string[] {
  const out: string[] = [];
  const seen = new Set<string>();
  const add = (raw: string) => {
    const n = normalizeTag(raw);
    if (!n) return;
    const key = dedupeKey(n);
    if (seen.has(key)) return;
    seen.add(key);
    out.push(n);
  };
  for (const t of extracted) {
    if (out.length >= MAX_TAGS) return out;
    add(t);
  }
  for (const t of explicit) {
    if (out.length >= MAX_TAGS) return out;
    add(t);
  }
  return out;
}

export interface HashtagMatch {
  start: number;
  end: number;
  tag: string;
}

/** Character offsets (UTF-16) of hashtags in text for editor decorations. */
export function findHashtagMatches(text: string): HashtagMatch[] {
  if (!text) return [];
  const masked = maskURLs(text);
  const matches: HashtagMatch[] = [];
  for (let i = 0; i < masked.length; i++) {
    if (masked[i] !== "#") continue;
    if (i > 0 && !isBoundary(masked[i - 1])) continue;
    let j = i + 1;
    while (j < masked.length && isTagChar(masked[j]!)) j++;
    if (j === i + 1) continue;
    const body = masked.slice(i + 1, j);
    // Skip masked URL regions (spaces only from maskURLs).
    if (body.length > 0 && /^ +$/.test(body)) continue;
    const norm = normalizeTag(body);
    if (!norm) continue;
    matches.push({ start: i, end: j, tag: norm });
    i = j - 1;
  }
  return matches;
}

/** Active `#query` at cursor for autocomplete. */
export function activeHashtagQuery(
  text: string,
  cursor: number
): { query: string; from: number; to: number } | null {
  if (cursor < 0 || cursor > text.length) return null;
  let i = cursor - 1;
  while (i >= 0 && isTagChar(text[i]!)) i--;
  if (i < 0 || text[i] !== "#") return null;
  if (i > 0 && !isBoundary(text[i - 1])) return null;
  const from = i;
  const to = cursor;
  const query = text.slice(from + 1, to);
  if (query.length > MAX_TAG_LEN) return null;
  return { query, from, to };
}

function escapeHtml(s: string): string {
  return s
    .replace(/&/g, "&amp;")
    .replace(/</g, "&lt;")
    .replace(/>/g, "&gt;")
    .replace(/"/g, "&quot;");
}

/**
 * Linkify hashtags in already-escaped HTML text nodes only (not inside tags/attrs).
 * Operates on plain escaped segments; safe for post-render HTML.
 */
export function linkifyHashtagsInHtml(html: string): string {
  if (!html) return html;
  const parts = html.split(/(<[^>]+>)/g);
  return parts
    .map((part) => {
      if (!part || part.startsWith("<")) return part;
      return linkifyHashtagsInPlainEscaped(part);
    })
    .join("");
}

function linkifyHashtagsInPlainEscaped(text: string): string {
  // Work on decoded-ish escaped text: # is never escaped.
  return text.replace(
    /(^|[\s\p{P}\p{S}])#([\p{L}\p{N}_-]{1,30})/gu,
    (full, prefix: string, body: string) => {
      const norm = normalizeTag(body);
      if (!norm) return full;
      const href = `/tag/${encodeURIComponent(norm)}`;
      return `${prefix}<a href="${href}" class="hashtag-link text-primary font-medium hover:underline" data-tag="${escapeHtml(norm)}">#${escapeHtml(body)}</a>`;
    }
  );
}

/** React-friendly segments for plain text (excerpt). */
export type TextSegment =
  | { type: "text"; value: string }
  | { type: "hashtag"; value: string; tag: string };

export function segmentHashtags(text: string): TextSegment[] {
  if (!text) return [];
  const matches = findHashtagMatches(text);
  if (!matches.length) return [{ type: "text", value: text }];
  const segments: TextSegment[] = [];
  let cursor = 0;
  for (const m of matches) {
    if (m.start > cursor) {
      segments.push({ type: "text", value: text.slice(cursor, m.start) });
    }
    segments.push({
      type: "hashtag",
      value: text.slice(m.start, m.end),
      tag: m.tag,
    });
    cursor = m.end;
  }
  if (cursor < text.length) {
    segments.push({ type: "text", value: text.slice(cursor) });
  }
  return segments;
}
