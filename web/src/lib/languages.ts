export type AppLanguageCode = "fa" | "en" | "ar" | "ru" | "tr";

export interface AppLanguage {
  code: AppLanguageCode;
  nameNative: string;
  nameEnglish: string;
  direction: "rtl" | "ltr";
}

export const APP_LANGUAGES: AppLanguage[] = [
  { code: "fa", nameNative: "فارسی", nameEnglish: "Persian", direction: "rtl" },
  { code: "en", nameNative: "English", nameEnglish: "English", direction: "ltr" },
  { code: "ar", nameNative: "العربية", nameEnglish: "Arabic", direction: "rtl" },
  { code: "ru", nameNative: "Русский", nameEnglish: "Russian", direction: "ltr" },
  { code: "tr", nameNative: "Türkçe", nameEnglish: "Turkish", direction: "ltr" },
];

export const DEFAULT_LANGUAGE: AppLanguageCode = "fa";

export function getLanguage(code?: string | null): AppLanguage {
  const found = APP_LANGUAGES.find((l) => l.code === code);
  return found ?? APP_LANGUAGES.find((l) => l.code === DEFAULT_LANGUAGE)!;
}

export function applyDocumentLanguage(code?: string | null): void {
  if (typeof document === "undefined") return;
  const lang = getLanguage(code);
  document.documentElement.lang = lang.code;
  document.documentElement.dir = lang.direction;
}
