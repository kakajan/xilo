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

export function getChat(id: string) {
  return apiFetch<Chat>(`/api/chats/${id}`);
}

export function getSavedMessagesChat() {
  return apiFetch<Chat>("/api/chats/saved");
}

export function createChat(
  memberIds: string[],
  type: "direct" | "group" = "direct",
  options?: { name?: string; avatarUrl?: string | null }
) {
  const body: Record<string, unknown> = {
    type,
    member_ids: memberIds,
  };
  if (type === "group") {
    body.name = options?.name;
    if (options?.avatarUrl) body.avatar_url = options.avatarUrl;
  }
  return apiFetch<Chat>("/api/chats", {
    method: "POST",
    headers: { "Idempotency-Key": idempotencyKey() },
    body: JSON.stringify(body),
  });
}

export function createGroupChat(name: string, memberIds: string[], avatarUrl?: string | null) {
  return createChat(memberIds, "group", { name, avatarUrl });
}

/** Leave a group or delete/hide a direct chat for the current user. */
export function leaveChat(id: string) {
  return apiFetch<{ message?: string; code?: string }>(`/api/chats/${id}`, {
    method: "DELETE",
  });
}

export function updateChat(
  id: string,
  patch: { is_archived?: boolean; is_muted?: boolean; name?: string; avatar_url?: string }
) {
  return apiFetch<Chat>(`/api/chats/${id}`, {
    method: "PATCH",
    body: JSON.stringify(patch),
  });
}

export function addChatMembers(chatId: string, userIds: string[]) {
  return apiFetch<Chat>(`/api/chats/${chatId}/members`, {
    method: "POST",
    body: JSON.stringify({ user_ids: userIds }),
  });
}

export function removeChatMember(chatId: string, userId: string) {
  return apiFetch<{ message?: string }>(`/api/chats/${chatId}/members/${userId}`, {
    method: "DELETE",
  });
}

export function updateChatMemberRole(chatId: string, userId: string, role: "admin" | "member") {
  return apiFetch<Chat>(`/api/chats/${chatId}/members/${userId}`, {
    method: "PATCH",
    body: JSON.stringify({ role }),
  });
}

export function listChatPins(chatId: string) {
  return apiFetch<
    Array<{
      chat_id: string;
      message_id: string;
      pinned_by: string;
      pinned_at: string;
      content?: string | null;
      type?: string;
    }>
  >(`/api/chats/${chatId}/pins`);
}

export function pinChatMessage(chatId: string, messageId: string) {
  return apiFetch<{ message?: string }>(`/api/chats/${chatId}/pins`, {
    method: "POST",
    body: JSON.stringify({ message_id: messageId }),
  });
}

export function unpinChatMessage(chatId: string, messageId: string) {
  return apiFetch<{ message?: string }>(`/api/chats/${chatId}/pins/${messageId}`, {
    method: "DELETE",
  });
}

export function createChatInviteLink(chatId: string) {
  return apiFetch<{
    id: string;
    chat_id: string;
    token: string;
    created_by: string;
    created_at: string;
    use_count: number;
  }>(`/api/chats/${chatId}/invite-links`, { method: "POST" });
}

export function revokeChatInviteLink(chatId: string, token: string) {
  return apiFetch<{ message?: string }>(`/api/chats/${chatId}/invite-links/${token}`, {
    method: "DELETE",
  });
}

export function joinChatByInvite(token: string) {
  return apiFetch<Chat>("/api/chats/join", {
    method: "POST",
    body: JSON.stringify({ token }),
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

export function markMessageRead(messageId: string) {
  return apiFetch<Record<string, string>>(`/api/messages/${messageId}/read`, {
    method: "POST",
  });
}

export function editMessage(messageId: string, content: string) {
  return apiFetch<ChatMessage>(`/api/messages/${messageId}`, {
    method: "PATCH",
    body: JSON.stringify({ content }),
  });
}

export function deleteMessage(messageId: string) {
  return apiFetch<{ message?: string }>(`/api/messages/${messageId}`, {
    method: "DELETE",
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
