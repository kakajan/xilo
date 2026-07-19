import { readFileSync } from "node:fs";
import { join } from "node:path";
import { describe, expect, it } from "vitest";
import {
  extractHashtags,
  mergeTags,
  linkifyHashtagsInHtml,
  normalizeTag,
} from "./hashtag";

describe("hashtag", () => {
  it("matches shared fixtures", () => {
    const raw = readFileSync(
      join(process.cwd(), "..", "testdata", "hashtags", "fixtures.json"),
      "utf8"
    );
    const doc = JSON.parse(raw) as {
      cases: Array<{ id: string; input: string; expected: string[] }>;
    };
    for (const c of doc.cases) {
      expect(extractHashtags(c.input), c.id).toEqual(c.expected);
    }
  });

  it("extracts persian and latin", () => {
    expect(extractHashtags("سلام #خبر و #Xilo_App")).toEqual(["خبر", "xilo_app"]);
  });

  it("skips url fragments", () => {
    expect(extractHashtags("see https://example.com/path#fragment and #real")).toEqual([
      "real",
    ]);
  });

  it("rejects digits-only", () => {
    expect(extractHashtags("#123 #ok1")).toEqual(["ok1"]);
  });

  it("merges with cap", () => {
    const extracted = Array.from({ length: 8 }, (_, i) => `${String.fromCharCode(97 + i)}tag`);
    const merged = mergeTags(extracted, ["x1", "x2", "x3"]);
    expect(merged).toHaveLength(10);
    expect(merged[8]).toBe("x1");
  });

  it("linkifies escaped html", () => {
    const html = linkifyHashtagsInHtml("<p>hello #news</p>");
    expect(html).toContain('href="/tag/news"');
    expect(html).toContain("#news");
  });

  it("normalizes latin case", () => {
    expect(normalizeTag("#News")).toBe("news");
  });
});
