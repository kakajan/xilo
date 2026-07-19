import { apiFetch } from "@/lib/api-client";
import { getAnalyticsSessionId } from "@/lib/analytics-session";

export interface RecordViewResponse {
  counted: boolean;
  view_count: number;
}

export async function recordPostView(postId: string): Promise<RecordViewResponse> {
  return apiFetch<RecordViewResponse>(`/api/posts/${postId}/view`, {
    method: "POST",
    body: JSON.stringify({ session_id: getAnalyticsSessionId() }),
  });
}
