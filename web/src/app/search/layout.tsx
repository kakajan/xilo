import type { Metadata } from "next";
import { noIndexCanonical } from "@/lib/seo";

export const metadata: Metadata = noIndexCanonical("/search", "جستجو");

export default function SearchLayout({ children }: { children: React.ReactNode }) {
  return children;
}
