import Image from "next/image";
import { cn } from "@/lib/utils";

interface OptimizedImageProps {
  src: string;
  alt: string;
  width?: number;
  height?: number;
  className?: string;
  priority?: boolean;
  blurDataURL?: string;
}

export function OptimizedImage({
  src,
  alt,
  width = 800,
  height = 450,
  className,
  priority = false,
  blurDataURL,
}: OptimizedImageProps) {
  if (!src) return null;

  return (
    <div className={cn("relative overflow-hidden rounded-lg", className)}>
      <Image
        src={src}
        alt={alt}
        width={width}
        height={height}
        className="object-cover"
        sizes="(max-width: 768px) 100vw, (max-width: 1200px) 50vw, 33vw"
        priority={priority}
        placeholder={blurDataURL ? "blur" : "empty"}
        blurDataURL={blurDataURL}
        loading={priority ? undefined : "lazy"}
      />
    </div>
  );
}
