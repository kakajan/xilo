import type { Metadata } from "next";
import { noIndexCanonical } from "@/lib/seo";

export const metadata: Metadata = noIndexCanonical("/login", "ورود");

export default function LoginLayout({ children }: { children: React.ReactNode }) {
  return children;
}
