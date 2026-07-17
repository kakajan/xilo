"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import { Compass, Home, MessageCircle, User } from "lucide-react";
import { cn } from "@/lib/utils";
import { useAuthStore } from "@/stores/auth-store";

const ITEMS = [
  { href: "/", label: "فید", icon: Home },
  { href: "/discover", label: "اکتشاف", icon: Compass },
  { href: "/chat", label: "پیام‌ها", icon: MessageCircle },
] as const;

export function DesktopSideNav() {
  const pathname = usePathname();
  const username = useAuthStore((s) => s.user?.username);

  return (
    <aside className="hidden w-44 shrink-0 md:block">
      <nav className="sticky top-20 space-y-1" aria-label="منوی دسکتاپ">
        {ITEMS.map(({ href, label, icon: Icon }) => {
          const active =
            href === "/" ? pathname === "/" : pathname.startsWith(href);
          return (
            <Link
              key={href}
              href={href}
              className={cn(
                "flex min-h-11 items-center gap-3 rounded-xl px-3 py-2 text-sm font-medium transition-colors",
                active
                  ? "bg-primary/10 text-primary"
                  : "text-muted-foreground hover:bg-muted hover:text-foreground"
              )}
            >
              <Icon className="h-5 w-5 shrink-0" />
              <span className="min-w-0">{label}</span>
            </Link>
          );
        })}
        <Link
          href={username ? `/${username}` : "/login"}
          className={cn(
            "flex min-h-11 items-center gap-3 rounded-xl px-3 py-2 text-sm font-medium transition-colors",
            username && pathname === `/${username}`
              ? "bg-primary/10 text-primary"
              : "text-muted-foreground hover:bg-muted hover:text-foreground"
          )}
        >
          <User className="h-5 w-5 shrink-0" />
          <span className="min-w-0">پروفایل</span>
        </Link>
      </nav>
    </aside>
  );
}
