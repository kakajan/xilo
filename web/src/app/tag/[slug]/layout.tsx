import type { Metadata } from "next";
import { canonicalSiteOrigin } from "@/lib/public-url";

export async function generateMetadata({
  params,
}: {
  params: Promise<{ slug: string }>;
}): Promise<Metadata> {
  const { slug } = await params;
  const tag = decodeURIComponent(slug);
  const url = `${canonicalSiteOrigin()}/tag/${encodeURIComponent(tag)}`;
  return {
    title: `#${tag}`,
    alternates: { canonical: url },
    openGraph: { url },
    robots: { index: true, follow: true },
  };
}

export default function TagLayout({ children }: { children: React.ReactNode }) {
  return children;
}
