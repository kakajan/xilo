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
      <h3 className="font-semibold text-sm">Post Settings</h3>

      <div>
        <label className="block text-xs font-medium mb-1 text-muted-foreground">Title</label>
        <input
          type="text"
          value={title}
          onChange={(e) => handleTitleChange(e.target.value)}
          placeholder="Post title"
          maxLength={200}
          className="w-full px-3 py-2 border rounded-lg bg-background text-sm"
        />
      </div>

      <div>
        <label className="block text-xs font-medium mb-1 text-muted-foreground">Slug</label>
        <input
          type="text"
          value={slug}
          onChange={(e) => setSlug(e.target.value)}
          placeholder="post-slug"
          maxLength={250}
          className="w-full px-3 py-2 border rounded-lg bg-background text-sm font-mono"
        />
      </div>

      <div>
        <label className="block text-xs font-medium mb-1 text-muted-foreground">Excerpt</label>
        <textarea
          value={excerpt}
          onChange={(e) => setExcerpt(e.target.value)}
          placeholder="Brief description..."
          rows={3}
          maxLength={500}
          className="w-full px-3 py-2 border rounded-lg bg-background text-sm resize-none"
        />
      </div>

      <div>
        <label className="block text-xs font-medium mb-1 text-muted-foreground">Category</label>
        <input
          type="text"
          value={category}
          onChange={(e) => setCategory(e.target.value)}
          placeholder="Technology, Design, etc."
          maxLength={100}
          className="w-full px-3 py-2 border rounded-lg bg-background text-sm"
        />
      </div>

      <div>
        <label className="block text-xs font-medium mb-1 text-muted-foreground">Tags</label>
        <div className="flex items-center gap-2 mb-2 flex-wrap">
          {tags.map((tag) => (
            <span
              key={tag}
              className="inline-flex items-center gap-1 bg-secondary text-secondary-foreground px-2 py-1 rounded-full text-xs"
            >
              {tag}
              <button onClick={() => removeTag(tag)}>
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
            placeholder="Add tag"
            maxLength={30}
            className="flex-1 px-3 py-2 border rounded-lg bg-background text-sm"
          />
          <Button variant="outline" size="sm" onClick={handleAddTag} disabled={tags.length >= 10}>
            <Plus className="h-4 w-4" />
          </Button>
        </div>
      </div>

      <div>
        <label className="block text-xs font-medium mb-1 text-muted-foreground">Cover Image</label>
        {coverImageUrl ? (
          <div className="relative">
            <img src={coverImageUrl} alt="Cover" className="w-full h-32 object-cover rounded-lg" />
            <button
              onClick={() => setCoverImageUrl("")}
              className="absolute top-2 right-2 bg-background/80 rounded-full p-1"
            >
              <X className="h-4 w-4" />
            </button>
          </div>
        ) : (
          <label className="flex items-center justify-center border-2 border-dashed rounded-lg h-20 cursor-pointer hover:bg-accent/50 text-sm text-muted-foreground">
            {uploading ? "Uploading..." : "Click to upload"}
            <input type="file" accept="image/*" className="hidden" onChange={handleCoverUpload} />
          </label>
        )}
      </div>

      <div className="flex items-center justify-between">
        <label className="text-xs font-medium">Premium</label>
        <button
          onClick={() => setIsPremium(!isPremium)}
          className={`relative w-9 h-5 rounded-full transition-colors ${
            isPremium ? "bg-primary" : "bg-muted"
          }`}
        >
          <span
            className={`absolute top-0.5 h-4 w-4 rounded-full bg-white transition-transform ${
              isPremium ? "left-4" : "left-0.5"
            }`}
          />
        </button>
      </div>

      <div>
        <label className="text-xs font-medium mb-2 block text-muted-foreground">Status</label>
        <div className="flex gap-2">
          <Button
            variant={status === "draft" ? "default" : "outline"}
            size="sm"
            onClick={() => setStatus("draft")}
            className="flex-1"
          >
            Draft
          </Button>
          <Button
            variant={status === "published" ? "default" : "outline"}
            size="sm"
            onClick={() => setStatus("published")}
            className="flex-1"
          >
            Publish
          </Button>
        </div>
      </div>
    </div>
  );
}
