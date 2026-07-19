import { describe, expect, it } from "vitest";
import { locales } from "./config";
import { messagesByLocale } from "./messages";

function flattenKeys(obj: unknown, prefix = ""): string[] {
  if (!obj || typeof obj !== "object") return prefix ? [prefix] : [];
  const entries = Object.entries(obj as Record<string, unknown>);
  return entries.flatMap(([key, value]) => {
    const path = prefix ? `${prefix}.${key}` : key;
    if (value && typeof value === "object" && !Array.isArray(value)) {
      return flattenKeys(value, path);
    }
    return [path];
  });
}

describe("i18n message catalogs", () => {
  it("includes every supported locale", () => {
    for (const locale of locales) {
      expect(messagesByLocale[locale]).toBeTruthy();
    }
  });

  it("keeps the same keys across all locales", () => {
    const baseKeys = flattenKeys(messagesByLocale.fa).sort();
    for (const locale of locales) {
      expect(flattenKeys(messagesByLocale[locale]).sort()).toEqual(baseKeys);
    }
  });
});
