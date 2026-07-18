"use client";

import { useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import { AlertCircle, ArrowRight, AtSign, CheckCircle2 } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Skeleton } from "@/components/ui/skeleton";
import { useAuthStore } from "@/stores/auth-store";

const USERNAME_RE = /^[a-zA-Z0-9_]{3,32}$/;

export default function UsernameSettingsPage() {
  const router = useRouter();
  const { user, isAuthenticated, isLoading, authChecked, fetchMe, updateProfile } =
    useAuthStore();
  const [usernameDraft, setUsernameDraft] = useState("");
  const [saving, setSaving] = useState(false);
  const [message, setMessage] = useState<{ type: "success" | "error"; text: string } | null>(
    null
  );

  useEffect(() => {
    if (authChecked && !isAuthenticated) router.replace("/login");
  }, [authChecked, isAuthenticated, router]);

  useEffect(() => {
    if (!user) return;
    if (!user.username_pending) {
      setUsernameDraft(user.username);
    } else {
      setUsernameDraft("");
    }
  }, [user?.username, user?.username_pending]);

  const onSave = async () => {
    const next = usernameDraft.trim();
    if (!USERNAME_RE.test(next)) {
      setMessage({
        type: "error",
        text: "نام کاربری باید ۳ تا ۳۲ کاراکتر و فقط شامل حروف انگلیسی، عدد و _ باشد",
      });
      return;
    }
    if (next.startsWith("tmp_")) {
      setMessage({ type: "error", text: "این پیشوند رزرو شده است" });
      return;
    }
    setSaving(true);
    setMessage(null);
    try {
      await updateProfile({ username: next });
      await fetchMe({ force: true });
      setMessage({ type: "success", text: "نام کاربری ذخیره شد" });
      router.replace("/settings");
    } catch (e) {
      setMessage({ type: "error", text: e instanceof Error ? e.message : "ذخیره ناموفق بود" });
    } finally {
      setSaving(false);
    }
  };

  if (!authChecked || isLoading || !user) {
    return <Skeleton className="mx-auto h-40 w-full max-w-lg" />;
  }

  const title = user.username_pending ? "انتخاب نام کاربری" : "نام کاربری";

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
          <AtSign className="h-5 w-5 shrink-0 text-rose-600" />
          <h1 className="text-xl font-bold">{title}</h1>
        </div>
      </div>

      {user.username_pending && (
        <div className="mb-4 rounded-xl border border-amber-500/40 bg-amber-500/10 px-4 py-3 text-sm">
          برای ادامه یک نام کاربری دائمی انتخاب کنید.
        </div>
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

      <section className="rounded-2xl border p-4">
        <p className="mb-4 text-sm text-muted-foreground">
          {user.username_pending || user.username.startsWith("tmp_")
            ? "یک نام کاربری یکتا انتخاب کنید؛ آدرس پروفایل شماست و از ایمیل ساخته نمی‌شود."
            : "می‌توانید نام کاربری را تغییر دهید (حروف انگلیسی، عدد و _)."}
        </p>
        <label className="mb-3 block space-y-1 text-sm">
          <span className="text-muted-foreground">نام کاربری</span>
          <div className="flex items-center gap-2" dir="ltr">
            <span className="text-muted-foreground">@</span>
            <input
              type="text"
              className="min-h-11 w-full rounded-lg border bg-background px-3 py-2 font-mono"
              value={usernameDraft}
              onChange={(e) => setUsernameDraft(e.target.value.replace(/\s/g, ""))}
              placeholder="your_name"
              autoComplete="username"
            />
          </div>
        </label>
        <Button className="min-h-11" disabled={saving} onClick={() => void onSave()}>
          {saving ? "در حال ذخیره..." : "ذخیره نام کاربری"}
        </Button>
      </section>
    </div>
  );
}
