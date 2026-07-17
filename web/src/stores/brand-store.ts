"use client";

import { create } from "zustand";
import { apiFetch } from "@/lib/api-client";
import { DEFAULT_BRAND, type PlatformBrand } from "@/lib/brand";

interface BrandState {
  brand: PlatformBrand;
  loaded: boolean;
  fetchBrand: () => Promise<void>;
  setBrand: (brand: PlatformBrand) => void;
}

export const useBrandStore = create<BrandState>((set) => ({
  brand: DEFAULT_BRAND,
  loaded: false,
  setBrand: (brand) => set({ brand, loaded: true }),
  fetchBrand: async () => {
    try {
      const data = await apiFetch<{ brand?: PlatformBrand }>("/api/platform/settings");
      set({ brand: data.brand ?? DEFAULT_BRAND, loaded: true });
    } catch {
      set({ brand: DEFAULT_BRAND, loaded: true });
    }
  },
}));
