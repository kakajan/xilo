import { create } from "zustand";
import { persist } from "zustand/middleware";
import type { PostMedia, PostType } from "@/types/post";

interface EditorState {
  title: string;
  slug: string;
  excerpt: string;
  coverImageUrl: string;
  coverMediaId: string;
  audioUrl: string;
  category: string;
  tags: string[];
  status: "draft" | "published";
  isPremium: boolean;
  hasUnsaved: boolean;
  postType: PostType;
  linkUrl: string;
  mediaIds: string[];
  media: PostMedia[];
  /** TipTap JSON for the new-post composer (local draft). */
  contentJson: string;
  /** In-progress edit recovery keyed by post id. */
  editDraftId: string | null;
  editContentJson: string;

  setTitle: (title: string) => void;
  setSlug: (slug: string) => void;
  setExcerpt: (excerpt: string) => void;
  setCoverImageUrl: (url: string) => void;
  setCoverMediaId: (id: string) => void;
  setAudioUrl: (url: string) => void;
  setCategory: (category: string) => void;
  setTags: (tags: string[]) => void;
  addTag: (tag: string) => void;
  removeTag: (tag: string) => void;
  setStatus: (status: "draft" | "published") => void;
  setIsPremium: (v: boolean) => void;
  setHasUnsaved: (v: boolean) => void;
  setPostType: (postType: PostType) => void;
  setLinkUrl: (url: string) => void;
  setMedia: (media: PostMedia[]) => void;
  addMedia: (item: PostMedia) => void;
  removeMedia: (id: string) => void;
  clearMedia: () => void;
  setContentJson: (json: string) => void;
  setEditDraft: (postId: string, json: string) => void;
  clearEditDraft: () => void;
  reset: () => void;
}

const initial = {
  title: "",
  slug: "",
  excerpt: "",
  coverImageUrl: "",
  coverMediaId: "",
  audioUrl: "",
  category: "",
  tags: [] as string[],
  status: "draft" as const,
  isPremium: false,
  hasUnsaved: false,
  postType: "article" as PostType,
  linkUrl: "",
  mediaIds: [] as string[],
  media: [] as PostMedia[],
  contentJson: "",
  editDraftId: null as string | null,
  editContentJson: "",
};

function syncMediaIds(media: PostMedia[]): string[] {
  return media.map((m) => m.id);
}

export const useEditorStore = create<EditorState>()(
  persist(
    (set) => ({
      ...initial,

      setTitle: (title) => set({ title, hasUnsaved: true }),
      setSlug: (slug) => set({ slug, hasUnsaved: true }),
      setExcerpt: (excerpt) => set({ excerpt, hasUnsaved: true }),
      setCoverImageUrl: (coverImageUrl) => set({ coverImageUrl, hasUnsaved: true }),
      setCoverMediaId: (coverMediaId) => set({ coverMediaId, hasUnsaved: true }),
      setAudioUrl: (audioUrl) => set({ audioUrl, hasUnsaved: true }),
      setCategory: (category) => set({ category, hasUnsaved: true }),
      setTags: (tags) => set({ tags, hasUnsaved: true }),
      addTag: (tag) =>
        set((s) => (s.tags.length < 10 ? { tags: [...s.tags, tag], hasUnsaved: true } : s)),
      removeTag: (tag) =>
        set((s) => ({ tags: s.tags.filter((t) => t !== tag), hasUnsaved: true })),
      setStatus: (status) => set({ status, hasUnsaved: true }),
      setIsPremium: (isPremium) => set({ isPremium, hasUnsaved: true }),
      setHasUnsaved: (hasUnsaved) => set({ hasUnsaved }),
      setPostType: (postType) => set({ postType, hasUnsaved: true }),
      setLinkUrl: (linkUrl) => set({ linkUrl, hasUnsaved: true }),
      setMedia: (media) =>
        set({ media, mediaIds: syncMediaIds(media), hasUnsaved: true }),
      addMedia: (item) =>
        set((s) => {
          const media = [...s.media, item];
          return { media, mediaIds: syncMediaIds(media), hasUnsaved: true };
        }),
      removeMedia: (id) =>
        set((s) => {
          const media = s.media.filter((m) => m.id !== id);
          return { media, mediaIds: syncMediaIds(media), hasUnsaved: true };
        }),
      clearMedia: () => set({ media: [], mediaIds: [], hasUnsaved: true }),
      setContentJson: (contentJson) => set({ contentJson, hasUnsaved: true }),
      setEditDraft: (postId, json) =>
        set({ editDraftId: postId, editContentJson: json, hasUnsaved: true }),
      clearEditDraft: () => set({ editDraftId: null, editContentJson: "" }),
      reset: () => set(initial),
    }),
    { name: "xilo-editor-draft" }
  )
);
