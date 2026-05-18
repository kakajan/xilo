"use client";

import { useState, useCallback } from "react";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { Bookmark } from "lucide-react";
import { apiFetch } from "@/lib/api-client";
import { Button } from "@/components/ui/button";

interface BookmarkButtonProps {
  postId: string;
  bookmarked?: boolean;
}

export function BookmarkButton({ postId, bookmarked: initialBookmarked = false }: BookmarkButtonProps) {
  const [bookmarked, setBookmarked] = useState(initialBookmarked);
  const queryClient = useQueryClient();

  const mutation = useMutation({
    mutationFn: () =>
      apiFetch(`/api/posts/${postId}/bookmark`, {
        method: bookmarked ? "DELETE" : "POST",
      }),
    onSuccess: () => {
      setBookmarked(!bookmarked);
      queryClient.invalidateQueries({ queryKey: ["bookmarks"] });
    },
  });

  return (
    <Button
      variant="ghost"
      size="icon"
      onClick={() => mutation.mutate()}
      disabled={mutation.isPending}
      className={bookmarked ? "text-primary" : ""}
    >
      <Bookmark className="h-5 w-5" fill={bookmarked ? "currentColor" : "none"} />
    </Button>
  );
}
