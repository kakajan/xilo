"use client";

import { useEffect, useRef, useState } from "react";
import Link from "next/link";
import { useParams, useRouter, useSearchParams } from "next/navigation";
import { useInfiniteQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { ArrowRight, Send } from "lucide-react";
import { listMessages, sendMessage } from "@/lib/api/chats";
import { useAuthStore } from "@/stores/auth-store";
import { useChatStore } from "@/stores/chat-store";
import { useWebSocket } from "@/hooks/use-websocket";
import { Button } from "@/components/ui/button";
import { Skeleton } from "@/components/ui/skeleton";
import { cn } from "@/lib/utils";
import { useFormatDate } from "@/hooks/use-format-date";
import type { ChatMessage } from "@/types/chat";

export default function ChatConversationPage() {
  const { id } = useParams<{ id: string }>();
  const searchParams = useSearchParams();
  const isSaved = searchParams.get("saved") === "1";
  const router = useRouter();
  const { user, isAuthenticated, isLoading: authLoading } = useAuthStore();
  const setActiveChat = useChatStore((s) => s.setActiveChat);
  const [text, setText] = useState("");
  const bottomRef = useRef<HTMLDivElement>(null);
  const formatDate = useFormatDate();
  const queryClient = useQueryClient();
  const { addHandler } = useWebSocket();

  useEffect(() => {
    if (!authLoading && !isAuthenticated) router.replace("/login");
  }, [authLoading, isAuthenticated, router]);

  useEffect(() => {
    setActiveChat(id);
    return () => setActiveChat(null);
  }, [id, setActiveChat]);

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
    return addHandler((event, payload) => {
      if (
        event === "chat.message" ||
        event === "message.created" ||
        event === "chat:message"
      ) {
        const msg = payload as ChatMessage;
        if (msg?.chat_id === id) {
          queryClient.invalidateQueries({ queryKey: ["messages", id] });
          queryClient.invalidateQueries({ queryKey: ["chats"] });
        }
      }
    });
  }, [addHandler, id, queryClient]);

  const sendMut = useMutation({
    mutationFn: () => sendMessage(id, text.trim()),
    onSuccess: () => {
      setText("");
      queryClient.invalidateQueries({ queryKey: ["messages", id] });
      queryClient.invalidateQueries({ queryKey: ["chats"] });
    },
  });

  if (authLoading || !isAuthenticated) {
    return <Skeleton className="h-64 w-full" />;
  }

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
            {isSaved ? "پیام‌های ذخیره‌شده" : "گفتگو"}
          </h1>
          <Link
            href={`/chat/${id}/contact`}
            className="text-xs text-muted-foreground hover:text-primary"
          >
            جزئیات مخاطب
          </Link>
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
            const own = m.sender_id === user?.id;
            return (
              <div
                key={m.id}
                className={cn("flex", own ? "justify-start" : "justify-end")}
              >
                <div
                  className={cn(
                    "max-w-[80%] rounded-[1rem] px-3.5 py-2.5 text-[15px]",
                    own ? "bg-chat-bubble-own" : "bg-chat-bubble-others"
                  )}
                >
                  {!own && m.sender_name && (
                    <p className="mb-1 text-xs font-semibold text-primary">{m.sender_name}</p>
                  )}
                  <p className="whitespace-pre-wrap">{m.is_deleted ? "پیام حذف شد" : m.content}</p>
                  <p className="mt-1 text-end text-[11px] text-muted-foreground">
                    {formatDate(m.created_at)}
                    {m.is_edited ? " · ویرایش‌شده" : ""}
                  </p>
                </div>
              </div>
            );
          })
        )}
        <div ref={bottomRef} />
      </div>

      <form
        className="mt-3 flex gap-2 border-t pt-3"
        onSubmit={(e) => {
          e.preventDefault();
          if (!text.trim() || sendMut.isPending) return;
          sendMut.mutate();
        }}
      >
        <input
          value={text}
          onChange={(e) => setText(e.target.value)}
          placeholder="پیام..."
          className="min-h-11 flex-1 rounded-full border bg-background px-4 text-sm"
        />
        <Button
          type="submit"
          size="icon"
          className="min-h-11 min-w-11 rounded-full"
          disabled={!text.trim() || sendMut.isPending}
          aria-label="ارسال"
        >
          <Send className="h-5 w-5" />
        </Button>
      </form>
    </div>
  );
}
