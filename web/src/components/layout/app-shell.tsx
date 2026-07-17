"use client";

import { usePathname } from "next/navigation";
import { OfflineBanner } from "@/components/layout/offline-banner";
import { FloatingBottomNav } from "@/components/layout/floating-bottom-nav";
import { XiloFab } from "@/components/layout/xilo-fab";
import { Navbar } from "@/components/layout/navbar";
import { DesktopSideNav } from "@/components/layout/desktop-side-nav";
import { useChromeVisibility } from "@/hooks/use-chrome-visibility";
import { OnboardingGate } from "@/components/onboarding/onboarding-gate";

const BARE_PREFIXES = ["/write", "/dashboard", "/login", "/register"];

function isBareLayout(pathname: string) {
  return BARE_PREFIXES.some((p) => pathname === p || pathname.startsWith(`${p}/`));
}

export function AppShell({ children }: { children: React.ReactNode }) {
  const pathname = usePathname();
  const bare = isBareLayout(pathname);
  const { visible } = useChromeVisibility();

  if (bare) {
    return (
      <div className="min-h-screen bg-background">
        <OfflineBanner />
        <main className="mx-auto max-w-4xl px-4 py-6">{children}</main>
      </div>
    );
  }

  return (
    <OnboardingGate>
      <div className="min-h-screen bg-background">
        <OfflineBanner />
        <Navbar chromeVisible={visible} />
        <div className="mx-auto flex max-w-5xl gap-6 px-4 pb-28 pt-4 md:pb-8">
          <DesktopSideNav />
          <main className="min-w-0 flex-1">{children}</main>
        </div>
        <XiloFab chromeVisible={visible} />
        <FloatingBottomNav visible={visible} />
      </div>
    </OnboardingGate>
  );
}
