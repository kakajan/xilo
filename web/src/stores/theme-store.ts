"use client";

import { create } from "zustand";
import { apiFetch } from "@/lib/api-client";
import {
  applyThemeToDocument,
  DEFAULT_THEME,
  mergeTheme,
  type PlatformTheme,
} from "@/lib/theme";

interface ThemeState {
  theme: PlatformTheme;
  loaded: boolean;
  fetchTheme: () => Promise<void>;
  setTheme: (theme: PlatformTheme) => void;
}

export const useThemeStore = create<ThemeState>((set) => ({
  theme: DEFAULT_THEME,
  loaded: false,
  setTheme: (theme) => {
    applyThemeToDocument(theme);
    set({ theme, loaded: true });
  },
  fetchTheme: async () => {
    try {
      const data = await apiFetch<{ theme?: PlatformTheme }>("/api/platform/settings");
      const theme = mergeTheme(data.theme);
      applyThemeToDocument(theme);
      set({ theme, loaded: true });
    } catch {
      applyThemeToDocument(DEFAULT_THEME);
      set({ theme: DEFAULT_THEME, loaded: true });
    }
  },
}));
