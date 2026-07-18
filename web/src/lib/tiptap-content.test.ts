import { describe, expect, it } from "vitest";
import {
  extractTextFromTipTapJSON,
  isEmptyTipTapJSON,
  resolvePostBodyHTML,
  tipTapJSONToHTML,
} from "./tiptap-content";

const sampleDoc = JSON.stringify({
  type: "doc",
  content: [
    {
      type: "paragraph",
      content: [{ type: "text", text: "سلام خانواده" }],
    },
  ],
});

describe("tiptap-content", () => {
  it("treats {} as empty", () => {
    expect(isEmptyTipTapJSON("{}")).toBe(true);
    expect(isEmptyTipTapJSON("")).toBe(true);
    expect(isEmptyTipTapJSON(sampleDoc)).toBe(false);
  });

  it("extracts plain text", () => {
    expect(extractTextFromTipTapJSON(sampleDoc)).toBe("سلام خانواده");
  });

  it("renders paragraph html", () => {
    expect(tipTapJSONToHTML(sampleDoc)).toContain("<p>سلام خانواده</p>");
  });

  it("resolvePostBodyHTML ignores empty {}", () => {
    const { html, plain } = resolvePostBodyHTML({
      content: "{}",
      content_md: "",
      excerpt: "",
    });
    expect(html).toBe("");
    expect(plain).toBe("");
  });

  it("resolvePostBodyHTML prefers tip tap json", () => {
    const { html, plain } = resolvePostBodyHTML({
      content: sampleDoc,
      content_md: "fallback",
    });
    expect(plain).toBe("سلام خانواده");
    expect(html).toContain("سلام خانواده");
  });
});
