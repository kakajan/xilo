/** Browser token cache for SPA reloads (aile.ir → brain.aile.ir). */

const ACCESS_KEY = "xilo_access_token";
const REFRESH_KEY = "xilo_refresh_token";

function canUseStorage(): boolean {
  return typeof window !== "undefined" && typeof localStorage !== "undefined";
}

/** One-time migrate from sessionStorage (legacy) → localStorage. */
function migrateFromSession(): void {
  if (!canUseStorage() || typeof sessionStorage === "undefined") return;
  try {
    for (const key of [ACCESS_KEY, REFRESH_KEY]) {
      if (!localStorage.getItem(key)) {
        const legacy = sessionStorage.getItem(key);
        if (legacy) localStorage.setItem(key, legacy);
      }
      sessionStorage.removeItem(key);
    }
  } catch {
    // private mode / blocked storage
  }
}

export function getAccessToken(): string | null {
  if (!canUseStorage()) return null;
  migrateFromSession();
  try {
    return localStorage.getItem(ACCESS_KEY);
  } catch {
    return null;
  }
}

export function getRefreshToken(): string | null {
  if (!canUseStorage()) return null;
  migrateFromSession();
  try {
    return localStorage.getItem(REFRESH_KEY);
  } catch {
    return null;
  }
}

export function setAuthTokens(access: string, refresh?: string): void {
  if (!canUseStorage()) return;
  try {
    if (access) localStorage.setItem(ACCESS_KEY, access);
    if (refresh) localStorage.setItem(REFRESH_KEY, refresh);
    if (typeof sessionStorage !== "undefined") {
      sessionStorage.removeItem(ACCESS_KEY);
      sessionStorage.removeItem(REFRESH_KEY);
    }
  } catch {
    // private mode / blocked storage
  }
}

export function clearAuthTokens(): void {
  if (!canUseStorage()) return;
  try {
    localStorage.removeItem(ACCESS_KEY);
    localStorage.removeItem(REFRESH_KEY);
    if (typeof sessionStorage !== "undefined") {
      sessionStorage.removeItem(ACCESS_KEY);
      sessionStorage.removeItem(REFRESH_KEY);
    }
  } catch {
    // private mode / blocked storage
  }
}
