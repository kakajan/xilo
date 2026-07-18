/** Browser token cache for SPA reloads (aile.ir → brain.aile.ir). */

const ACCESS_KEY = "xilo_access_token";
const REFRESH_KEY = "xilo_refresh_token";

function canUseStorage(): boolean {
  return typeof window !== "undefined" && typeof sessionStorage !== "undefined";
}

export function getAccessToken(): string | null {
  if (!canUseStorage()) return null;
  return sessionStorage.getItem(ACCESS_KEY);
}

export function getRefreshToken(): string | null {
  if (!canUseStorage()) return null;
  return sessionStorage.getItem(REFRESH_KEY);
}

export function setAuthTokens(access: string, refresh?: string): void {
  if (!canUseStorage()) return;
  if (access) sessionStorage.setItem(ACCESS_KEY, access);
  if (refresh) sessionStorage.setItem(REFRESH_KEY, refresh);
}

export function clearAuthTokens(): void {
  if (!canUseStorage()) return;
  sessionStorage.removeItem(ACCESS_KEY);
  sessionStorage.removeItem(REFRESH_KEY);
}
