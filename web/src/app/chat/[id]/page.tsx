"use client";

import { useEffect, useRef, useState } from "react";
import Link from "next/link";
import { useParams, useRouter, useSearchParams } from "next/navigation";
import { useInfiniteQuery, useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { ArrowRight, Check, Pencil, Send, Trash2, X } from "lucide-react";
import {
  deleteMessage,
  editMessage,
  getChat,
  listMessages,
  markMessageRead,
  sendMessage,
} from "@/lib/api/chats";
import { useAuthStore } from "@/stores/auth-store";
import { useChatStore } from "@/stores/chat-store";
import { useWebSocket } from "@/hooks/use-websocket";
import { sendRealtime } from "@/lib/realtime-socket";
import { Button } from "@/components/ui/button";
import { Skeleton } from "@/components/ui/skeleton";
import { cn } from "@/lib/utils";
import { TimeLabel } from "@/components/user/username-handle";
import { useFormatDate } from "@/hooks/use-format-date";
import type { Chat, ChatMessage } from "@/types/chat";

const EDIT_WINDOW_MS = 48 * 60 * 60 * 1000;

function clearChatUnreadInCache(
  queryClient: ReturnType<typeof useQueryClient>,
  chatId: string
) {
  queryClient.setQueryData<Chat[]>(["chats"], (prev) =>
    (prev ?? []).map((c) => (c.id === chatId ? { ...c, unread_count: 0 } : c))
  );
}

function canEditOwnMessage(message: ChatMessage, userId?: string) {
  if (!userId || message.sender_id !== userId) return false;
  if (message.is_deleted || message.type === "system") return false;
  if (!message.content?.trim()) return false;
  const created = Date.parse(message.created_at);
  if (Number.isNaN(created)) return false;
  return Date.now() - created < EDIT_WINDOW_MS;
}

function canDeleteOwnMessage(message: ChatMessage, userId?: string) {
  if (!userId || message.sender_id !== userId) return false;
  if (message.is_deleted || message.type === "system") return false;
  return true;
}

export default function ChatConversationPage() {
  const { id } = useParams<{ id: string }>();
  const searchParams = useSearchParams();
  const isSaved = searchParams.get("saved") === "1";
  const router = useRouter();
  const { user, isAuthenticated, isLoading: authLoading, authChecked } = useAuthStore();
  const setActiveChat = useChatStore((s) => s.setActiveChat);
  const [text, setText] = useState("");
  const [editingMessage, setEditingMessage] = useState<ChatMessage | null>(null);
  const [menuMessageId, setMenuMessageId] = useState<string | null>(null);
  const bottomRef = useRef<HTMLDivElement>(null);
  const formatDate = useFormatDate();
  const queryClient = useQueryClient();
  const { addHandler, joinChat, leaveChat } = useWebSocket();
  const lastMarkedReadId = useRef<string | null>(null);

  useEffect(() => {
    if (authChecked && !isAuthenticated) router.replace("/login");
  }, [authChecked, isAuthenticated, router]);

  useEffect(() => {
    setActiveChat(id);
    lastMarkedReadId.current = null;
    setEditingMessage(null);
    setMenuMessageId(null);
    setText("");
    return () => setActiveChat(null);
  }, [id, setActiveChat]);

  useEffect(() => {
    if (!isAuthenticated || !id) return;
    joinChat(id);
    return () => leaveChat(id);
  }, [isAuthenticated, id, joinChat, leaveChat]);

  const { data: chat } = useQuery({
    queryKey: ["chat", id],
    enabled: isAuthenticated && !!id && !isSaved,
    queryFn: () => getChat(id),
  });

  const { data, isLoading, fetchNextPage, hasNextPage, isFetchingNextPage } =
    useInfiniteQuery({
      queryKey: ["messages", id],
      enabled: isAuthenticated && !!id,
      initialPageParam: undefined as string | undefined,
      queryFn: ({ pageParam }) => listMessages(id, pageParam),
      getNextPageParam: (last) => (last.has_more ? last.next_cursor : undefined),
    });

  const messages = (data?.pages ?? [])
    .flatMap((p) => p.data ?? [])
    .slice()
    .reverse();

  useEffect(() => {
    bottomRef.current?.scrollIntoView({ behavior: "smooth" });
  }, [messages.length]);

  useEffect(() => {
    if (!isAuthenticated || !id || messages.length === 0) return;
    const latest =
      [...messages].reverse().find((m) => m.sender_id !== user?.id) ??
      messages[messages.length - 1];
    if (!latest?.id || latest.id === lastMarkedReadId.current) return;
    lastMarkedReadId.current = latest.id;
    clearChatUnreadInCache(queryClient, id);
    sendRealtime("message.read", { message_id: latest.id });
    void markMessageRead(latest.id).catch(() => {
      /* local cache already cleared */
    });
  }, [isAuthenticated, id, messages, user?.id, queryClient]);

  useEffect(() => {
    return addHandler((event, payload) => {
      const chatId =
        payload && typeof payload === "object" && "chat_id" in payload
          ? String((payload as { chat_id?: string }).chat_id ?? "")
          : "";
      if (chatId && chatId !== id) return;

      if (event === "message.receive") {
        const messageId =
          payload && typeof payload === "object" && "id" in payload
            ? String((payload as { id?: string }).id ?? "")
            : "";
        const senderId =
          payload && typeof payload === "object" && "sender_id" in payload
            ? String((payload as { sender_id?: string }).sender_id ?? "")
            : "";
        if (messageId && senderId && senderId !== user?.id) {
          lastMarkedReadId.current = messageId;
          clearChatUnreadInCache(queryClient, id);
          sendRealtime("message.read", { message_id: messageId });
          void markMessageRead(messageId)
            .catch(() => {})
            .finally(() => {
              queryClient.invalidateQueries({ queryKey: ["chats"] });
            });
        } else {
          queryClient.invalidateQueries({ queryKey: ["chats"] });
        }
        queryClient.invalidateQueries({ queryKey: ["messages", id] });
        return;
      }

      if (
        event === "message.edit" ||
        event === "message.delete" ||
        event === "message.read" ||
        event === "message.reaction"
      ) {
        queryClient.invalidateQueries({ queryKey: ["messages", id] });
        queryClient.invalidateQueries({ queryKey: ["chats"] });
      }
    });
  }, [addHandler, id, queryClient, user?.id]);

  const refreshMessages = () => {
    queryClient.invalidateQueries({ queryKey: ["messages", id] });
    queryClient.invalidateQueries({ queryKey: ["chats"] });
  };

  const sendMut = useMutation({
    mutationFn: () => sendMessage(id, text.trim()),
    onSuccess: () => {
      setText("");
      refreshMessages();
    },
  });

  const editMut = useMutation({
    mutationFn: () => {
      if (!editingMessage) throw new Error("no message");
      return editMessage(editingMessage.id, text.trim());
    },
    onSuccess: () => {
      setText("");
      setEditingMessage(null);
      refreshMessages();
    },
  });

  const deleteMut = useMutation({
    mutationFn: (messageId: string) => deleteMessage(messageId),
    onSuccess: (_data, messageId) => {
      if (editingMessage?.id === messageId) {
        setEditingMessage(null);
        setText("");
      }
      setMenuMessageId(null);
      refreshMessages();
    },
  });

  const beginEdit = (message: ChatMessage) => {
    setEditingMessage(message);
    setText(message.content ?? "");
    setMenuMessageId(null);
  };

  const cancelEdit = () => {
    setEditingMessage(null);
    setText("");
  };

  const handleDelete = (message: ChatMessage) => {
    if (!window.confirm("این پیام حذف شود؟")) return;
    deleteMut.mutate(message.id);
  };

  if (authLoading || !isAuthenticated) {
    return <Skeleton className="h-64 w-full" />;
  }

  const submitting = sendMut.isPending || editMut.isPending;

  return (
    <div className="flex h-[min(70vh,720px)] flex-col">
      <header className="mb-3 flex items-center gap-3 border-b pb-3">
        <Button
          variant="ghost"
          size="icon"
          className="min-h-11 min-w-11"
          onClick={() => router.push("/chat")}
          aria-label="بازگشت"
        >
          <ArrowRight className="h-5 w-5" />
        </Button>
        <div className="min-w-0 flex-1">
          <h1 className="truncate font-bold">
            {isSaved
              ? "پیام‌های ذخیره‌شده"
              : chat?.type === "group"
                ? chat.name || "گروه"
                : "گفتگو"}
          </h1>
          {!isSaved ? (
            <Link
              href={
                chat?.type === "group" ? `/chat/${id}/group` : `/chat/${id}/contact`
              }
              className="text-xs text-muted-foreground hover:text-primary"
            >
              {chat?.type === "group" ? "اطلاعات گروه" : "جزئیات مخاطب"}
            </Link>
          ) : null}
        </div>
      </header>

      <div className="min-h-0 flex-1 space-y-2 overflow-y-auto px-1">
        {hasNextPage && (
          <div className="text-center">
            <Button
              variant="ghost"
              size="sm"
              disabled={isFetchingNextPage}
              onClick={() => fetchNextPage()}
            >
              پیام‌های قدیمی‌تر
            </Button>
          </div>
        )}
        {isLoading ? (
          <Skeleton className="h-40 w-full" />
        ) : messages.length === 0 ? (
          <p className="py-16 text-center text-muted-foreground">اولین پیام را بفرستید</p>
        ) : (
          messages.map((m) => {
            if (m.type === "system") {
              return (
                <div key={m.id} className="flex justify-center py-1">
                  <p className="rounded-full bg-muted px-3 py-1 text-center text-xs text-muted-foreground">
                    {m.content}
                  </p>
                </div>
              );
            }
            const own = m.sender_id === user?.id;
            const content = m.content ?? "";
            const mentionHighlighted = content.replace(
              /(@[A-Za-z0-9_]+)/g,
              "⟪$1⟫"
            );
            const showEdit = canEditOwnMessage(m, user?.id);
            const showDelete = canDeleteOwnMessage(m, user?.id);
            const menuOpen = menuMessageId === m.id;
            return (
              <div
                key={m.id}
                className={cn("group/msg relative flex", own ? "justify-start" : "justify-end")}
              >
                <div
                  className={cn(
                    "max-w-[80%] rounded-[1rem] px-3.5 py-2.5 text-[15px]",
                    own ? "bg-chat-bubble-own" : "bg-chat-bubble-others"
                  )}
                  onContextMenu={(e) => {
                    if (!showEdit && !showDelete) return;
                    e.preventDefault();
                    setMenuMessageId(m.id);
                  }}
                >
                  {!own && m.sender_name && (
                    <p className="mb-1 text-xs font-semibold text-primary">{m.sender_name}</p>
                  )}
                  <p className="whitespace-pre-wrap">
                    {m.is_deleted
                      ? "پیام حذف شد"
                      : mentionHighlighted.split(/(⟪@?[A-Za-z0-9_]+⟫)/g).map((part, idx) =>
                          part.startsWith("⟪") && part.endsWith("⟫") ? (
                            <span key={idx} className="font-semibold text-primary">
                              {part.slice(1, -1)}
                            </span>
                          ) : (
                            <span key={idx}>{part}</span>
                          )
                        )}
                  </p>
                  <p className="mt-1 flex flex-wrap items-center justify-end gap-x-1.5 text-end text-[11px] text-muted-foreground">
                    <TimeLabel>{formatDate(m.created_at, { withTime: true })}</TimeLabel>
                    {m.is_edited ? (
                      <>
                        <span aria-hidden>·</span>
                        <span>ویرایش‌شده</span>
                      </>
                    ) : null}
                  </p>
                  {(showEdit || showDelete) && (
                    <div className="mt-1.5 flex items-center justify-end gap-1 opacity-100 sm:opacity-0 sm:group-hover/msg:opacity-100">
                      {showEdit ? (
                        <Button
                          type="button"
                          variant="ghost"
                          size="icon"
                          className="h-8 w-8"
                          aria-label="ویرایش پیام"
                          onClick={() => beginEdit(m)}
                        >
                          <Pencil className="h-3.5 w-3.5" />
                        </Button>
                      ) : null}
                      {showDelete ? (
                        <Button
                          type="button"
                          variant="ghost"
                          size="icon"
                          className="h-8 w-8 text-destructive"
                          aria-label="حذف پیام"
                          disabled={deleteMut.isPending}
                          onClick={() => handleDelete(m)}
                        >
                          <Trash2 className="h-3.5 w-3.5" />
                        </Button>
                      ) : null}
                    </div>
                  )}
                  {menuOpen && (showEdit || showDelete) ? (
                    <div className="absolute z-10 mt-1 flex min-w-32 flex-col rounded-md border bg-popover p-1 text-sm shadow-md ltr:right-0 rtl:left-0">
                      {showEdit ? (
                        <button
                          type="button"
                          className="flex items-center gap-2 rounded px-2 py-1.5 text-start hover:bg-muted"
                          onClick={() => beginEdit(m)}
                        >
                          <Pencil className="h-3.5 w-3.5 shrink-0" />
                          <span className="min-w-0">ویرایش</span>
                        </button>
                      ) : null}
                      {showDelete ? (
                        <button
                          type="button"
                          className="flex items-center gap-2 rounded px-2 py-1.5 text-start text-destructive hover:bg-muted"
                          disabled={deleteMut.isPending}
                          onClick={() => handleDelete(m)}
                        >
                          <Trash2 className="h-3.5 w-3.5 shrink-0" />
                          <span className="min-w-0">حذف</span>
                        </button>
                      ) : null}
                      <button
                        type="button"
                        className="flex items-center gap-2 rounded px-2 py-1.5 text-start text-muted-foreground hover:bg-muted"
                        onClick={() => setMenuMessageId(null)}
                      >
                        <X className="h-3.5 w-3.5 shrink-0" />
                        <span className="min-w-0">بستن</span>
                      </button>
                    </div>
                  ) : null}
                </div>
              </div>
            );
          })
        )}
        <div ref={bottomRef} />
      </div>

      {editingMessage ? (
        <div className="mt-3 flex items-center gap-2 rounded-lg border bg-muted/40 px-3 py-2 text-sm">
          <Pencil className="h-4 w-4 shrink-0 text-primary" />
          <p className="min-w-0 flex-1 truncate text-muted-foreground">
            در حال ویرایش پیام
          </p>
          <Button
            type="button"
            variant="ghost"
            size="sm"
            className="shrink-0"
            onClick={cancelEdit}
          >
            انصراف
          </Button>
        </div>
      ) : null}

      <form
        className="mt-3 flex gap-2 border-t pt-3"
        onSubmit={(e) => {
          e.preventDefault();
          if (!text.trim() || submitting) return;
          if (editingMessage) {
            editMut.mutate();
            return;
          }
          sendMut.mutate();
        }}
      >
        <input
          value={text}
          onChange={(e) => setText(e.target.value)}
          placeholder={editingMessage ? "متن ویرایش‌شده..." : "پیام..."}
          className="min-h-11 flex-1 rounded-full border bg-background px-4 text-sm"
        />
        <Button
          type="submit"
          size="icon"
          className="min-h-11 min-w-11 rounded-full"
          disabled={!text.trim() || submitting}
          aria-label={editingMessage ? "ذخیره ویرایش" : "ارسال"}
        >
          {editingMessage ? <Check className="h-5 w-5" /> : <Send className="h-5 w-5" />}
        </Button>
      </form>
    </div>
  );
}
