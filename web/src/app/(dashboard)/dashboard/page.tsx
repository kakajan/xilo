"use client";

import { AdminAnalytics } from "@/components/analytics/analytics-dashboard";

export default function AdminDashboardPage() {
  return (
    <div>
      <h1 className="text-2xl font-bold mb-6">Admin Dashboard</h1>
      <AdminAnalytics />
    </div>
  );
}
