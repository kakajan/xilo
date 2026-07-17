import { create } from "zustand";
import type { User, AuthResponse, LoginRequest, RegisterRequest } from "@/types/user";
import { apiFetch } from "@/lib/api-client";

interface AuthState {
  user: User | null;
  isLoading: boolean;
  isAuthenticated: boolean;

  login: (req: LoginRequest) => Promise<void>;
  register: (req: RegisterRequest) => Promise<void>;
  logout: () => Promise<void>;
  fetchMe: () => Promise<void>;
  updateProfile: (data: {
    display_name?: string;
    bio?: string;
    avatar_url?: string;
    preferred_language?: string;
    preferred_calendar?: string;
  }) => Promise<void>;
}

export const useAuthStore = create<AuthState>()((set, get) => ({
  user: null,
  isLoading: false,
  isAuthenticated: false,

  login: async (req) => {
    set({ isLoading: true });
    try {
      const res = await apiFetch<AuthResponse>("/api/auth/login", {
        method: "POST",
        body: JSON.stringify(req),
      });
      set({ user: res.user, isAuthenticated: true, isLoading: false });
    } catch {
      set({ isLoading: false });
      throw new Error("login failed");
    }
  },

  register: async (req) => {
    set({ isLoading: true });
    try {
      const res = await apiFetch<AuthResponse>("/api/auth/register", {
        method: "POST",
        body: JSON.stringify(req),
      });
      set({ user: res.user, isAuthenticated: true, isLoading: false });
    } catch {
      set({ isLoading: false });
      throw new Error("registration failed");
    }
  },

  logout: async () => {
    try {
      await apiFetch("/api/auth/logout", { method: "POST" });
    } catch {}
    set({ user: null, isAuthenticated: false });
    if (typeof window !== "undefined") {
      window.location.href = "/";
    }
  },

  fetchMe: async () => {
    if (get().isLoading || get().isAuthenticated) return;
    set({ isLoading: true });
    try {
      const user = await apiFetch<User>("/api/auth/me");
      set({ user, isAuthenticated: true, isLoading: false });
    } catch {
      set({ user: null, isAuthenticated: false, isLoading: false });
    }
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
    useAuthStore.getState().logout();
  });
}
