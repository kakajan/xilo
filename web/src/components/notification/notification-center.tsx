"use client";

import { useEffect } from "react";
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { Bell } from "lucide-react";
import { useWebSocket } from "@/hooks/use-websocket";
import { useAuthStore } from "@/stores/auth-store";
import { apiFetch } from "@/lib/api-client";
import { formatDate } from "@/lib/utils";
import { Button } from "@/components/ui/button";
import { Skeleton } from "@/components/ui/skeleton";
import type { Notification } from "@/types/notification";

export function NotificationCenter() {
  const queryClient = useQueryClient();
  const { isAuthenticated } = useAuthStore();
  const { addHandler } = useWebSocket();

  const { data, isLoading } = useQuery({
    queryKey: ["notifications"],
    queryFn: () => apiFetch<{ data: Notification[] }>("/api/notifications"),
    enabled: isAuthenticated,
    refetchInterval: 60_000,
  });

  const markRead = useMutation({
    mutationFn: (id: string) => apiFetch(`/api/notifications/${id}/read`, { method: "POST" }),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ["notifications"] }),
  });

  const markAll = useMutation({
    mutationFn: () => apiFetch("/api/notifications/read-all", { method: "POST" }),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ["notifications"] }),
  });

  useEffect(() => {
    if (!isAuthenticated) return;
    const remove = addHandler((event) => {
      if (event.startsWith("notification.")) {
        queryClient.invalidateQueries({ queryKey: ["notifications"] });
      }
    });
    return remove;
  }, [isAuthenticated, addHandler, queryClient]);

  const notifications = data?.data || [];
  const unreadCount = notifications.filter((n) => !n.is_read).length;

  if (!isAuthenticated) return null;

  return (
    <div className="space-y-2">
      <div className="flex items-center justify-between mb-3">
        <h2 className="text-lg font-semibold">Notifications</h2>
        {unreadCount > 0 && (
          <Button variant="ghost" size="sm" onClick={() => markAll.mutate()}>
            Mark all read
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
                <Skeleton className="h-3 w-1/2 mt-1" />
              </div>
            </div>
          ))}
        </div>
      ) : notifications.length === 0 ? (
        <p className="text-center text-muted-foreground py-8">No notifications yet</p>
      ) : (
        <div className="space-y-1">
          {notifications.map((n) => (
            <button
              key={n.id}
              onClick={() => markRead.mutate(n.id)}
              className={`w-full text-left px-3 py-2 rounded-lg transition-colors hover:bg-accent ${
                !n.is_read ? "bg-accent/50" : ""
              }`}
            >
              <p className="text-sm font-medium">{n.title}</p>
              {n.body && <p className="text-xs text-muted-foreground mt-0.5">{n.body}</p>}
              <p className="text-xs text-muted-foreground mt-1">{formatDate(n.created_at)}</p>
            </button>
          ))}
        </div>
      )}
    </div>
  );
}
