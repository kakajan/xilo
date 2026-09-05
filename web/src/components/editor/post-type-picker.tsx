"use client";

import { Check } from "lucide-react";
import { cn } from "@/lib/utils";
import { POST_TYPE_LABELS } from "@/lib/post-type";
import type { PostType } from "@/types/post";

const TYPES: PostType[] = ["article", "micro", "photo", "video", "link"];

interface PostTypePickerProps {
  value: PostType;
  onChange: (type: PostType) => void;
  open: boolean;
  onOpenChange: (open: boolean) => void;
}

export function PostTypePicker({ value, onChange, open, onOpenChange }: PostTypePickerProps) {
  if (!open) return null;

  return (
    <div className="fixed inset-0 z-50 flex items-end justify-center sm:items-center">
      <button
        type="button"
        className="absolute inset-0 bg-black/50"
        aria-label="بستن"
        onClick={() => onOpenChange(false)}
      />
      <div
        role="dialog"
        aria-modal="true"
        aria-labelledby="post-type-picker-title"
        className="relative z-10 w-full max-w-md rounded-t-2xl border border-border bg-background p-4 shadow-xl sm:rounded-2xl"
      >
        <h2 id="post-type-picker-title" className="mb-1 text-base font-bold">
          نوع پست
        </h2>
        <p className="mb-4 text-sm text-muted-foreground">نوع محتوایی که می‌خواهید منتشر کنید را انتخاب کنید.</p>
        <ul className="space-y-1">
          {TYPES.map((type) => {
            const selected = value === type;
            return (
              <li key={type}>
                <button
                  type="button"
                  className={cn(
                    "flex w-full items-center justify-between rounded-xl px-4 py-3 text-start text-sm transition-colors",
                    selected ? "bg-primary/10 text-primary" : "hover:bg-accent"
                  )}
                  onClick={() => {
                    onChange(type);
                    onOpenChange(false);
                  }}
                >
                  <span className="font-medium">{POST_TYPE_LABELS[type]}</span>
                  {selected ? <Check className="h-4 w-4 shrink-0" aria-hidden /> : null}
                </button>
              </li>
            );
          })}
        </ul>
      </div>
    </div>
  );
}

interface PostTypeBadgeProps {
  value: PostType;
  onClick: () => void;
}

export function PostTypeBadge({ value, onClick }: PostTypeBadgeProps) {
  return (
    <button
      type="button"
      onClick={onClick}
      className="rounded-full border border-border bg-secondary px-3 py-1 text-xs font-medium text-secondary-foreground transition-colors hover:bg-accent"
    >
      {POST_TYPE_LABELS[value]}
    </button>
  );
}
