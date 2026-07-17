"use client";

import { useEffect } from "react";
import { useAuthStore } from "@/stores/auth-store";
import { useCalendarStore } from "@/stores/calendar-store";
import { formatDateString, resolveCalendar, type FormatDateOptions } from "@/lib/format-date";

/** Default UI locale until full i18n lands (platform default is fa). */
const UI_LOCALE = "fa";

export function useFormatDate() {
  const user = useAuthStore((s) => s.user);
  const defaults = useCalendarStore((s) => s.defaults);
  const loadDefaults = useCalendarStore((s) => s.loadDefaults);

  useEffect(() => {
    void loadDefaults();
  }, [loadDefaults]);

  const calendar = resolveCalendar(user?.preferred_calendar, UI_LOCALE, defaults);

  return (date: string | Date | null | undefined, options?: Omit<FormatDateOptions, "calendar" | "locale">) =>
    formatDateString(date, {
      ...options,
      calendar,
      locale: UI_LOCALE,
    });
}
