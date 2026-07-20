"use client";

import { useEffect } from "react";
import { useRouter } from "next/navigation";
import { ArrowRight, Bell } from "lucide-react";
import { useTranslations } from "next-intl";
import { useAuthStore } from "@/stores/auth-store";
import { Button } from "@/components/ui/button";
import { Skeleton } from "@/components/ui/skeleton";
import { NotificationPreferences } from "@/components/notification/notification-preferences";

export default function NotificationSettingsPage() {
  const t = useTranslations("settings");
  const router = useRouter();
  const { isAuthenticated, isLoading: authLoading, authChecked } = useAuthStore();

  useEffect(() => {
    if (authChecked && !isAuthenticated) router.replace("/login");
  }, [authChecked, isAuthenticated, router]);

  if (authLoading || !isAuthenticated) return <Skeleton className="h-40 w-full" />;

  return (
    <div className="mx-auto max-w-lg">
      <div className="mb-6 flex items-center gap-2">
        <Button
          variant="ghost"
          size="icon"
          className="min-h-11 min-w-11"
          onClick={() => router.push("/settings")}
          aria-label="Back"
        >
          <ArrowRight className="h-5 w-5 rtl:rotate-180" />
        </Button>
        <div className="flex min-w-0 items-center gap-2">
          <Bell className="h-5 w-5 shrink-0 text-primary" />
          <h1 className="min-w-0 text-xl font-semibold">{t("notifications")}</h1>
        </div>
      </div>
      <NotificationPreferences />
    </div>
  );
}
