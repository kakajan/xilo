"use client";

import { useState } from "react";
import {
  AlertCircle,
  Bell,
  CheckCircle2,
  Loader2,
  Send,
} from "lucide-react";
import { apiFetch } from "@/lib/api-client";
import { Button } from "@/components/ui/button";
import { useAuthStore } from "@/stores/auth-store";

interface BroadcastResult {
  users_total: number;
  inbox_created: number;
  inbox_failed: number;
  push_tokens: number;
  push_send_errors: number;
}

export default function DashboardNotificationsPage() {
  const { user } = useAuthStore();
  const [title, setTitle] = useState("");
  const [body, setBody] = useState("");
  const [link, setLink] = useState("");
  const [sendInbox, setSendInbox] = useState(true);
  const [sendPush, setSendPush] = useState(true);
  const [sending, setSending] = useState(false);
  const [confirmOpen, setConfirmOpen] = useState(false);
  const [message, setMessage] = useState<{ type: "success" | "error"; text: string } | null>(
    null
  );
  const [result, setResult] = useState<BroadcastResult | null>(null);

  if (user?.role !== "admin" && user?.role !== "superadmin") {
    return (
      <div className="rounded-lg border border-destructive/40 bg-destructive/10 px-4 py-3 text-sm">
        دسترسی محدود به ادمین
      </div>
    );
  }

  const canSubmit =
    title.trim().length > 0 &&
    title.trim().length <= 120 &&
    body.trim().length <= 1000 &&
    (sendInbox || sendPush) &&
    !sending;

  const onSubmit = async () => {
    setConfirmOpen(false);
    setSending(true);
    setMessage(null);
    setResult(null);
    try {
      const res = await apiFetch<BroadcastResult>("/api/notifications/push/broadcast", {
        method: "POST",
        body: JSON.stringify({
          title: title.trim(),
          body: body.trim(),
          link: link.trim() || undefined,
          send_inbox: sendInbox,
          send_push: sendPush,
        }),
      });
      setResult(res);
      setMessage({
        type: "success",
        text: "پیام برای کاربران ارسال شد",
      });
      setTitle("");
      setBody("");
      setLink("");
    } catch (e) {
      setMessage({
        type: "error",
        text: e instanceof Error ? e.message : "ارسال ناموفق بود",
      });
    } finally {
      setSending(false);
    }
  };

  return (
    <div className="mx-auto max-w-xl space-y-6">
      <div className="flex items-center gap-2">
        <Bell className="h-5 w-5 shrink-0 text-primary" />
        <h1 className="min-w-0 text-2xl font-bold">پوش ناتیفیکیشن</h1>
      </div>
      <p className="text-sm text-muted-foreground">
        یک پیام سفارشی برای همهٔ کاربران فعال ارسال کنید. پیام در inbox داخل اپ/وب ذخیره
        می‌شود و در صورت پیکربندی FCM به‌صورت پوش هم می‌رود.
      </p>

      {message && (
        <div
          className={`flex items-center gap-2 rounded-lg border px-4 py-3 text-sm ${
            message.type === "success"
              ? "border-green-500/40 bg-green-500/10 text-green-700 dark:text-green-400"
              : "border-destructive/40 bg-destructive/10 text-destructive"
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

      <div className="space-y-4 rounded-xl border p-4">
        <label className="block space-y-1.5">
          <span className="text-sm font-medium">عنوان</span>
          <input
            value={title}
            onChange={(e) => setTitle(e.target.value)}
            maxLength={120}
            placeholder="مثلاً: به‌روزرسانی جدید"
            className="w-full rounded-lg border bg-background px-3 py-2 text-sm min-h-11"
          />
          <span className="text-xs text-muted-foreground">{title.trim().length}/120</span>
        </label>

        <label className="block space-y-1.5">
          <span className="text-sm font-medium">متن پیام</span>
          <textarea
            value={body}
            onChange={(e) => setBody(e.target.value)}
            maxLength={1000}
            rows={4}
            placeholder="متن اعلان…"
            className="w-full rounded-lg border bg-background px-3 py-2 text-sm"
          />
          <span className="text-xs text-muted-foreground">{body.trim().length}/1000</span>
        </label>

        <label className="block space-y-1.5">
          <span className="text-sm font-medium">لینک (اختیاری)</span>
          <input
            value={link}
            onChange={(e) => setLink(e.target.value)}
            placeholder="/discover یا https://…"
            className="w-full rounded-lg border bg-background px-3 py-2 text-sm min-h-11"
            dir="ltr"
          />
        </label>

        <div className="flex flex-col gap-2 sm:flex-row sm:gap-6">
          <label className="flex items-center gap-2 text-sm">
            <input
              type="checkbox"
              checked={sendInbox}
              onChange={(e) => setSendInbox(e.target.checked)}
              className="size-4 accent-primary"
            />
            <span>inbox داخل اپ/وب</span>
          </label>
          <label className="flex items-center gap-2 text-sm">
            <input
              type="checkbox"
              checked={sendPush}
              onChange={(e) => setSendPush(e.target.checked)}
              className="size-4 accent-primary"
            />
            <span>پوش FCM (اندروید)</span>
          </label>
        </div>

        <Button
          className="min-h-11 gap-2"
          disabled={!canSubmit}
          onClick={() => setConfirmOpen(true)}
        >
          {sending ? <Loader2 className="h-4 w-4 animate-spin" /> : <Send className="h-4 w-4" />}
          ارسال به همه کاربران
        </Button>
      </div>

      {result && (
        <div className="rounded-xl border bg-muted/40 p-4 text-sm space-y-1">
          <p className="font-medium">نتیجه ارسال</p>
          <p>کاربران فعال: {result.users_total}</p>
          <p>inbox ایجادشده: {result.inbox_created}</p>
          {result.inbox_failed > 0 && <p>خطای inbox: {result.inbox_failed}</p>}
          <p>توکن‌های پوش: {result.push_tokens}</p>
          {result.push_send_errors > 0 && (
            <p className="text-destructive">خطای ارسال پوش: {result.push_send_errors}</p>
          )}
        </div>
      )}

      {confirmOpen && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 p-4">
          <div className="w-full max-w-md space-y-4 rounded-xl border bg-background p-5 shadow-lg">
            <div className="flex items-center gap-2">
              <AlertCircle className="h-5 w-5 shrink-0 text-amber-600" />
              <h2 className="min-w-0 text-lg font-semibold">تأیید ارسال همگانی</h2>
            </div>
            <p className="text-sm text-muted-foreground">
              پیام «{title.trim()}» برای همه کاربران فعال ارسال می‌شود. این عمل قابل بازگشت نیست.
            </p>
            <div className="flex justify-end gap-2">
              <Button variant="ghost" onClick={() => setConfirmOpen(false)}>
                انصراف
              </Button>
              <Button onClick={() => void onSubmit()}>تأیید و ارسال</Button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
