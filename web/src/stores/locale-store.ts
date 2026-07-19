"use client";

import { create } from "zustand";
import {
  LOCALE_STORAGE_KEY,
  applyDocumentLocale,
  defaultLocale,
  resolveLocale,
  type Locale,
} from "@/i18n/config";

interface LocaleState {
  locale: Locale;
  hydrated: boolean;
  setLocale: (locale: Locale | string | null | undefined) => void;
  hydrate: (preferred?: string | null) => void;
}

function readStoredLocale(): Locale | null {
  if (typeof window === "undefined") return null;
  try {
    const raw = window.localStorage.getItem(LOCALE_STORAGE_KEY);
    return raw ? resolveLocale(raw) : null;
  } catch {
    return null;
  }
}

function persistLocale(locale: Locale): void {
  if (typeof window === "undefined") return;
  try {
    window.localStorage.setItem(LOCALE_STORAGE_KEY, locale);
  } catch {
    /* ignore quota / private mode */
  }
}

export const useLocaleStore = create<LocaleState>((set, get) => ({
  locale: defaultLocale,
  hydrated: false,

  setLocale: (value) => {
    const locale = resolveLocale(value);
    if (get().locale === locale && get().hydrated) {
      applyDocumentLocale(locale);
      return;
    }
    persistLocale(locale);
    applyDocumentLocale(locale);
    set({ locale, hydrated: true });
  },

  hydrate: (preferred) => {
    if (get().hydrated) {
      if (preferred) get().setLocale(preferred);
      return;
    }
    const fromProfile = preferred ? resolveLocale(preferred) : null;
    const fromStorage = readStoredLocale();
    const locale = fromProfile ?? fromStorage ?? defaultLocale;
    persistLocale(locale);
    applyDocumentLocale(locale);
    set({ locale, hydrated: true });
  },
}));
