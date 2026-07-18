import { describe, expect, it } from "vitest";
import {
  applyThemeToDocument,
  DEFAULT_THEME,
  isHexColor,
  mergeTheme,
} from "./theme";

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

  it("applyThemeToDocument writes light/dark prefixes not active --xilo-*", () => {
    // jsdom may be absent; skip DOM assertion in that case.
    if (typeof document === "undefined") return;
    document.documentElement.style.cssText = "";
    applyThemeToDocument(DEFAULT_THEME);
    expect(document.documentElement.style.getPropertyValue("--xilo-light-background")).toBe(
      DEFAULT_THEME.light.background
    );
    expect(document.documentElement.style.getPropertyValue("--xilo-dark-background")).toBe(
      DEFAULT_THEME.dark.background
    );
    // Must not pin the active token — that blocks .dark remapping.
    expect(document.documentElement.style.getPropertyValue("--xilo-background")).toBe("");
  });
});
