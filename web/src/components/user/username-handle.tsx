import type { ReactNode } from "react";
import { cn } from "@/lib/utils";

/** LTR-isolated @handle so RTL chrome never renders "handle@". */
export function UsernameHandle({
  username,
  className,
}: {
  username: string;
  className?: string;
}) {
  if (!username) return null;
  return (
    <span dir="ltr" className={cn("inline-block", className)}>
      @{username}
    </span>
  );
}

/** Keeps relative/absolute date phrases as one directional unit next to LTR handles. */
export function TimeLabel({
  children,
  className,
}: {
  children: ReactNode;
  className?: string;
}) {
  if (children == null || children === false) return null;
  return (
    <span dir="auto" className={className}>
      {children}
    </span>
  );
}

/** Meta row: @handle · time — each segment isolated for RTL BiDi safety. */
export function AuthorHandleMeta({
  username,
  timeLabel,
  className,
  trailing,
}: {
  username?: string | null;
  timeLabel?: ReactNode;
  className?: string;
  trailing?: ReactNode;
}) {
  const handle = username?.trim() ? username.trim() : "";
  const hasTime = timeLabel != null && timeLabel !== false && timeLabel !== "";

  if (!handle && !hasTime && !trailing) return null;

  return (
    <p
      className={cn(
        "flex flex-wrap items-center gap-x-1.5 text-xs text-muted-foreground",
        className
      )}
    >
      {handle ? <UsernameHandle username={handle} /> : null}
      {handle && hasTime ? <span aria-hidden>·</span> : null}
      {hasTime ? <TimeLabel>{timeLabel}</TimeLabel> : null}
      {trailing}
    </p>
  );
}
