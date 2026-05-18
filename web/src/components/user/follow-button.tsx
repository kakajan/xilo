"use client";

import { useState } from "react";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { UserPlus, UserCheck } from "lucide-react";
import { apiFetch } from "@/lib/api-client";
import { Button } from "@/components/ui/button";

interface FollowButtonProps {
  username: string;
  followed?: boolean;
}

export function FollowButton({ username, followed: initialFollowed = false }: FollowButtonProps) {
  const [followed, setFollowed] = useState(initialFollowed);
  const queryClient = useQueryClient();

  const mutation = useMutation({
    mutationFn: () =>
      apiFetch(`/api/users/${username}/follow`, {
        method: followed ? "DELETE" : "POST",
      }),
    onSuccess: () => {
      setFollowed(!followed);
      queryClient.invalidateQueries({ queryKey: ["user", username] });
    },
  });

  return (
    <Button
      variant={followed ? "outline" : "default"}
      size="sm"
      onClick={() => mutation.mutate()}
      disabled={mutation.isPending}
    >
      {followed ? (
        <>
          <UserCheck className="h-4 w-4 mr-1" /> Following
        </>
      ) : (
        <>
          <UserPlus className="h-4 w-4 mr-1" /> Follow
        </>
      )}
    </Button>
  );
}
