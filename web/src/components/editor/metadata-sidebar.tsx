"use client";

import { useState } from "react";
import { X, Plus } from "lucide-react";
import { useEditorStore } from "@/stores/editor-store";
import { Button } from "@/components/ui/button";
import { apiUpload } from "@/lib/api-client";

export function MetadataSidebar() {
  const {
    title,
    slug,
    excerpt,
    coverImageUrl,
    category,
    tags,
    status,
    isPremium,
    setTitle,
    setSlug,
    setExcerpt,
    setCoverImageUrl,
    setCategory,
    addTag,
    removeTag,
    setStatus,
    setIsPremium,
  } = useEditorStore();

  const [tagInput, setTagInput] = useState("");
  const [uploading, setUploading] = useState(false);

  const handleTitleChange = (value: string) => {
    setTitle(value);
    if (!slug) {
      setSlug(
        value
          .toLowerCase()
          .replace(/[^a-z0-9\s-]/g, "")
          .replace(/\s+/g, "-")
          .replace(/-+/g, "-")
      );
    }
  };

  const handleCoverUpload = async (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (!file) return;
    setUploading(true);
    const formData = new FormData();
    formData.append("file", file);
    try {
      const res = await apiUpload<{ url: string }>("/api/media/upload", formData);
      setCoverImageUrl(res.url);
    } catch {}
    setUploading(false);
  };

  const handleAddTag = () => {
    if (tagInput.trim() && tags.length < 10) {
      addTag(tagInput.trim());
      setTagInput("");
    }
  };

  return (
    <div className="space-y-5">
      <h3 className="text-sm font-semibold">تنظیمات پست</h3>

      <div>
        <label className="mb-1 block text-xs font-medium text-muted-foreground">عنوان</label>
        <input
          type="text"
          value={title}
          onChange={(e) => handleTitleChange(e.target.value)}
          placeholder="عنوان پست"
          maxLength={200}
          className="w-full rounded-lg border bg-background px-3 py-2 text-sm"
        />
      </div>

      <div>
        <label className="mb-1 block text-xs font-medium text-muted-foreground">نامک (اسلاگ)</label>
        <input
          type="text"
          value={slug}
          onChange={(e) => setSlug(e.target.value)}
          placeholder="namak-post"
          maxLength={250}
          className="w-full rounded-lg border bg-background px-3 py-2 font-mono text-sm"
          dir="ltr"
        />
      </div>

      <div>
        <label className="mb-1 block text-xs font-medium text-muted-foreground">خلاصه</label>
        <textarea
          value={excerpt}
          onChange={(e) => setExcerpt(e.target.value)}
          placeholder="توضیح کوتاه..."
          rows={3}
          maxLength={500}
          className="w-full resize-none rounded-lg border bg-background px-3 py-2 text-sm"
        />
      </div>

      <div>
        <label className="mb-1 block text-xs font-medium text-muted-foreground">دسته‌بندی</label>
        <input
          type="text"
          value={category}
          onChange={(e) => setCategory(e.target.value)}
          placeholder="فناوری، طراحی و ..."
          maxLength={100}
          className="w-full rounded-lg border bg-background px-3 py-2 text-sm"
        />
      </div>

      <div>
        <label className="mb-1 block text-xs font-medium text-muted-foreground">برچسب‌ها</label>
        <div className="mb-2 flex flex-wrap items-center gap-2">
          {tags.map((tag) => (
            <span
              key={tag}
              className="inline-flex items-center gap-1 rounded-full bg-secondary px-2 py-1 text-xs text-secondary-foreground"
            >
              {tag}
              <button type="button" onClick={() => removeTag(tag)} aria-label={`حذف ${tag}`}>
                <X className="h-3 w-3" />
              </button>
            </span>
          ))}
        </div>
        <div className="flex gap-2">
          <input
            type="text"
            value={tagInput}
            onChange={(e) => setTagInput(e.target.value)}
            onKeyDown={(e) => e.key === "Enter" && (e.preventDefault(), handleAddTag())}
            placeholder="افزودن برچسب"
            maxLength={30}
            className="flex-1 rounded-lg border bg-background px-3 py-2 text-sm"
          />
          <Button variant="outline" size="sm" onClick={handleAddTag} disabled={tags.length >= 10}>
            <Plus className="h-4 w-4" />
          </Button>
        </div>
      </div>

      <div>
        <label className="mb-1 block text-xs font-medium text-muted-foreground">تصویر کاور</label>
        {coverImageUrl ? (
          <div className="relative">
            <img src={coverImageUrl} alt="کاور" className="h-32 w-full rounded-lg object-cover" />
            <button
              type="button"
              onClick={() => setCoverImageUrl("")}
              className="absolute top-2 end-2 rounded-full bg-background/80 p-1"
              aria-label="حذف تصویر کاور"
            >
              <X className="h-4 w-4" />
            </button>
          </div>
        ) : (
          <label className="flex h-20 cursor-pointer items-center justify-center rounded-lg border-2 border-dashed text-sm text-muted-foreground hover:bg-accent/50">
            {uploading ? "در حال آپلود..." : "برای آپلود کلیک کنید"}
            <input type="file" accept="image/*" className="hidden" onChange={handleCoverUpload} />
          </label>
        )}
      </div>

      <div className="flex items-center justify-between">
        <label className="text-xs font-medium">ویژه (پرمیوم)</label>
        <button
          type="button"
          onClick={() => setIsPremium(!isPremium)}
          className={`relative h-5 w-9 rounded-full transition-colors ${
            isPremium ? "bg-primary" : "bg-muted"
          }`}
          aria-pressed={isPremium}
          aria-label="پست پرمیوم"
        >
          <span
            className={`absolute top-0.5 h-4 w-4 rounded-full bg-white transition-transform ${
              isPremium ? "start-4" : "start-0.5"
            }`}
          />
        </button>
      </div>

      <div>
        <label className="mb-2 block text-xs font-medium text-muted-foreground">وضعیت</label>
        <div className="flex gap-2">
          <Button
            variant={status === "draft" ? "default" : "outline"}
            size="sm"
            onClick={() => setStatus("draft")}
            className="flex-1"
          >
            پیش‌نویس
          </Button>
          <Button
            variant={status === "published" ? "default" : "outline"}
            size="sm"
            onClick={() => setStatus("published")}
            className="flex-1"
          >
            انتشار
          </Button>
        </div>
      </div>
    </div>
  );
}
