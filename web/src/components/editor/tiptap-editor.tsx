"use client";

import { useRef, useCallback, useState, useEffect, type MutableRefObject } from "react";
import { useEditor, EditorContent } from "@tiptap/react";
import StarterKit from "@tiptap/starter-kit";
import Underline from "@tiptap/extension-underline";
import LinkExtension from "@tiptap/extension-link";
import ImageExtension from "@tiptap/extension-image";
import TableExtension from "@tiptap/extension-table";
import TableRow from "@tiptap/extension-table-row";
import TableCell from "@tiptap/extension-table-cell";
import TableHeader from "@tiptap/extension-table-header";
import Placeholder from "@tiptap/extension-placeholder";
import { EditorToolbar } from "./editor-toolbar";
import { apiUpload } from "@/lib/api-client";
import { useAuthStore } from "@/stores/auth-store";

interface TiptapEditorProps {
  content?: string;
  onSave?: (html: string, json: string) => void;
  /** Imperative flush of current editor state (e.g. before publish). */
  contentRef?: MutableRefObject<{ html: string; json: string } | null>;
}

export function TiptapEditor({ content, onSave, contentRef }: TiptapEditorProps) {
  const fileInputRef = useRef<HTMLInputElement>(null);
  const [wordCount, setWordCount] = useState(0);
  const { isAuthenticated } = useAuthStore();
  const onSaveRef = useRef(onSave);
  onSaveRef.current = onSave;

  const syncContent = useCallback(
    (editor: NonNullable<ReturnType<typeof useEditor>>) => {
      const html = editor.getHTML();
      const json = JSON.stringify(editor.getJSON());
      if (contentRef) contentRef.current = { html, json };
      onSaveRef.current?.(html, json);
      const text = editor.state.doc.textContent;
      setWordCount(text.trim() ? text.split(/\s+/).length : 0);
    },
    [contentRef]
  );

  const editor = useEditor({
    extensions: [
      StarterKit.configure({
        heading: { levels: [1, 2, 3] },
      }),
      Underline,
      LinkExtension.configure({ openOnClick: false }),
      ImageExtension.configure({ inline: true }),
      TableExtension.configure({ resizable: true }),
      TableRow,
      TableCell,
      TableHeader,
      Placeholder.configure({ placeholder: "داستان خود را بنویسید..." }),
    ],
    content: parseInitialContent(content),
    immediatelyRender: false,
    onUpdate: ({ editor: ed }) => {
      syncContent(ed);
    },
    onCreate: ({ editor: ed }) => {
      syncContent(ed);
    },
    editorProps: {
      attributes: {
        // Padding lives in globals.css (.tiptap-editor .ProseMirror) so toolbar never covers text.
        class: "tiptap-prose prose dark:prose-invert max-w-none focus:outline-none",
      },
    },
  });

  // Load async content into an already-mounted editor (edit page).
  useEffect(() => {
    if (!editor || !content?.trim()) return;
    try {
      const parsed = JSON.parse(content);
      const current = JSON.stringify(editor.getJSON());
      if (current !== content) {
        editor.commands.setContent(parsed);
        syncContent(editor);
      }
    } catch {
      // ignore invalid JSON
    }
  }, [content, editor, syncContent]);

  const handleImageUpload = useCallback(
    async (e: React.ChangeEvent<HTMLInputElement>) => {
      const file = e.target.files?.[0];
      if (!file || !editor) return;

      const formData = new FormData();
      formData.append("file", file);

      try {
        const res = await apiUpload<{ url: string }>("/api/media/upload", formData);
        editor.chain().focus().setImage({ src: res.url }).run();
      } catch (err) {
        console.error("Image upload failed", err);
      }

      if (fileInputRef.current) fileInputRef.current.value = "";
    },
    [editor]
  );

  const handleDrop = useCallback(
    async (e: React.DragEvent) => {
      e.preventDefault();
      const file = e.dataTransfer.files?.[0];
      if (!file || !editor || !isAuthenticated) return;

      const formData = new FormData();
      formData.append("file", file);

      try {
        const res = await apiUpload<{ url: string }>("/api/media/upload", formData);
        editor.chain().focus().setImage({ src: res.url }).run();
      } catch (err) {
        console.error("Image upload failed", err);
      }
    },
    [editor, isAuthenticated]
  );

  return (
    <div className="tiptap-editor flex flex-col rounded-xl border bg-background">
      {/* Document flow only — never sticky/absolute over the body. */}
      <div className="tiptap-editor__toolbar shrink-0 border-b bg-background">
        <EditorToolbar
          editor={editor}
          onImageClick={() => fileInputRef.current?.click()}
        />
      </div>

      <input
        ref={fileInputRef}
        type="file"
        accept="image/jpeg,image/png,image/webp,image/gif"
        className="hidden"
        onChange={handleImageUpload}
      />

      <div
        className="tiptap-editor__body relative z-0 min-h-0 flex-1 overflow-y-auto"
        onDrop={handleDrop}
        onDragOver={(e) => e.preventDefault()}
      >
        <EditorContent editor={editor} />
      </div>

      <div className="flex shrink-0 items-center justify-between border-t px-6 py-2 text-xs text-muted-foreground">
        <span>
          {wordCount} واژه · ~{Math.max(1, Math.ceil(wordCount / 200))} دقیقه مطالعه
        </span>
      </div>
    </div>
  );
}

function parseInitialContent(content?: string) {
  if (!content?.trim() || content.trim() === "{}") return "";
  try {
    return JSON.parse(content);
  } catch {
    return content;
  }
}
