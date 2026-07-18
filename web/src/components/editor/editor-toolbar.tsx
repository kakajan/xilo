"use client";

import { type Editor } from "@tiptap/react";
import {
  Bold,
  Italic,
  Underline,
  Strikethrough,
  Code,
  List,
  ListOrdered,
  Quote,
  Undo2,
  Redo2,
  Heading1,
  Heading2,
  Heading3,
  Image,
  Link,
  Table,
  Minus,
} from "lucide-react";
import { Button } from "@/components/ui/button";
import { cn } from "@/lib/utils";

interface EditorToolbarProps {
  editor: Editor | null;
  onImageClick: () => void;
}

export function EditorToolbar({ editor, onImageClick }: EditorToolbarProps) {
  if (!editor) return null;

  const items = [
    { label: "Bold", icon: Bold, action: () => editor.chain().focus().toggleBold().run(), active: editor.isActive("bold") },
    { label: "Italic", icon: Italic, action: () => editor.chain().focus().toggleItalic().run(), active: editor.isActive("italic") },
    { label: "Underline", icon: Underline, action: () => editor.chain().focus().toggleUnderline().run(), active: editor.isActive("underline") },
    { label: "Strikethrough", icon: Strikethrough, action: () => editor.chain().focus().toggleStrike().run(), active: editor.isActive("strike") },
    { label: "Code", icon: Code, action: () => editor.chain().focus().toggleCode().run(), active: editor.isActive("code") },
    null,
    { label: "H1", icon: Heading1, action: () => editor.chain().focus().toggleHeading({ level: 1 }).run(), active: editor.isActive("heading", { level: 1 }) },
    { label: "H2", icon: Heading2, action: () => editor.chain().focus().toggleHeading({ level: 2 }).run(), active: editor.isActive("heading", { level: 2 }) },
    { label: "H3", icon: Heading3, action: () => editor.chain().focus().toggleHeading({ level: 3 }).run(), active: editor.isActive("heading", { level: 3 }) },
    null,
    { label: "Bullet list", icon: List, action: () => editor.chain().focus().toggleBulletList().run(), active: editor.isActive("bulletList") },
    { label: "Ordered list", icon: ListOrdered, action: () => editor.chain().focus().toggleOrderedList().run(), active: editor.isActive("orderedList") },
    { label: "Blockquote", icon: Quote, action: () => editor.chain().focus().toggleBlockquote().run(), active: editor.isActive("blockquote") },
    { label: "Divider", icon: Minus, action: () => editor.chain().focus().setHorizontalRule().run(), active: false },
    null,
    { label: "Image", icon: Image, action: onImageClick, active: false },
    { label: "Link", icon: Link, action: () => {
      const url = window.prompt("Link URL");
      if (url) editor.chain().focus().setLink({ href: url }).run();
    }, active: editor.isActive("link") },
    { label: "Table", icon: Table, action: () => editor.chain().focus().insertTable({ rows: 3, cols: 3 }).run(), active: false },
    null,
    { label: "Undo", icon: Undo2, action: () => editor.chain().focus().undo().run(), active: false },
    { label: "Redo", icon: Redo2, action: () => editor.chain().focus().redo().run(), active: false },
  ];

  return (
    <div
      className="flex max-w-full items-center gap-1 overflow-x-auto px-2 py-1.5 [scrollbar-width:thin]"
      role="toolbar"
      aria-label="نوار ابزار ویرایشگر"
    >
      {items.map((item, i) => {
        if (!item) {
          return <div key={i} className="mx-1 h-6 w-px shrink-0 bg-border" aria-hidden />;
        }
        return (
          <Button
            key={item.label}
            variant="ghost"
            size="icon"
            className={cn("h-8 w-8 shrink-0", item.active && "bg-accent text-accent-foreground")}
            onClick={item.action}
            title={item.label}
            type="button"
          >
            <item.icon className="h-4 w-4" />
          </Button>
        );
      })}
    </div>
  );
}
