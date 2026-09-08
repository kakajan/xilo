import type { MetadataRoute } from "next";
import { canonicalSiteOrigin } from "@/lib/public-url";

export default function robots(): MetadataRoute.Robots {
  const origin = canonicalSiteOrigin();
  return {
    rules: [
      {
        userAgent: "*",
        allow: "/",
        disallow: [
          "/api/",
          "/dashboard/",
          "/write/",
          "/settings",
          "/chat",
          "/notifications",
          "/bookmarks",
          "/saved",
          "/quote",
        ],
      },
    ],
    sitemap: `${origin}/sitemap.xml`,
    host: origin.replace(/^https?:\/\//, ""),
  };
}
