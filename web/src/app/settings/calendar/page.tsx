"use client";

import { useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import { AlertCircle, ArrowRight, Calendar, CheckCircle2 } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Skeleton } from "@/components/ui/skeleton";
import { useAuthStore } from "@/stores/auth-store";
import type { PreferredCalendar } from "@/types/user";

const CALENDAR_OPTIONS: { value: PreferredCalendar; label: string; hint: string }[] = [
  { value: "auto", label: "خودکار", hint: "بر اساس پیش‌فرض زبان از پنل مدیریت" },
  { value: "jalali", label: "شمسی", hint: "همیشه تقویم شمسی" },
  { value: "gregorian", label: "میلادی", hint: "حتی با رابط فارسی" },
];

export default function CalendarSettingsPage() {
  const router = useRouter();
  const { user, isAuthenticated, isLoading, authChecked, updateProfile } = useAuthStore();
  const [calendar, setCalendar] = useState<PreferredCalendar>("auto");
  const [saving, setSaving] = useState(false);
  const [message, setMessage] = useState<{ type: "success" | "error"; text: string } | null>(
    null
  );

  useEffect(() => {
    if (authChecked && !isAuthenticated) router.replace("/login");
  }, [authChecked, isAuthenticated, router]);

  useEffect(() => {
    if (user?.preferred_calendar) setCalendar(user.preferred_calendar);
  }, [user?.preferred_calendar]);

  const onSave = async () => {
    setSaving(true);
    setMessage(null);
    try {
      await updateProfile({ preferred_calendar: calendar });
      setMessage({ type: "success", text: "ترجیح تقویم ذخیره شد" });
    } catch (e) {
      setMessage({ type: "error", text: e instanceof Error ? e.message : "ذخیره ناموفق بود" });
    } finally {
      setSaving(false);
    }
  };

  if (!authChecked || isLoading || !user) {
    return <Skeleton className="mx-auto h-40 w-full max-w-lg" />;
  }

  return (
    <div className="mx-auto max-w-lg py-4">
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
          <Calendar className="h-5 w-5 shrink-0 text-orange-600" />
          <h1 className="text-xl font-bold">تقویم نمایش تاریخ</h1>
        </div>
      </div>

      {message && (
        <div
          className={`mb-4 flex items-center gap-2 text-sm ${
            message.type === "success" ? "text-green-600" : "text-destructive"
          }`}
        >
          {message.type === "success" ? (
            <CheckCircle2 className="h-4 w-4 shrink-0" />
          ) : (
            <AlertCircle className="h-4 w-4 shrink-0" />
          )}
          <span className="min-w-0">{message.text}</span>
        </div>
      )}

      <section className="rounded-2xl border p-4">
        <p className="mb-4 text-sm text-muted-foreground">
          می‌توانید حتی با زبان فارسی، تقویم میلادی را انتخاب کنید.
        </p>
        <div className="mb-4 space-y-2">
          {CALENDAR_OPTIONS.map((opt) => (
            <label
              key={opt.value}
              className={`flex cursor-pointer items-start gap-3 rounded-lg border px-3 py-3 transition-colors ${
                calendar === opt.value ? "border-primary bg-primary/5" : "hover:bg-muted/50"
              }`}
            >
              <input
                type="radio"
                name="calendar"
                className="mt-1"
                checked={calendar === opt.value}
                onChange={() => setCalendar(opt.value)}
              />
              <span className="min-w-0">
                <span className="block text-sm font-medium">{opt.label}</span>
                <span className="block text-xs text-muted-foreground">{opt.hint}</span>
              </span>
            </label>
          ))}
        </div>
        <Button className="min-h-11" disabled={saving} onClick={() => void onSave()}>
          {saving ? "در حال ذخیره..." : "ذخیره"}
        </Button>
      </section>
    </div>
  );
}
