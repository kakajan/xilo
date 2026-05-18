"use client";

import { useState, useCallback } from "react";
import { Heart, ThumbsUp, Laugh, PartyPopper, Lightbulb, Flame } from "lucide-react";
import { motion, AnimatePresence } from "framer-motion";
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
        <motion.button
          key={key}
          onClick={() => toggleReaction(key)}
          whileTap={{ scale: 1.3 }}
          className={cn(
            "relative flex items-center gap-1 px-2 py-1 rounded-full text-xs transition-colors hover:bg-accent",
            active === key && "bg-accent"
          )}
          title={label}
        >
          <Icon className={cn("h-4 w-4", active === key && color)} />
          {counts[key] ? (
            <span className="text-muted-foreground">{counts[key]}</span>
          ) : null}
          <AnimatePresence>
            {animating === key && active !== null && (
              <motion.span
                className="absolute -top-6 left-1/2 -translate-x-1/2 text-lg"
                initial={{ opacity: 1, y: 0 }}
                animate={{ opacity: 0, y: -20 }}
                exit={{ opacity: 0 }}
              >
                {icon({ className: cn("h-4 w-4", color) })}
              </motion.span>
            )}
          </AnimatePresence>
        </motion.button>
      ))}
    </div>
  );
}
