"use client";

import * as React from "react";
import { cn } from "@/lib/utils";

/**
 * SSR-safe avatar (no Radix image-loading status).
 * Radix Avatar omits <img> on the server and injects it after hydrate when
 * the image is cached — that mismatches React 19 hydration (#418).
 */
const Avatar = React.forwardRef<HTMLSpanElement, React.HTMLAttributes<HTMLSpanElement>>(
  ({ className, ...props }, ref) => (
    <span
      ref={ref}
      className={cn("relative flex h-10 w-10 shrink-0 overflow-hidden rounded-full", className)}
      {...props}
    />
  )
);
Avatar.displayName = "Avatar";

const AvatarImage = React.forwardRef<
  HTMLImageElement,
  React.ImgHTMLAttributes<HTMLImageElement>
>(({ className, alt = "", onError, ...props }, ref) => {
  const [failed, setFailed] = React.useState(false);
  if (failed || !props.src) return null;
  return (
    // eslint-disable-next-line @next/next/no-img-element -- remote avatars; sizes vary
    <img
      ref={ref}
      alt={alt}
      className={cn("relative z-10 aspect-square h-full w-full object-cover", className)}
      onError={(e) => {
        setFailed(true);
        onError?.(e);
      }}
      {...props}
    />
  );
});
AvatarImage.displayName = "AvatarImage";

const AvatarFallback = React.forwardRef<HTMLSpanElement, React.HTMLAttributes<HTMLSpanElement>>(
  ({ className, ...props }, ref) => (
    <span
      ref={ref}
      className={cn(
        "flex h-full w-full items-center justify-center rounded-full bg-muted text-sm font-medium",
        // Sit behind AvatarImage when both are present
        "absolute inset-0 z-0",
        className
      )}
      {...props}
    />
  )
);
AvatarFallback.displayName = "AvatarFallback";

export { Avatar, AvatarImage, AvatarFallback };
