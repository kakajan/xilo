"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import { BarChart3, CreditCard, LayoutDashboard, Settings, Tags, Users } from "lucide-react";

const navItems = [
  { href: "/dashboard", label: "نمای کلی", icon: LayoutDashboard },
  { href: "/dashboard/analytics", label: "آمار", icon: BarChart3 },
  { href: "/dashboard/users", label: "کاربران", icon: Users },
  { href: "/dashboard/interests", label: "علایق", icon: Tags },
  { href: "/dashboard/payments", label: "پرداخت‌ها", icon: CreditCard },
  { href: "/dashboard/settings", label: "تنظیمات", icon: Settings },
];

export default function DashboardLayout({ children }: { children: React.ReactNode }) {
  const pathname = usePathname();

  return (
    <div className="flex gap-8">
      <nav className="w-48 shrink-0">
        <h2 className="mb-3 text-xs font-semibold uppercase tracking-wider text-muted-foreground">
          مدیریت
        </h2>
        <ul className="space-y-1">
          {navItems.map((item) => {
            const Icon = item.icon;
            const isActive = pathname === item.href;
            return (
              <li key={item.href}>
                <Link
                  href={item.href}
                  className={`flex items-center gap-2 rounded-lg px-3 py-2 text-sm transition-colors ${
                    isActive
                      ? "bg-primary/10 font-medium text-primary"
                      : "text-muted-foreground hover:bg-muted hover:text-foreground"
                  }`}
                >
                  <Icon className="h-4 w-4 shrink-0" />
                  <span className="min-w-0">{item.label}</span>
                </Link>
              </li>
            );
          })}
        </ul>
      </nav>
      <div className="min-w-0 flex-1">{children}</div>
    </div>
  );
}
