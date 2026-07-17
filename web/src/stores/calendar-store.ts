import { create } from "zustand";
import { apiFetch } from "@/lib/api-client";
import {
  DEFAULT_CALENDAR_DEFAULTS,
  type CalendarSystem,
} from "@/lib/format-date";

interface CalendarState {
  defaults: Record<string, CalendarSystem>;
  loaded: boolean;
  loadDefaults: () => Promise<void>;
  setDefaults: (defaults: Record<string, CalendarSystem>) => void;
}

function normalizeDefaults(raw: Record<string, string> | undefined): Record<string, CalendarSystem> {
  const out: Record<string, CalendarSystem> = { ...DEFAULT_CALENDAR_DEFAULTS };
  if (!raw) return out;
  for (const [lang, cal] of Object.entries(raw)) {
    if (cal === "jalali" || cal === "gregorian") {
      out[lang] = cal;
    }
  }
  return out;
}

export const useCalendarStore = create<CalendarState>()((set, get) => ({
  defaults: DEFAULT_CALENDAR_DEFAULTS,
  loaded: false,

  loadDefaults: async () => {
    if (get().loaded) return;
    try {
      const res = await apiFetch<{ calendar_defaults?: Record<string, string> }>("/api/languages");
      set({ defaults: normalizeDefaults(res.calendar_defaults), loaded: true });
    } catch {
      set({ defaults: DEFAULT_CALENDAR_DEFAULTS, loaded: true });
    }
  },

  setDefaults: (defaults) => set({ defaults, loaded: true }),
}));
