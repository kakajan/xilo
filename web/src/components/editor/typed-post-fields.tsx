"use client";

import { useState } from "react";
import { X, Upload, Film } from "lucide-react";
import { Button } from "@/components/ui/button";
import { apiUpload } from "@/lib/api-client";
import { mediaItemFromUpload } from "@/lib/post-type";
import { useEditorStore } from "@/stores/editor-store";
import type { PostType } from "@/types/post";

const MAX_IMAGE_BYTES = 10 * 1024 * 1024;
const MAX_VIDEO_BYTES = 100 * 1024 * 1024;

interface TypedPostFieldsProps {
  postType: PostType;
  microText: string;
  onMicroTextChange: (text: string) => void;
}

export function TypedPostFields({ postType, microText, onMicroTextChange }: TypedPostFieldsProps) {
  const { linkUrl, setLinkUrl, media, addMedia, removeMedia, clearMedia } = useEditorStore();
  const [uploadError, setUploadError] = useState("");
  const [uploading, setUploading] = useState(false);

  if (postType === "article") return null;

  const handlePhotoUpload = async (e: React.ChangeEvent<HTMLInputElement>) => {
    const files = Array.from(e.target.files ?? []);
    e.target.value = "";
    if (files.length === 0) return;

    if (media.length + files.length > 10) {
      setUploadError("حداکثر ۱۰ عکس مجاز است");
      return;
    }

    setUploadError("");
    setUploading(true);
    try {
      for (const file of files) {
        if (file.size > MAX_IMAGE_BYTES) {
          setUploadError("حجم هر عکس نباید بیشتر از ۱۰ مگابایت باشد");
          continue;
        }
        const formData = new FormData();
        formData.append("file", file);
        const res = await apiUpload<{ id: string; url: string; mime_type?: string }>(
          "/api/media/upload",
          formData
        );
        addMedia(mediaItemFromUpload(res));
      }
    } catch {
      setUploadError("آپلود عکس ناموفق بود");
    }
    setUploading(false);
  };

  const handleVideoUpload = async (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    e.target.value = "";
    if (!file) return;

    if (file.size > MAX_VIDEO_BYTES) {
      setUploadError("حجم ویدیو نباید بیشتر از ۱۰۰ مگابایت باشد");
      return;
    }

    setUploadError("");
    setUploading(true);
    clearMedia();
    try {
      const formData = new FormData();
      formData.append("file", file);
      const res = await apiUpload<{ id: string; url: string; mime_type?: string }>(
        "/api/media/upload",
        formData
      );
      addMedia(mediaItemFromUpload(res));
    } catch {
      setUploadError("آپلود ویدیو ناموفق بود");
    }
    setUploading(false);
  };

  if (postType === "micro") {
    return (
      <div className="space-y-2">
        <label className="block text-sm font-medium">متن کوتاه</label>
        <textarea
          value={microText}
          onChange={(e) => onMicroTextChange(e.target.value)}
          rows={5}
          maxLength={500}
          placeholder="چه چیزی در ذهن دارید؟"
          className="w-full resize-none rounded-xl border bg-background px-4 py-3 text-sm leading-relaxed"
        />
        <p className="text-xs text-muted-foreground">
          <bdi>{microText.length}</bdi> / ۵۰۰ نویسه
        </p>
      </div>
    );
  }

  if (postType === "photo") {
    return (
      <div className="space-y-3">
        <div className="flex items-center justify-between gap-2">
          <label className="text-sm font-medium">عکس‌ها (۱ تا ۱۰)</label>
          <span className="text-xs text-muted-foreground">
            <bdi>{media.length}</bdi> / ۱۰
          </span>
        </div>
        {media.length > 0 ? (
          <div className="grid grid-cols-2 gap-2 sm:grid-cols-3">
            {media.map((item) => (
              <div key={item.id} className="relative aspect-square overflow-hidden rounded-xl">
                {/* eslint-disable-next-line @next/next/no-img-element */}
                <img src={item.url} alt="" className="h-full w-full object-cover" />
                <button
                  type="button"
                  onClick={() => removeMedia(item.id)}
                  className="absolute top-2 end-2 rounded-full bg-background/80 p-1"
                  aria-label="حذف عکس"
                >
                  <X className="h-4 w-4" />
                </button>
              </div>
            ))}
          </div>
        ) : null}
        {media.length < 10 ? (
          <label className="flex h-24 cursor-pointer items-center justify-center gap-2 rounded-xl border-2 border-dashed text-sm text-muted-foreground hover:bg-accent/50">
            <Upload className="h-4 w-4 shrink-0" aria-hidden />
            {uploading ? "در حال آپلود..." : "افزودن عکس"}
            <input
              type="file"
              accept="image/*"
              multiple
              className="hidden"
              onChange={handlePhotoUpload}
              disabled={uploading}
            />
          </label>
        ) : null}
        {uploadError ? (
          <p className="text-sm text-destructive" role="alert">
            {uploadError}
          </p>
        ) : null}
        <p className="text-xs text-muted-foreground">توضیح زیر عکس‌ها اختیاری است — در ویرایشگر پایین بنویسید.</p>
      </div>
    );
  }

  if (postType === "video") {
    const video = media[0];
    return (
      <div className="space-y-3">
        <label className="block text-sm font-medium">ویدیو</label>
        {video ? (
          <div className="relative overflow-hidden rounded-xl border">
            <video src={video.url} controls className="max-h-80 w-full bg-black" />
            <Button
              type="button"
              variant="outline"
              size="sm"
              className="absolute top-2 end-2 bg-background/90"
              onClick={() => clearMedia()}
            >
              حذف
            </Button>
          </div>
        ) : (
          <label className="flex h-28 cursor-pointer flex-col items-center justify-center gap-2 rounded-xl border-2 border-dashed text-sm text-muted-foreground hover:bg-accent/50">
            <Film className="h-6 w-6 shrink-0" aria-hidden />
            {uploading ? "در حال آپلود..." : "انتخاب ویدیو (حداکثر ۱۰۰ مگابایت)"}
            <input
              type="file"
              accept="video/mp4,video/webm,video/quicktime,.mp4,.webm,.mov"
              className="hidden"
              onChange={handleVideoUpload}
              disabled={uploading}
            />
          </label>
        )}
        {uploadError ? (
          <p className="text-sm text-destructive" role="alert">
            {uploadError}
          </p>
        ) : null}
      </div>
    );
  }

  if (postType === "link") {
    return (
      <div className="space-y-2">
        <label className="block text-sm font-medium">آدرس لینک</label>
        <input
          type="url"
          value={linkUrl}
          onChange={(e) => setLinkUrl(e.target.value)}
          placeholder="https://example.com"
          dir="ltr"
          className="w-full rounded-xl border bg-background px-4 py-3 font-mono text-sm"
        />
        <p className="text-xs text-muted-foreground">
          لینک باید با https شروع شود. نظر یا توضیح اختیاری را در ویرایشگر پایین بنویسید.
        </p>
      </div>
    );
  }

  return null;
}
