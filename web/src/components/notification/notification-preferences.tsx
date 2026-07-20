"use client";

import { useState, useEffect } from "react";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { useTranslations } from "next-intl";
import { apiFetch } from "@/lib/api-client";
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
  new_message_web: boolean;
  new_message_push: boolean;
}

const defaults: NotificationPrefs = {
  comment_reply_web: true,
  comment_reply_email: false,
  comment_mention_web: true,
  comment_mention_email: false,
  post_reaction_web: true,
  new_follower_web: true,
  post_published_web: true,
  post_published_email: false,
  new_message_web: true,
  new_message_push: true,
};

export function NotificationPreferences() {
  const t = useTranslations("notification.prefs");
  const queryClient = useQueryClient();
  const [prefs, setPrefs] = useState<NotificationPrefs>(defaults);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    async function load() {
      try {
        const res = await apiFetch<NotificationPrefs>("/api/notifications/preferences");
        setPrefs({ ...defaults, ...res });
      } catch {
        // keep defaults
      }
      setLoading(false);
    }
    void load();
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
    { key: "comment_reply_web", label: t("commentReply") },
    { key: "new_follower_web", label: t("newFollower") },
    { key: "post_published_web", label: t("postPublished") },
    { key: "new_message_web", label: t("newMessage") },
    { key: "new_message_push", label: t("newMessagePush") },
    { key: "post_reaction_web", label: t("postReaction") },
    { key: "comment_mention_web", label: t("mention") },
    { key: "comment_reply_email", label: t("commentReplyEmail") },
    { key: "post_published_email", label: t("postPublishedEmail") },
  ];

  return (
    <div className="space-y-1">
      {items.map(({ key, label }) => (
        <button
          key={key}
          type="button"
          onClick={() => toggle(key)}
          className="flex w-full items-center justify-between rounded-lg px-3 py-2 transition-colors hover:bg-accent"
        >
          <span className="min-w-0 text-sm">{label}</span>
          <span
            className={`relative h-5 w-9 shrink-0 rounded-full transition-colors ${
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
