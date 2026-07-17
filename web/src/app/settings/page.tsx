"use client";

import { useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import { CheckCircle2, AlertCircle, Calendar } from "lucide-react";
import { Button } from "@/components/ui/button";
import { useAuthStore } from "@/stores/auth-store";
import type { PreferredCalendar } from "@/types/user";

const OPTIONS: { value: PreferredCalendar; label: string; hint: string }[] = [
  { value: "auto", label: "خودکار", hint: "بر اساس پیش‌فرض زبان از پنل مدیریت" },
  { value: "jalali", label: "شمسی", hint: "همیشه تقویم شمسی" },
  { value: "gregorian", label: "میلادی", hint: "حتی با رابط فارسی" },
];

export default function UserSettingsPage() {
  const router = useRouter();
  const { user, isAuthenticated, isLoading, fetchMe, updateProfile } = useAuthStore();
  const [calendar, setCalendar] = useState<PreferredCalendar>("auto");
  const [saving, setSaving] = useState(false);
  const [message, setMessage] = useState<{ type: "success" | "error"; text: string } | null>(null);

  useEffect(() => {
    void fetchMe();
  }, [fetchMe]);

  useEffect(() => {
    if (!isLoading && !isAuthenticated) {
      router.replace("/login");
    }
  }, [isLoading, isAuthenticated, router]);

  useEffect(() => {
    if (user?.preferred_calendar) {
      setCalendar(user.preferred_calendar);
    }
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

  if (isLoading || !user) {
    return (
      <div className="max-w-lg mx-auto py-12 px-4">
        <div className="animate-pulse h-40 bg-muted rounded-lg" />
      </div>
    );
  }

  return (
    <div className="max-w-lg mx-auto py-12 px-4">
      <div className="flex items-center gap-3 mb-6">
        <Calendar className="h-6 w-6 shrink-0" />
        <h1 className="text-2xl font-bold min-w-0">تنظیمات</h1>
      </div>

      <section>
        <h2 className="text-sm font-semibold mb-1">تقویم نمایش تاریخ</h2>
        <p className="text-sm text-muted-foreground mb-4">
          می‌توانید حتی با زبان فارسی، تقویم میلادی را انتخاب کنید.
        </p>

        <div className="space-y-2 mb-6">
          {OPTIONS.map((opt) => (
            <label
              key={opt.value}
              className={`flex items-start gap-3 rounded-lg border px-3 py-3 cursor-pointer transition-colors ${
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

        {message && (
          <div
            className={`flex items-center gap-2 mb-4 text-sm ${
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

        <Button onClick={onSave} disabled={saving}>
          {saving ? "در حال ذخیره…" : "ذخیره"}
        </Button>
      </section>
    </div>
  );
}
