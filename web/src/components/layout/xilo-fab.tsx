"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import { motion } from "framer-motion";
import { Plus } from "lucide-react";
import { canCreatePost } from "@/lib/auth/permissions";
import { useAuthStore } from "@/stores/auth-store";

interface XiloFabProps {
  chromeVisible?: boolean;
}

export function XiloFab({ chromeVisible = true }: XiloFabProps) {
  const pathname = usePathname();
  const isAuthenticated = useAuthStore((s) => s.isAuthenticated);
  const role = useAuthStore((s) => s.user?.role);
  const show =
    pathname === "/" && isAuthenticated && canCreatePost(role) && chromeVisible;

  return (
    <motion.div
      initial={false}
      animate={{
        scale: show ? 1 : 0,
        opacity: show ? 1 : 0,
      }}
      transition={{ duration: 0.25 }}
      className="pointer-events-none fixed bottom-[5.5rem] end-6 z-50 md:bottom-8"
      style={{ transformOrigin: "bottom center" }}
    >
      {show && (
        <Link
          href="/write"
          className="pointer-events-auto flex h-14 w-14 items-center justify-center rounded-full bg-primary text-primary-foreground shadow-md ring-1 ring-white/50"
          aria-label="نوشتن پست"
        >
          <Plus className="h-7 w-7" strokeWidth={2.5} />
        </Link>
      )}
    </motion.div>
  );
}
