import { format as formatGregorian } from "date-fns";
import { format as formatJalali } from "date-fns-jalali";
import { enUS } from "date-fns/locale";
import { faIR } from "date-fns-jalali/locale/fa-IR";

export type CalendarSystem = "jalali" | "gregorian";
export type CalendarPreference = "auto" | CalendarSystem;

export const DEFAULT_CALENDAR_DEFAULTS: Record<string, CalendarSystem> = {
  fa: "jalali",
  en: "gregorian",
  ar: "gregorian",
  ru: "gregorian",
  tr: "gregorian",
};

export function resolveCalendar(
  userPref: string | null | undefined,
  locale: string,
  defaults: Record<string, string> = DEFAULT_CALENDAR_DEFAULTS
): CalendarSystem {
  if (userPref === "jalali" || userPref === "gregorian") {
    return userPref;
  }
  const fromDefaults = defaults[locale];
  if (fromDefaults === "jalali" || fromDefaults === "gregorian") {
    return fromDefaults;
  }
  return DEFAULT_CALENDAR_DEFAULTS[locale] ?? "gregorian";
}

export interface FormatDateOptions {
  calendar?: CalendarSystem;
  locale?: string;
  /** date-fns pattern; defaults depend on calendar */
  pattern?: string;
}

export function formatDateString(date: string | Date | null | undefined, options: FormatDateOptions = {}): string {
  if (!date) return "-";
  const d = typeof date === "string" ? new Date(date) : date;
  if (Number.isNaN(d.getTime())) return "-";

  const locale = options.locale ?? "fa";
  const calendar = options.calendar ?? resolveCalendar("auto", locale);
  const pattern = options.pattern ?? (calendar === "jalali" ? "d MMMM yyyy" : "MMM d, yyyy");

  if (calendar === "jalali") {
    return formatJalali(d, pattern, { locale: faIR });
  }
  return formatGregorian(d, pattern, { locale: locale.startsWith("fa") ? enUS : enUS });
}
