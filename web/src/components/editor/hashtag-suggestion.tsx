"use client";

import { useEffect, useState } from "react";
import type { Editor } from "@tiptap/react";
import { apiFetch } from "@/lib/api-client";
import { activeHashtagQuery } from "@/lib/hashtag";

interface TagSuggestion {
  tag: string;
  count: number;
}

interface HashtagSuggestionProps {
  editor: Editor | null;
}

export function HashtagSuggestion({ editor }: HashtagSuggestionProps) {
  const [items, setItems] = useState<TagSuggestion[]>([]);
  const [query, setQuery] = useState<string | null>(null);
  const [range, setRange] = useState<{ from: number; to: number } | null>(null);

  useEffect(() => {
    if (!editor) return;

    const update = () => {
      const { from } = editor.state.selection;
      const $from = editor.state.doc.resolve(from);
      const textBefore = $from.parent.textBetween(0, $from.parentOffset, undefined, "\ufffc");
      const active = activeHashtagQuery(textBefore, textBefore.length);
      if (!active) {
        setQuery(null);
        setRange(null);
        setItems([]);
        return;
      }
      const start = from - active.query.length - 1;
      setQuery(active.query);
      setRange({ from: start, to: from });
    };

    editor.on("selectionUpdate", update);
    editor.on("update", update);
    update();
    return () => {
      editor.off("selectionUpdate", update);
      editor.off("update", update);
    };
  }, [editor]);

  useEffect(() => {
    if (query === null) return;
    let cancelled = false;
    const t = setTimeout(async () => {
      try {
        const res = await apiFetch<{ data: TagSuggestion[] }>(
          `/api/tags/suggest?q=${encodeURIComponent(query)}&limit=8`
        );
        if (!cancelled) setItems(res.data ?? []);
      } catch {
        if (!cancelled) setItems([]);
      }
    }, 200);
    return () => {
      cancelled = true;
      clearTimeout(t);
    };
  }, [query]);

  if (query === null || !range || items.length === 0) return null;

  const apply = (tag: string) => {
    if (!editor) return;
    editor
      .chain()
      .focus()
      .deleteRange(range)
      .insertContent(`#${tag} `)
      .run();
    setQuery(null);
    setItems([]);
  };

  return (
    <div
      className="absolute bottom-12 left-4 z-20 max-h-48 w-56 overflow-y-auto rounded-lg border bg-popover p-1 shadow-md"
      role="listbox"
    >
      {items.map((item) => (
        <button
          key={item.tag}
          type="button"
          role="option"
          className="flex w-full items-center justify-between gap-2 rounded-md px-2 py-1.5 text-start text-sm hover:bg-accent"
          aria-selected={false}
          onMouseDown={(e) => {
            e.preventDefault();
            apply(item.tag);
          }}
        >
          <span className="min-w-0 truncate text-primary">#{item.tag}</span>
          <span className="shrink-0 text-xs text-muted-foreground">{item.count}</span>
        </button>
      ))}
    </div>
  );
}
