export interface Notification {
  id: string;
  user_id: string;
  type: string;
  title: string;
  body: string;
  data: Record<string, unknown> | string | null;
  is_read: boolean;
  created_at: string;
}
