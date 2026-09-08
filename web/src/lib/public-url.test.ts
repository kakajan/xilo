import { afterEach, describe, expect, it, vi } from "vitest";
import {
  canonicalSiteOrigin,
  isCrawlerJunkPath,
  isNoIndexPath,
  isValidPublicSlug,
  isValidPublicUsername,
  uniqueSitemapUrls,
} from "./public-url";
import { buildPublicSitemap, jsonLdString, sitemapPostEntry } from "./seo";
import { postPath, profileShareUrl } from "./share-urls";

describe("public-url", () => {
  afterEach(() => {
    vi.unstubAllEnvs();
  });

  it("rejects junk usernames that must not be indexed", () => {
    expect(isValidPublicUsername("$")).toBe(false);
    expect(isValidPublicUsername("")).toBe(false);
    expect(isValidPublicUsername("ab")).toBe(false);
    expect(isValidPublicUsername("unknown")).toBe(true);
    expect(isValidPublicUsername("MathIron")).toBe(true);
  });

  it("rejects invalid slugs", () => {
    expect(isValidPublicSlug("$")).toBe(false);
    expect(isValidPublicSlug("")).toBe(false);
    expect(isValidPublicSlug("hello-world")).toBe(true);
  });

  it("never uses the API host as the public origin", () => {
    vi.stubEnv("NEXT_PUBLIC_URL", "https://brain.aile.ir/");
    expect(canonicalSiteOrigin()).toBe("https://aile.ir");
  });

  it("normalizes www and http to the HTTPS apex host", () => {
    expect(canonicalSiteOrigin("http://www.aile.ir/")).toBe("https://aile.ir");
    expect(canonicalSiteOrigin("https://www.aile.ir")).toBe("https://aile.ir");
  });

  it("marks junk first segments like /$ as crawler junk", () => {
    expect(isCrawlerJunkPath("/$")).toBe(true);
    expect(isCrawlerJunkPath("/%24")).toBe(true);
    expect(isCrawlerJunkPath("/itsmarybecoming")).toBe(false);
    expect(isCrawlerJunkPath("/login")).toBe(false);
    expect(isCrawlerJunkPath("/MathIron")).toBe(false);
  });

  it("marks private app routes as noindex", () => {
    expect(isNoIndexPath("/contacts")).toBe(true);
    expect(isNoIndexPath("/login")).toBe(true);
    expect(isNoIndexPath("/chat/abc")).toBe(true);
    expect(isNoIndexPath("/itsmarybecoming")).toBe(false);
    expect(isNoIndexPath("/")).toBe(false);
  });
});

describe("share-urls seo guards", () => {
  afterEach(() => {
    vi.unstubAllEnvs();
  });

  it("does not emit /$ for invalid usernames", () => {
    vi.stubEnv("NEXT_PUBLIC_URL", "https://aile.ir/");
    expect(profileShareUrl("$")).toBe("https://aile.ir");
    expect(postPath("$", "hello-world")).toBe("/p/hello-world");
  });
});

describe("sitemap builders", () => {
  afterEach(() => {
    vi.unstubAllEnvs();
  });

  it("drops posts without a real author/slug and never lists the API host", () => {
    vi.stubEnv("NEXT_PUBLIC_URL", "https://aile.ir");
    expect(sitemapPostEntry({ slug: "", author: { username: "usher" } })).toBeNull();
    expect(sitemapPostEntry({ slug: "hello", author: { username: "$" } })).toBeNull();
    expect(sitemapPostEntry({ slug: "hello", status: "draft", author: { username: "usher" } })).toBeNull();

    const urls = buildPublicSitemap(
      [
        { slug: "hello-world", author: { username: "usher" }, status: "published", updated_at: "2026-01-01" },
        { slug: "$", author: { username: "usher" } },
        { slug: "ghost", author: { username: "" } },
      ],
      ["react", "$"],
    );
    expect(urls.some((e) => e.url === "https://aile.ir/usher/hello-world")).toBe(true);
    expect(urls.some((e) => e.url === "https://aile.ir/tag/react")).toBe(true);
    expect(urls.some((e) => e.url === "https://aile.ir/usher")).toBe(true);
    expect(urls.some((e) => e.url.includes("/$"))).toBe(false);
    expect(urls.some((e) => e.url.includes("brain.aile.ir"))).toBe(false);
    expect(urls.some((e) => e.url.includes("/p/"))).toBe(false);
    expect(urls.some((e) => e.url.includes("/contacts"))).toBe(false);
    expect(urls.some((e) => e.url.includes("/login"))).toBe(false);
  });

  it("dedupes and upgrades http sitemap URLs", () => {
    const urls = uniqueSitemapUrls([
      { url: "http://aile.ir/" },
      { url: "https://www.aile.ir/" },
      { url: "https://brain.aile.ir/" },
      { url: "https://aile.ir/$" },
    ]);
    expect(urls.map((e) => e.url)).toEqual(["https://aile.ir/"]);
  });

  it("omits empty JSON-LD fields", () => {
    expect(jsonLdString({ name: "aile", image: "", bio: null })).toBe('{"name":"aile"}');
  });
});
