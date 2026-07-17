"use client";

import { useState } from "react";
import { UserPlus, UserMinus } from "lucide-react";
import { Button } from "@/components/ui/button";
import { followUser, unfollowUser } from "@/lib/api/users";
import { useAuthStore } from "@/stores/auth-store";

export function FollowButton({
  username,
  initialFollowing,
}: {
  username: string;
  initialFollowing?: boolean;
}) {
  const isAuthenticated = useAuthStore((s) => s.isAuthenticated);
  const self = useAuthStore((s) => s.user?.username);
  const [following, setFollowing] = useState(!!initialFollowing);
  const [busy, setBusy] = useState(false);

  if (!isAuthenticated || self === username) return null;

  const toggle = async () => {
    const prev = following;
    setFollowing(!prev);
    setBusy(true);
    try {
      if (prev) await unfollowUser(username);
      else await followUser(username);
    } catch {
      setFollowing(prev);
    } finally {
      setBusy(false);
    }
  };

  return (
    <Button
      variant={following ? "outline" : "default"}
      className="min-h-11"
      disabled={busy}
      onClick={() => void toggle()}
    >
      {following ? (
        <>
          <UserMinus className="ms-1 h-4 w-4" />
          لغو دنبال
        </>
      ) : (
        <>
          <UserPlus className="ms-1 h-4 w-4" />
          دنبال کردن
        </>
      )}
    </Button>
  );
}
