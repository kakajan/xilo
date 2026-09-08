import { Suspense } from "react";
import type { Metadata } from "next";
import { PostFeed } from "@/components/post/post-feed";
import { Skeleton } from "@/components/ui/skeleton";
import { canonicalSiteOrigin } from "@/lib/public-url";
import { getBlogJsonLd, jsonLdString, siteNameFa } from "@/lib/seo";
import { fetchPublishedPosts } from "@/lib/posts-server";

export const revalidate = 60;

const homeUrl = `${canonicalSiteOrigin()}/`;

export const metadata: Metadata = {
  title: { absolute: `${siteNameFa()} | ${process.env.NEXT_PUBLIC_SITE_NAME_EN || "aile"}` },
  description: "تازه‌ترین نوشته‌های جامعه آیله",
  alternates: { canonical: homeUrl },
  openGraph: {
    url: homeUrl,
    type: "website",
    locale: "fa_IR",
    title: siteNameFa(),
    description: "تازه‌ترین نوشته‌های جامعه آیله",
  },
  twitter: {
    card: "summary_large_image",
    title: siteNameFa(),
    description: "تازه‌ترین نوشته‌های جامعه آیله",
  },
  robots: { index: true, follow: true },
};

export default async function HomePage() {
  const posts = await fetchPublishedPosts(10);
  return (
    <div>
      <script
        type="application/ld+json"
        dangerouslySetInnerHTML={{ __html: jsonLdString(getBlogJsonLd(posts)) }}
      />
      <div className="mb-6">
        <h1 className="text-2xl font-bold">فید</h1>
        <p className="mt-1 text-muted-foreground">تازه‌ترین نوشته‌های جامعه</p>
      </div>
      <Suspense fallback={<FeedSkeleton />}>
        <PostFeed initialPosts={posts} />
      </Suspense>
    </div>
  );
}

function FeedSkeleton() {
  return (
    <div className="space-y-8">
      {Array.from({ length: 5 }).map((_, i) => (
        <div key={i} className="space-y-3">
          <div className="flex items-center gap-3">
            <Skeleton className="h-10 w-10 rounded-full" />
            <div>
              <Skeleton className="h-4 w-24" />
              <Skeleton className="mt-1 h-3 w-16" />
            </div>
          </div>
          <Skeleton className="h-6 w-3/4" />
          <Skeleton className="h-4 w-full" />
        </div>
      ))}
    </div>
  );
}
