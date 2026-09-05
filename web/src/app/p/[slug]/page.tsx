import { notFound, redirect } from "next/navigation";
import { fetchPublishedPost } from "@/lib/posts-server";
import { postPath } from "@/lib/share-urls";

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
