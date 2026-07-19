"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import { Plus } from "lucide-react";
import { useTranslations } from "next-intl";
import { canCreatePost } from "@/lib/auth/permissions";
import { useAuthStore } from "@/stores/auth-store";
import { cn } from "@/lib/utils";

interface XiloFabProps {
  chromeVisible?: boolean;
}

export function XiloFab({ chromeVisible = true }: XiloFabProps) {
  const t = useTranslations("common.nav");
  const pathname = usePathname();
  const isAuthenticated = useAuthStore((s) => s.isAuthenticated);
  const role = useAuthStore((s) => s.user?.role);
  const show =
    pathname === "/" && isAuthenticated && canCreatePost(role) && chromeVisible;

  return (
    <div
      className={cn(
        "pointer-events-none fixed bottom-[5.5rem] end-6 z-50 origin-bottom md:bottom-8",
        "transition-[transform,opacity] duration-200 ease-out",
        show ? "scale-100 opacity-100" : "scale-0 opacity-0"
      )}
    >
      {show ? (
        <Link
          href="/write"
          className="pointer-events-auto flex h-14 w-14 items-center justify-center rounded-full bg-primary text-primary-foreground shadow-md ring-1 ring-white/50"
          aria-label={t("writePost")}
        >
          <Plus className="h-7 w-7" strokeWidth={2.5} />
        </Link>
      ) : null}
    </div>
  );
}
