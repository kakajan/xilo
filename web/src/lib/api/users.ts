import { apiFetch } from "@/lib/api-client";
import type { CursorPage, FollowListUser, PublicProfile } from "@/types/chat";
import type { Comment } from "@/types/comment";
import type { Post } from "@/types/post";

export function getPublicProfile(username: string) {
  return apiFetch<PublicProfile>(`/api/users/${username}`);
}

export function listUserPosts(username: string, tab = "posts", cursor?: string) {
  const q = new URLSearchParams({ tab, limit: "20" });
  if (cursor) q.set("cursor", cursor);
  return apiFetch<CursorPage<Post>>(`/api/users/${username}/posts?${q}`);
}

export function listUserReplies(username: string, cursor?: string) {
  const q = new URLSearchParams({ limit: "20" });
  if (cursor) q.set("cursor", cursor);
  return apiFetch<CursorPage<Comment>>(`/api/users/${username}/replies?${q}`);
}

export function listUserLikes(username: string, cursor?: string) {
  const q = new URLSearchParams({ limit: "20" });
  if (cursor) q.set("cursor", cursor);
  return apiFetch<CursorPage<Post>>(`/api/users/${username}/likes?${q}`);
}

export function listFollowers(username: string, cursor?: string) {
  const q = new URLSearchParams({ limit: "20" });
  if (cursor) q.set("cursor", cursor);
  return apiFetch<CursorPage<FollowListUser>>(`/api/users/${username}/followers?${q}`);
}

export function listFollowing(username: string, cursor?: string, limit = 20) {
  const q = new URLSearchParams({ limit: String(limit) });
  if (cursor) q.set("cursor", cursor);
  return apiFetch<CursorPage<FollowListUser>>(`/api/users/${username}/following?${q}`);
}

export function followUser(username: string) {
  return apiFetch<{ following: boolean }>(`/api/users/${username}/follow`, {
    method: "POST",
  });
}

export function unfollowUser(username: string) {
  return apiFetch<{ following: boolean }>(`/api/users/${username}/follow`, {
    method: "DELETE",
  });
}
