"use client";

import { cn } from "@/lib/utils";

interface PostMediaCarouselProps {
  urls: string[];
  className?: string;
  imageClassName?: string;
}

export function PostMediaCarousel({ urls, className, imageClassName }: PostMediaCarouselProps) {
  if (urls.length === 0) return null;

  if (urls.length === 1) {
    return (
      <div className={cn("overflow-hidden rounded-xl", className)}>
        {/* eslint-disable-next-line @next/next/no-img-element */}
        <img src={urls[0]} alt="" className={cn("max-h-72 w-full object-cover", imageClassName)} />
      </div>
    );
  }

  return (
    <div
      className={cn(
        "flex snap-x snap-mandatory gap-2 overflow-x-auto rounded-xl pb-1 [-ms-overflow-style:none] [scrollbar-width:none] [&::-webkit-scrollbar]:hidden",
        className
      )}
    >
      {urls.map((url) => (
        <div key={url} className="w-[85%] shrink-0 snap-start sm:w-[70%]">
          {/* eslint-disable-next-line @next/next/no-img-element */}
          <img
            src={url}
            alt=""
            className={cn("aspect-[4/3] w-full rounded-xl object-cover", imageClassName)}
          />
        </div>
      ))}
    </div>
  );
}
