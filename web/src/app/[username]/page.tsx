"use client";

import { useMemo, useState } from "react";
import Link from "next/link";
import { useParams, useRouter } from "next/navigation";
import { useMutation, useQuery } from "@tanstack/react-query";
import { BadgeCheck, MessageCircle, Share2 } from "lucide-react";
import {
  getPublicProfile,
  listUserLikes,
  listUserPosts,
  listUserReplies,
} from "@/lib/api/users";
import { createChat } from "@/lib/api/chats";
import { useAuthStore } from "@/stores/auth-store";
import { PostCard } from "@/components/post/post-card";
import { Avatar, AvatarFallback, AvatarImage } from "@/components/ui/avatar";
import { Button } from "@/components/ui/button";
import { Skeleton } from "@/components/ui/skeleton";
import { FollowButton } from "@/components/user/follow-button";
import { UsernameHandle } from "@/components/user/username-handle";
import { cn, getInitials } from "@/lib/utils";
import type { Post } from "@/types/post";
import type { Comment } from "@/types/comment";

type Tab = "posts" | "replies" | "likes" | "archived";

export default function AuthorProfilePage() {
  const { username } = useParams<{ username: string }>();
  const router = useRouter();
  const { user, isAuthenticated } = useAuthStore();
  const isOwn = !!user?.username && user.username === username;
  const [tab, setTab] = useState<Tab>("posts");

  const tabs = useMemo(() => {
    if (isOwn) {
      return [
        ["posts", "پست‌ها"],
        ["archived", "بایگانی"],
      ] as const;
    }
    return [
      ["posts", "پست‌ها"],
      ["replies", "پاسخ‌ها"],
      ["likes", "پسندها"],
    ] as const;
  }, [isOwn]);

  // Keep active tab valid when own/other tab sets change (auth hydrate / navigation).
  const activeTab: Tab = tabs.some(([key]) => key === tab) ? tab : "posts";

  const { data: profile, isLoading } = useQuery({
    queryKey: ["profile", username],
    queryFn: () => getPublicProfile(username),
  });

  // Separate keys so switching tabs reuses cache instead of remounting a shared query.
  const postsQ = useQuery({
    queryKey: ["user-posts", username, "posts"],
    enabled: activeTab === "posts",
    queryFn: async () => (await listUserPosts(username, "posts")).data ?? [],
  });

  const archivedQ = useQuery({
    queryKey: ["user-posts", username, "archived"],
    enabled: activeTab === "archived" && isOwn,
    queryFn: async () => (await listUserPosts(username, "archived")).data ?? [],
  });

  const repliesQ = useQuery({
    queryKey: ["user-replies", username],
    enabled: activeTab === "replies" && !isOwn,
    queryFn: async () => (await listUserReplies(username)).data ?? [],
  });

  const likesQ = useQuery({
    queryKey: ["user-likes", username],
    enabled: activeTab === "likes" && !isOwn,
    queryFn: async () => (await listUserLikes(username)).data ?? [],
  });

  const messageMut = useMutation({
    mutationFn: async () => {
      if (!profile?.id) throw new Error("no user");
      return createChat([profile.id], "direct");
    },
    onSuccess: (chat) => router.push(`/chat/${chat.id}`),
  });

  if (isLoading) {
    return (
      <div className="space-y-4">
        <Skeleton className="h-36 w-full rounded-xl" />
        <Skeleton className="mx-auto h-24 w-24 rounded-full" />
      </div>
    );
  }

  if (!profile) {
    return (
      <div className="py-20 text-center text-muted-foreground">کاربر یافت نشد</div>
    );
  }

  const display = profile.display_name || profile.username;

  return (
    <div>
      <div
        className="relative mb-[-3rem] h-36 w-full overflow-hidden rounded-xl bg-gradient-to-br from-primary/30 via-secondary to-muted"
        style={
          profile.banner_url
            ? {
                backgroundImage: `url(${profile.banner_url})`,
                backgroundSize: "cover",
                backgroundPosition: "center",
              }
            : undefined
        }
      />

      <div className="relative z-10 flex flex-col items-center px-2 text-center">
        <Avatar className="mb-3 h-24 w-24 ring-4 ring-background">
          {profile.avatar_url ? <AvatarImage src={profile.avatar_url} alt="" /> : null}
          <AvatarFallback className="text-2xl">{getInitials(display)}</AvatarFallback>
        </Avatar>

        <div className="mb-1 flex items-center justify-center gap-2">
          <h1 className="min-w-0 text-2xl font-bold">{display}</h1>
          {profile.is_verified && (
            <BadgeCheck className="h-5 w-5 shrink-0 text-primary" aria-label="تأییدشده" />
          )}
        </div>
        <UsernameHandle
          username={profile.username}
          className="text-muted-foreground"
        />
        {profile.bio && <p className="mt-3 max-w-md text-[15px] leading-relaxed">{profile.bio}</p>}

        <div className="mt-4 grid w-full max-w-sm grid-cols-3 gap-2">
          <Stat label="پست‌ها" value={profile.post_count ?? postsQ.data?.length ?? 0} />
          <Link href={`/${username}/followers`} className="rounded-xl hover:bg-muted/60">
            <Stat label="دنبال‌کننده" value={profile.follower_count ?? 0} />
          </Link>
          <Link href={`/${username}/following`} className="rounded-xl hover:bg-muted/60">
            <Stat label="دنبال‌شونده" value={profile.following_count ?? 0} />
          </Link>
        </div>

        <div className="mt-4 flex flex-wrap items-center justify-center gap-2">
          {isOwn ? (
            <Button className="min-h-11" onClick={() => router.push("/settings")}>
              ویرایش پروفایل
            </Button>
          ) : (
            <>
              {isAuthenticated && (
                <FollowButton
                  username={username}
                  initialFollowing={profile.is_following}
                />
              )}
              {isAuthenticated && (
                <Button
                  variant="outline"
                  className="min-h-11"
                  disabled={messageMut.isPending}
                  onClick={() => messageMut.mutate()}
                >
                  <MessageCircle className="ms-1 h-4 w-4" />
                  پیام
                </Button>
              )}
            </>
          )}
          <Button
            variant="outline"
            className="min-h-11"
            onClick={async () => {
              const url = `${window.location.origin}/${username}`;
              try {
                await navigator.clipboard.writeText(url);
              } catch {
                /* ignore */
              }
            }}
          >
            <Share2 className="ms-1 h-4 w-4" />
            اشتراک
          </Button>
        </div>
      </div>

      <div className="mt-8 flex gap-1 border-b">
        {tabs.map(([key, label]) => (
          <button
            key={key}
            type="button"
            onClick={() => setTab(key)}
            className={cn(
              "relative min-h-11 flex-1 px-2 text-sm font-medium",
              activeTab === key ? "text-primary" : "text-muted-foreground"
            )}
          >
            {label}
            {activeTab === key && (
              <span className="absolute inset-x-6 -bottom-px h-1 rounded-full bg-primary" />
            )}
          </button>
        ))}
      </div>

      <div className="mt-4 space-y-4">
        {activeTab === "posts" && (
          <PostListPanel
            isLoading={postsQ.isLoading}
            isError={postsQ.isError}
            posts={postsQ.data}
            emptyLabel="پستی نیست"
            onRetry={() => void postsQ.refetch()}
          />
        )}

        {activeTab === "archived" && isOwn && (
          <PostListPanel
            isLoading={archivedQ.isLoading}
            isError={archivedQ.isError}
            posts={archivedQ.data}
            emptyLabel="پست بایگانی‌شده‌ای نیست"
            onRetry={() => void archivedQ.refetch()}
          />
        )}

        {activeTab === "replies" &&
          (repliesQ.isLoading ? (
            <Skeleton className="h-32 w-full" />
          ) : repliesQ.isError ? (
            <ErrorRetry label="خطا در بارگذاری پاسخ‌ها" onRetry={() => void repliesQ.refetch()} />
          ) : (repliesQ.data?.length ?? 0) === 0 ? (
            <p className="py-12 text-center text-muted-foreground">پاسخی نیست</p>
          ) : (
            repliesQ.data!.map((c: Comment) => (
              <div key={c.id} className="rounded-2xl bg-bubble-others px-4 py-3">
                <p className="whitespace-pre-wrap text-[15px]">{c.content}</p>
              </div>
            ))
          ))}

        {activeTab === "likes" && (
          <PostListPanel
            isLoading={likesQ.isLoading}
            isError={likesQ.isError}
            posts={likesQ.data}
            emptyLabel="پسندی نیست"
            onRetry={() => void likesQ.refetch()}
          />
        )}
      </div>
    </div>
  );
}

function Stat({ label, value }: { label: string; value: number }) {
  return (
    <div className="px-2 py-2">
      <p className="text-lg font-bold">{value}</p>
      <p className="text-xs text-muted-foreground">{label}</p>
    </div>
  );
}

function ErrorRetry({ label, onRetry }: { label: string; onRetry: () => void }) {
  return (
    <div className="py-12 text-center">
      <p className="mb-3 text-muted-foreground">{label}</p>
      <Button type="button" variant="outline" className="min-h-11" onClick={onRetry}>
        تلاش دوباره
      </Button>
    </div>
  );
}

function PostListPanel({
  isLoading,
  isError,
  posts,
  emptyLabel,
  onRetry,
}: {
  isLoading: boolean;
  isError: boolean;
  posts: Post[] | undefined;
  emptyLabel: string;
  onRetry: () => void;
}) {
  if (isLoading) {
    return <Skeleton className="h-32 w-full" />;
  }
  if (isError) {
    return <ErrorRetry label="خطا در بارگذاری پست‌ها" onRetry={onRetry} />;
  }
  if ((posts?.length ?? 0) === 0) {
    return <p className="py-12 text-center text-muted-foreground">{emptyLabel}</p>;
  }
  return (
    <>
      {posts!.map((p) => (
        <PostCard key={p.id} post={p} />
      ))}
    </>
  );
}
