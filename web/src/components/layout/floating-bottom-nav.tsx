"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import { Compass, Home, MessageCircle, User } from "lucide-react";
import { useTranslations } from "next-intl";
import { cn } from "@/lib/utils";
import { useAuthStore } from "@/stores/auth-store";

interface FloatingBottomNavProps {
  visible?: boolean;
}

export function FloatingBottomNav({ visible = true }: FloatingBottomNavProps) {
  const t = useTranslations("common.nav");
  const pathname = usePathname();
  const username = useAuthStore((s) => s.user?.username);

  const tabs = [
    { href: "/", label: t("feed"), icon: Home, match: (p: string) => p === "/" },
    {
      href: "/discover",
      label: t("discover"),
      icon: Compass,
      match: (p: string) => p.startsWith("/discover"),
    },
    {
      href: "/chat",
      label: t("messages"),
      icon: MessageCircle,
      match: (p: string) => p.startsWith("/chat") || p.startsWith("/saved"),
    },
    {
      href: "/profile",
      label: t("profile"),
      icon: User,
      match: (p: string) => p === "/profile" || p.startsWith("/settings"),
    },
  ] as const;

  return (
    <nav
      aria-label={t("mainNavigation")}
      className={cn(
        "pointer-events-none fixed inset-x-0 bottom-0 z-50 px-6 pb-3 pt-2 md:hidden",
        "bg-gradient-to-t from-background via-background/80 to-transparent",
        "transition-[transform,opacity] duration-300 ease-[cubic-bezier(0.4,0,0.2,1)]",
        visible ? "translate-y-0 opacity-100" : "translate-y-[88px] opacity-0"
      )}
    >
      <div
        className={cn(
          "pointer-events-auto mx-auto flex h-14 max-w-lg items-center justify-evenly rounded-full",
          "border border-white/40 bg-card/55 shadow-lg backdrop-blur-xl",
          "dark:border-white/10"
        )}
      >
        {tabs.map((tab) => {
          const href =
            tab.href === "/profile" ? (username ? `/${username}` : "/login") : tab.href;
          const active =
            tab.href === "/profile"
              ? Boolean(username && pathname === `/${username}`) ||
                pathname.startsWith("/settings")
              : tab.match(pathname);
          const Icon = tab.icon;
          return (
            <Link
              key={tab.href}
              href={href}
              className={cn(
                "flex min-h-11 min-w-11 items-center justify-center gap-1.5 rounded-2xl px-3 py-2 transition-colors",
                active ? "bg-primary/12 text-primary" : "text-muted-foreground"
              )}
            >
              <Icon className="h-6 w-6 shrink-0" strokeWidth={active ? 2.4 : 2} />
              {active && (
                <span className="min-w-0 text-xs font-bold">{tab.label}</span>
              )}
            </Link>
          );
        })}
      </div>
    </nav>
  );
}
