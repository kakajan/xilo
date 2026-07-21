"use client";

import { useEffect, useMemo, useState } from "react";
import { useRouter } from "next/navigation";
import { useMutation, useQuery } from "@tanstack/react-query";
import { createGroupChat } from "@/lib/api/chats";
import { listFollowing } from "@/lib/api/users";
import { useAuthStore } from "@/stores/auth-store";
import { canCreateGroup } from "@/lib/auth/permissions";
import { Avatar, AvatarFallback, AvatarImage } from "@/components/ui/avatar";
import { Button } from "@/components/ui/button";
import { Skeleton } from "@/components/ui/skeleton";
import { UsernameHandle } from "@/components/user/username-handle";
import { getInitials } from "@/lib/utils";

export default function NewGroupPage() {
  const router = useRouter();
  const { isAuthenticated, authChecked, user } = useAuthStore();
  const [step, setStep] = useState<"members" | "name">("members");
  const [selected, setSelected] = useState<Set<string>>(new Set());
  const [name, setName] = useState("");
  const [q, setQ] = useState("");
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (authChecked && !isAuthenticated) router.replace("/login");
  }, [authChecked, isAuthenticated, router]);

  useEffect(() => {
    if (authChecked && isAuthenticated && !canCreateGroup(user?.role)) {
      router.replace("/chat");
    }
  }, [authChecked, isAuthenticated, user?.role, router]);

  const { data: following, isLoading } = useQuery({
    queryKey: ["following-for-group", user?.username],
    enabled: !!user?.username,
    queryFn: async () => {
      const res = await listFollowing(user!.username, undefined, 100);
      return (res.data ?? []).filter((u) => u.id !== user?.id);
    },
  });

  const filtered = useMemo(() => {
    const needle = q.trim().toLowerCase().replace(/^@/, "");
    if (!needle) return following ?? [];
    return (following ?? []).filter((c) => {
      const display = (c.display_name || "").toLowerCase();
      const username = (c.username || "").toLowerCase();
      return display.includes(needle) || username.includes(needle);
    });
  }, [following, q]);

  const createMut = useMutation({
    mutationFn: () => createGroupChat(name.trim(), Array.from(selected)),
    onSuccess: (chat) => router.replace(`/chat/${chat.id}`),
    onError: () => setError("ایجاد گروه ناموفق بود"),
  });

  function toggle(id: string) {
    setSelected((prev) => {
      const next = new Set(prev);
      if (next.has(id)) next.delete(id);
      else next.add(id);
      return next;
    });
  }

  if (!authChecked || !isAuthenticated) {
    return <Skeleton className="h-64 w-full" />;
  }

  return (
    <div className="mx-auto w-full max-w-xl">
      <header className="mb-4 flex items-center justify-between gap-3">
        <h1 className="text-xl font-bold">
          {step === "name" ? "نام گروه" : "گروه جدید"}
        </h1>
        <Button variant="ghost" onClick={() => router.back()}>
          بازگشت
        </Button>
      </header>

      {error ? (
        <p className="mb-3 rounded-xl bg-destructive/10 px-3 py-2 text-sm text-destructive">
          {error}
        </p>
      ) : null}

      {step === "members" ? (
        <>
          <input
            value={q}
            onChange={(e) => setQ(e.target.value)}
            placeholder="جستجوی مخاطب…"
            className="mb-4 min-h-11 w-full rounded-xl border bg-background px-4 text-sm"
          />
          {isLoading ? (
            <Skeleton className="h-40 w-full" />
          ) : (
            <ul className="mb-4 divide-y divide-border rounded-xl border">
              {filtered.map((contact) => {
                const checked = selected.has(contact.id);
                return (
                  <li key={contact.id}>
                    <button
                      type="button"
                      onClick={() => toggle(contact.id)}
                      className="flex w-full min-h-14 items-center gap-3 px-3 py-2 text-start hover:bg-muted/50"
                    >
                      <input type="checkbox" readOnly checked={checked} className="shrink-0" />
                      <Avatar className="h-10 w-10 shrink-0">
                        {contact.avatar_url ? (
                          <AvatarImage src={contact.avatar_url} alt="" />
                        ) : null}
                        <AvatarFallback>
                          {getInitials(contact.display_name || contact.username)}
                        </AvatarFallback>
                      </Avatar>
                      <div className="min-w-0 flex-1">
                        <p className="truncate font-semibold">
                          {contact.display_name || contact.username}
                        </p>
                        <UsernameHandle username={contact.username} className="text-sm" />
                      </div>
                    </button>
                  </li>
                );
              })}
            </ul>
          )}
          <Button
            className="w-full min-h-11"
            disabled={selected.size === 0}
            onClick={() => setStep("name")}
          >
            ادامه
          </Button>
        </>
      ) : (
        <>
          <input
            value={name}
            onChange={(e) => setName(e.target.value)}
            placeholder="نام گروه را وارد کنید"
            className="mb-3 min-h-11 w-full rounded-xl border bg-background px-4 text-sm"
          />
          <p className="mb-4 text-sm text-muted-foreground">{selected.size} عضو انتخاب شده</p>
          <div className="flex gap-2">
            <Button variant="outline" className="min-h-11" onClick={() => setStep("members")}>
              بازگشت
            </Button>
            <Button
              className="min-h-11 flex-1"
              disabled={!name.trim() || createMut.isPending}
              onClick={() => {
                setError(null);
                createMut.mutate();
              }}
            >
              ایجاد گروه
            </Button>
          </div>
        </>
      )}
    </div>
  );
}
