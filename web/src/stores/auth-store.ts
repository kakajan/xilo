import { create } from "zustand";
import type { User, AuthResponse, LoginRequest, RegisterRequest } from "@/types/user";
import { apiFetch } from "@/lib/api-client";
import {
  clearAuthTokens,
  getAccessToken,
  getRefreshToken,
  setAuthTokens,
} from "@/lib/auth-tokens";
import { markOnboardingPending } from "@/lib/onboarding";

function isUnauthorizedError(err: unknown): boolean {
  const msg = err instanceof Error ? err.message : String(err);
  return (
    /\(401\)/.test(msg) ||
    /session expired/i.test(msg) ||
    /unauthorized/i.test(msg) ||
    /missing refresh token/i.test(msg) ||
    /invalid refresh token/i.test(msg)
  );
}

interface AuthState {
  user: User | null;
  isLoading: boolean;
  /** True after the first /me (or failed) bootstrap attempt. */
  authChecked: boolean;
  isAuthenticated: boolean;

  login: (req: LoginRequest) => Promise<void>;
  register: (req: RegisterRequest) => Promise<void>;
  logout: () => Promise<void>;
  fetchMe: (opts?: { force?: boolean }) => Promise<void>;
  applyAuthResponse: (res: AuthResponse) => void;
  updateProfile: (data: {
    display_name?: string;
    bio?: string;
    avatar_url?: string;
    preferred_language?: string;
    preferred_calendar?: string;
    username?: string;
  }) => Promise<void>;
}

let bootstrapPromise: Promise<void> | null = null;

export const useAuthStore = create<AuthState>()((set, get) => ({
  user: null,
  isLoading: true,
  authChecked: false,
  isAuthenticated: false,

  applyAuthResponse: (res) => {
    setAuthTokens(res.access_token, res.refresh_token);
    set({ user: res.user, isAuthenticated: true, isLoading: false, authChecked: true });
  },

  login: async (req) => {
    set({ isLoading: true });
    try {
      const res = await apiFetch<AuthResponse>("/api/auth/login", {
        method: "POST",
        body: JSON.stringify(req),
      });
      get().applyAuthResponse(res);
    } catch (err) {
      set({ isLoading: false, authChecked: true });
      throw err instanceof Error ? err : new Error("login failed");
    }
  },

  register: async (req) => {
    set({ isLoading: true });
    try {
      const res = await apiFetch<AuthResponse>("/api/auth/register", {
        method: "POST",
        body: JSON.stringify(req),
      });
      markOnboardingPending();
      get().applyAuthResponse(res);
    } catch (err) {
      set({ isLoading: false, authChecked: true });
      throw err instanceof Error ? err : new Error("registration failed");
    }
  },


  logout: async () => {
    const wasAuthenticated = get().isAuthenticated;
    try {
      if (wasAuthenticated) {
        const refresh = getRefreshToken();
        await apiFetch("/api/auth/logout", {
          method: "POST",
          body: JSON.stringify(refresh ? { refresh_token: refresh } : {}),
        });
      }
    } catch {
      // ignore network errors on logout
    }
    clearAuthTokens();
    set({ user: null, isAuthenticated: false, isLoading: false, authChecked: true });
    if (wasAuthenticated && typeof window !== "undefined") {
      window.location.href = "/";
    }
  },

  fetchMe: async (opts) => {
    const force = opts?.force === true;
    if (!force && get().authChecked) return;
    if (!force && bootstrapPromise) return bootstrapPromise;

    const run = async () => {
      if (!force) set({ isLoading: true });
      try {
        const user = await apiFetch<User>("/api/auth/me");
        set({ user, isAuthenticated: true, isLoading: false, authChecked: true });
      } catch (err) {
        if (force) {
          // Keep existing session UI; caller handles errors.
          set({ isLoading: false, authChecked: true });
          throw new Error("failed to refresh profile");
        }
        if (isUnauthorizedError(err)) {
          clearAuthTokens();
          set({ user: null, isAuthenticated: false, isLoading: false, authChecked: true });
          return;
        }
        // Transport/5xx: do not wipe a still-valid cookie/local session.
        const hasTokenHint = Boolean(getAccessToken() || getRefreshToken());
        set({
          isLoading: false,
          authChecked: true,
          isAuthenticated: get().isAuthenticated || hasTokenHint,
        });
      }
    };

    if (force) {
      await run();
      return;
    }

    bootstrapPromise = run().finally(() => {
      bootstrapPromise = null;
    });
    return bootstrapPromise;
  },

  updateProfile: async (data) => {
    const user = await apiFetch<User>("/api/auth/me", {
      method: "PATCH",
      body: JSON.stringify(data),
    });
    set({ user });
  },
}));

if (typeof window !== "undefined") {
  window.addEventListener("auth:logout", () => {
    const state = useAuthStore.getState();
    if (state.isAuthenticated) {
      void state.logout();
    } else {
      clearAuthTokens();
      useAuthStore.setState({
        user: null,
        isAuthenticated: false,
        isLoading: false,
        authChecked: true,
      });
    }
  });
}
