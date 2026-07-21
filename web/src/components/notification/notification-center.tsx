"use client";

import { useEffect } from "react";
import { useRouter } from "next/navigation";
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { useTranslations } from "next-intl";
import { useWebSocket } from "@/hooks/use-websocket";
import { useAuthStore } from "@/stores/auth-store";
import { apiFetch } from "@/lib/api-client";
import { hrefForNotification } from "@/lib/notification-href";
import { Button } from "@/components/ui/button";
import { Skeleton } from "@/components/ui/skeleton";
import { TimeLabel } from "@/components/user/username-handle";
import { useFormatDate } from "@/hooks/use-format-date";
import type { Notification } from "@/types/notification";

function titleForType(
  n: Notification,
  t: (key: "types.postComment" | "types.commentReply" | "types.newFollower" | "types.postPublished" | "types.newMessage") => string
): string {
  switch (n.type) {
    case "post_comment":
      return t("types.postComment");
    case "comment_reply":
      return t("types.commentReply");
    case "new_follower":
      return t("types.newFollower");
    case "post_published":
      return t("types.postPublished");
    case "new_message":
      return t("types.newMessage");
    default:
      return n.title;
  }
}

function notificationData(n: Notification): Record<string, unknown> {
  if (n.data && typeof n.data === "object" && !Array.isArray(n.data)) {
    return n.data as Record<string, unknown>;
  }
  return {};
}

function asDataString(data: Record<string, unknown>, key: string): string | undefined {
  const value = data[key];
  return typeof value === "string" && value.trim() ? value.trim() : undefined;
}

function actorLabel(data: Record<string, unknown>, type: string): string {
  if (type === "new_message") {
    const displayName = asDataString(data, "sender_display_name");
    const username = asDataString(data, "sender_username");
    return displayName || (username ? `@${username}` : "");
  }
  if (
    type === "post_comment" ||
    type === "comment_reply" ||
    type === "comment_mention"
  ) {
    const displayName = asDataString(data, "author_display_name");
    const username = asDataString(data, "author_username");
    return displayName || (username ? `@${username}` : "");
  }
  return "";
}

/** Template bodies only — user content (comments, messages, post titles) stays as API text. */
function bodyForType(
  n: Notification,
  t: (key: "bodies.newFollower" | "bodies.newFollowerAnonymous", values?: { name: string }) => string
): string {
  switch (n.type) {
    case "new_follower": {
      const data = notificationData(n);
      const displayName = asDataString(data, "follower_display_name");
      const username = asDataString(data, "follower_username") ?? asDataString(data, "username");
      const name = displayName || (username ? `@${username}` : "");
      if (name) return t("bodies.newFollower", { name });
      return t("bodies.newFollowerAnonymous");
    }
    case "new_message":
    case "post_comment":
    case "comment_reply":
    case "comment_mention": {
      const name = actorLabel(notificationData(n), n.type);
      const content = (n.body || "").trim();
      if (name && content) return `${name}: ${content}`;
      if (name) return name;
      return content;
    }
    default:
      return n.body;
  }
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
          {notifications.map((n) => {
            const body = bodyForType(n, t);
            return (
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
                <p className="text-sm font-medium">{titleForType(n, t)}</p>
                {body && <p className="mt-0.5 text-xs text-muted-foreground">{body}</p>}
                <TimeLabel className="mt-1 text-xs text-muted-foreground">
                  {formatDate(n.created_at, { withTime: true })}
                </TimeLabel>
              </button>
            );
          })}
        </div>
      )}
    </div>
  );
}
