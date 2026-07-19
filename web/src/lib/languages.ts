import {
  applyDocumentLocale,
  defaultLocale,
  localeConfig,
  locales,
  resolveLocale,
  type Locale,
} from "@/i18n/config";

export type AppLanguageCode = Locale;

export interface AppLanguage {
  code: AppLanguageCode;
  nameNative: string;
  nameEnglish: string;
  direction: "rtl" | "ltr";
}

export const APP_LANGUAGES: AppLanguage[] = locales.map((code) => ({
  code,
  nameNative: localeConfig[code].name,
  nameEnglish: localeConfig[code].nameEnglish,
  direction: localeConfig[code].direction,
}));

export const DEFAULT_LANGUAGE: AppLanguageCode = defaultLocale;

export function getLanguage(code?: string | null): AppLanguage {
  const resolved = resolveLocale(code);
  return APP_LANGUAGES.find((l) => l.code === resolved)!;
}

export function applyDocumentLanguage(code?: string | null): void {
  applyDocumentLocale(resolveLocale(code));
}
