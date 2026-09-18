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
  defaults: Record<string, string> = DEFAULT_CALENDAR_DEFAULTS,
  isLoggedIn: boolean = true
): CalendarSystem {
  if (!isLoggedIn) {
    return "gregorian";
  }
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
  /** When true, use date+time default pattern (hour:minute). */
  withTime?: boolean;
}

/** Default absolute date+time patterns (hour:minute included). */
export function defaultDateTimePattern(calendar: CalendarSystem): string {
  return calendar === "jalali" ? "d MMMM yyyy، HH:mm" : "MMM d, yyyy, HH:mm";
}

export function defaultDatePattern(calendar: CalendarSystem): string {
  return calendar === "jalali" ? "d MMMM yyyy" : "MMM d, yyyy";
}

/** Display clock for UI dates — matches next-intl and Iranian users. */
export const DISPLAY_TIME_ZONE = "Asia/Tehran";

/**
 * Build a Date whose local wall clock equals `timeZone`, so date-fns/jalali
 * format the same string on UTC servers and Tehran (or any) clients.
 */
export function zonedWallClockDate(date: Date, timeZone: string = DISPLAY_TIME_ZONE): Date {
  const parts = new Intl.DateTimeFormat("en-GB", {
    timeZone,
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
    hour: "2-digit",
    minute: "2-digit",
    second: "2-digit",
    hourCycle: "h23",
  }).formatToParts(date);
  const num = (type: Intl.DateTimeFormatPartTypes) =>
    Number(parts.find((part) => part.type === type)?.value);
  return new Date(
    num("year"),
    num("month") - 1,
    num("day"),
    num("hour"),
    num("minute"),
    num("second"),
  );
}

export function formatDateString(date: string | Date | null | undefined, options: FormatDateOptions = {}): string {
  if (!date) return "-";
  const instant = typeof date === "string" ? new Date(date) : date;
  if (Number.isNaN(instant.getTime())) return "-";
  const d = zonedWallClockDate(instant);

  const locale = options.locale ?? "fa";
  const calendar = options.calendar ?? "gregorian";
  const pattern =
    options.pattern ??
    (options.withTime ? defaultDateTimePattern(calendar) : defaultDatePattern(calendar));

  if (calendar === "jalali") {
    return formatJalali(d, pattern, { locale: faIR });
  }
  return formatGregorian(d, pattern, { locale: locale.startsWith("fa") ? enUS : enUS });
}
