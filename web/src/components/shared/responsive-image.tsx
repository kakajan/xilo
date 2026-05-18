"use client";

import { OptimizedImage } from "@/components/shared/optimized-image";

export function ResponsiveSourceImage({
  src,
  alt,
  className,
  priority,
}: {
  src: string;
  alt: string;
  className?: string;
  priority?: boolean;
}) {
  if (!src) return null;

  const baseUrl = src.replace(/\/[^/]+$/, "");
  const filename = src.split("/").pop()?.split(".")[0] || "image";

  const sizes = [480, 768, 1280];
  const srcSet = sizes
    .map((w) => `${baseUrl}/${filename}/small-${w}.webp ${w}w`)
    .join(", ");

  return (
    <picture>
      {sizes.map((w) => (
        <source
          key={w}
          srcSet={`${baseUrl}/${filename}/small-${w}.avif ${w}w`}
          type="image/avif"
          media={`(max-width: ${w}px)`}
        />
      ))}
      <OptimizedImage
        src={src}
        alt={alt}
        className={className}
        priority={priority}
      />
    </picture>
  );

  return <OptimizedImage src={src} alt={alt} className={className} priority={priority} />;
}
