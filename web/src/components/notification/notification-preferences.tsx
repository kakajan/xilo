"use client";

import { useState, useEffect } from "react";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { apiFetch } from "@/lib/api-client";
import { Button } from "@/components/ui/button";
import { Skeleton } from "@/components/ui/skeleton";

interface NotificationPrefs {
  comment_reply_web: boolean;
  comment_reply_email: boolean;
  comment_mention_web: boolean;
  comment_mention_email: boolean;
  post_reaction_web: boolean;
  new_follower_web: boolean;
  post_published_web: boolean;
  post_published_email: boolean;
}

export function NotificationPreferences() {
  const queryClient = useQueryClient();
  const [prefs, setPrefs] = useState<NotificationPrefs>({
    comment_reply_web: true,
    comment_reply_email: false,
    comment_mention_web: true,
    comment_mention_email: false,
    post_reaction_web: true,
    new_follower_web: true,
    post_published_web: false,
    post_published_email: false,
  });
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    async function load() {
      try {
        const res = await apiFetch<NotificationPrefs>("/api/notifications/preferences");
        setPrefs(res);
      } catch {}
      setLoading(false);
    }
    load();
  }, []);

  const mutation = useMutation({
    mutationFn: (data: Partial<NotificationPrefs>) =>
      apiFetch("/api/notifications/preferences", {
        method: "PATCH",
        body: JSON.stringify(data),
      }),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ["notif-prefs"] }),
  });

  const toggle = (key: keyof NotificationPrefs) => {
    const next = !prefs[key];
    setPrefs((p) => ({ ...p, [key]: next }));
    mutation.mutate({ [key]: next });
  };

  if (loading) return <Skeleton className="h-48 w-full" />;

  const items: { key: keyof NotificationPrefs; label: string }[] = [
    { key: "comment_reply_web", label: "Comment replies (web)" },
    { key: "comment_reply_email", label: "Comment replies (email)" },
    { key: "comment_mention_web", label: "Mentions (web)" },
    { key: "comment_mention_email", label: "Mentions (email)" },
    { key: "post_reaction_web", label: "Post reactions" },
    { key: "new_follower_web", label: "New followers" },
    { key: "post_published_web", label: "New posts from authors" },
    { key: "post_published_email", label: "New posts (email digest)" },
  ];

  return (
    <div className="space-y-1">
      {items.map(({ key, label }) => (
        <button
          key={key}
          onClick={() => toggle(key)}
          className="w-full flex items-center justify-between px-3 py-2 rounded-lg hover:bg-accent transition-colors"
        >
          <span className="text-sm">{label}</span>
          <span
            className={`relative w-9 h-5 rounded-full transition-colors ${
              prefs[key] ? "bg-primary" : "bg-muted"
            }`}
          >
            <span
              className={`absolute top-0.5 h-4 w-4 rounded-full bg-white transition-transform ${
                prefs[key] ? "translate-x-4" : "translate-x-0.5"
              }`}
            />
          </span>
        </button>
      ))}
    </div>
  );
}
