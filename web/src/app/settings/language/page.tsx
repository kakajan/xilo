"use client";

import { useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import { AlertCircle, ArrowRight, CheckCircle2, Languages } from "lucide-react";
import { useTranslations } from "next-intl";
import { Button } from "@/components/ui/button";
import { Skeleton } from "@/components/ui/skeleton";
import { APP_LANGUAGES, type AppLanguageCode } from "@/lib/languages";
import { useAuthStore } from "@/stores/auth-store";
import { useLocaleStore } from "@/stores/locale-store";

export default function LanguageSettingsPage() {
  const t = useTranslations("settings.language");
  const tCommon = useTranslations("common.actions");
  const router = useRouter();
  const { user, isAuthenticated, isLoading, authChecked, updateProfile } = useAuthStore();
  const setLocale = useLocaleStore((s) => s.setLocale);
  const currentLocale = useLocaleStore((s) => s.locale);
  const [language, setLanguage] = useState<AppLanguageCode>(currentLocale);
  const [saving, setSaving] = useState(false);
  const [message, setMessage] = useState<{ type: "success" | "error"; text: string } | null>(
    null
  );

  useEffect(() => {
    if (authChecked && !isAuthenticated) router.replace("/login");
  }, [authChecked, isAuthenticated, router]);

  // Only sync from saved preference. Preview updates currentLocale via setLocale;
  // depending on it here reset the radio on the first click.
  useEffect(() => {
    if (user?.preferred_language) {
      setLanguage(user.preferred_language as AppLanguageCode);
    }
  }, [user?.preferred_language]);

  const onSave = async () => {
    setSaving(true);
    setMessage(null);
    try {
      await updateProfile({ preferred_language: language });
      setLocale(language);
      setMessage({ type: "success", text: t("saved") });
    } catch (e) {
      setMessage({
        type: "error",
        text: e instanceof Error ? e.message : t("saveFailed"),
      });
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
          aria-label={tCommon("back")}
        >
          <ArrowRight className="h-5 w-5" />
        </Button>
        <div className="flex min-w-0 items-center gap-2">
          <Languages className="h-5 w-5 shrink-0 text-indigo-600" />
          <h1 className="text-xl font-bold">{t("title")}</h1>
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
        <p className="mb-4 text-sm text-muted-foreground">{t("description")}</p>
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
                onChange={() => {
                  setLanguage(opt.code);
                  setLocale(opt.code);
                }}
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
          {saving ? t("saving") : t("save")}
        </Button>
      </section>
    </div>
  );
}
