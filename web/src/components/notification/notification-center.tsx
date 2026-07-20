"use client";

import { useEffect } from "react";
import { useRouter } from "next/navigation";
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { useTranslations } from "next-intl";
import { useWebSocket } from "@/hooks/use-websocket";
import { useAuthStore } from "@/stores/auth-store";
import { apiFetch } from "@/lib/api-client";
import { Button } from "@/components/ui/button";
import { Skeleton } from "@/components/ui/skeleton";
import { TimeLabel } from "@/components/user/username-handle";
import { useFormatDate } from "@/hooks/use-format-date";
import type { Notification } from "@/types/notification";

function parseData(data: Notification["data"]): Record<string, unknown> {
  if (!data) return {};
  if (typeof data === "string") {
    try {
      return JSON.parse(data) as Record<string, unknown>;
    } catch {
      return {};
    }
  }
  return data;
}

function hrefForNotification(n: Notification): string | null {
  const data = parseData(n.data);
  if (typeof data.link === "string" && data.link) {
    const link = data.link;
    if (link.startsWith("/") && !link.startsWith("//")) return link;
    if (typeof window !== "undefined") {
      try {
        const url = new URL(link);
        if (url.origin === window.location.origin) {
          return `${url.pathname}${url.search}${url.hash}`;
        }
      } catch {
        // ignore invalid URL
      }
    }
  }
  if (typeof data.slug === "string" && data.slug) return `/${data.slug}`;
  if (typeof data.chat_id === "string" && data.chat_id) return `/chat/${data.chat_id}`;
  if (typeof data.follower_id === "string" && data.follower_id) {
    if (typeof data.username === "string" && data.username) return `/${data.username}`;
  }
  return null;
}

export function NotificationCenter() {
  const t = useTranslations("notification");
  const router = useRouter();
  const queryClient = useQueryClient();
  const { isAuthenticated } = useAuthStore();
  const { addHandler } = useWebSocket();
  const formatDate = useFormatDate();

  const { data, isLoading } = useQuery({
    queryKey: ["notifications"],
    queryFn: () => apiFetch<{ data: Notification[] }>("/api/notifications"),
    enabled: isAuthenticated,
    refetchInterval: 60_000,
  });

  const markRead = useMutation({
    mutationFn: (id: string) => apiFetch(`/api/notifications/${id}/read`, { method: "POST" }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["notifications"] });
      queryClient.invalidateQueries({ queryKey: ["notifications", "unread-count"] });
    },
  });

  const markAll = useMutation({
    mutationFn: () => apiFetch("/api/notifications/read-all", { method: "POST" }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["notifications"] });
      queryClient.invalidateQueries({ queryKey: ["notifications", "unread-count"] });
    },
  });

  useEffect(() => {
    if (!isAuthenticated) return;
    const remove = addHandler((event) => {
      if (event.startsWith("notification.")) {
        queryClient.invalidateQueries({ queryKey: ["notifications"] });
        queryClient.invalidateQueries({ queryKey: ["notifications", "unread-count"] });
      }
    });
    return remove;
  }, [isAuthenticated, addHandler, queryClient]);

  const notifications = data?.data || [];
  const unreadCount = notifications.filter((n) => !n.is_read).length;

  if (!isAuthenticated) return null;

  return (
    <div className="space-y-2">
      <div className="mb-3 flex items-center justify-between">
        <h2 className="text-lg font-semibold">{t("title")}</h2>
        {unreadCount > 0 && (
          <Button variant="ghost" size="sm" onClick={() => markAll.mutate()}>
            {t("markAllRead")}
          </Button>
        )}
      </div>

      {isLoading ? (
        <div className="space-y-3">
          {[1, 2, 3].map((i) => (
            <div key={i} className="flex gap-3">
              <Skeleton className="h-8 w-8 rounded-full" />
              <div className="flex-1">
                <Skeleton className="h-4 w-3/4" />
                <Skeleton className="mt-1 h-3 w-1/2" />
              </div>
            </div>
          ))}
        </div>
      ) : notifications.length === 0 ? (
        <p className="py-8 text-center text-muted-foreground">{t("empty")}</p>
      ) : (
        <div className="space-y-1">
          {notifications.map((n) => (
            <button
              key={n.id}
              type="button"
              onClick={() => {
                markRead.mutate(n.id);
                const href = hrefForNotification(n);
                if (href) router.push(href);
              }}
              className={`w-full rounded-lg px-3 py-2 text-start transition-colors hover:bg-accent ${
                !n.is_read ? "bg-accent/50" : ""
              }`}
            >
              <p className="text-sm font-medium">{n.title}</p>
              {n.body && <p className="mt-0.5 text-xs text-muted-foreground">{n.body}</p>}
              <TimeLabel className="mt-1 text-xs text-muted-foreground">
                {formatDate(n.created_at)}
              </TimeLabel>
            </button>
          ))}
        </div>
      )}
    </div>
  );
}
