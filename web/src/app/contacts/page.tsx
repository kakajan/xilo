"use client";

import { useEffect, useMemo, useState } from "react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { useMutation, useQuery } from "@tanstack/react-query";
import { MessageCircle, Search, User } from "lucide-react";
import { useTranslations } from "next-intl";
import { listContacts } from "@/lib/api/contacts";
import { createChat } from "@/lib/api/chats";
import { useAuthStore } from "@/stores/auth-store";
import { Avatar, AvatarFallback, AvatarImage } from "@/components/ui/avatar";
import { Button } from "@/components/ui/button";
import { Skeleton } from "@/components/ui/skeleton";
import { UsernameHandle } from "@/components/user/username-handle";
import { getInitials } from "@/lib/utils";
import type { ContactUser } from "@/types/chat";

export default function ContactsPage() {
  const t = useTranslations("common.contacts");
  const tNav = useTranslations("common.nav");
  const router = useRouter();
  const { isAuthenticated, authChecked } = useAuthStore();
  const [q, setQ] = useState("");
  const [messagingId, setMessagingId] = useState<string | null>(null);

  useEffect(() => {
    if (authChecked && !isAuthenticated) router.replace("/login");
  }, [authChecked, isAuthenticated, router]);

  const { data, isLoading } = useQuery({
    queryKey: ["contacts"],
    enabled: isAuthenticated,
    queryFn: async () => {
      const res = await listContacts();
      return res.data ?? [];
    },
  });

  const filtered = useMemo(() => {
    const needle = q.trim().toLowerCase().replace(/^@/, "");
    if (!needle) return data ?? [];
    return (data ?? []).filter((c) => {
      const name = (c.display_name || "").toLowerCase();
      const username = (c.username || "").toLowerCase();
      return name.includes(needle) || username.includes(needle);
    });
  }, [data, q]);

  const messageMut = useMutation({
    mutationFn: (contact: ContactUser) => createChat([contact.id], "direct"),
    onMutate: (contact) => setMessagingId(contact.id),
    onSuccess: (chat) => router.push(`/chat/${chat.id}`),
    onSettled: () => setMessagingId(null),
  });

  if (!authChecked || !isAuthenticated) {
    return <Skeleton className="h-64 w-full" />;
  }

  return (
    <div className="mx-auto w-full max-w-xl">
      <header className="mb-4">
        <h1 className="text-xl font-bold">{tNav("contacts")}</h1>
        <p className="mt-1 text-sm text-muted-foreground">{t("subtitle")}</p>
      </header>

      <div className="relative mb-4">
        <Search className="pointer-events-none absolute start-3 top-1/2 h-4 w-4 -translate-y-1/2 text-muted-foreground" />
        <input
          value={q}
          onChange={(e) => setQ(e.target.value)}
          placeholder={t("searchPlaceholder")}
          className="min-h-11 w-full rounded-full border bg-background pe-4 ps-10 text-sm"
        />
      </div>

      {isLoading ? (
        <Skeleton className="h-40 w-full" />
      ) : filtered.length === 0 ? (
        <div className="space-y-2 py-12 text-center text-muted-foreground">
          <p>{(data?.length ?? 0) === 0 ? t("empty") : t("emptySearch")}</p>
          {(data?.length ?? 0) === 0 && (
            <p className="text-xs">{t("syncHint")}</p>
          )}
        </div>
      ) : (
        <ul className="divide-y divide-border">
          {filtered.map((c) => (
            <li key={c.id} className="flex items-center gap-3 py-3">
              <Link href={`/${c.username}`} className="shrink-0">
                <Avatar className="h-12 w-12">
                  {c.avatar_url ? <AvatarImage src={c.avatar_url} alt="" /> : null}
                  <AvatarFallback>
                    {getInitials(c.display_name || c.username)}
                  </AvatarFallback>
                </Avatar>
              </Link>
              <div className="min-w-0 flex-1">
                <div className="flex min-w-0 flex-wrap items-center gap-2">
                  <Link
                    href={`/${c.username}`}
                    className="truncate font-semibold hover:underline"
                  >
                    {c.display_name || c.username}
                  </Link>
                  {c.from_contacts && (
                    <span className="shrink-0 rounded-full bg-primary/10 px-2 py-0.5 text-[11px] font-medium text-primary">
                      {t("fromContacts")}
                    </span>
                  )}
                </div>
                <UsernameHandle
                  username={c.username}
                  className="truncate text-sm text-muted-foreground"
                />
              </div>
              <div className="flex shrink-0 items-center gap-1">
                <Button
                  variant="outline"
                  size="icon"
                  className="min-h-11 min-w-11"
                  aria-label={t("message")}
                  disabled={messagingId === c.id && messageMut.isPending}
                  onClick={() => messageMut.mutate(c)}
                >
                  <MessageCircle className="h-4 w-4" />
                </Button>
                <Button
                  variant="ghost"
                  size="icon"
                  className="min-h-11 min-w-11"
                  aria-label={t("profile")}
                  asChild
                >
                  <Link href={`/${c.username}`}>
                    <User className="h-4 w-4" />
                  </Link>
                </Button>
              </div>
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}
