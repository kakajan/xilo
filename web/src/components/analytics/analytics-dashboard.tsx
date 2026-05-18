"use client";

import { useQuery } from "@tanstack/react-query";
import { useRouter } from "next/navigation";
import { apiFetch } from "@/lib/api-client";
import { Button } from "@/components/ui/button";
import { Skeleton } from "@/components/ui/skeleton";

export function AuthorAnalytics() {
  const { data, isLoading } = useQuery({
    queryKey: ["analytics", "author"],
    queryFn: () => apiFetch<{
      total_views: number;
      total_reads: number;
      daily_stats: { day: string; views: number; reads: number }[];
      top_posts: { title: string; views: number }[];
    }>("/api/analytics/author-dashboard?days=30"),
    refetchInterval: 60_000,
  });

  if (isLoading) return <Skeleton className="h-64 w-full rounded-xl" />;
  if (!data) return null;

  return (
    <div className="space-y-6">
      <div className="grid grid-cols-2 gap-4">
        <div className="border rounded-xl p-4">
          <p className="text-xs text-muted-foreground">Total Views</p>
          <p className="text-2xl font-bold">{data.total_views.toLocaleString()}</p>
        </div>
        <div className="border rounded-xl p-4">
          <p className="text-xs text-muted-foreground">Total Reads</p>
          <p className="text-2xl font-bold">{data.total_reads.toLocaleString()}</p>
        </div>
      </div>
      <div className="border rounded-xl p-4">
        <p className="text-sm font-medium mb-3">Daily Activity</p>
        <div className="flex items-end gap-1 h-32">
          {data.daily_stats.map((d) => {
            const max = Math.max(...data.daily_stats.map((s) => s.views), 1);
            const h = Math.max(4, (d.views / max) * 100);
            return (
              <div key={d.day} className="flex-1 flex flex-col items-center gap-1">
                <span className="text-xs text-muted-foreground">{d.views}</span>
                <div className="w-full bg-primary rounded-t" style={{ height: `${h}%` }} />
                <span className="text-[10px] text-muted-foreground">{d.day.slice(5)}</span>
              </div>
            );
          })}
        </div>
      </div>
      <div className="border rounded-xl p-4">
        <p className="text-sm font-medium mb-3">Top Posts</p>
        {data.top_posts?.length === 0 ? (
          <p className="text-muted-foreground text-sm">No data yet</p>
        ) : (
          data.top_posts?.map((p, i) => (
            <div key={i} className="flex justify-between py-1 text-sm">
              <span className="truncate">{p.title || "Untitled"}</span>
              <span className="text-muted-foreground ml-2">{p.views} views</span>
            </div>
          ))
        )}
      </div>
    </div>
  );
}

export function AdminAnalytics() {
  const { data, isLoading } = useQuery({
    queryKey: ["analytics", "admin"],
    queryFn: () => apiFetch<{
      dau: number; wau: number; mau: number;
      total_posts: number; total_users: number; total_comments: number;
      revenue_cents: number;
    }>("/api/analytics/admin-dashboard?days=30"),
    refetchInterval: 60_000,
  });

  if (isLoading) return <Skeleton className="h-64 w-full rounded-xl" />;
  if (!data) return null;

  return (
    <div className="grid grid-cols-2 sm:grid-cols-3 gap-4">
      <Stat label="DAU" value={data.dau} />
      <Stat label="WAU" value={data.wau} />
      <Stat label="MAU" value={data.mau} />
      <Stat label="Posts" value={data.total_posts} />
      <Stat label="Users" value={data.total_users} />
      <Stat label="Comments" value={data.total_comments} />
      <Stat label="Revenue" value={`$${(data.revenue_cents / 100).toFixed(2)}`} />
    </div>
  );
}

function Stat({ label, value }: { label: string; value: number | string }) {
  return (
    <div className="border rounded-xl p-4">
      <p className="text-xs text-muted-foreground">{label}</p>
      <p className="text-2xl font-bold">
        {typeof value === "number" ? value.toLocaleString() : value}
      </p>
    </div>
  );
}
