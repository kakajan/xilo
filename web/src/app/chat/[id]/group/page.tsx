"use client";

import { useEffect, useState } from "react";
import { useParams, useRouter } from "next/navigation";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import {
  createChatInviteLink,
  getChat,
  leaveChat,
  listChatPins,
  removeChatMember,
  revokeChatInviteLink,
  unpinChatMessage,
  updateChat,
  updateChatMemberRole,
} from "@/lib/api/chats";
import { useAuthStore } from "@/stores/auth-store";
import { Avatar, AvatarFallback, AvatarImage } from "@/components/ui/avatar";
import { Button } from "@/components/ui/button";
import { Skeleton } from "@/components/ui/skeleton";
import { UsernameHandle } from "@/components/user/username-handle";
import { getInitials } from "@/lib/utils";

export default function GroupInfoPage() {
  const params = useParams<{ id: string }>();
  const chatId = params.id;
  const router = useRouter();
  const queryClient = useQueryClient();
  const { isAuthenticated, authChecked, user } = useAuthStore();
  const [editName, setEditName] = useState("");
  const [inviteToken, setInviteToken] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (authChecked && !isAuthenticated) router.replace("/login");
  }, [authChecked, isAuthenticated, router]);

  const { data: chat, isLoading } = useQuery({
    queryKey: ["chat", chatId],
    enabled: isAuthenticated && !!chatId,
    queryFn: () => getChat(chatId),
  });

  useEffect(() => {
    if (chat?.name) setEditName(chat.name);
  }, [chat?.name]);

  const { data: pins } = useQuery({
    queryKey: ["chat-pins", chatId],
    enabled: isAuthenticated && !!chatId,
    queryFn: () => listChatPins(chatId),
  });

  const isAdmin = chat?.current_role === "admin";

  const saveNameMut = useMutation({
    mutationFn: () => updateChat(chatId, { name: editName.trim() }),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ["chat", chatId] }),
    onError: () => setError("ذخیره نام ناموفق بود"),
  });

  const muteMut = useMutation({
    mutationFn: (muted: boolean) => updateChat(chatId, { is_muted: muted }),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ["chat", chatId] }),
  });

  const roleMut = useMutation({
    mutationFn: ({ userId, role }: { userId: string; role: "admin" | "member" }) =>
      updateChatMemberRole(chatId, userId, role),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ["chat", chatId] }),
    onError: () => setError("تغییر نقش ناموفق بود"),
  });

  const removeMut = useMutation({
    mutationFn: (userId: string) => removeChatMember(chatId, userId),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ["chat", chatId] }),
    onError: () => setError("حذف عضو ناموفق بود"),
  });

  const inviteMut = useMutation({
    mutationFn: () => createChatInviteLink(chatId),
    onSuccess: (link) => setInviteToken(link.token),
    onError: () => setError("ساخت لینک دعوت ناموفق بود"),
  });

  const revokeMut = useMutation({
    mutationFn: (token: string) => revokeChatInviteLink(chatId, token),
    onSuccess: () => setInviteToken(null),
  });

  const unpinMut = useMutation({
    mutationFn: (messageId: string) => unpinChatMessage(chatId, messageId),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ["chat-pins", chatId] }),
  });

  const leaveMut = useMutation({
    mutationFn: () => leaveChat(chatId),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ["chats"] });
      router.replace("/chat");
    },
    onError: () => setError("ترک گروه ناموفق بود"),
  });

  if (!authChecked || !isAuthenticated || isLoading || !chat) {
    return <Skeleton className="h-64 w-full" />;
  }

  if (chat.type !== "group") {
    router.replace(`/chat/${chatId}/contact`);
    return null;
  }

  return (
    <div className="mx-auto w-full max-w-xl pb-16">
      <header className="mb-4 flex items-center justify-between gap-3">
        <h1 className="text-xl font-bold">اطلاعات گروه</h1>
        <Button variant="ghost" onClick={() => router.back()}>
          بازگشت
        </Button>
      </header>

      {error ? (
        <p className="mb-3 rounded-xl bg-destructive/10 px-3 py-2 text-sm text-destructive">
          {error}
        </p>
      ) : null}

      <div className="mb-6 flex flex-col items-center gap-3">
        <Avatar className="h-20 w-20">
          {chat.avatar_url ? <AvatarImage src={chat.avatar_url} alt="" /> : null}
          <AvatarFallback>{getInitials(chat.name || "گروه")}</AvatarFallback>
        </Avatar>
        {isAdmin ? (
          <div className="flex w-full flex-col gap-2">
            <input
              value={editName}
              onChange={(e) => setEditName(e.target.value)}
              className="min-h-11 w-full rounded-xl border bg-background px-4 text-sm"
            />
            <Button
              onClick={() => saveNameMut.mutate()}
              disabled={!editName.trim() || saveNameMut.isPending}
            >
              ذخیره نام
            </Button>
          </div>
        ) : (
          <p className="text-lg font-bold">{chat.name}</p>
        )}
        <p className="text-sm text-muted-foreground">{chat.members?.length ?? 0} عضو</p>
      </div>

      <label className="mb-4 flex items-center justify-between gap-3 rounded-xl border px-4 py-3">
        <span>بی‌صدا</span>
        <input
          type="checkbox"
          checked={!!chat.is_muted}
          onChange={(e) => muteMut.mutate(e.target.checked)}
        />
      </label>

      {isAdmin ? (
        <section className="mb-6 rounded-xl border p-4">
          <h2 className="mb-2 font-semibold">لینک دعوت</h2>
          {inviteToken ? (
            <div className="space-y-2">
              <code className="block break-all rounded-lg bg-muted px-3 py-2 text-xs">
                {inviteToken}
              </code>
              <Button
                variant="outline"
                className="w-full"
                onClick={() => revokeMut.mutate(inviteToken)}
              >
                ابطال لینک
              </Button>
            </div>
          ) : (
            <Button className="w-full" onClick={() => inviteMut.mutate()}>
              ساخت لینک دعوت
            </Button>
          )}
        </section>
      ) : null}

      {(pins?.length ?? 0) > 0 ? (
        <section className="mb-6">
          <h2 className="mb-2 font-semibold">پیام‌های سنجاق‌شده</h2>
          <ul className="space-y-2">
            {pins?.map((pin) => (
              <li
                key={pin.message_id}
                className="flex items-center gap-2 rounded-xl border px-3 py-2 text-sm"
              >
                <span className="min-w-0 flex-1 truncate">
                  {pin.content || "پیام سنجاق‌شده"}
                </span>
                {isAdmin ? (
                  <Button
                    variant="ghost"
                    size="sm"
                    onClick={() => unpinMut.mutate(pin.message_id)}
                  >
                    برداشتن
                  </Button>
                ) : null}
              </li>
            ))}
          </ul>
        </section>
      ) : null}

      <section className="mb-6">
        <h2 className="mb-2 font-semibold">اعضا</h2>
        <ul className="divide-y divide-border rounded-xl border">
          {(chat.members ?? []).map((member) => (
            <li key={member.user_id} className="flex items-center gap-3 px-3 py-3">
              <Avatar className="h-10 w-10 shrink-0">
                {member.avatar_url ? <AvatarImage src={member.avatar_url} alt="" /> : null}
                <AvatarFallback>
                  {getInitials(member.display_name || member.username)}
                </AvatarFallback>
              </Avatar>
              <div className="min-w-0 flex-1">
                <p className="truncate font-semibold">
                  {member.display_name || member.username}
                </p>
                <UsernameHandle username={member.username} className="text-sm" />
                {member.role === "admin" ? (
                  <p className="text-xs text-primary">ادمین گروه</p>
                ) : null}
              </div>
              {isAdmin && member.user_id !== user?.id ? (
                <div className="flex shrink-0 flex-col gap-1">
                  <Button
                    variant="ghost"
                    size="sm"
                    onClick={() =>
                      roleMut.mutate({
                        userId: member.user_id,
                        role: member.role === "admin" ? "member" : "admin",
                      })
                    }
                  >
                    {member.role === "admin" ? "حذف ادمین" : "ارتقا"}
                  </Button>
                  <Button
                    variant="ghost"
                    size="sm"
                    onClick={() => removeMut.mutate(member.user_id)}
                  >
                    حذف
                  </Button>
                </div>
              ) : null}
            </li>
          ))}
        </ul>
      </section>

      <Button
        variant="outline"
        className="w-full min-h-11 text-destructive"
        onClick={() => leaveMut.mutate()}
        disabled={leaveMut.isPending}
      >
        ترک گروه
      </Button>
    </div>
  );
}
