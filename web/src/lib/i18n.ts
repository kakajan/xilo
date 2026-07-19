/**
 * Web UI internationalization.
 * Catalogs live under `src/i18n/messages/{locale}/`.
 * Runtime wiring: NextIntlClientProvider + locale store (preferred_language / localStorage).
 */
export {
  locales,
  localeConfig,
  defaultLocale,
  resolveLocale,
  type Locale,
} from "@/i18n/config";
export { getMessages, messagesByLocale } from "@/i18n/messages";
