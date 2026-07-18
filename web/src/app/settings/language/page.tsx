"use client";

import { useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import { AlertCircle, ArrowRight, CheckCircle2, Languages } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Skeleton } from "@/components/ui/skeleton";
import { APP_LANGUAGES, applyDocumentLanguage, type AppLanguageCode } from "@/lib/languages";
import { useAuthStore } from "@/stores/auth-store";

export default function LanguageSettingsPage() {
  const router = useRouter();
  const { user, isAuthenticated, isLoading, authChecked, updateProfile } = useAuthStore();
  const [language, setLanguage] = useState<AppLanguageCode>("fa");
  const [saving, setSaving] = useState(false);
  const [message, setMessage] = useState<{ type: "success" | "error"; text: string } | null>(
    null
  );

  useEffect(() => {
    if (authChecked && !isAuthenticated) router.replace("/login");
  }, [authChecked, isAuthenticated, router]);

  useEffect(() => {
    if (user?.preferred_language) {
      setLanguage(user.preferred_language as AppLanguageCode);
      applyDocumentLanguage(user.preferred_language);
    }
  }, [user?.preferred_language]);

  const onSave = async () => {
    setSaving(true);
    setMessage(null);
    try {
      await updateProfile({ preferred_language: language });
      applyDocumentLanguage(language);
      setMessage({ type: "success", text: "زبان رابط ذخیره شد" });
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
          <Languages className="h-5 w-5 shrink-0 text-indigo-600" />
          <h1 className="text-xl font-bold">زبان رابط</h1>
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
          جهت صفحه (راست‌به‌چپ / چپ‌به‌راست) با زبان انتخابی تنظیم می‌شود.
        </p>
        <div className="mb-4 space-y-2">
          {APP_LANGUAGES.map((opt) => (
            <label
              key={opt.code}
              className={`flex cursor-pointer items-start gap-3 rounded-lg border px-3 py-3 transition-colors ${
                language === opt.code ? "border-primary bg-primary/5" : "hover:bg-muted/50"
              }`}
            >
              <input
                type="radio"
                name="language"
                className="mt-1"
                checked={language === opt.code}
                onChange={() => setLanguage(opt.code)}
              />
              <span className="min-w-0">
                <span className="block text-sm font-medium">{opt.nameNative}</span>
                <span className="block text-xs text-muted-foreground">
                  {opt.nameEnglish} · {opt.direction.toUpperCase()}
                </span>
              </span>
            </label>
          ))}
        </div>
        <Button className="min-h-11" disabled={saving} onClick={() => void onSave()}>
          {saving ? "در حال ذخیره…" : "ذخیره زبان"}
        </Button>
      </section>
    </div>
  );
}
