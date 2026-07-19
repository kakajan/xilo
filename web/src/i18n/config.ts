export const locales = ["fa", "en", "ar", "ru", "tr"] as const;
export type Locale = (typeof locales)[number];

export const localeConfig: Record<
  Locale,
  { direction: "rtl" | "ltr"; name: string; nameEnglish: string }
> = {
  fa: { direction: "rtl", name: "فارسی", nameEnglish: "Persian" },
  en: { direction: "ltr", name: "English", nameEnglish: "English" },
  ar: { direction: "rtl", name: "العربية", nameEnglish: "Arabic" },
  ru: { direction: "ltr", name: "Русский", nameEnglish: "Russian" },
  tr: { direction: "ltr", name: "Türkçe", nameEnglish: "Turkish" },
};

export const defaultLocale: Locale = "fa";

export const LOCALE_STORAGE_KEY = "xilo.locale";

export function isLocale(value: unknown): value is Locale {
  return typeof value === "string" && (locales as readonly string[]).includes(value);
}

export function resolveLocale(value?: string | null): Locale {
  return isLocale(value) ? value : defaultLocale;
}

export function applyDocumentLocale(locale: Locale): void {
  if (typeof document === "undefined") return;
  const cfg = localeConfig[locale];
  document.documentElement.lang = locale;
  document.documentElement.dir = cfg.direction;
}
