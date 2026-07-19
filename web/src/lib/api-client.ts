import {
  clearAuthTokens,
  getAccessToken,
  getRefreshToken,
  setAuthTokens,
} from "@/lib/auth-tokens";

const API_BASE = process.env.NEXT_PUBLIC_API_URL || "http://localhost:8000";

function isAuthBootstrapPath(path: string): boolean {
  return (
    path === "/api/auth/me" ||
    path.startsWith("/api/auth/me?") ||
    path === "/api/auth/refresh" ||
    path.startsWith("/api/auth/refresh?")
  );
}

function authHeaders(extra?: HeadersInit): Record<string, string> {
  const headers: Record<string, string> = {
    ...(extra as Record<string, string>),
  };
  const token = getAccessToken();
  if (token && !headers.Authorization) {
    headers.Authorization = `Bearer ${token}`;
  }
  return headers;
}

async function refreshSession(): Promise<boolean> {
  // Body may be {}; backend falls back to HttpOnly refresh cookie.
  const body: Record<string, string> = {};
  const refresh = getRefreshToken();
  if (refresh) body.refresh_token = refresh;

  const headers: Record<string, string> = { "Content-Type": "application/json" };
  if (refresh) headers["X-Refresh-Token"] = refresh;

  const refreshRes = await fetch(`${API_BASE}/api/auth/refresh`, {
    method: "POST",
    credentials: "include",
    headers,
    body: JSON.stringify(body),
  });

  if (!refreshRes.ok) {
    clearAuthTokens();
    return false;
  }

  try {
    const data = (await refreshRes.json()) as {
      access_token?: string;
      refresh_token?: string;
    };
    if (data.access_token) {
      setAuthTokens(data.access_token, data.refresh_token);
    }
  } catch {
    // cookies-only refresh still counts as success
  }
  return true;
}

export async function apiFetch<T>(path: string, options: RequestInit = {}): Promise<T> {
  const headers = authHeaders(options.headers);

  if (options.body && typeof options.body === "string" && !headers["Content-Type"]) {
    headers["Content-Type"] = "application/json";
  }

  let res = await fetch(`${API_BASE}${path}`, {
    ...options,
    headers,
    credentials: "include",
  });

  if (res.status === 401 && !isAuthBootstrapPath(path)) {
    const ok = await refreshSession();
    if (ok) {
      res = await fetch(`${API_BASE}${path}`, {
        ...options,
        headers: authHeaders(options.headers),
        credentials: "include",
      });
    } else if (typeof window !== "undefined") {
      window.dispatchEvent(new Event("auth:logout"));
      throw new Error("session expired");
    } else {
      throw new Error("session expired");
    }
  } else if (res.status === 401 && (path === "/api/auth/me" || path.startsWith("/api/auth/me?"))) {
    // Soft restore for bootstrap — never emit auth:logout (avoids reload loops).
    const ok = await refreshSession();
    if (ok) {
      res = await fetch(`${API_BASE}${path}`, {
        ...options,
        headers: authHeaders(options.headers),
        credentials: "include",
      });
    }
  }

  if (!res.ok) {
    const err = await res.json().catch(() => ({ error: "request failed" }));
    const message = typeof err.error === "string" ? err.error : "request failed";
    throw new Error(`${message} (${res.status})`);
  }

  return res.json();
}

export async function apiUpload<T>(path: string, formData: FormData): Promise<T> {
  let res = await fetch(`${API_BASE}${path}`, {
    method: "POST",
    body: formData,
    credentials: "include",
    headers: authHeaders(),
  });

  if (res.status === 401) {
    const ok = await refreshSession();
    if (ok) {
      res = await fetch(`${API_BASE}${path}`, {
        method: "POST",
        body: formData,
        credentials: "include",
        headers: authHeaders(),
      });
    }
  }

  if (!res.ok) {
    const err = await res.json().catch(() => ({ error: "upload failed" }));
    throw new Error(err.error || "upload failed");
  }

  return res.json();
}
