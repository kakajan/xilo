"use client";

import { useEffect } from "react";
import { useRouter } from "next/navigation";
import { useWebSocket } from "@/hooks/use-websocket";
import { useAuthStore } from "@/stores/auth-store";
import type { Notification } from "@/types/notification";

export function GlobalWebSocketListener() {
  const { addHandler } = useWebSocket();
  const { isAuthenticated } = useAuthStore();
  const router = useRouter();

  useEffect(() => {
    if (!isAuthenticated) return;

    const remove = addHandler((event, data) => {
      switch (event) {
        case "notification.created": {
          const notif = data as Notification;
          if (notif.type === "comment_reply") {
            router.push(`/notifications`);
          }
          break;
        }
        case "comment.created":
        case "comment.reaction":
          break;
      }
    });

    return remove;
  }, [isAuthenticated, addHandler, router]);

  return null;
}
