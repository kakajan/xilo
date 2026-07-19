"use client";

import { useCallback, useEffect, useState } from "react";
import { useAuthStore } from "@/stores/auth-store";
import {
  addRealtimeHandler,
  connectRealtime,
  disconnectRealtime,
  joinChat as joinChatSocket,
  leaveChat as leaveChatSocket,
  sendRealtime,
  subscribeConnection,
  subscribePost,
  unsubscribePost,
  type RealtimeHandler,
} from "@/lib/realtime-socket";

export function useWebSocket() {
  const isAuthenticated = useAuthStore((s) => s.isAuthenticated);
  const [connected, setConnected] = useState(false);

  useEffect(() => {
    if (isAuthenticated) {
      connectRealtime();
    } else {
      disconnectRealtime();
    }
    return subscribeConnection(setConnected);
  }, [isAuthenticated]);

  const addHandler = useCallback((handler: RealtimeHandler) => {
    return addRealtimeHandler(handler);
  }, []);

  const joinChat = useCallback((chatId: string) => {
    joinChatSocket(chatId);
  }, []);

  const leaveChat = useCallback((chatId: string) => {
    leaveChatSocket(chatId);
  }, []);

  const subscribe = useCallback((channel: string) => {
    subscribePost(channel);
  }, []);

  const unsubscribe = useCallback((channel: string) => {
    unsubscribePost(channel);
  }, []);

  const send = useCallback((event: string, data: unknown) => {
    sendRealtime(event, data as Record<string, unknown> | undefined);
  }, []);

  return {
    connected,
    subscribe,
    unsubscribe,
    joinChat,
    leaveChat,
    addHandler,
    send,
  };
}
