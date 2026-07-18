"use client";

import { useQuery } from "@tanstack/react-query";
import { apiFetch } from "@/lib/api-client";
import { Skeleton } from "@/components/ui/skeleton";

type DailyStat = { day: string; views: number; reads: number };
type TopPost = { title: string; views: number };

interface AuthorDashboardData {
  total_views: number;
  total_reads: number;
  daily_stats: DailyStat[] | null;
  top_posts: TopPost[] | null;
}

export function AuthorAnalytics() {
  const { data, isLoading, isError } = useQuery({
    queryKey: ["analytics", "author"],
    queryFn: () =>
      apiFetch<AuthorDashboardData>("/api/analytics/author-dashboard?days=30"),
    refetchInterval: 60_000,
  });

  if (isLoading) return <Skeleton className="h-64 w-full rounded-xl" />;
  if (isError) {
    return (
      <p className="text-sm text-muted-foreground">بارگذاری آمار ناموفق بود.</p>
    );
  }
  if (!data) return null;

  const dailyStats = data.daily_stats ?? [];
  const topPosts = data.top_posts ?? [];
  const maxViews = Math.max(1, ...dailyStats.map((s) => s.views));

  return (
    <div className="space-y-6">
      <div className="grid grid-cols-2 gap-4">
        <div className="rounded-xl border p-4">
          <p className="text-xs text-muted-foreground">بازدید کل</p>
          <p className="text-2xl font-bold">
            {(data.total_views ?? 0).toLocaleString()}
          </p>
        </div>
        <div className="rounded-xl border p-4">
          <p className="text-xs text-muted-foreground">خوانده‌شده کل</p>
          <p className="text-2xl font-bold">
            {(data.total_reads ?? 0).toLocaleString()}
          </p>
        </div>
      </div>

      <div className="rounded-xl border p-4">
        <p className="mb-3 text-sm font-medium">فعالیت روزانه</p>
        {dailyStats.length === 0 ? (
          <p className="text-sm text-muted-foreground">هنوز داده‌ای نیست</p>
        ) : (
          <div className="flex h-32 items-end gap-1">
            {dailyStats.map((d) => {
              const h = Math.max(4, (d.views / maxViews) * 100);
              return (
                <div key={d.day} className="flex flex-1 flex-col items-center gap-1">
                  <span className="text-xs text-muted-foreground">{d.views}</span>
                  <div
                    className="w-full rounded-t bg-primary"
                    style={{ height: `${h}%` }}
                  />
                  <span className="text-[10px] text-muted-foreground">
                    {d.day.slice(5)}
                  </span>
                </div>
              );
            })}
          </div>
        )}
      </div>

      <div className="rounded-xl border p-4">
        <p className="mb-3 text-sm font-medium">پست‌های برتر</p>
        {topPosts.length === 0 ? (
          <p className="text-sm text-muted-foreground">هنوز داده‌ای نیست</p>
        ) : (
          topPosts.map((p, i) => (
            <div key={i} className="flex justify-between py-1 text-sm">
              <span className="truncate">{p.title || "بدون عنوان"}</span>
              <span className="ml-2 text-muted-foreground">{p.views} بازدید</span>
            </div>
          ))
        )}
      </div>
    </div>
  );
}

export function AdminAnalytics() {
  const { data, isLoading, isError } = useQuery({
    queryKey: ["analytics", "admin"],
    queryFn: () =>
      apiFetch<{
        dau: number;
        wau: number;
        mau: number;
        total_posts: number;
        total_users: number;
        total_comments: number;
        revenue_cents: number;
      }>("/api/analytics/admin-dashboard?days=30"),
    refetchInterval: 60_000,
  });

  if (isLoading) return <Skeleton className="h-64 w-full rounded-xl" />;
  if (isError) {
    return (
      <p className="text-sm text-muted-foreground">بارگذاری آمار ناموفق بود.</p>
    );
  }
  if (!data) return null;

  return (
    <div className="grid grid-cols-2 gap-4 sm:grid-cols-3">
      <Stat label="DAU" value={data.dau ?? 0} />
      <Stat label="WAU" value={data.wau ?? 0} />
      <Stat label="MAU" value={data.mau ?? 0} />
      <Stat label="پست‌ها" value={data.total_posts ?? 0} />
      <Stat label="کاربران" value={data.total_users ?? 0} />
      <Stat label="نظرات" value={data.total_comments ?? 0} />
      <Stat
        label="درآمد"
        value={`$${((data.revenue_cents ?? 0) / 100).toFixed(2)}`}
      />
    </div>
  );
}

function Stat({ label, value }: { label: string; value: number | string }) {
  return (
    <div className="rounded-xl border p-4">
      <p className="text-xs text-muted-foreground">{label}</p>
      <p className="text-2xl font-bold">
        {typeof value === "number" ? value.toLocaleString() : value}
      </p>
    </div>
  );
}
