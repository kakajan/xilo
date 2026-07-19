"use client";

import { useEffect } from "react";
import { useRouter } from "next/navigation";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { ArrowRight, MonitorSmartphone } from "lucide-react";
import { listSessions, revokeSession } from "@/lib/api/sessions";
import { useAuthStore } from "@/stores/auth-store";
import { Button } from "@/components/ui/button";
import { Skeleton } from "@/components/ui/skeleton";
import { TimeLabel } from "@/components/user/username-handle";
import { useFormatDate } from "@/hooks/use-format-date";

export default function DevicesPage() {
  const router = useRouter();
  const { isAuthenticated, isLoading: authLoading, authChecked, logout } = useAuthStore();
  const queryClient = useQueryClient();
  const formatDate = useFormatDate();

  useEffect(() => {
    if (authChecked && !isAuthenticated) router.replace("/login");
  }, [authChecked, isAuthenticated, router]);

  const { data, isLoading } = useQuery({
    queryKey: ["sessions"],
    enabled: isAuthenticated,
    queryFn: listSessions,
  });

  const revokeMut = useMutation({
    mutationFn: (id: string) => revokeSession(id),
    onSuccess: async (_res, id) => {
      const session = data?.find((s) => s.id === id);
      if (session?.is_current) {
        await logout();
        return;
      }
      queryClient.invalidateQueries({ queryKey: ["sessions"] });
    },
  });

  if (authLoading || !isAuthenticated) return <Skeleton className="h-40 w-full" />;

  return (
    <div className="mx-auto max-w-lg">
      <div className="mb-6 flex items-center gap-2">
        <Button
          variant="ghost"
          size="icon"
          className="min-h-11 min-w-11"
          onClick={() => router.push("/settings")}
        >
          <ArrowRight className="h-5 w-5" />
        </Button>
        <div className="flex min-w-0 items-center gap-2">
          <MonitorSmartphone className="h-5 w-5 shrink-0 text-emerald-600" />
          <h1 className="text-xl font-bold">دستگاه‌ها</h1>
        </div>
      </div>

      {isLoading ? (
        <Skeleton className="h-40 w-full" />
      ) : (data?.length ?? 0) === 0 ? (
        <p className="py-12 text-center text-muted-foreground">نشستی یافت نشد</p>
      ) : (
        <ul className="divide-y rounded-2xl border">
          {data!.map((s) => (
            <li key={s.id} className="flex items-start justify-between gap-3 px-4 py-3">
              <div className="min-w-0">
                <p className="font-medium">
                  {s.device_name || s.platform || "دستگاه ناشناس"}
                  {s.is_current ? " · فعلی" : ""}
                </p>
                <p className="flex flex-wrap items-center gap-x-1.5 text-xs text-muted-foreground">
                  <span dir="ltr" className="inline-block">
                    {s.ip || "IP نامشخص"}
                  </span>
                  {s.last_seen_at ? (
                    <>
                      <span aria-hidden>·</span>
                      <TimeLabel>{formatDate(s.last_seen_at)}</TimeLabel>
                    </>
                  ) : null}
                </p>
                {s.user_agent && (
                  <p className="mt-1 truncate text-xs text-muted-foreground">{s.user_agent}</p>
                )}
              </div>
              <Button
                variant="outline"
                size="sm"
                className="min-h-11 shrink-0 text-destructive"
                disabled={revokeMut.isPending}
                onClick={() => revokeMut.mutate(s.id)}
              >
                لغو
              </Button>
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}
