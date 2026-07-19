import { Extension } from "@tiptap/core";
import { Plugin, PluginKey } from "@tiptap/pm/state";
import { Decoration, DecorationSet } from "@tiptap/pm/view";
import { findHashtagMatches } from "@/lib/hashtag";

const key = new PluginKey("hashtagHighlight");

/** Non-destructive highlight of #hashtags while editing. */
export const HashtagHighlight = Extension.create({
  name: "hashtagHighlight",

  addProseMirrorPlugins() {
    return [
      new Plugin({
        key,
        props: {
          decorations(state) {
            const decorations: ReturnType<typeof Decoration.inline>[] = [];
            state.doc.descendants((node, pos) => {
              if (!node.isText || !node.text) return;
              // Skip code marks
              if (node.marks.some((m) => m.type.name === "code")) return;
              for (const match of findHashtagMatches(node.text)) {
                decorations.push(
                  Decoration.inline(pos + match.start, pos + match.end, {
                    class: "hashtag-token text-primary font-medium",
                  })
                );
              }
            });
            return DecorationSet.create(state.doc, decorations);
          },
        },
      }),
    ];
  },
});
