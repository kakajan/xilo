"use client";

import { useCallback, useEffect, useRef, useState, type MutableRefObject } from "react";
import { useEditorStore } from "@/stores/editor-store";

const DEFAULT_DELAY_MS = 800;

/** True once zustand persist has rehydrated from localStorage. */
export function useEditorDraftHydrated(): boolean {
  const [hydrated, setHydrated] = useState(() => useEditorStore.persist.hasHydrated());

  useEffect(() => {
    setHydrated(useEditorStore.persist.hasHydrated());
    return useEditorStore.persist.onFinishHydration(() => setHydrated(true));
  }, []);

  return hydrated;
}

type ContentSnapshot = { html: string; json: string };

/**
 * Debounced local draft persistence: saves after typing pause,
 * and flushes immediately on tab hide / unmount / beforeunload.
 */
export function useDraftAutosave(options: {
  persist: (json: string) => void;
  contentRef: MutableRefObject<ContentSnapshot | null>;
  delayMs?: number;
  enabled?: boolean;
}) {
  const { persist, contentRef, delayMs = DEFAULT_DELAY_MS, enabled = true } = options;
  const persistRef = useRef(persist);
  persistRef.current = persist;
  const timerRef = useRef<ReturnType<typeof setTimeout> | null>(null);
  const latestJsonRef = useRef<string | null>(null);

  const flush = useCallback(() => {
    if (timerRef.current) {
      clearTimeout(timerRef.current);
      timerRef.current = null;
    }
    const json = latestJsonRef.current ?? contentRef.current?.json;
    if (!json || !enabled) return;
    persistRef.current(json);
  }, [contentRef, enabled]);

  const schedule = useCallback(
    (json: string) => {
      if (!enabled) return;
      latestJsonRef.current = json;
      if (timerRef.current) clearTimeout(timerRef.current);
      timerRef.current = setTimeout(() => {
        timerRef.current = null;
        persistRef.current(json);
      }, delayMs);
    },
    [delayMs, enabled]
  );

  useEffect(() => {
    if (!enabled) return;

    const onVisibility = () => {
      if (document.visibilityState === "hidden") flush();
    };
    const onPageHide = () => flush();

    document.addEventListener("visibilitychange", onVisibility);
    window.addEventListener("pagehide", onPageHide);
    window.addEventListener("beforeunload", onPageHide);
    return () => {
      flush();
      document.removeEventListener("visibilitychange", onVisibility);
      window.removeEventListener("pagehide", onPageHide);
      window.removeEventListener("beforeunload", onPageHide);
    };
  }, [enabled, flush]);

  return { schedule, flush };
}
