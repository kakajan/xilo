"use client";

import Link from "next/link";
import { useParams, useRouter } from "next/navigation";
import { useQuery } from "@tanstack/react-query";
import { ArrowRight } from "lucide-react";
import { listFollowers, listFollowing } from "@/lib/api/users";
import { Avatar, AvatarFallback, AvatarImage } from "@/components/ui/avatar";
import { Button } from "@/components/ui/button";
import { Skeleton } from "@/components/ui/skeleton";
import { FollowButton } from "@/components/user/follow-button";
import { UsernameHandle } from "@/components/user/username-handle";
import { getInitials } from "@/lib/utils";

export function FollowListPage({ mode }: { mode: "followers" | "following" }) {
  const { username } = useParams<{ username: string }>();
  const router = useRouter();

  const { data, isLoading } = useQuery({
    queryKey: [mode, username],
    queryFn: async () => {
      const res =
        mode === "followers"
          ? await listFollowers(username)
          : await listFollowing(username);
      return res.data ?? [];
    },
  });

  return (
    <div>
      <div className="mb-4 flex items-center gap-2">
        <Button
          variant="ghost"
          size="icon"
          className="min-h-11 min-w-11"
          onClick={() => router.push(`/${username}`)}
        >
          <ArrowRight className="h-5 w-5" />
        </Button>
        <h1 className="flex min-w-0 flex-wrap items-center gap-x-2 text-xl font-bold">
          <span>{mode === "followers" ? "دنبال‌کنندگان" : "دنبال‌شوندگان"}</span>
          <UsernameHandle username={username} />
        </h1>
      </div>

      {isLoading ? (
        <Skeleton className="h-40 w-full" />
      ) : (data?.length ?? 0) === 0 ? (
        <p className="py-12 text-center text-muted-foreground">لیست خالی است</p>
      ) : (
        <ul className="divide-y divide-border">
          {data!.map((u) => (
            <li key={u.id} className="flex items-center gap-3 py-3">
              <Link href={`/${u.username}`}>
                <Avatar className="h-12 w-12">
                  {u.avatar_url ? <AvatarImage src={u.avatar_url} alt="" /> : null}
                  <AvatarFallback>
                    {getInitials(u.display_name || u.username)}
                  </AvatarFallback>
                </Avatar>
              </Link>
              <div className="flex min-w-0 flex-1 flex-col gap-0.5">
                <Link
                  href={`/${u.username}`}
                  className="block truncate font-semibold hover:underline"
                >
                  {u.display_name || u.username}
                </Link>
                <UsernameHandle
                  username={u.username}
                  className="block truncate text-sm text-muted-foreground"
                />
              </div>
              <FollowButton username={u.username} initialFollowing={u.is_following} />
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}
