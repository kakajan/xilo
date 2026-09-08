import { notFound, redirect } from "next/navigation";
import type { Metadata } from "next";
import { fetchPublishedPost } from "@/lib/posts-server";
import { noIndexMetadata } from "@/lib/seo";
import { postPath } from "@/lib/share-urls";

export const metadata: Metadata = noIndexMetadata;

export default async function ShortPostRedirectPage({
  params,
}: {
  params: Promise<{ slug: string }>;
}) {
  const { slug } = await params;
  const post = await fetchPublishedPost(slug);
  if (!post) notFound();
  redirect(postPath(post.author?.username || "", post.slug));
}
