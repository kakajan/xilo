import { afterEach, describe, expect, it, vi } from "vitest";
import {
  commentShareUrl,
  postPath,
  postShareUrl,
  profileShareUrl,
  publicSiteOrigin,
} from "./share-urls";

describe("share-urls", () => {
  afterEach(() => {
    vi.unstubAllEnvs();
  });

  it("builds canonical post paths", () => {
    expect(postPath("usher", "hello-world")).toBe("/usher/hello-world");
    expect(postPath("  ", "hello-world")).toBe("/p/hello-world");
  });

  it("uses NEXT_PUBLIC_URL for absolute links", () => {
    vi.stubEnv("NEXT_PUBLIC_URL", "https://aile.ir/");
    expect(publicSiteOrigin()).toBe("https://aile.ir");
    expect(profileShareUrl("$")).toBe("https://aile.ir");
    expect(postShareUrl("usher", "hello-world")).toBe(
      "https://aile.ir/usher/hello-world",
    );
    expect(commentShareUrl("usher", "hello-world", "c1")).toBe(
      "https://aile.ir/usher/hello-world?reply=c1",
    );
    expect(profileShareUrl("usher")).toBe("https://aile.ir/usher");
  });
});
