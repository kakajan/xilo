const STORAGE_KEY = "xilo_analytics_session";

function createSessionId(): string {
  if (typeof crypto !== "undefined" && typeof crypto.randomUUID === "function") {
    return crypto.randomUUID();
  }
  return `anon-${Date.now().toString(36)}-${Math.random().toString(36).slice(2, 12)}`;
}

/** Stable anonymous session id used for view deduplication. */
export function getAnalyticsSessionId(): string {
  if (typeof window === "undefined") return "";
  try {
    const existing = window.localStorage.getItem(STORAGE_KEY);
    if (existing && existing.length >= 16) return existing;
    const next = createSessionId();
    window.localStorage.setItem(STORAGE_KEY, next);
    return next;
  } catch {
    return createSessionId();
  }
}
