import type { Metadata } from "next";
import { noIndexCanonical } from "@/lib/seo";

export const metadata: Metadata = noIndexCanonical("/contacts", "مخاطبین");

export default function ContactsLayout({ children }: { children: React.ReactNode }) {
  return children;
}
