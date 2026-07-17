import { apiFetch } from "@/lib/api-client";
import type { Session } from "@/types/chat";

export function listSessions() {
  return apiFetch<Session[]>("/api/auth/sessions");
}

export function revokeSession(id: string) {
  return apiFetch<{ ok?: string }>(`/api/auth/sessions/${id}`, { method: "DELETE" });
}
