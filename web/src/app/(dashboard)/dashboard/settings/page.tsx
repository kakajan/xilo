"use client";

import { useEffect, useState } from "react";
import {
  AlertCircle,
  CheckCircle2,
  Palette,
  RotateCcw,
  Save,
  Settings,
  Tag,
} from "lucide-react";
import { apiFetch } from "@/lib/api-client";
import { Button } from "@/components/ui/button";
import { useAuthStore } from "@/stores/auth-store";
import { useBrandStore } from "@/stores/brand-store";
import { useCalendarStore } from "@/stores/calendar-store";
import { useThemeStore } from "@/stores/theme-store";
import type { CalendarSystem } from "@/lib/format-date";
import { DEFAULT_BRAND, type PlatformBrand } from "@/lib/brand";
import {
  DEFAULT_THEME,
  THEME_FIELD_LABELS,
  isHexColor,
  type PlatformTheme,
  type ThemePalette,
} from "@/lib/theme";

interface PlatformSettings {
  calendar_defaults: Record<string, CalendarSystem>;
  theme: PlatformTheme;
  brand: PlatformBrand;
}

const LOCALE_LABELS: { code: string; label: string }[] = [
  { code: "fa", label: "فارسی (fa)" },
  { code: "en", label: "English (en)" },
  { code: "ar", label: "العربية (ar)" },
  { code: "ru", label: "Русский (ru)" },
  { code: "tr", label: "Türkçe (tr)" },
];

type ThemeMode = "light" | "dark";

export default function PlatformSettingsPage() {
  const { user } = useAuthStore();
  const setDefaults = useCalendarStore((s) => s.setDefaults);
  const setTheme = useThemeStore((s) => s.setTheme);
  const setBrand = useBrandStore((s) => s.setBrand);
  const [defaults, setLocalDefaults] = useState<Record<string, CalendarSystem>>({});
  const [theme, setLocalTheme] = useState<PlatformTheme>(DEFAULT_THEME);
  const [brand, setLocalBrand] = useState<PlatformBrand>(DEFAULT_BRAND);
  const [themeMode, setThemeMode] = useState<ThemeMode>("light");
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [savingTheme, setSavingTheme] = useState(false);
  const [savingBrand, setSavingBrand] = useState(false);
  const [message, setMessage] = useState<{ type: "success" | "error"; text: string } | null>(null);

  useEffect(() => {
    void load();
  }, []);

  const load = async () => {
    try {
      const data = await apiFetch<PlatformSettings>("/api/platform/settings");
      setLocalDefaults(data.calendar_defaults ?? {});
      setDefaults(data.calendar_defaults ?? {});
      const nextTheme = data.theme ?? DEFAULT_THEME;
      setLocalTheme(nextTheme);
      setTheme(nextTheme);
      const nextBrand = data.brand ?? DEFAULT_BRAND;
      setLocalBrand(nextBrand);
      setBrand(nextBrand);
    } catch {
      setMessage({ type: "error", text: "بارگذاری تنظیمات ناموفق بود" });
    } finally {
      setLoading(false);
    }
  };

  const onSaveCalendar = async () => {
    setSaving(true);
    setMessage(null);
    try {
      const data = await apiFetch<PlatformSettings>("/api/platform/settings", {
        method: "PATCH",
        body: JSON.stringify({ calendar_defaults: defaults }),
      });
      setLocalDefaults(data.calendar_defaults);
      setDefaults(data.calendar_defaults);
      setMessage({ type: "success", text: "تنظیمات تقویم ذخیره شد" });
    } catch (e) {
      setMessage({ type: "error", text: e instanceof Error ? e.message : "ذخیره ناموفق بود" });
    } finally {
      setSaving(false);
    }
  };

  const onSaveTheme = async () => {
    const palette = theme[themeMode];
    for (const { key, label } of THEME_FIELD_LABELS) {
      if (!isHexColor(palette[key])) {
        setMessage({ type: "error", text: `رنگ نامعتبر برای «${label}» — فرمت #RRGGBB` });
        return;
      }
    }
    setSavingTheme(true);
    setMessage(null);
    try {
      const data = await apiFetch<PlatformSettings>("/api/platform/settings", {
        method: "PATCH",
        body: JSON.stringify({ theme }),
      });
      const next = data.theme ?? theme;
      setLocalTheme(next);
      setTheme(next);
      setMessage({ type: "success", text: "تم پلتفرم ذخیره شد" });
    } catch (e) {
      setMessage({ type: "error", text: e instanceof Error ? e.message : "ذخیره تم ناموفق بود" });
    } finally {
      setSavingTheme(false);
    }
  };

  const updateColor = (key: keyof ThemePalette, value: string) => {
    setLocalTheme((prev) => ({
      ...prev,
      [themeMode]: { ...prev[themeMode], [key]: value },
    }));
  };

  const resetTheme = () => {
    setLocalTheme(DEFAULT_THEME);
  };

  const onSaveBrand = async () => {
    if (!brand.name_fa.trim() || !brand.name_en.trim() || !brand.display.trim()) {
      setMessage({ type: "error", text: "همهٔ فیلدهای برند لازم است" });
      return;
    }
    setSavingBrand(true);
    setMessage(null);
    try {
      const data = await apiFetch<PlatformSettings>("/api/platform/settings", {
        method: "PATCH",
        body: JSON.stringify({ brand }),
      });
      const next = data.brand ?? brand;
      setLocalBrand(next);
      setBrand(next);
      setMessage({ type: "success", text: "برند پلتفرم ذخیره شد" });
    } catch (e) {
      setMessage({ type: "error", text: e instanceof Error ? e.message : "ذخیره برند ناموفق بود" });
    } finally {
      setSavingBrand(false);
    }
  };

  const resetBrand = () => {
    setLocalBrand(DEFAULT_BRAND);
  };

  if (user?.role !== "admin" && user?.role !== "superadmin") {
    return (
      <div className="flex items-center justify-center py-20">
        <div className="text-center">
          <AlertCircle className="mx-auto h-12 w-12 text-destructive mb-4" />
          <p className="text-muted-foreground">Access denied</p>
        </div>
      </div>
    );
  }

  return (
    <div className="max-w-2xl mx-auto space-y-10">
      <div className="flex items-center gap-3">
        <Settings className="h-6 w-6 shrink-0" />
        <h1 className="text-2xl font-bold min-w-0">تنظیمات پلتفرم</h1>
      </div>

      {message && (
        <div
          className={`flex items-center gap-2 text-sm ${
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

      {loading ? (
        <div className="animate-pulse space-y-4">
          <div className="h-10 bg-muted rounded-lg" />
          <div className="h-10 bg-muted rounded-lg" />
        </div>
      ) : (
        <>
          <section className="space-y-4">
            <div className="flex items-center gap-2">
              <Tag className="h-5 w-5 shrink-0 text-primary" />
              <h2 className="min-w-0 text-lg font-semibold">برندینگ دیپلوی</h2>
            </div>
            <p className="text-sm text-muted-foreground">
              نام نمایشی اپ برای کاربران (وب و اندروید). زیرساخت همچنان Xilo است؛ این فقط برند کاربرپسند است.
            </p>
            <div className="grid gap-3 sm:grid-cols-2">
              <label className="space-y-1 text-sm">
                <span className="text-muted-foreground">نام فارسی</span>
                <input
                  type="text"
                  className="min-h-11 w-full rounded-lg border bg-background px-3 py-2"
                  value={brand.name_fa}
                  onChange={(e) => setLocalBrand((prev) => ({ ...prev, name_fa: e.target.value }))}
                />
              </label>
              <label className="space-y-1 text-sm">
                <span className="text-muted-foreground">نام انگلیسی</span>
                <input
                  type="text"
                  className="min-h-11 w-full rounded-lg border bg-background px-3 py-2"
                  dir="ltr"
                  value={brand.name_en}
                  onChange={(e) => setLocalBrand((prev) => ({ ...prev, name_en: e.target.value }))}
                />
              </label>
              <label className="space-y-1 text-sm sm:col-span-2">
                <span className="text-muted-foreground">نمایش ترکیبی</span>
                <input
                  type="text"
                  className="min-h-11 w-full rounded-lg border bg-background px-3 py-2"
                  value={brand.display}
                  onChange={(e) => setLocalBrand((prev) => ({ ...prev, display: e.target.value }))}
                  placeholder="آیله | aile"
                />
              </label>
            </div>
            <div className="flex flex-wrap gap-2">
              <Button onClick={onSaveBrand} disabled={savingBrand}>
                <Save className="mr-2 h-4 w-4" />
                {savingBrand ? "در حال ذخیره…" : "ذخیره برند"}
              </Button>
              <Button type="button" variant="outline" onClick={resetBrand}>
                <RotateCcw className="mr-2 h-4 w-4" />
                بازگردانی پیش‌فرض
              </Button>
            </div>
          </section>

          <section className="space-y-4">
            <div className="flex items-center gap-2">
              <Palette className="h-5 w-5 shrink-0 text-primary" />
              <h2 className="text-lg font-semibold min-w-0">تم یکدست اپلیکیشن</h2>
            </div>
            <p className="text-sm text-muted-foreground">
              رنگ‌های کل پلتفرم (وب و اندروید). مقدار پیش‌فرض آبی برند است؛ پس از ذخیره همه کلاینت‌ها این ست را می‌گیرند.
            </p>

            <div className="flex gap-2">
              {(["light", "dark"] as ThemeMode[]).map((mode) => (
                <button
                  key={mode}
                  type="button"
                  onClick={() => setThemeMode(mode)}
                  className={`px-3 py-1.5 rounded-lg text-sm transition-colors ${
                    themeMode === mode
                      ? "bg-primary text-primary-foreground"
                      : "bg-muted text-muted-foreground hover:bg-accent"
                  }`}
                >
                  {mode === "light" ? "روشن" : "تاریک"}
                </button>
              ))}
            </div>

            <div
              className="rounded-xl border p-4 space-y-3"
              style={{
                background: theme[themeMode].background_secondary,
                borderColor: theme[themeMode].border,
              }}
            >
              <div className="flex flex-wrap gap-2">
                <span
                  className="inline-flex items-center px-3 py-1.5 rounded-full text-sm"
                  style={{
                    background: theme[themeMode].background_tertiary,
                    color: theme[themeMode].text_secondary,
                  }}
                >
                  پیش‌نمایش چیپ
                </span>
                <span
                  className="inline-flex items-center px-3 py-1.5 rounded-full text-sm text-white"
                  style={{ background: theme[themeMode].primary }}
                >
                  انتخاب‌شده
                </span>
              </div>
              <div
                className="h-10 rounded-full px-4 flex items-center text-sm"
                style={{
                  background: theme[themeMode].background_tertiary,
                  color: theme[themeMode].text_secondary,
                }}
              >
                جستجو در همه چیز...
              </div>
            </div>

            <div className="grid gap-3 sm:grid-cols-2">
              {THEME_FIELD_LABELS.map(({ key, label }) => (
                <label key={key} className="flex items-center gap-3 text-sm">
                  <input
                    type="color"
                    className="h-9 w-10 shrink-0 cursor-pointer rounded border bg-transparent p-0.5"
                    value={normalizeColorInput(theme[themeMode][key])}
                    onChange={(e) => updateColor(key, e.target.value.toUpperCase())}
                  />
                  <span className="min-w-0 flex-1 text-muted-foreground">{label}</span>
                  <input
                    type="text"
                    className="w-[7.5rem] shrink-0 rounded-lg border bg-background px-2 py-1.5 font-mono text-xs uppercase"
                    value={theme[themeMode][key]}
                    onChange={(e) => updateColor(key, e.target.value)}
                    spellCheck={false}
                  />
                </label>
              ))}
            </div>

            <div className="flex flex-wrap gap-2">
              <Button onClick={onSaveTheme} disabled={savingTheme}>
                <Save className="h-4 w-4 mr-2" />
                {savingTheme ? "در حال ذخیره…" : "ذخیره تم"}
              </Button>
              <Button type="button" variant="outline" onClick={resetTheme}>
                <RotateCcw className="h-4 w-4 mr-2" />
                بازگردانی پیش‌فرض
              </Button>
            </div>
          </section>

          <section className="space-y-4">
            <h2 className="text-lg font-semibold">تقویم پیش‌فرض زبان‌ها</h2>
            <p className="text-sm text-muted-foreground">
              تقویم پیش‌فرض هر زبان برای کاربرانی که گزینه «خودکار» را انتخاب کرده‌اند.
            </p>

            {LOCALE_LABELS.map(({ code, label }) => (
              <div key={code} className="flex items-center justify-between gap-4">
                <label className="text-sm font-medium min-w-0">{label}</label>
                <select
                  className="rounded-lg border bg-background px-3 py-2 text-sm"
                  value={defaults[code] ?? "gregorian"}
                  onChange={(e) =>
                    setLocalDefaults((prev) => ({
                      ...prev,
                      [code]: e.target.value as CalendarSystem,
                    }))
                  }
                >
                  <option value="jalali">شمسی (Jalali)</option>
                  <option value="gregorian">میلادی (Gregorian)</option>
                </select>
              </div>
            ))}

            <Button onClick={onSaveCalendar} disabled={saving}>
              <Save className="h-4 w-4 mr-2" />
              {saving ? "در حال ذخیره…" : "ذخیره تقویم"}
            </Button>
          </section>
        </>
      )}
    </div>
  );
}

function normalizeColorInput(value: string): string {
  return isHexColor(value) ? value : "#000000";
}
