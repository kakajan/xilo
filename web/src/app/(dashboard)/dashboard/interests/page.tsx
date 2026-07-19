"use client";

import { useCallback, useEffect, useState } from "react";
import {
  AlertCircle,
  ArrowDown,
  ArrowUp,
  CheckCircle2,
  Plus,
  Tags,
  Trash2,
} from "lucide-react";
import { apiFetch } from "@/lib/api-client";
import { Button } from "@/components/ui/button";
import { useAuthStore } from "@/stores/auth-store";

interface InterestLabels {
  en?: string;
  fa?: string;
  [key: string]: string | undefined;
}

interface Interest {
  id: string;
  slug: string;
  labels: InterestLabels;
  icon?: string;
  sort_order: number;
  is_active: boolean;
}

interface InterestsResponse {
  interests: Interest[];
}

const emptyForm = {
  slug: "",
  labelEn: "",
  labelFa: "",
  icon: "",
  is_active: true,
};

export default function DashboardInterestsPage() {
  const { user } = useAuthStore();
  const [interests, setInterests] = useState<Interest[]>([]);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [message, setMessage] = useState<{ type: "success" | "error"; text: string } | null>(
    null
  );
  const [form, setForm] = useState(emptyForm);
  const [editingId, setEditingId] = useState<string | null>(null);

  const load = useCallback(async () => {
    setLoading(true);
    setMessage(null);
    try {
      const data = await apiFetch<InterestsResponse>("/api/admin/interests");
      setInterests(data.interests ?? []);
    } catch (e) {
      setMessage({
        type: "error",
        text: e instanceof Error ? e.message : "بارگذاری علایق ناموفق بود",
      });
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void load();
  }, [load]);

  const resetForm = () => {
    setForm(emptyForm);
    setEditingId(null);
  };

  const startEdit = (item: Interest) => {
    setEditingId(item.id);
    setForm({
      slug: item.slug,
      labelEn: item.labels?.en ?? "",
      labelFa: item.labels?.fa ?? "",
      icon: item.icon ?? "",
      is_active: item.is_active,
    });
  };

  const onSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    const slug = form.slug.trim().toLowerCase().replace(/\s+/g, "-");
    if (!slug || !form.labelEn.trim() || !form.labelFa.trim()) {
      setMessage({ type: "error", text: "اسلاگ و برچسب‌های فارسی و انگلیسی لازم است" });
      return;
    }
    setSaving(true);
    setMessage(null);
    const body = {
      slug,
      labels: { en: form.labelEn.trim(), fa: form.labelFa.trim() },
      icon: form.icon.trim() || undefined,
      is_active: form.is_active,
    };
    try {
      if (editingId) {
        await apiFetch<Interest>(`/api/admin/interests/${editingId}`, {
          method: "PATCH",
          body: JSON.stringify(body),
        });
        setMessage({ type: "success", text: "علاقه به‌روزرسانی شد" });
      } else {
        await apiFetch<Interest>("/api/admin/interests", {
          method: "POST",
          body: JSON.stringify(body),
        });
        setMessage({ type: "success", text: "علاقه جدید اضافه شد" });
      }
      resetForm();
      await load();
    } catch (err) {
      setMessage({
        type: "error",
        text: err instanceof Error ? err.message : "ذخیره ناموفق بود",
      });
    } finally {
      setSaving(false);
    }
  };

  const toggleActive = async (item: Interest) => {
    setMessage(null);
    try {
      await apiFetch<Interest>(`/api/admin/interests/${item.id}`, {
        method: "PATCH",
        body: JSON.stringify({ is_active: !item.is_active }),
      });
      await load();
    } catch (err) {
      setMessage({
        type: "error",
        text: err instanceof Error ? err.message : "تغییر وضعیت ناموفق بود",
      });
    }
  };

  const deactivate = async (item: Interest) => {
    setMessage(null);
    try {
      await apiFetch(`/api/admin/interests/${item.id}`, { method: "DELETE" });
      setMessage({ type: "success", text: `«${item.slug}» غیرفعال شد` });
      await load();
    } catch (err) {
      setMessage({
        type: "error",
        text: err instanceof Error ? err.message : "حذف ناموفق بود",
      });
    }
  };

  const move = async (index: number, direction: -1 | 1) => {
    const next = index + direction;
    if (next < 0 || next >= interests.length) return;
    const reordered = [...interests];
    const [item] = reordered.splice(index, 1);
    reordered.splice(next, 0, item);
    setInterests(reordered);
    try {
      await apiFetch("/api/admin/interests/reorder", {
        method: "PUT",
        body: JSON.stringify({ ordered_ids: reordered.map((i) => i.id) }),
      });
    } catch (err) {
      setMessage({
        type: "error",
        text: err instanceof Error ? err.message : "جابه‌جایی ناموفق بود",
      });
      await load();
    }
  };

  if (user?.role !== "admin" && user?.role !== "superadmin") {
    return (
      <div className="flex items-center justify-center py-20">
        <div className="text-center">
          <AlertCircle className="mx-auto mb-4 h-12 w-12 text-destructive" />
          <p className="text-muted-foreground">Access denied</p>
        </div>
      </div>
    );
  }

  return (
    <div className="mx-auto max-w-3xl space-y-6">
      <div className="flex items-center gap-3">
        <Tags className="h-6 w-6 shrink-0" />
        <h1 className="min-w-0 text-2xl font-bold">علایق کاربران</h1>
      </div>
      <p className="text-sm text-muted-foreground">
        کاتالوگ علایق آنبوردینگ و شخصی‌سازی Discover را مدیریت کنید. برچسب‌ها به‌صورت فارسی و
        انگلیسی ذخیره می‌شوند.
      </p>

      {message && (
        <div
          className={`flex items-center gap-2 text-sm ${
            message.type === "success" ? "text-green-600" : "text-destructive"
          }`}
        >
          {message.type === "success" ? (
            <CheckCircle2 className="h-4 w-4 shrink-0" />
          ) : (
            <AlertCircle className="h-4 w-4 shrink-0" />
          )}
          <span className="min-w-0">{message.text}</span>
        </div>
      )}

      <form onSubmit={onSubmit} className="space-y-3 rounded-lg border p-4">
        <div className="flex items-center gap-2">
          <Plus className="h-4 w-4 shrink-0" />
          <h2 className="min-w-0 text-sm font-semibold">
            {editingId ? "ویرایش علاقه" : "افزودن علاقه"}
          </h2>
        </div>
        <div className="grid gap-3 sm:grid-cols-2">
          <label className="block text-sm">
            <span className="mb-1 block text-muted-foreground">اسلاگ (انگلیسی)</span>
            <input
              className="w-full rounded-md border bg-background px-3 py-2"
              value={form.slug}
              onChange={(e) => setForm((f) => ({ ...f, slug: e.target.value }))}
              placeholder="technology"
              disabled={!!editingId}
            />
          </label>
          <label className="block text-sm">
            <span className="mb-1 block text-muted-foreground">آیکون (اختیاری)</span>
            <input
              className="w-full rounded-md border bg-background px-3 py-2"
              value={form.icon}
              onChange={(e) => setForm((f) => ({ ...f, icon: e.target.value }))}
              placeholder="cpu"
            />
          </label>
          <label className="block text-sm">
            <span className="mb-1 block text-muted-foreground">برچسب انگلیسی</span>
            <input
              className="w-full rounded-md border bg-background px-3 py-2"
              value={form.labelEn}
              onChange={(e) => setForm((f) => ({ ...f, labelEn: e.target.value }))}
              placeholder="Technology"
            />
          </label>
          <label className="block text-sm">
            <span className="mb-1 block text-muted-foreground">برچسب فارسی</span>
            <input
              className="w-full rounded-md border bg-background px-3 py-2"
              value={form.labelFa}
              onChange={(e) => setForm((f) => ({ ...f, labelFa: e.target.value }))}
              placeholder="فناوری"
            />
          </label>
        </div>
        <label className="flex items-center gap-2 text-sm">
          <input
            type="checkbox"
            checked={form.is_active}
            onChange={(e) => setForm((f) => ({ ...f, is_active: e.target.checked }))}
          />
          <span>فعال</span>
        </label>
        <div className="flex flex-wrap gap-2">
          <Button type="submit" disabled={saving}>
            {saving ? "در حال ذخیره…" : editingId ? "ذخیره تغییرات" : "افزودن"}
          </Button>
          {editingId && (
            <Button type="button" variant="outline" onClick={resetForm}>
              انصراف
            </Button>
          )}
        </div>
      </form>

      {loading ? (
        <p className="text-sm text-muted-foreground">در حال بارگذاری…</p>
      ) : interests.length === 0 ? (
        <p className="text-sm text-muted-foreground">هنوز علاقه‌ای تعریف نشده است.</p>
      ) : (
        <ul className="divide-y rounded-lg border">
          {interests.map((item, index) => (
            <li key={item.id} className="flex items-center gap-3 px-4 py-3">
              <div className="flex shrink-0 flex-col gap-1">
                <button
                  type="button"
                  className="rounded p-1 text-muted-foreground hover:bg-muted"
                  onClick={() => void move(index, -1)}
                  aria-label="Move up"
                  disabled={index === 0}
                >
                  <ArrowUp className="h-4 w-4" />
                </button>
                <button
                  type="button"
                  className="rounded p-1 text-muted-foreground hover:bg-muted"
                  onClick={() => void move(index, 1)}
                  aria-label="Move down"
                  disabled={index === interests.length - 1}
                >
                  <ArrowDown className="h-4 w-4" />
                </button>
              </div>
              <div className="min-w-0 flex-1">
                <div className="flex flex-wrap items-center gap-2">
                  <span className="font-medium">{item.labels?.fa || item.slug}</span>
                  <span className="text-xs text-muted-foreground">{item.labels?.en}</span>
                  <code className="rounded bg-muted px-1.5 py-0.5 text-xs">{item.slug}</code>
                  {!item.is_active && (
                    <span className="text-xs text-destructive">غیرفعال</span>
                  )}
                </div>
              </div>
              <div className="flex shrink-0 flex-wrap gap-2">
                <Button type="button" size="sm" variant="outline" onClick={() => startEdit(item)}>
                  ویرایش
                </Button>
                <Button type="button" size="sm" variant="outline" onClick={() => void toggleActive(item)}>
                  {item.is_active ? "غیرفعال" : "فعال"}
                </Button>
                <Button
                  type="button"
                  size="sm"
                  variant="outline"
                  onClick={() => void deactivate(item)}
                  aria-label="Deactivate"
                >
                  <Trash2 className="h-4 w-4" />
                </Button>
              </div>
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}
