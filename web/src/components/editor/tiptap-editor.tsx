"use client";

import { useRef, useCallback, useState } from "react";
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
import { useDebounce } from "@/hooks/use-debounce";
import { useEditorStore } from "@/stores/editor-store";
import { useAuthStore } from "@/stores/auth-store";

interface TiptapEditorProps {
  content?: string;
  onSave?: (html: string, json: string) => void;
}

export function TiptapEditor({ content, onSave }: TiptapEditorProps) {
  const fileInputRef = useRef<HTMLInputElement>(null);
  const [wordCount, setWordCount] = useState(0);
  const { isAuthenticated } = useAuthStore();
  const { title } = useEditorStore();

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
      Placeholder.configure({ placeholder: "Tell your story..." }),
    ],
    content: content ? JSON.parse(content) : "",
    onUpdate: ({ editor }) => {
      const text = editor.state.doc.textContent;
      setWordCount(text.trim() ? text.split(/\s+/).length : 0);
    },
    editorProps: {
      attributes: {
        class: "prose dark:prose-invert max-w-none focus:outline-none min-h-[400px] px-6 py-4",
      },
    },
  });

  const autoSave = useDebounce(
    useCallback(() => {
      if (editor && onSave) {
        const html = editor.getHTML();
        const json = JSON.stringify(editor.getJSON());
        onSave(html, json);
      }
    }, [editor, onSave]),
    3000
  );

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
    <div className="border rounded-xl overflow-hidden bg-background">
      <EditorToolbar
        editor={editor}
        onImageClick={() => fileInputRef.current?.click()}
      />

      <input
        ref={fileInputRef}
        type="file"
        accept="image/jpeg,image/png,image/webp,image/gif"
        className="hidden"
        onChange={handleImageUpload}
      />

      <div
        onDrop={handleDrop}
        onDragOver={(e) => e.preventDefault()}
        onChange={autoSave}
      >
        <EditorContent editor={editor} />
      </div>

      <div className="border-t px-6 py-2 flex items-center justify-between text-xs text-muted-foreground">
        <span>{wordCount} words · ~{Math.max(1, Math.ceil(wordCount / 200))} min read</span>
        <span className="flex items-center gap-2">
          {editor && title && <span className="text-muted-foreground">Auto-saving…</span>}
        </span>
      </div>
    </div>
  );
}
