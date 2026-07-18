"use client";

import { ArrowLeft } from "lucide-react";
import { useRouter } from "next/navigation";
import { Button } from "@/components/ui/button";
import { cn } from "@/lib/utils";

interface BackButtonProps {
  /** Fallback when history is empty (e.g. deep link). */
  fallbackHref?: string;
  label?: string;
  className?: string;
}

export function BackButton({
  fallbackHref = "/",
  label = "بازگشت",
  className,
}: BackButtonProps) {
  const router = useRouter();

  const goBack = () => {
    if (typeof window !== "undefined" && window.history.length > 1) {
      router.back();
      return;
    }
    router.push(fallbackHref);
  };

  return (
    <Button
      type="button"
      variant="ghost"
      className={cn("min-h-11 gap-2 px-2", className)}
      onClick={goBack}
      aria-label={label}
    >
      {/* Points left in LTR; flips to point right in RTL */}
      <ArrowLeft className="h-5 w-5 shrink-0 rtl:rotate-180" />
      <span className="min-w-0 font-medium">{label}</span>
    </Button>
  );
}
