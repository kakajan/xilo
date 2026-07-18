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
import { motion } from "framer-motion";
import { canCreatePost } from "@/lib/auth/permissions";
import { useAuthStore } from "@/stores/auth-store";
import { useBrandStore } from "@/stores/brand-store";
import { Button } from "@/components/ui/button";
import { Avatar, AvatarFallback, AvatarImage } from "@/components/ui/avatar";
import { ThemeToggle } from "@/components/shared/theme-toggle";
import { getInitials } from "@/lib/utils";

interface NavbarProps {
  chromeVisible?: boolean;
}

export function Navbar({ chromeVisible = true }: NavbarProps) {
  const { user, isAuthenticated } = useAuthStore();
  const brandName = useBrandStore((s) => s.brand.name_fa);
  const router = useRouter();
  const [search, setSearch] = useState("");

  const handleSearch = (e: React.FormEvent) => {
    e.preventDefault();
    if (search.trim()) {
      router.push(`/search?q=${encodeURIComponent(search.trim())}`);
    }
  };

  return (
    <motion.header
      initial={false}
      animate={{ y: chromeVisible ? 0 : -56, opacity: chromeVisible ? 1 : 0.85 }}
      transition={{ duration: 0.25 }}
      className="sticky top-0 z-50 border-b bg-background/95 backdrop-blur"
    >
      <div className="mx-auto flex h-14 max-w-5xl items-center justify-between px-4">
        <Link href="/" className="text-xl font-bold text-primary">
          {brandName}
        </Link>

        <form
          onSubmit={handleSearch}
          className="mx-4 hidden max-w-sm flex-1 items-center gap-2 sm:flex"
        >
          <div className="relative w-full">
            <Search className="absolute start-3 top-1/2 h-4 w-4 -translate-y-1/2 text-muted-foreground" />
            <input
              type="search"
              placeholder="جستجو..."
              value={search}
              onChange={(e) => setSearch(e.target.value)}
              className="w-full rounded-lg border bg-background py-2 pe-4 ps-9 text-sm min-h-11"
            />
          </div>
        </form>

        <div className="flex items-center gap-1">
          <ThemeToggle />
          {isAuthenticated ? (
            <>
              {(user?.role === "admin" || user?.role === "superadmin") && (
                <Button
                  variant="ghost"
                  size="icon"
                  className="min-h-11 min-w-11"
                  onClick={() => router.push("/dashboard")}
                  aria-label="داشبورد"
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
                  aria-label="نوشتن"
                >
                  <PlusCircle className="h-5 w-5" />
                </Button>
              )}
              <Button
                variant="ghost"
                size="icon"
                className="min-h-11 min-w-11"
                onClick={() => router.push("/notifications")}
                aria-label="اعلان‌ها"
              >
                <Bell className="h-5 w-5" />
              </Button>
              <Button
                variant="ghost"
                size="icon"
                className="min-h-11 min-w-11"
                onClick={() => router.push("/saved")}
                aria-label="ذخیره‌ها"
              >
                <Bookmark className="h-5 w-5" />
              </Button>
              <Button
                variant="ghost"
                size="icon"
                className="min-h-11 min-w-11"
                onClick={() => router.push("/settings")}
                aria-label="تنظیمات"
              >
                <Settings className="h-5 w-5" />
              </Button>
              <Button
                variant="ghost"
                size="icon"
                className="min-h-11 min-w-11"
                onClick={() => router.push(`/${user?.username}`)}
                aria-label="پروفایل"
              >
                <Avatar className="h-8 w-8">
                  {user?.avatar_url ? (
                    <AvatarImage src={user.avatar_url} alt="" />
                  ) : null}
                  <AvatarFallback>
                    {user ? getInitials(user.display_name || user.username) : "؟"}
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
                ورود
              </Button>
              <Button size="sm" className="min-h-11" onClick={() => router.push("/register")}>
                <UserPlus className="ms-1 h-4 w-4" />
                ثبت‌نام
              </Button>
            </>
          )}
        </div>
      </div>
    </motion.header>
  );
}
