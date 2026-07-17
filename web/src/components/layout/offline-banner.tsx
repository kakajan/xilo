"use client";

import { WifiOff } from "lucide-react";
import { useOnline } from "@/hooks/use-online";

export function OfflineBanner() {
  const online = useOnline();
  if (online) return null;

  return (
    <div
      role="status"
      className="sticky top-0 z-[60] flex items-center justify-center gap-2 bg-warning/90 px-3 py-1.5 text-sm font-medium text-[#0F1419]"
      style={{ backgroundColor: "#FFAD1F" }}
    >
      <WifiOff className="h-4 w-4 shrink-0" />
      <span className="min-w-0">آفلاین هستید — داده‌های ذخیره‌شده نمایش داده می‌شود</span>
    </div>
  );
}
