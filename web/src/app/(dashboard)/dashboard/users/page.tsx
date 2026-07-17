"use client";

import { useCallback, useEffect, useState } from "react";
import { AlertCircle, CheckCircle2, Search, Users } from "lucide-react";
import { apiFetch } from "@/lib/api-client";
import { Button } from "@/components/ui/button";
import { useAuthStore } from "@/stores/auth-store";
import type { User } from "@/types/user";

interface AdminUser {
  id: string;
  email: string;
  username: string;
  display_name: string;
  avatar_url: string;
  role: User["role"];
  created_at: string;
}

const ROLE_OPTIONS: { value: User["role"]; label: string }[] = [
  { value: "reader", label: "خواننده (بدون ارسال پست)" },
  { value: "author", label: "نویسنده" },
  { value: "editor", label: "ویراستار" },
  { value: "admin", label: "ادمین" },
  { value: "superadmin", label: "سوپرادمین" },
];

export default function DashboardUsersPage() {
  const { user } = useAuthStore();
  const [query, setQuery] = useState("");
  const [users, setUsers] = useState<AdminUser[]>([]);
  const [loading, setLoading] = useState(true);
  const [savingId, setSavingId] = useState<string | null>(null);
  const [message, setMessage] = useState<{ type: "success" | "error"; text: string } | null>(
    null
  );

  const load = useCallback(async (q: string) => {
    setLoading(true);
    setMessage(null);
    try {
      const params = new URLSearchParams();
      if (q.trim()) params.set("q", q.trim());
      params.set("limit", "30");
      const data = await apiFetch<{ users: AdminUser[] }>(
        `/api/admin/users?${params.toString()}`
      );
      setUsers(data.users ?? []);
    } catch (e) {
      setMessage({
        type: "error",
        text: e instanceof Error ? e.message : "بارگذاری کاربران ناموفق بود",
      });
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void load("");
  }, [load]);

  const onSearch = (e: React.FormEvent) => {
    e.preventDefault();
    void load(query);
  };

  const onRoleChange = async (id: string, role: User["role"]) => {
    setSavingId(id);
    setMessage(null);
    try {
      const updated = await apiFetch<AdminUser>(`/api/admin/users/${id}/role`, {
        method: "PATCH",
        body: JSON.stringify({ role }),
      });
      setUsers((prev) => prev.map((u) => (u.id === id ? updated : u)));
      setMessage({ type: "success", text: `نقش @${updated.username} به ${role} تغییر کرد` });
    } catch (e) {
      setMessage({
        type: "error",
        text: e instanceof Error ? e.message : "تغییر نقش ناموفق بود",
      });
    } finally {
      setSavingId(null);
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
        <Users className="h-6 w-6 shrink-0" />
        <h1 className="min-w-0 text-2xl font-bold">کاربران و اجازهٔ ارسال پست</h1>
      </div>
      <p className="text-sm text-muted-foreground">
        فقط نقش‌های نویسنده و بالاتر می‌توانند پست بفرستند. خواننده‌ها همچنان نظر و چت دارند.
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

      <form onSubmit={onSearch} className="flex items-center gap-2">
        <div className="relative min-w-0 flex-1">
          <Search className="absolute start-3 top-1/2 h-4 w-4 -translate-y-1/2 text-muted-foreground" />
          <input
            type="search"
            value={query}
            onChange={(e) => setQuery(e.target.value)}
            placeholder="جستجو با نام کاربری، ایمیل یا نام..."
            className="min-h-11 w-full rounded-lg border bg-background py-2 pe-4 ps-9 text-sm"
          />
        </div>
        <Button type="submit" className="min-h-11 shrink-0">
          جستجو
        </Button>
      </form>

      {loading ? (
        <div className="animate-pulse space-y-3">
          <div className="h-14 rounded-lg bg-muted" />
          <div className="h-14 rounded-lg bg-muted" />
        </div>
      ) : users.length === 0 ? (
        <p className="text-sm text-muted-foreground">کاربری یافت نشد.</p>
      ) : (
        <ul className="divide-y rounded-xl border">
          {users.map((u) => (
            <li key={u.id} className="flex flex-wrap items-center gap-3 p-4">
              <div className="min-w-0 flex-1">
                <p className="truncate font-medium">
                  {u.display_name || u.username}{" "}
                  <span className="text-muted-foreground">@{u.username}</span>
                </p>
                <p className="truncate text-xs text-muted-foreground">{u.email}</p>
              </div>
              <select
                className="min-h-11 rounded-lg border bg-background px-3 py-2 text-sm"
                value={u.role}
                disabled={savingId === u.id}
                onChange={(e) => void onRoleChange(u.id, e.target.value as User["role"])}
              >
                {ROLE_OPTIONS.map((opt) => (
                  <option key={opt.value} value={opt.value}>
                    {opt.label}
                  </option>
                ))}
              </select>
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}
