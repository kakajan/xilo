"use client";

import { useRouter } from "next/navigation";
import { useQueryClient } from "@tanstack/react-query";
import { Archive, MoreVertical, Pencil, Trash2 } from "lucide-react";
import { apiFetch } from "@/lib/api-client";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";
import type { Post } from "@/types/post";

interface PostOwnerMenuProps {
  post: Post;
  onRemoved?: () => void;
}

export function PostOwnerMenu({ post, onRemoved }: PostOwnerMenuProps) {
  const router = useRouter();
  const queryClient = useQueryClient();

  const invalidate = async () => {
    await queryClient.invalidateQueries({ queryKey: ["feed"] });
    await queryClient.invalidateQueries({ queryKey: ["user-posts"] });
    await queryClient.invalidateQueries({ queryKey: ["discover"] });
    onRemoved?.();
  };

  const onEdit = () => {
    // Prefer id in the path; pass slug so the editor can load via public GET.
    const slug = post.slug ? `?slug=${encodeURIComponent(post.slug)}` : "";
    router.push(`/write/${post.id}${slug}`);
  };

  const onArchive = async () => {
    const ok = window.confirm("این پست به بایگانی منتقل شود؟ از فید عمومی حذف می‌شود.");
    if (!ok) return;
    try {
      await apiFetch(`/api/posts/${post.id}`, {
        method: "PATCH",
        body: JSON.stringify({ status: "archived" }),
      });
      await invalidate();
    } catch (err) {
      window.alert(err instanceof Error ? err.message : "آرشیو ناموفق بود");
    }
  };

  const onDelete = async () => {
    const ok = window.confirm("این پست برای همیشه حذف شود؟ این کار قابل بازگشت نیست.");
    if (!ok) return;
    try {
      await apiFetch(`/api/posts/${post.id}`, { method: "DELETE" });
      await invalidate();
    } catch (err) {
      window.alert(err instanceof Error ? err.message : "حذف ناموفق بود");
    }
  };

  return (
    <DropdownMenu>
      <DropdownMenuTrigger asChild>
        <button
          type="button"
          className="inline-flex min-h-11 min-w-11 shrink-0 items-center justify-center rounded-full text-muted-foreground hover:bg-accent hover:text-foreground"
          aria-label="گزینه‌های پست"
          onClick={(e) => {
            e.preventDefault();
            e.stopPropagation();
          }}
        >
          <MoreVertical className="h-5 w-5" />
        </button>
      </DropdownMenuTrigger>
      <DropdownMenuContent align="start" className="min-w-[9rem]">
        <DropdownMenuItem
          onSelect={(e) => {
            e.preventDefault();
            onEdit();
          }}
        >
          <Pencil className="h-4 w-4 shrink-0" />
          <span className="min-w-0">ویرایش</span>
        </DropdownMenuItem>
        <DropdownMenuItem
          onSelect={(e) => {
            e.preventDefault();
            void onArchive();
          }}
        >
          <Archive className="h-4 w-4 shrink-0" />
          <span className="min-w-0">آرشیو</span>
        </DropdownMenuItem>
        <DropdownMenuItem
          destructive
          onSelect={(e) => {
            e.preventDefault();
            void onDelete();
          }}
        >
          <Trash2 className="h-4 w-4 shrink-0" />
          <span className="min-w-0">حذف</span>
        </DropdownMenuItem>
      </DropdownMenuContent>
    </DropdownMenu>
  );
}
