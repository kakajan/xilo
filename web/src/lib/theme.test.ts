import { describe, expect, it } from "vitest";
import { isHexColor, mergeTheme } from "./theme";

describe("theme helpers", () => {
  it("isHexColor tolerates undefined", () => {
    expect(isHexColor(undefined)).toBe(false);
    expect(isHexColor("#1D9BF0")).toBe(true);
  });

  it("mergeTheme fills missing chat bubble keys", () => {
    const merged = mergeTheme({
      light: { primary: "#112233" } as never,
      dark: {},
    });
    expect(merged.light.primary).toBe("#112233");
    expect(merged.light.chat_bubble_own).toMatch(/^#/);
    expect(merged.dark.chat_bubble_others).toMatch(/^#/);
  });
});
