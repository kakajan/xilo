import { clsx, type ClassValue } from "clsx";
import { twMerge } from "tailwind-merge";
import { formatDateString, resolveCalendar, DEFAULT_CALENDAR_DEFAULTS } from "@/lib/format-date";

export function cn(...inputs: ClassValue[]) {
  return twMerge(clsx(inputs));
}

/** Sync formatter for server components; defaults to fa → jalali. */
export function formatDate(date: string, preferredCalendar?: string) {
  const calendar = resolveCalendar(preferredCalendar, "fa", DEFAULT_CALENDAR_DEFAULTS);
  return formatDateString(date, { calendar, locale: "fa" });
}

export function readingTimeText(minutes: number) {
  return `${minutes} دقیقه مطالعه`;
}

export function getInitials(name: string) {
  return name
    .split(" ")
    .map((n) => n[0])
    .join("")
    .toUpperCase()
    .slice(0, 2);
}
