import type { Metadata } from "next";
import { noIndexMetadata } from "@/lib/seo";

export const metadata: Metadata = noIndexMetadata;

export default function FollowersLayout({ children }: { children: React.ReactNode }) {
  return children;
}
