import type { User } from "@/types/user";

const CREATE_POST_ROLES = new Set([
  "author",
  "editor",
  "admin",
  "superadmin",
]);

export function canCreatePost(role: User["role"] | string | undefined | null): boolean {
  if (!role) return false;
  return CREATE_POST_ROLES.has(role);
}

/** Repost is limited to the same roles that can publish posts. */
export function canRepost(role: User["role"] | string | undefined | null): boolean {
  return canCreatePost(role);
}
