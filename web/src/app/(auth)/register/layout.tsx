import type { Metadata } from "next";
import { noIndexCanonical } from "@/lib/seo";

export const metadata: Metadata = noIndexCanonical("/register", "ثبت‌نام");

export default function RegisterLayout({ children }: { children: React.ReactNode }) {
  return children;
}
