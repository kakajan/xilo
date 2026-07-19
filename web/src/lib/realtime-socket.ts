import { getAccessToken } from "./auth-tokens";

const WS_URL = process.env.NEXT_PUBLIC_WS_URL || "ws://localhost:8000";
const PROTOCOL_VERSION = "1";
const MAX_BACKOFF = 30_000;

export type RealtimeHandler = (event: string, data: unknown) => void;

type ConnectionListener = (connected: boolean) => void;

let socket: WebSocket | null = null;
let reconnectTimer: ReturnType<typeof setTimeout> | undefined;
let backoffMs = 1000;
let shouldConnect = false;
let connected = false;

const handlers = new Set<RealtimeHandler>();
const connectionListeners = new Set<ConnectionListener>();
const joinedChats = new Set<string>();
const joinedChatRefs = new Map<string, number>();
const subscribedPosts = new Set<string>();
const seenEventIds = new Set<string>();
const SEEN_EVENT_LIMIT = 500;

function setConnected(next: boolean) {
  if (connected === next) return;
  connected = next;
  connectionListeners.forEach((listener) => listener(next));
}

function wsEndpoint(token: string): string {
  const base = WS_URL.replace(/\/$/, "");
  const url = base.endsWith("/ws") ? base : `${base}/ws`;
  const separator = url.includes("?") ? "&" : "?";
  return `${url}${separator}token=${encodeURIComponent(token)}`;
}

function requestId(): string {
  if (typeof crypto !== "undefined" && "randomUUID" in crypto) {
    return crypto.randomUUID();
  }
  return `req_${Date.now()}_${Math.random().toString(16).slice(2)}`;
}

export function buildRealtimeEnvelope(
  event: string,
  data?: Record<string, unknown>,
  operationKey?: string
): string {
  return JSON.stringify({
    version: PROTOCOL_VERSION,
    event,
    request_id: requestId(),
    ...(operationKey ? { operation_key: operationKey } : {}),
    ...(data ? { data } : {}),
  });
}

function sendRaw(payload: string) {
  if (socket?.readyState === WebSocket.OPEN) {
    socket.send(payload);
  }
}

function sendEnvelope(
  event: string,
  data?: Record<string, unknown>,
  operationKey?: string
) {
  sendRaw(buildRealtimeEnvelope(event, data, operationKey));
}

function rememberEventId(eventId: string | undefined) {
  if (!eventId) return false;
  if (seenEventIds.has(eventId)) return true;
  seenEventIds.add(eventId);
  if (seenEventIds.size > SEEN_EVENT_LIMIT) {
    const first = seenEventIds.values().next().value;
    if (first !== undefined) seenEventIds.delete(first);
  }
  return false;
}

function resubscribeAll() {
  joinedChats.forEach((chatId) => {
    sendEnvelope("chat.join", { chat_id: chatId });
  });
  subscribedPosts.forEach((postId) => {
    sendRaw(JSON.stringify({ event: "subscribe:post", postId }));
  });
}

function scheduleReconnect() {
  if (!shouldConnect || reconnectTimer) return;
  reconnectTimer = setTimeout(() => {
    reconnectTimer = undefined;
    backoffMs = Math.min(backoffMs * 2, MAX_BACKOFF);
    openSocket();
  }, backoffMs);
}

function openSocket() {
  if (typeof window === "undefined" || !shouldConnect) return;
  if (
    socket &&
    (socket.readyState === WebSocket.OPEN ||
      socket.readyState === WebSocket.CONNECTING)
  ) {
    return;
  }

  const token = getAccessToken();
  if (!token) return;

  try {
    const ws = new WebSocket(wsEndpoint(token));
    socket = ws;

    ws.onopen = () => {
      backoffMs = 1000;
      setConnected(true);
      resubscribeAll();
    };

    ws.onmessage = (event) => {
      try {
        const msg = JSON.parse(event.data as string) as {
          event?: string;
          event_id?: string;
          data?: unknown;
        };
        if (!msg.event) return;
        if (rememberEventId(msg.event_id)) return;
        handlers.forEach((handler) => handler(msg.event!, msg.data));
      } catch {
        // Ignore malformed frames; keep the socket alive.
      }
    };

    ws.onclose = () => {
      socket = null;
      setConnected(false);
      if (shouldConnect) scheduleReconnect();
    };

    ws.onerror = () => {
      ws.close();
    };
  } catch {
    scheduleReconnect();
  }
}

export function connectRealtime() {
  shouldConnect = true;
  openSocket();
}

export function disconnectRealtime() {
  shouldConnect = false;
  if (reconnectTimer) {
    clearTimeout(reconnectTimer);
    reconnectTimer = undefined;
  }
  backoffMs = 1000;
  joinedChats.clear();
  joinedChatRefs.clear();
  subscribedPosts.clear();
  seenEventIds.clear();
  const current = socket;
  socket = null;
  setConnected(false);
  current?.close();
}

export function addRealtimeHandler(handler: RealtimeHandler): () => void {
  handlers.add(handler);
  return () => {
    handlers.delete(handler);
  };
}

export function subscribeConnection(
  listener: ConnectionListener
): () => void {
  connectionListeners.add(listener);
  listener(connected);
  return () => {
    connectionListeners.delete(listener);
  };
}

export function joinChat(chatId: string) {
  if (!chatId) return;
  const refs = (joinedChatRefs.get(chatId) ?? 0) + 1;
  joinedChatRefs.set(chatId, refs);
  if (refs === 1) {
    joinedChats.add(chatId);
    sendEnvelope("chat.join", { chat_id: chatId });
  }
}

export function leaveChat(chatId: string) {
  if (!chatId) return;
  const refs = (joinedChatRefs.get(chatId) ?? 0) - 1;
  if (refs > 0) {
    joinedChatRefs.set(chatId, refs);
    return;
  }
  joinedChatRefs.delete(chatId);
  if (!joinedChats.delete(chatId)) return;
  sendEnvelope("chat.leave", { chat_id: chatId });
}

export function subscribePost(postId: string) {
  if (!postId) return;
  if (subscribedPosts.has(postId)) return;
  subscribedPosts.add(postId);
  sendRaw(JSON.stringify({ event: "subscribe:post", postId }));
}

export function unsubscribePost(postId: string) {
  if (!postId) return;
  if (!subscribedPosts.delete(postId)) return;
  sendRaw(JSON.stringify({ event: "unsubscribe:post", postId }));
}

export function sendRealtime(event: string, data?: Record<string, unknown>) {
  sendEnvelope(event, data);
}

export function isRealtimeConnected() {
  return connected;
}
