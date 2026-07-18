"use client";

import { Suspense, useEffect, useRef, useState } from "react";
import { useRouter, useSearchParams } from "next/navigation";
import {
  AtSign,
  Bookmark,
  Calendar,
  FolderOpen,
  Languages,
  LogOut,
  MonitorSmartphone,
  User,
  Wallet,
  Camera,
  CheckCircle2,
  AlertCircle,
} from "lucide-react";
import { Avatar, AvatarFallback, AvatarImage } from "@/components/ui/avatar";
import { AvatarCropDialog } from "@/components/settings/avatar-crop-dialog";
import { useAuthStore } from "@/stores/auth-store";
import { apiUpload } from "@/lib/api-client";
import { getInitials } from "@/lib/utils";

export default function UserSettingsPage() {
  return (
    <Suspense
      fallback={
        <div className="mx-auto max-w-lg py-12">
          <div className="h-40 animate-pulse rounded-lg bg-muted" />
        </div>
      }
    >
      <UserSettingsContent />
    </Suspense>
  );
}

function UserSettingsContent() {
  const router = useRouter();
  const searchParams = useSearchParams();
  const { user, isAuthenticated, isLoading, authChecked, fetchMe, updateProfile, logout } =
    useAuthStore();
  const [walletMsg, setWalletMsg] = useState(false);
  const [message, setMessage] = useState<{ type: "success" | "error"; text: string } | null>(
    null
  );
  const [cropFile, setCropFile] = useState<File | null>(null);
  const [uploadingAvatar, setUploadingAvatar] = useState(false);
  const fileRef = useRef<HTMLInputElement>(null);

  useEffect(() => {
    if (authChecked && !isAuthenticated) router.replace("/login");
  }, [authChecked, isAuthenticated, router]);

  // Backward-compatible redirect from older /settings?username=1 links
  useEffect(() => {
    if (searchParams.get("username") === "1") {
      router.replace("/settings/username");
    }
  }, [searchParams, router]);

  const onAvatarCropped = async (blob: Blob) => {
    setCropFile(null);
    setUploadingAvatar(true);
    const fd = new FormData();
    fd.append("file", blob, "avatar.png");
    try {
      const res = await apiUpload<{ url?: string; avatar_url?: string }>("/api/auth/avatar", fd);
      const url = res.avatar_url || res.url;
      if (url) await updateProfile({ avatar_url: url });
      await fetchMe({ force: true });
      setMessage({ type: "success", text: "عکس پروفایل به‌روز شد" });
    } catch (e) {
      setMessage({ type: "error", text: e instanceof Error ? e.message : "آپلود ناموفق بود" });
    } finally {
      setUploadingAvatar(false);
    }
  };

  if (!authChecked || isLoading || !user) {
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
      onClick: () => {
        if (!uploadingAvatar) fileRef.current?.click();
      },
    },
    {
      title: user.username_pending ? "انتخاب نام کاربری" : "نام کاربری",
      icon: AtSign,
      tint: "text-rose-600",
      onClick: () => router.push("/settings/username"),
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
      title: "زبان رابط",
      icon: Languages,
      tint: "text-indigo-600",
      onClick: () => router.push("/settings/language"),
    },
    {
      title: "تقویم نمایش تاریخ",
      icon: Calendar,
      tint: "text-orange-600",
      onClick: () => router.push("/settings/calendar"),
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
          <p className="truncate text-sm text-muted-foreground">
            {user.username_pending ? "نام کاربری هنوز انتخاب نشده" : `@${user.username}`}
          </p>
        </div>
      </div>

      {user.username_pending && (
        <div className="mb-4 rounded-xl border border-amber-500/40 bg-amber-500/10 px-4 py-3 text-sm">
          یک نام کاربری دائمی برای پروفایل خود انتخاب کنید.
        </div>
      )}

      <input
        ref={fileRef}
        type="file"
        accept="image/*"
        className="hidden"
        onChange={(e) => {
          const f = e.target.files?.[0];
          e.target.value = "";
          if (f) setCropFile(f);
        }}
      />

      {cropFile && (
        <AvatarCropDialog
          file={cropFile}
          onDismiss={() => setCropFile(null)}
          onConfirm={(blob) => void onAvatarCropped(blob)}
        />
      )}

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
    </div>
  );
}
