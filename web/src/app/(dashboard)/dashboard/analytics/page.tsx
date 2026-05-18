"use client";

import { AuthorAnalytics } from "@/components/analytics/analytics-dashboard";

export default function AuthorDashboardPage() {
  return (
    <div className="max-w-3xl mx-auto">
      <h1 className="text-2xl font-bold mb-6">Dashboard</h1>
      <AuthorAnalytics />
    </div>
  );
}
