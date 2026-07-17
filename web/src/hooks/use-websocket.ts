"use client";

import { useEffect, useRef, useCallback, useState } from "react";
import { useAuthStore } from "@/stores/auth-store";

const WS_URL = process.env.NEXT_PUBLIC_WS_URL || "ws://localhost:8000";
const MAX_BACKOFF = 30_000;

type MessageHandler = (event: string, data: unknown) => void;

export function useWebSocket() {
  const wsRef = useRef<WebSocket | null>(null);
  const reconnectTimeout = useRef<ReturnType<typeof setTimeout> | undefined>(undefined);
  const backoffRef = useRef(1000);
  const handlersRef = useRef<MessageHandler[]>([]);
  const [connected, setConnected] = useState(false);
  const { isAuthenticated } = useAuthStore();

  const connect = useCallback(() => {
    if (!isAuthenticated) return;

    try {
      const ws = new WebSocket(`${WS_URL}/ws`);
      wsRef.current = ws;

      ws.onopen = () => {
        setConnected(true);
        backoffRef.current = 1000;
      };

      ws.onmessage = (event) => {
        try {
          const msg = JSON.parse(event.data);
          handlersRef.current.forEach((handler) => handler(msg.event, msg.data));
        } catch {}
      };

      ws.onclose = () => {
        setConnected(false);
        if (!isAuthenticated) return;
        reconnectTimeout.current = setTimeout(() => {
          backoffRef.current = Math.min(backoffRef.current * 2, MAX_BACKOFF);
          connect();
        }, backoffRef.current);
      };

      ws.onerror = () => {
        ws.close();
      };
    } catch {}
  }, [isAuthenticated]);

  useEffect(() => {
    connect();
    return () => {
      if (reconnectTimeout.current) clearTimeout(reconnectTimeout.current);
      wsRef.current?.close();
    };
  }, [connect]);

  const subscribe = useCallback((channel: string) => {
    wsRef.current?.send(JSON.stringify({ event: "subscribe:post", postId: channel }));
  }, []);

  const unsubscribe = useCallback((channel: string) => {
    wsRef.current?.send(JSON.stringify({ event: "unsubscribe:post", postId: channel }));
  }, []);

  const addHandler = useCallback((handler: MessageHandler) => {
    handlersRef.current.push(handler);
    return () => {
      handlersRef.current = handlersRef.current.filter((h) => h !== handler);
    };
  }, []);

  const send = useCallback((event: string, data: unknown) => {
    wsRef.current?.send(JSON.stringify({ event, ...(data as Record<string, unknown>) }));
  }, []);

  return { connected, subscribe, unsubscribe, addHandler, send };
}
