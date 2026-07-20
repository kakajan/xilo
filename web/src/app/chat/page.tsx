"use client";

import { useEffect, useMemo, useState } from "react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Bookmark, Search, Trash2 } from "lucide-react";
import {
  listChats,
  getSavedMessagesChat,
  listChatFolders,
  leaveChat,
} from "@/lib/api/chats";
import { useAuthStore } from "@/stores/auth-store";
import { useChatStore } from "@/stores/chat-store";
import { Avatar, AvatarFallback, AvatarImage } from "@/components/ui/avatar";
import { Skeleton } from "@/components/ui/skeleton";
import { getInitials } from "@/lib/utils";
import { TimeLabel } from "@/components/user/username-handle";
import { useFormatDate } from "@/hooks/use-format-date";
import type { Chat } from "@/types/chat";

export default function ChatListPage() {
  const router = useRouter();
  const queryClient = useQueryClient();
  const { isAuthenticated, isLoading: authLoading, authChecked, user } = useAuthStore();
  const setChatsCache = useChatStore((s) => s.setChatsCache);
  const [q, setQ] = useState("");
  const [folderId, setFolderId] = useState<string | "all">("all");
  const [error, setError] = useState<string | null>(null);
  const formatDate = useFormatDate();

  useEffect(() => {
    if (authChecked && !isAuthenticated) router.replace("/login");
  }, [authChecked, isAuthenticated, router]);

  const { data, isLoading } = useQuery({
    queryKey: ["chats"],
    enabled: isAuthenticated,
    queryFn: async () => {
      const res = await listChats();
      const rows = (res.data ?? []).filter((c) => !c.is_archived && c.type !== "saved");
      setChatsCache(rows);
      return rows;
    },
  });

  const { data: folders } = useQuery({
    queryKey: ["chat-folders"],
    enabled: isAuthenticated,
    queryFn: listChatFolders,
  });

  const { data: savedChat } = useQuery({
    queryKey: ["saved-chat"],
    enabled: isAuthenticated,
    queryFn: getSavedMessagesChat,
    retry: false,
  });

  const deleteMut = useMutation({
    mutationFn: (chatId: string) => leaveChat(chatId),
    onMutate: async (chatId) => {
      setError(null);
      await queryClient.cancelQueries({ queryKey: ["chats"] });
      const previous = queryClient.getQueryData<Chat[]>(["chats"]);
      queryClient.setQueryData<Chat[]>(["chats"], (prev) =>
        (prev ?? []).filter((c) => c.id !== chatId)
      );
      return { previous };
    },
    onError: (_err, _id, ctx) => {
      if (ctx?.previous) queryClient.setQueryData(["chats"], ctx.previous);
      setError("حذف گفتگو ناموفق بود");
    },
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ["chats"] });
    },
  });

  const filtered = useMemo(() => {
    let list = data ?? [];
    if (folderId !== "all" && folders) {
      const folder = folders.find((f) => f.id === folderId);
      const ids = new Set(folder?.chat_ids ?? []);
      list = list.filter((c) => ids.has(c.id));
    }
    if (q.trim()) {
      const needle = q.trim().toLowerCase();
      list = list.filter((c) => chatTitle(c, user?.id).toLowerCase().includes(needle));
    }
    return list;
  }, [data, folderId, folders, q, user?.id]);

  if (authLoading || !isAuthenticated) {
    return <Skeleton className="h-40 w-full" />;
  }

  return (
    <div>
      <div className="mb-4 flex items-center justify-between gap-3">
        <h1 className="text-2xl font-bold">پیام‌ها</h1>
        <Link
          href="/saved"
          className="inline-flex min-h-11 items-center gap-2 rounded-full px-3 text-sm text-primary hover:bg-accent"
        >
          <Bookmark className="h-4 w-4 shrink-0" />
          <span className="min-w-0">ذخیره‌ها</span>
        </Link>
      </div>

      {error ? (
        <p className="mb-3 rounded-xl bg-destructive/10 px-3 py-2 text-sm text-destructive">
          {error}
        </p>
      ) : null}

      <div className="relative mb-3">
        <Search className="absolute start-3 top-1/2 h-4 w-4 -translate-y-1/2 text-muted-foreground" />
        <input
          value={q}
          onChange={(e) => setQ(e.target.value)}
          placeholder="جستجوی گفتگو..."
          className="w-full min-h-11 rounded-xl border bg-background py-2 pe-4 ps-9 text-sm"
        />
      </div>

      <div className="mb-4 flex gap-2 overflow-x-auto pb-1">
        <FolderChip active={folderId === "all"} onClick={() => setFolderId("all")} label="همه" />
        {folders?.map((f) => (
          <FolderChip
            key={f.id}
            active={folderId === f.id}
            onClick={() => setFolderId(f.id)}
            label={f.name}
          />
        ))}
      </div>

      {savedChat && (
        <Link
          href={`/chat/${savedChat.id}?saved=1`}
          className="mb-2 flex min-h-14 items-center gap-3 rounded-xl px-2 py-2 hover:bg-muted/60"
        >
          <div className="flex h-12 w-12 items-center justify-center rounded-full bg-primary text-primary-foreground">
            <Bookmark className="h-5 w-5" />
          </div>
          <div className="min-w-0 flex-1">
            <p className="font-semibold">پیام‌های ذخیره‌شده</p>
            <p className="truncate text-sm text-muted-foreground">
              {savedChat.last_message?.content || "یادداشت‌ها و ارسال به خود"}
            </p>
          </div>
        </Link>
      )}

      {isLoading ? (
        <div className="space-y-2">
          {[1, 2, 3].map((i) => (
            <Skeleton key={i} className="h-16 w-full rounded-xl" />
          ))}
        </div>
      ) : filtered.length === 0 ? (
        <p className="py-16 text-center text-muted-foreground">گفتگویی نیست</p>
      ) : (
        <ul className="divide-y divide-border">
          {filtered.map((chat) => {
            const title = chatTitle(chat, user?.id);
            const avatar = chatAvatar(chat, user?.id);
            return (
              <li key={chat.id} className="flex items-center gap-1">
                <Link
                  href={`/chat/${chat.id}`}
                  className="flex min-h-16 min-w-0 flex-1 items-center gap-3 px-1 py-3 hover:bg-muted/50"
                >
                  <Avatar className="h-12 w-12 shrink-0">
                    {avatar ? <AvatarImage src={avatar} alt="" /> : null}
                    <AvatarFallback>{getInitials(title)}</AvatarFallback>
                  </Avatar>
                  <div className="min-w-0 flex-1">
                    <div className="flex items-center justify-between gap-2">
                      <p className="truncate font-semibold">{title}</p>
                      {chat.last_message_at && (
                        <TimeLabel className="shrink-0 text-xs text-muted-foreground">
                          {formatDate(chat.last_message_at)}
                        </TimeLabel>
                      )}
                    </div>
                    <div className="flex items-center justify-between gap-2">
                      <p className="truncate text-sm text-muted-foreground">
                        {chat.last_message?.content || "بدون پیام"}
                      </p>
                      {(chat.unread_count ?? 0) > 0 && (
                        <span className="shrink-0 rounded-full bg-primary px-2 py-0.5 text-xs text-primary-foreground">
                          {chat.unread_count}
                        </span>
                      )}
                    </div>
                  </div>
                </Link>
                <button
                  type="button"
                  className="inline-flex min-h-11 min-w-11 shrink-0 items-center justify-center rounded-full text-destructive hover:bg-destructive/10"
                  aria-label="حذف گفتگو"
                  title="حذف گفتگو"
                  disabled={deleteMut.isPending}
                  onClick={() => {
                    if (!window.confirm(`گفتگوی «${title}» حذف شود؟`)) return;
                    deleteMut.mutate(chat.id);
                  }}
                >
                  <Trash2 className="h-4 w-4" />
                </button>
              </li>
            );
          })}
        </ul>
      )}
    </div>
  );
}

function FolderChip({
  label,
  active,
  onClick,
}: {
  label: string;
  active: boolean;
  onClick: () => void;
}) {
  return (
    <button
      type="button"
      onClick={onClick}
      className={`shrink-0 rounded-full px-3 py-1.5 text-sm min-h-9 ${
        active ? "bg-primary text-primary-foreground" : "bg-muted text-muted-foreground"
      }`}
    >
      {label}
    </button>
  );
}

function chatTitle(chat: Chat, selfId?: string) {
  if (chat.type === "saved" || chat.name === "Saved Messages") return "پیام‌های ذخیره‌شده";
  if (chat.name) return chat.name;
  const other = chat.members?.find((m) => m.user_id !== selfId);
  return other?.display_name || other?.username || "گفتگو";
}

function chatAvatar(chat: Chat, selfId?: string) {
  if (chat.avatar_url) return chat.avatar_url;
  const other = chat.members?.find((m) => m.user_id !== selfId);
  return other?.avatar_url || undefined;
}
