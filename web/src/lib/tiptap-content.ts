/**
 * Helpers for TipTap JSON stored in posts.content and plain text in content_md.
 */

type TipTapNode = {
  type?: string;
  text?: string;
  marks?: Array<{ type: string; attrs?: Record<string, unknown> }>;
  attrs?: Record<string, unknown>;
  content?: TipTapNode[];
};

export function isEmptyTipTapJSON(raw: string | null | undefined): boolean {
  if (!raw?.trim()) return true;
  const trimmed = raw.trim();
  if (trimmed === "{}" || trimmed === "null") return true;
  try {
    const doc = JSON.parse(trimmed) as TipTapNode;
    if (!doc || typeof doc !== "object") return true;
    if (doc.type === "doc") {
      return !doc.content?.some((n) => nodeHasVisibleText(n));
    }
    return !nodeHasVisibleText(doc);
  } catch {
    return false;
  }
}

function nodeHasVisibleText(node: TipTapNode): boolean {
  if (node.text?.trim()) return true;
  if (node.type === "image" && node.attrs?.src) return true;
  return Boolean(node.content?.some(nodeHasVisibleText));
}

/** Plain text from TipTap JSON (same idea as write page extractText). */
export function extractTextFromTipTapJSON(json: string): string {
  try {
    const obj = JSON.parse(json) as TipTapNode;
    const texts: string[] = [];
    const walk = (node: TipTapNode) => {
      if (node.text) texts.push(node.text);
      node.content?.forEach(walk);
    };
    walk(obj);
    return texts.join(" ").trim();
  } catch {
    return "";
  }
}

function escapeHtml(s: string): string {
  return s
    .replace(/&/g, "&amp;")
    .replace(/</g, "&lt;")
    .replace(/>/g, "&gt;")
    .replace(/"/g, "&quot;");
}

function renderMarks(text: string, marks?: TipTapNode["marks"]): string {
  let html = escapeHtml(text);
  if (!marks?.length) return html;
  for (const mark of marks) {
    switch (mark.type) {
      case "bold":
        html = `<strong>${html}</strong>`;
        break;
      case "italic":
        html = `<em>${html}</em>`;
        break;
      case "underline":
        html = `<u>${html}</u>`;
        break;
      case "code":
        html = `<code>${html}</code>`;
        break;
      case "link": {
        const href = escapeHtml(String(mark.attrs?.href ?? "#"));
        html = `<a href="${href}" rel="noopener noreferrer" target="_blank">${html}</a>`;
        break;
      }
      default:
        break;
    }
  }
  return html;
}

function renderNode(node: TipTapNode): string {
  const children = node.content?.map(renderNode).join("") ?? "";

  switch (node.type) {
    case "doc":
      return children;
    case "paragraph":
      return `<p>${children || "<br>"}</p>`;
    case "heading": {
      const level = Math.min(3, Math.max(1, Number(node.attrs?.level) || 1));
      return `<h${level}>${children}</h${level}>`;
    }
    case "bulletList":
      return `<ul>${children}</ul>`;
    case "orderedList":
      return `<ol>${children}</ol>`;
    case "listItem":
      return `<li>${children}</li>`;
    case "blockquote":
      return `<blockquote>${children}</blockquote>`;
    case "codeBlock":
      return `<pre><code>${children}</code></pre>`;
    case "horizontalRule":
      return "<hr>";
    case "hardBreak":
      return "<br>";
    case "image": {
      const src = escapeHtml(String(node.attrs?.src ?? ""));
      const alt = escapeHtml(String(node.attrs?.alt ?? ""));
      if (!src) return "";
      return `<img src="${src}" alt="${alt}" loading="lazy" />`;
    }
    case "text":
      return renderMarks(node.text ?? "", node.marks);
    default:
      return children;
  }
}

/** Convert TipTap JSON string to safe HTML for display. */
export function tipTapJSONToHTML(raw: string): string {
  try {
    const doc = JSON.parse(raw) as TipTapNode;
    if (!doc || typeof doc !== "object") return "";
    return renderNode(doc);
  } catch {
    return "";
  }
}

/**
 * Pick the best display body for a post.
 * Prefers TipTap HTML when JSON has real content; falls back to content_md / excerpt.
 */
export function resolvePostBodyHTML(post: {
  content?: string;
  content_md?: string;
  excerpt?: string;
}): { html: string; plain: string } {
  const content = post.content?.trim() ?? "";
  if (content && !isEmptyTipTapJSON(content)) {
    const html = tipTapJSONToHTML(content);
    if (html.trim()) {
      return { html, plain: extractTextFromTipTapJSON(content) };
    }
  }

  const plain = (post.content_md || post.excerpt || "").trim();
  if (!plain || plain === "{}") {
    return { html: "", plain: "" };
  }
  return { html: `<p>${escapeHtml(plain).replace(/\n/g, "<br>")}</p>`, plain };
}
