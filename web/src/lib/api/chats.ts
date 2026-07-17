import { apiFetch } from "@/lib/api-client";
import type { Chat, ChatFolder, ChatMessage, CursorPage } from "@/types/chat";

function idempotencyKey() {
  return crypto.randomUUID();
}

export function listChats(cursor?: string, limit = 20) {
  const q = new URLSearchParams({ limit: String(limit) });
  if (cursor) q.set("cursor", cursor);
  return apiFetch<CursorPage<Chat>>(`/api/chats?${q}`);
}

export function getSavedMessagesChat() {
  return apiFetch<Chat>("/api/chats/saved");
}

export function createChat(memberIds: string[], type: "direct" | "group" = "direct") {
  return apiFetch<Chat>("/api/chats", {
    method: "POST",
    headers: { "Idempotency-Key": idempotencyKey() },
    body: JSON.stringify({ type, member_ids: memberIds }),
  });
}

export function listMessages(chatId: string, cursor?: string, limit = 50) {
  const q = new URLSearchParams({ limit: String(limit) });
  if (cursor) q.set("cursor", cursor);
  return apiFetch<CursorPage<ChatMessage>>(`/api/chats/${chatId}/messages?${q}`);
}

export function sendMessage(
  chatId: string,
  content: string,
  replyToId?: string | null
) {
  return apiFetch<ChatMessage>(`/api/chats/${chatId}/messages`, {
    method: "POST",
    headers: { "Idempotency-Key": idempotencyKey() },
    body: JSON.stringify({
      type: "text",
      content,
      reply_to_id: replyToId ?? undefined,
    }),
  });
}

export function listChatFolders() {
  return apiFetch<ChatFolder[]>("/api/chat-folders");
}

export function createChatFolder(name: string) {
  return apiFetch<ChatFolder>("/api/chat-folders", {
    method: "POST",
    body: JSON.stringify({ name }),
  });
}

export function updateChatFolder(id: string, name: string) {
  return apiFetch<ChatFolder>(`/api/chat-folders/${id}`, {
    method: "PATCH",
    body: JSON.stringify({ name }),
  });
}

export function deleteChatFolder(id: string) {
  return apiFetch<{ ok?: string }>(`/api/chat-folders/${id}`, { method: "DELETE" });
}

export function setChatFolderChats(id: string, chatIds: string[]) {
  return apiFetch<ChatFolder>(`/api/chat-folders/${id}/chats`, {
    method: "PUT",
    body: JSON.stringify({ chat_ids: chatIds }),
  });
}
