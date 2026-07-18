"use client";

import { useEffect } from "react";
import Link from "next/link";
import { useParams, useRouter } from "next/navigation";
import { useQuery } from "@tanstack/react-query";
import { ArrowRight } from "lucide-react";
import { getChat, listChats } from "@/lib/api/chats";
import { useAuthStore } from "@/stores/auth-store";
import { Avatar, AvatarFallback, AvatarImage } from "@/components/ui/avatar";
import { Button } from "@/components/ui/button";
import { Skeleton } from "@/components/ui/skeleton";
import { getInitials } from "@/lib/utils";

export default function ContactDetailPage() {
  const { id } = useParams<{ id: string }>();
  const router = useRouter();
  const { user, isAuthenticated, isLoading: authLoading, authChecked } = useAuthStore();

  useEffect(() => {
    if (authChecked && !isAuthenticated) router.replace("/login");
  }, [authChecked, isAuthenticated, router]);

  const {
    data: chats,
    isLoading: listLoading,
    isFetched: listFetched,
  } = useQuery({
    queryKey: ["chats"],
    enabled: isAuthenticated,
    queryFn: async () => (await listChats()).data ?? [],
  });

  const chatFromList = chats?.find((c) => c.id === id);

  const {
    data: chatFromApi,
    isLoading: detailLoading,
    isError: detailError,
    isFetched: detailFetched,
  } = useQuery({
    queryKey: ["chat", id],
    enabled: isAuthenticated && listFetched && !chatFromList && Boolean(id),
    queryFn: () => getChat(id),
    retry: false,
  });

  const chat = chatFromList ?? chatFromApi;
  const other = chat?.members?.find((m) => m.user_id !== user?.id);
  const waitingForFallback = listFetched && !chatFromList && detailLoading;
  const notFound =
    listFetched &&
    !chatFromList &&
    (detailError || (detailFetched && !chatFromApi));

  if (authLoading || listLoading || waitingForFallback) {
    return <Skeleton className="h-40 w-full" />;
  }

  return (
    <div className="mx-auto max-w-md">
      <div className="mb-6 flex items-center gap-2">
        <Button
          variant="ghost"
          size="icon"
          className="min-h-11 min-w-11 shrink-0"
          onClick={() => router.back()}
        >
          <ArrowRight className="h-5 w-5" />
        </Button>
        <h1 className="min-w-0 text-xl font-bold">جزئیات مخاطب</h1>
      </div>

      {notFound || !chat || !other ? (
        <p className="py-12 text-center text-muted-foreground">مخاطب یافت نشد</p>
      ) : (
        <div className="flex flex-col items-center text-center">
          <Avatar className="mb-4 h-24 w-24">
            {other.avatar_url ? <AvatarImage src={other.avatar_url} alt="" /> : null}
            <AvatarFallback className="text-2xl">
              {getInitials(other.display_name || other.username || "?")}
            </AvatarFallback>
          </Avatar>
          <p className="text-xl font-bold">{other.display_name || chat.name || "گفتگو"}</p>
          {other.username ? (
            <Link href={`/${other.username}`} className="text-primary hover:underline">
              @{other.username}
            </Link>
          ) : null}
          <p className="mt-2 text-sm text-muted-foreground">
            نقش: {other.role || chat.current_role}
          </p>
        </div>
      )}
    </div>
  );
}
