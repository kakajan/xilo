"use client";

import { useEffect } from "react";
import { useRouter } from "next/navigation";
import { useAuthStore } from "@/stores/auth-store";

type UseRequireAuthOptions = {
  /** Default true — redirect to /login after bootstrap if unauthenticated. */
  redirectToLogin?: boolean;
};

/**
 * Wait for auth bootstrap before treating the user as logged out.
 * Prevents reload races where Zustand starts with isAuthenticated=false.
 */
export function useRequireAuth(opts?: UseRequireAuthOptions) {
  const redirectToLogin = opts?.redirectToLogin !== false;
  const router = useRouter();
  const user = useAuthStore((s) => s.user);
  const isAuthenticated = useAuthStore((s) => s.isAuthenticated);
  const authChecked = useAuthStore((s) => s.authChecked);
  const isLoading = useAuthStore((s) => s.isLoading);

  useEffect(() => {
    if (redirectToLogin && authChecked && !isAuthenticated) {
      router.replace("/login");
    }
  }, [redirectToLogin, authChecked, isAuthenticated, router]);

  return {
    user,
    isAuthenticated,
    authChecked,
    isLoading,
    /** True after the first bootstrap attempt finishes. */
    ready: authChecked,
  };
}
