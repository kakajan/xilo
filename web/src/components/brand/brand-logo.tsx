"use client";

import { useLocaleStore } from "@/stores/locale-store";
import type { Locale } from "@/i18n/config";
import { cn } from "@/lib/utils";

export type BrandLogoVariant = "mark" | "mark-mono" | "wordmark" | "lockup" | "app-icon";

const ASSET: Record<BrandLogoVariant, string> = {
  mark: "/brand/aile/mark-colored.svg",
  "mark-mono": "/brand/aile/mark-mono.svg",
  wordmark: "/brand/aile/wordmark-en.svg",
  lockup: "/brand/aile/lockup.svg",
  "app-icon": "/brand/aile/app-icon.svg",
};

function wordmarkForLocale(locale: Locale): string {
  if (locale === "fa" || locale === "ar") {
    return "/brand/aile/wordmark-fa.svg";
  }
  return "/brand/aile/wordmark-en.svg";
}

interface BrandLogoProps {
  variant?: BrandLogoVariant;
  className?: string;
  /** Override auto locale for wordmark (fa/ar → Persian, else English). */
  locale?: Locale;
  alt?: string;
}

/**
 * Aile brand art. Prefer logo variants over plain brand text on auth, nav, and onboarding.
 * Uses native <img> so SVG assets in /public work without next/image SVG config.
 */
export function BrandLogo({
  variant = "mark",
  className,
  locale: localeProp,
  alt = "aile",
}: BrandLogoProps) {
  const storeLocale = useLocaleStore((s) => s.locale);
  const locale = localeProp ?? storeLocale;
  const src =
    variant === "wordmark" ? wordmarkForLocale(locale) : ASSET[variant];

  return (
    // eslint-disable-next-line @next/next/no-img-element -- brand SVGs from /public
    <img
      src={src}
      alt={alt}
      className={cn("h-auto w-auto object-contain select-none", className)}
      draggable={false}
    />
  );
}
