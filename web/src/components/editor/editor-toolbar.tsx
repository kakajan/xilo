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

type ToolbarItem = {
  label: string;
  icon: typeof Bold;
  action: () => void;
  active: boolean;
};

export function EditorToolbar({ editor, onImageClick }: EditorToolbarProps) {
  if (!editor) return null;

  const row1: ToolbarItem[] = [
    { label: "ضخیم", icon: Bold, action: () => { editor.chain().focus().toggleBold().run(); }, active: editor.isActive("bold") },
    { label: "کج", icon: Italic, action: () => { editor.chain().focus().toggleItalic().run(); }, active: editor.isActive("italic") },
    { label: "زیرخط", icon: Underline, action: () => { editor.chain().focus().toggleUnderline().run(); }, active: editor.isActive("underline") },
    { label: "خط‌خورده", icon: Strikethrough, action: () => { editor.chain().focus().toggleStrike().run(); }, active: editor.isActive("strike") },
    { label: "کد", icon: Code, action: () => { editor.chain().focus().toggleCode().run(); }, active: editor.isActive("code") },
    { label: "عنوان ۱", icon: Heading1, action: () => { editor.chain().focus().toggleHeading({ level: 1 }).run(); }, active: editor.isActive("heading", { level: 1 }) },
    { label: "عنوان ۲", icon: Heading2, action: () => { editor.chain().focus().toggleHeading({ level: 2 }).run(); }, active: editor.isActive("heading", { level: 2 }) },
    { label: "عنوان ۳", icon: Heading3, action: () => { editor.chain().focus().toggleHeading({ level: 3 }).run(); }, active: editor.isActive("heading", { level: 3 }) },
  ];

  const row2: ToolbarItem[] = [
    { label: "فهرست نشانه‌دار", icon: List, action: () => { editor.chain().focus().toggleBulletList().run(); }, active: editor.isActive("bulletList") },
    { label: "فهرست شماره‌دار", icon: ListOrdered, action: () => { editor.chain().focus().toggleOrderedList().run(); }, active: editor.isActive("orderedList") },
    { label: "نقل‌قول", icon: Quote, action: () => { editor.chain().focus().toggleBlockquote().run(); }, active: editor.isActive("blockquote") },
    { label: "خط جداکننده", icon: Minus, action: () => { editor.chain().focus().setHorizontalRule().run(); }, active: false },
    { label: "تصویر", icon: Image, action: onImageClick, active: false },
    {
      label: "پیوند",
      icon: Link,
      action: () => {
        const url = window.prompt("آدرس پیوند");
        if (url) editor.chain().focus().setLink({ href: url }).run();
      },
      active: editor.isActive("link"),
    },
    { label: "جدول", icon: Table, action: () => { editor.chain().focus().insertTable({ rows: 3, cols: 3 }).run(); }, active: false },
    { label: "بازگردانی", icon: Undo2, action: () => { editor.chain().focus().undo().run(); }, active: false },
    { label: "بازانجام", icon: Redo2, action: () => { editor.chain().focus().redo().run(); }, active: false },
  ];

  const renderRow = (items: ToolbarItem[]) =>
    items.map((item) => (
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
    ));

  return (
    <div
      className="flex max-w-full flex-col gap-0.5 px-2 py-1.5"
      role="toolbar"
      aria-label="نوار ابزار ویرایشگر"
    >
      <div className="flex flex-wrap items-center gap-1">{renderRow(row1)}</div>
      <div className="flex flex-wrap items-center gap-1">{renderRow(row2)}</div>
    </div>
  );
}
