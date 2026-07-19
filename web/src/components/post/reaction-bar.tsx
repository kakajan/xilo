"use client";

import { useState, useCallback } from "react";
import { Heart, ThumbsUp, Laugh, PartyPopper, Lightbulb, Flame } from "lucide-react";
import { cn } from "@/lib/utils";
import { apiFetch } from "@/lib/api-client";

const EMOJIS = [
  { key: "heart", icon: Heart, label: "Like", color: "text-red-500" },
  { key: "thumbsup", icon: ThumbsUp, label: "Up", color: "text-blue-500" },
  { key: "laugh", icon: Laugh, label: "Haha", color: "text-yellow-500" },
  { key: "party", icon: PartyPopper, label: "Celebrate", color: "text-purple-500" },
  { key: "bulb", icon: Lightbulb, label: "Insight", color: "text-amber-500" },
  { key: "flame", icon: Flame, label: "Hot", color: "text-orange-500" },
];

interface ReactionBarProps {
  targetType: "post" | "comment";
  targetId: string;
  reactions?: Record<string, number>;
  className?: string;
}

export function ReactionBar({ targetType, targetId, reactions, className }: ReactionBarProps) {
  const [counts, setCounts] = useState<Record<string, number>>(reactions || {});
  const [active, setActive] = useState<string | null>(null);
  const [animating, setAnimating] = useState<string | null>(null);

  const toggleReaction = useCallback(
    async (key: string) => {
      const prevActive = active;
      setActive(prevActive === key ? null : key);
      setAnimating(key);

      setCounts((prev) => {
        const next = { ...prev };
        if (prevActive === key) {
          next[key] = Math.max(0, (prev[key] || 0) - 1);
        } else {
          next[key] = (prev[key] || 0) + 1;
          if (prevActive) {
            next[prevActive] = Math.max(0, (prev[prevActive] || 0) - 1);
          }
        }
        return next;
      });

      try {
        await apiFetch(`/api/${targetType}/${targetId}/reactions`, {
          method: "POST",
          body: JSON.stringify({ reaction: key }),
        });
      } catch {
        setActive(prevActive);
        setCounts(reactions || {});
      }

      setTimeout(() => setAnimating(null), 300);
    },
    [active, targetType, targetId, reactions]
  );

  return (
    <div className={cn("flex items-center gap-1", className)}>
      {EMOJIS.map(({ key, icon: Icon, label, color }) => (
        <button
          key={key}
          type="button"
          onClick={() => toggleReaction(key)}
          className={cn(
            "relative flex items-center gap-1 rounded-full px-2 py-1 text-xs transition-transform hover:bg-accent active:scale-125",
            active === key && "bg-accent"
          )}
          title={label}
        >
          <Icon className={cn("h-4 w-4", active === key && color)} />
          {counts[key] ? (
            <span className="text-muted-foreground">{counts[key]}</span>
          ) : null}
          {animating === key && active !== null ? (
            <span className="pointer-events-none absolute -top-6 left-1/2 -translate-x-1/2 animate-pulse">
              <Icon className={cn("h-4 w-4", color)} />
            </span>
          ) : null}
        </button>
      ))}
    </div>
  );
}
