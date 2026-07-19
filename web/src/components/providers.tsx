"use client";

import { useEffect, useState } from "react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { ThemeProvider } from "next-themes";
import { I18nProvider } from "@/components/i18n-provider";
import { useAuthStore } from "@/stores/auth-store";
import { useBrandStore } from "@/stores/brand-store";
import { useThemeStore } from "@/stores/theme-store";

export function Providers({ children }: { children: React.ReactNode }) {
  const [queryClient] = useState(
    () =>
      new QueryClient({
        defaultOptions: {
          queries: {
            staleTime: 30_000,
            retry: (count, err) => {
              const msg = err instanceof Error ? err.message : "";
              if (
                msg.includes("(429)") ||
                msg.includes("Too Many") ||
                msg.includes("session expired")
              ) {
                return false;
              }
              return count < 1;
            },
            refetchOnWindowFocus: false,
          },
        },
      })
  );

  return (
    <QueryClientProvider client={queryClient}>
      <ThemeProvider
        attribute="class"
        defaultTheme="system"
        enableSystem
        disableTransitionOnChange
      >
        <I18nProvider>
          <PlatformThemeInitializer>
            <AuthInitializer>{children}</AuthInitializer>
          </PlatformThemeInitializer>
        </I18nProvider>
      </ThemeProvider>
    </QueryClientProvider>
  );
}

function PlatformThemeInitializer({ children }: { children: React.ReactNode }) {
  const fetchTheme = useThemeStore((s) => s.fetchTheme);
  const fetchBrand = useBrandStore((s) => s.fetchBrand);

  useEffect(() => {
    void fetchTheme();
    void fetchBrand();
  }, [fetchTheme, fetchBrand]);

  return <>{children}</>;
}

function AuthInitializer({ children }: { children: React.ReactNode }) {
  const fetchMe = useAuthStore((s) => s.fetchMe);

  useEffect(() => {
    void fetchMe();
  }, [fetchMe]);

  return <>{children}</>;
}
