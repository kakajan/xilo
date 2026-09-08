import { notFound, redirect } from "next/navigation";
import type { Metadata } from "next";
import AuthorProfilePage from "./author-profile-page";
import { getPersonJsonLd, jsonLdString, noIndexMetadata } from "@/lib/seo";
import { decodePathSegment, isValidPublicUsername } from "@/lib/public-url";
import { profilePath, profileShareUrl } from "@/lib/share-urls";
import { fetchPublicProfile, fetchUserPublishedPosts } from "@/lib/users-server";

export const revalidate = 60;

export async function generateMetadata({
  params,
}: {
  params: Promise<{ username: string }>;
}): Promise<Metadata> {
  const username = decodePathSegment((await params).username);
  if (!isValidPublicUsername(username)) {
    return { title: "کاربر یافت نشد", ...noIndexMetadata };
  }
  const profile = await fetchPublicProfile(username);
  if (!profile) {
    return { title: "کاربر یافت نشد", ...noIndexMetadata };
  }
  const url = profileShareUrl(profile.username);
  const title = profile.display_name || profile.username;
  const description =
    profile.bio?.trim() || `پروفایل @${profile.username} در ${process.env.NEXT_PUBLIC_SITE_NAME_FA || "آیله"}`;
  return {
    title,
    description,
    alternates: { canonical: url },
    robots: { index: true, follow: true },
    openGraph: {
      title,
      description,
      url,
      type: "profile",
      locale: "fa_IR",
      images: profile.avatar_url ? [profile.avatar_url] : undefined,
    },
    twitter: {
      card: profile.avatar_url ? "summary_large_image" : "summary",
      title,
      description,
    },
  };
}

export default async function AuthorProfileRoute({
  params,
}: {
  params: Promise<{ username: string }>;
}) {
  const username = decodePathSegment((await params).username);
  if (!isValidPublicUsername(username)) notFound();
  const profile = await fetchPublicProfile(username);
  if (!profile) notFound();
  if (profile.username !== username) {
    redirect(profilePath(profile.username));
  }
  const initialPosts = await fetchUserPublishedPosts(profile.username);
  const jsonLd = getPersonJsonLd(profile);
  return (
    <>
      <script
        type="application/ld+json"
        dangerouslySetInnerHTML={{ __html: jsonLdString(jsonLd) }}
      />
      <AuthorProfilePage
        key={profile.username}
        initialProfile={profile}
        initialPosts={initialPosts}
      />
    </>
  );
}
