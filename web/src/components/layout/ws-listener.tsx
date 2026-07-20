"use client";

import { useEffect } from "react";
import { useQueryClient } from "@tanstack/react-query";
import { useWebSocket } from "@/hooks/use-websocket";
import { useAuthStore } from "@/stores/auth-store";
import { subscribeConnection } from "@/lib/realtime-socket";

export function GlobalWebSocketListener() {
  const { addHandler } = useWebSocket();
  const { isAuthenticated } = useAuthStore();
  const queryClient = useQueryClient();

  useEffect(() => {
    if (!isAuthenticated) return;

    const remove = addHandler((event, data) => {
      if (event === "notification.count") {
        const unread =
          data && typeof data === "object" && "unread" in data
            ? Number((data as { unread?: number }).unread ?? 0)
            : 0;
        queryClient.setQueryData(["notifications", "unread-count"], { unread });
        queryClient.invalidateQueries({ queryKey: ["notifications"] });
        return;
      }

      if (event.startsWith("notification.")) {
        queryClient.invalidateQueries({ queryKey: ["notifications"] });
        queryClient.invalidateQueries({ queryKey: ["notifications", "unread-count"] });
        return;
      }

      if (
        event === "message.receive" ||
        event === "message.edit" ||
        event === "message.delete" ||
        event === "chat.created" ||
        event === "chat.updated"
      ) {
        queryClient.invalidateQueries({ queryKey: ["chats"] });
        const chatId =
          data && typeof data === "object" && "chat_id" in data
            ? String((data as { chat_id?: string }).chat_id ?? "")
            : "";
        if (chatId) {
          queryClient.invalidateQueries({ queryKey: ["messages", chatId] });
        }
      }
    });

    return remove;
  }, [isAuthenticated, addHandler, queryClient]);

  useEffect(() => {
    if (!isAuthenticated) return;
    return subscribeConnection((connected) => {
      if (!connected) return;
      queryClient.invalidateQueries({ queryKey: ["notifications"] });
      queryClient.invalidateQueries({ queryKey: ["notifications", "unread-count"] });
    });
  }, [isAuthenticated, queryClient]);

  return null;
}
