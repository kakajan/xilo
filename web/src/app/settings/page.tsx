"use client";

import { useEffect, useRef, useState } from "react";
import { useRouter } from "next/navigation";
import {
  Bookmark,
  Calendar,
  FolderOpen,
  LogOut,
  MonitorSmartphone,
  User,
  Wallet,
  Camera,
  CheckCircle2,
  AlertCircle,
} from "lucide-react";
import { Button } from "@/components/ui/button";
import { Avatar, AvatarFallback, AvatarImage } from "@/components/ui/avatar";
import { useAuthStore } from "@/stores/auth-store";
import { apiUpload } from "@/lib/api-client";
import { getInitials } from "@/lib/utils";
import type { PreferredCalendar } from "@/types/user";

const CALENDAR_OPTIONS: { value: PreferredCalendar; label: string; hint: string }[] = [
  { value: "auto", label: "خودکار", hint: "بر اساس پیش‌فرض زبان از پنل مدیریت" },
  { value: "jalali", label: "شمسی", hint: "همیشه تقویم شمسی" },
  { value: "gregorian", label: "میلادی", hint: "حتی با رابط فارسی" },
];

export default function UserSettingsPage() {
  const router = useRouter();
  const { user, isAuthenticated, isLoading, fetchMe, updateProfile, logout } = useAuthStore();
  const [calendar, setCalendar] = useState<PreferredCalendar>("auto");
  const [showCalendar, setShowCalendar] = useState(false);
  const [saving, setSaving] = useState(false);
  const [walletMsg, setWalletMsg] = useState(false);
  const [message, setMessage] = useState<{ type: "success" | "error"; text: string } | null>(
    null
  );
  const fileRef = useRef<HTMLInputElement>(null);

  useEffect(() => {
    void fetchMe();
  }, [fetchMe]);

  useEffect(() => {
    if (!isLoading && !isAuthenticated) router.replace("/login");
  }, [isLoading, isAuthenticated, router]);

  useEffect(() => {
    if (user?.preferred_calendar) setCalendar(user.preferred_calendar);
  }, [user?.preferred_calendar]);

  const onSaveCalendar = async () => {
    setSaving(true);
    setMessage(null);
    try {
      await updateProfile({ preferred_calendar: calendar });
      setMessage({ type: "success", text: "ترجیح تقویم ذخیره شد" });
      setShowCalendar(false);
    } catch (e) {
      setMessage({ type: "error", text: e instanceof Error ? e.message : "ذخیره ناموفق بود" });
    } finally {
      setSaving(false);
    }
  };

  const onAvatar = async (file: File) => {
    const fd = new FormData();
    fd.append("file", file);
    try {
      const res = await apiUpload<{ url?: string; avatar_url?: string }>("/api/auth/avatar", fd);
      const url = res.avatar_url || res.url;
      if (url) await updateProfile({ avatar_url: url });
      await fetchMe();
      setMessage({ type: "success", text: "عکس پروفایل به‌روز شد" });
    } catch (e) {
      setMessage({ type: "error", text: e instanceof Error ? e.message : "آپلود ناموفق بود" });
    }
  };

  if (isLoading || !user) {
    return (
      <div className="mx-auto max-w-lg py-12">
        <div className="h-40 animate-pulse rounded-lg bg-muted" />
      </div>
    );
  }

  const menu = [
    {
      title: "تغییر عکس",
      icon: Camera,
      tint: "text-primary",
      onClick: () => fileRef.current?.click(),
    },
    {
      title: "پروفایل من",
      icon: User,
      tint: "text-primary",
      onClick: () => router.push(`/${user.username}`),
    },
    {
      title: "کیف پول",
      icon: Wallet,
      tint: "text-purple-600",
      onClick: () => {
        setWalletMsg(true);
        setTimeout(() => setWalletMsg(false), 2500);
      },
    },
    {
      title: "پیام‌ها و ذخیره‌ها",
      icon: Bookmark,
      tint: "text-amber-600",
      onClick: () => router.push("/saved"),
    },
    {
      title: "دستگاه‌ها",
      icon: MonitorSmartphone,
      tint: "text-emerald-600",
      onClick: () => router.push("/settings/devices"),
    },
    {
      title: "پوشه‌های چت",
      icon: FolderOpen,
      tint: "text-sky-600",
      onClick: () => router.push("/settings/chat-folders"),
    },
    {
      title: "تقویم نمایش تاریخ",
      icon: Calendar,
      tint: "text-orange-600",
      onClick: () => setShowCalendar((v) => !v),
    },
  ];

  return (
    <div className="mx-auto max-w-lg py-4">
      <div className="mb-6 flex items-center gap-3">
        <Avatar className="h-14 w-14 shrink-0">
          {user.avatar_url ? <AvatarImage src={user.avatar_url} alt="" /> : null}
          <AvatarFallback>{getInitials(user.display_name || user.username)}</AvatarFallback>
        </Avatar>
        <div className="min-w-0">
          <h1 className="text-2xl font-bold">تنظیمات</h1>
          <p className="truncate text-sm text-muted-foreground">@{user.username}</p>
        </div>
      </div>

      <input
        ref={fileRef}
        type="file"
        accept="image/*"
        className="hidden"
        onChange={(e) => {
          const f = e.target.files?.[0];
          if (f) void onAvatar(f);
        }}
      />

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

      {walletMsg && (
        <p className="mb-3 text-sm text-muted-foreground">کیف پول به‌زودی...</p>
      )}

      <ul className="overflow-hidden rounded-2xl border divide-y">
        {menu.map((item) => {
          const Icon = item.icon;
          return (
            <li key={item.title}>
              <button
                type="button"
                onClick={item.onClick}
                className="flex w-full min-h-14 items-center gap-3 px-4 py-3 text-start hover:bg-muted/50"
              >
                <Icon className={`h-5 w-5 shrink-0 ${item.tint}`} />
                <span className="min-w-0 font-medium">{item.title}</span>
              </button>
            </li>
          );
        })}
        <li>
          <button
            type="button"
            onClick={() => void logout()}
            className="flex w-full min-h-14 items-center gap-3 px-4 py-3 text-start text-destructive hover:bg-destructive/5"
          >
            <LogOut className="h-5 w-5 shrink-0" />
            <span className="min-w-0 font-medium">خروج</span>
          </button>
        </li>
      </ul>

      {showCalendar && (
        <section className="mt-6 rounded-2xl border p-4">
          <h2 className="mb-1 text-sm font-semibold">تقویم نمایش تاریخ</h2>
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
          <Button className="min-h-11" disabled={saving} onClick={() => void onSaveCalendar()}>
            {saving ? "در حال ذخیره..." : "ذخیره"}
          </Button>
        </section>
      )}
    </div>
  );
}
