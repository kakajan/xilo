"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useState } from "react";
import {
  Search,
  PlusCircle,
  Bell,
  LogIn,
  UserPlus,
  LayoutDashboard,
  Settings,
  Bookmark,
} from "lucide-react";
import { useQuery } from "@tanstack/react-query";
import { useTranslations } from "next-intl";
import { canCreatePost } from "@/lib/auth/permissions";
import { useAuthStore } from "@/stores/auth-store";
import { apiFetch } from "@/lib/api-client";
import { BrandLogo } from "@/components/brand/brand-logo";
import { Button } from "@/components/ui/button";
import { Avatar, AvatarFallback, AvatarImage } from "@/components/ui/avatar";
import { ThemeToggle } from "@/components/shared/theme-toggle";
import { cn, getInitials } from "@/lib/utils";

interface NavbarProps {
  chromeVisible?: boolean;
}

export function Navbar({ chromeVisible = true }: NavbarProps) {
  const t = useTranslations("common.nav");
  const tLang = useTranslations("common.language");
  const { user, isAuthenticated, authChecked } = useAuthStore();
  const router = useRouter();
  const [search, setSearch] = useState("");

  const { data: unreadData } = useQuery({
    queryKey: ["notifications", "unread-count"],
    queryFn: () => apiFetch<{ unread: number }>("/api/notifications/unread-count"),
    enabled: isAuthenticated,
    refetchInterval: 60_000,
  });
  const unread = unreadData?.unread ?? 0;

  const handleSearch = (e: React.FormEvent) => {
    e.preventDefault();
    if (search.trim()) {
      router.push(`/search?q=${encodeURIComponent(search.trim())}`);
    }
  };

  return (
    <header
      className={cn(
        "sticky top-0 z-50 border-b bg-background/95 backdrop-blur transition-[transform,opacity] duration-200 ease-out",
        chromeVisible ? "translate-y-0 opacity-100" : "-translate-y-14 opacity-[0.85]"
      )}
    >
      <div className="mx-auto flex h-14 max-w-5xl items-center justify-between px-4">
        <Link href="/" className="flex items-center shrink-0" aria-label="aile">
          {/* Compact mark on narrow screens; wordmark already includes the mark */}
          <BrandLogo variant="mark" className="h-8 w-auto sm:hidden" />
          <BrandLogo variant="wordmark" className="hidden h-8 w-auto sm:block" />
        </Link>

        <form
          onSubmit={handleSearch}
          className="mx-4 hidden max-w-sm flex-1 items-center gap-2 sm:flex"
        >
          <div className="relative w-full">
            <Search className="absolute start-3 top-1/2 h-4 w-4 -translate-y-1/2 text-muted-foreground" />
            <input
              type="search"
              placeholder={t("searchPlaceholder")}
              value={search}
              onChange={(e) => setSearch(e.target.value)}
              className="w-full rounded-lg border bg-background py-2 pe-4 ps-9 text-sm min-h-11"
            />
          </div>
        </form>

        <div className="flex items-center gap-1">
          <ThemeToggle />
          {!authChecked ? (
            <div
              className="h-8 w-20 animate-pulse rounded-md bg-muted"
              aria-hidden
            />
          ) : isAuthenticated ? (
            <>
              {(user?.role === "admin" || user?.role === "superadmin") && (
                <Button
                  variant="ghost"
                  size="icon"
                  className="min-h-11 min-w-11"
                  onClick={() => router.push("/dashboard")}
                  aria-label={t("dashboard")}
                >
                  <LayoutDashboard className="h-5 w-5" />
                </Button>
              )}
              {canCreatePost(user?.role) && (
                <Button
                  variant="ghost"
                  size="icon"
                  className="min-h-11 min-w-11 hidden sm:inline-flex"
                  onClick={() => router.push("/write")}
                  aria-label={t("write")}
                >
                  <PlusCircle className="h-5 w-5" />
                </Button>
              )}
              <Button
                variant="ghost"
                size="icon"
                className="relative min-h-11 min-w-11"
                onClick={() => router.push("/notifications")}
                aria-label={t("notifications")}
              >
                <Bell className="h-5 w-5" />
                {unread > 0 && (
                  <span className="absolute end-1 top-1 flex h-4 min-w-4 items-center justify-center rounded-full bg-destructive px-1 text-[10px] font-medium text-destructive-foreground">
                    {unread > 99 ? "99+" : unread}
                  </span>
                )}
              </Button>
              <Button
                variant="ghost"
                size="icon"
                className="min-h-11 min-w-11"
                onClick={() => router.push("/saved")}
                aria-label={t("saved")}
              >
                <Bookmark className="h-5 w-5" />
              </Button>
              <Button
                variant="ghost"
                size="icon"
                className="min-h-11 min-w-11"
                onClick={() => router.push("/settings")}
                aria-label={t("settings")}
              >
                <Settings className="h-5 w-5" />
              </Button>
              <Button
                variant="ghost"
                size="icon"
                className="min-h-11 min-w-11"
                onClick={() => router.push(`/${user?.username}`)}
                aria-label={t("profile")}
              >
                <Avatar className="h-8 w-8">
                  {user?.avatar_url ? (
                    <AvatarImage src={user.avatar_url} alt="" />
                  ) : null}
                  <AvatarFallback>
                    {user
                      ? getInitials(user.display_name || user.username)
                      : tLang("unknownInitial")}
                  </AvatarFallback>
                </Avatar>
              </Button>
            </>
          ) : (
            <>
              <Button
                variant="ghost"
                size="sm"
                className="min-h-11"
                onClick={() => router.push("/login")}
              >
                <LogIn className="ms-1 h-4 w-4" />
                {t("login")}
              </Button>
              <Button size="sm" className="min-h-11" onClick={() => router.push("/register")}>
                <UserPlus className="ms-1 h-4 w-4" />
                {t("register")}
              </Button>
            </>
          )}
        </div>
      </div>
    </header>
  );
}
