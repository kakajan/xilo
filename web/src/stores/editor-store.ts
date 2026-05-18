import { create } from "zustand";
import { persist } from "zustand/middleware";

interface EditorState {
  title: string;
  slug: string;
  excerpt: string;
  coverImageUrl: string;
  category: string;
  tags: string[];
  status: "draft" | "published";
  isPremium: boolean;
  hasUnsaved: boolean;

  setTitle: (title: string) => void;
  setSlug: (slug: string) => void;
  setExcerpt: (excerpt: string) => void;
  setCoverImageUrl: (url: string) => void;
  setCategory: (category: string) => void;
  setTags: (tags: string[]) => void;
  addTag: (tag: string) => void;
  removeTag: (tag: string) => void;
  setStatus: (status: "draft" | "published") => void;
  setIsPremium: (v: boolean) => void;
  setHasUnsaved: (v: boolean) => void;
  reset: () => void;
}

const initial = {
  title: "",
  slug: "",
  excerpt: "",
  coverImageUrl: "",
  category: "",
  tags: [] as string[],
  status: "draft" as const,
  isPremium: false,
  hasUnsaved: false,
};

export const useEditorStore = create<EditorState>()(
  persist(
    (set) => ({
      ...initial,

      setTitle: (title) => set({ title, hasUnsaved: true }),
      setSlug: (slug) => set({ slug, hasUnsaved: true }),
      setExcerpt: (excerpt) => set({ excerpt, hasUnsaved: true }),
      setCoverImageUrl: (coverImageUrl) => set({ coverImageUrl, hasUnsaved: true }),
      setCategory: (category) => set({ category, hasUnsaved: true }),
      setTags: (tags) => set({ tags, hasUnsaved: true }),
      addTag: (tag) =>
        set((s) => (s.tags.length < 10 ? { tags: [...s.tags, tag], hasUnsaved: true } : s)),
      removeTag: (tag) =>
        set((s) => ({ tags: s.tags.filter((t) => t !== tag), hasUnsaved: true })),
      setStatus: (status) => set({ status, hasUnsaved: true }),
      setIsPremium: (isPremium) => set({ isPremium, hasUnsaved: true }),
      setHasUnsaved: (hasUnsaved) => set({ hasUnsaved }),
      reset: () => set(initial),
    }),
    { name: "xilo-editor-draft" }
  )
);
