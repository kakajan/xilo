"use client";

import { useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { ArrowRight, FolderOpen, Plus, Trash2 } from "lucide-react";
import {
  createChatFolder,
  deleteChatFolder,
  listChatFolders,
  updateChatFolder,
} from "@/lib/api/chats";
import { useAuthStore } from "@/stores/auth-store";
import { Button } from "@/components/ui/button";
import { Skeleton } from "@/components/ui/skeleton";

export default function ChatFoldersPage() {
  const router = useRouter();
  const { isAuthenticated, isLoading: authLoading } = useAuthStore();
  const queryClient = useQueryClient();
  const [name, setName] = useState("");
  const [editingId, setEditingId] = useState<string | null>(null);
  const [editName, setEditName] = useState("");

  useEffect(() => {
    if (!authLoading && !isAuthenticated) router.replace("/login");
  }, [authLoading, isAuthenticated, router]);

  const { data, isLoading } = useQuery({
    queryKey: ["chat-folders"],
    enabled: isAuthenticated,
    queryFn: listChatFolders,
  });

  const createMut = useMutation({
    mutationFn: () => createChatFolder(name.trim()),
    onSuccess: () => {
      setName("");
      queryClient.invalidateQueries({ queryKey: ["chat-folders"] });
    },
  });

  const updateMut = useMutation({
    mutationFn: () => updateChatFolder(editingId!, editName.trim()),
    onSuccess: () => {
      setEditingId(null);
      queryClient.invalidateQueries({ queryKey: ["chat-folders"] });
    },
  });

  const deleteMut = useMutation({
    mutationFn: (id: string) => deleteChatFolder(id),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ["chat-folders"] }),
  });

  if (authLoading || !isAuthenticated) return <Skeleton className="h-40 w-full" />;

  return (
    <div className="mx-auto max-w-lg">
      <div className="mb-6 flex items-center gap-2">
        <Button
          variant="ghost"
          size="icon"
          className="min-h-11 min-w-11"
          onClick={() => router.push("/settings")}
        >
          <ArrowRight className="h-5 w-5" />
        </Button>
        <div className="flex min-w-0 items-center gap-2">
          <FolderOpen className="h-5 w-5 shrink-0 text-sky-600" />
          <h1 className="text-xl font-bold">پوشه‌های چت</h1>
        </div>
      </div>

      <form
        className="mb-6 flex gap-2"
        onSubmit={(e) => {
          e.preventDefault();
          if (!name.trim()) return;
          createMut.mutate();
        }}
      >
        <input
          value={name}
          onChange={(e) => setName(e.target.value)}
          placeholder="نام پوشه جدید"
          className="min-h-11 flex-1 rounded-xl border bg-background px-3 text-sm"
        />
        <Button type="submit" className="min-h-11" disabled={createMut.isPending}>
          <Plus className="h-4 w-4" />
        </Button>
      </form>

      {isLoading ? (
        <Skeleton className="h-32 w-full" />
      ) : (data?.length ?? 0) === 0 ? (
        <p className="py-12 text-center text-muted-foreground">هنوز پوشه‌ای نساخته‌اید</p>
      ) : (
        <ul className="divide-y rounded-2xl border">
          {data!.map((f) => (
            <li key={f.id} className="flex items-center gap-2 px-4 py-3">
              {editingId === f.id ? (
                <>
                  <input
                    value={editName}
                    onChange={(e) => setEditName(e.target.value)}
                    className="min-h-11 flex-1 rounded-lg border px-2 text-sm"
                  />
                  <Button
                    size="sm"
                    className="min-h-11"
                    onClick={() => updateMut.mutate()}
                    disabled={updateMut.isPending}
                  >
                    ذخیره
                  </Button>
                </>
              ) : (
                <>
                  <button
                    type="button"
                    className="min-w-0 flex-1 text-start font-medium"
                    onClick={() => {
                      setEditingId(f.id);
                      setEditName(f.name);
                    }}
                  >
                    {f.name}
                    <span className="ms-2 text-xs text-muted-foreground">
                      {(f.chat_ids?.length ?? 0)} گفتگو
                    </span>
                  </button>
                  <Button
                    variant="ghost"
                    size="icon"
                    className="min-h-11 min-w-11 text-destructive"
                    onClick={() => deleteMut.mutate(f.id)}
                  >
                    <Trash2 className="h-4 w-4" />
                  </Button>
                </>
              )}
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}
