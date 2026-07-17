"use client";

import { useEffect, useState } from "react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { ThemeProvider } from "next-themes";
import { useAuthStore } from "@/stores/auth-store";
import { useThemeStore } from "@/stores/theme-store";

export function Providers({ children }: { children: React.ReactNode }) {
  const [queryClient] = useState(
    () =>
      new QueryClient({
        defaultOptions: {
          queries: { staleTime: 30_000, retry: 1 },
        },
      })
  );

  return (
    <QueryClientProvider client={queryClient}>
      <ThemeProvider attribute="class" defaultTheme="system" enableSystem>
        <PlatformThemeInitializer>
          <AuthInitializer>{children}</AuthInitializer>
        </PlatformThemeInitializer>
      </ThemeProvider>
    </QueryClientProvider>
  );
}

function PlatformThemeInitializer({ children }: { children: React.ReactNode }) {
  const fetchTheme = useThemeStore((s) => s.fetchTheme);

  useEffect(() => {
    void fetchTheme();
  }, [fetchTheme]);

  return <>{children}</>;
}

function AuthInitializer({ children }: { children: React.ReactNode }) {
  const { fetchMe, isAuthenticated, isLoading } = useAuthStore();

  useEffect(() => {
    if (!isAuthenticated && !isLoading) {
      fetchMe();
    }
  }, []);

  return <>{children}</>;
}
