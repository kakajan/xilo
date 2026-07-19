import { apiFetch } from "@/lib/api-client";
import type { ContactUser } from "@/types/chat";

export function listContacts() {
  return apiFetch<{ data: ContactUser[] }>("/api/contacts");
}
