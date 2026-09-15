"use client";

import { useCallback, useEffect } from "react";
import { create } from "zustand";

interface ChromeVisibilityState {
  visible: boolean;
  setVisible: (visible: boolean) => void;
  show: () => void;
}

export const useChromeVisibilityStore = create<ChromeVisibilityState>((set) => ({
  visible: true,
  setVisible: (visible) => set({ visible }),
  show: () => set({ visible: true }),
}));

let listenerAttached = false;
let lastY = 0;
const DEFAULT_THRESHOLD = 8;

function initScrollListener() {
  if (listenerAttached || typeof window === "undefined") return;
  listenerAttached = true;
  lastY = window.scrollY;

  const onScroll = () => {
    const y = window.scrollY;
    const delta = y - lastY;
    if (Math.abs(delta) < DEFAULT_THRESHOLD) return;
    const shouldShow = delta < 0 || y < 48;
    useChromeVisibilityStore.getState().setVisible(shouldShow);
    lastY = y;
  };

  window.addEventListener("scroll", onScroll, { passive: true });
}

/** Hide chrome on scroll-down, show on scroll-up (Android ChromeVisibility). */
export function useChromeVisibility() {
  const visible = useChromeVisibilityStore((s) => s.visible);
  const setVisible = useChromeVisibilityStore((s) => s.setVisible);

  useEffect(() => {
    initScrollListener();
  }, []);

  const show = useCallback(() => setVisible(true), [setVisible]);

  return { visible, show };
}
