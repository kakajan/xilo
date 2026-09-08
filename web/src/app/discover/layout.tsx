import type { Metadata } from "next";
import { canonicalSiteOrigin } from "@/lib/public-url";

const url = `${canonicalSiteOrigin()}/discover`;

export const metadata: Metadata = {
  title: "اکتشاف",
  alternates: { canonical: url },
  openGraph: { url },
  robots: { index: true, follow: true },
};

export default function DiscoverLayout({ children }: { children: React.ReactNode }) {
  return children;
}
