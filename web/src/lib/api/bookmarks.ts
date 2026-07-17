import { apiFetch } from "@/lib/api-client";
import type { BookmarkedComment, CursorPage } from "@/types/chat";
import type { Post } from "@/types/post";

export function getBookmarks() {
  return apiFetch<CursorPage<Post>>("/api/bookmarks");
}

export function getCommentBookmarks() {
  return apiFetch<CursorPage<BookmarkedComment>>("/api/bookmarks/comments");
}

export function bookmarkPost(id: string) {
  return apiFetch<{ bookmarked: boolean }>(`/api/posts/${id}/bookmark`, {
    method: "POST",
  });
}

export function unbookmarkPost(id: string) {
  return apiFetch<{ bookmarked: boolean }>(`/api/posts/${id}/bookmark`, {
    method: "DELETE",
  });
}

export function bookmarkComment(id: string) {
  return apiFetch<{ bookmarked: boolean }>(`/api/comments/${id}/bookmark`, {
    method: "POST",
  });
}

export function unbookmarkComment(id: string) {
  return apiFetch<{ bookmarked: boolean }>(`/api/comments/${id}/bookmark`, {
    method: "DELETE",
  });
}
