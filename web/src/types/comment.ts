export interface Comment {
  id: string;
  post_id: string;
  author_id: string;
  parent_id: string | null;
  root_id: string | null;
  depth: number;
  content: string;
  content_html: string;
  media_url: string | null;
  is_pinned: boolean;
  is_spam: boolean;
  created_at: string;
  updated_at: string;
  author?: {
    id: string;
    username: string;
    display_name: string;
    avatar_url: string;
  };
  replies?: Comment[];
  reactions?: Record<string, number>;
}

export interface CreateCommentRequest {
  content: string;
  parent_id?: string;
  root_id?: string;
  media_url?: string;
}

export interface CommentListResponse {
  data: Comment[];
  next_cursor: string;
  has_more: boolean;
}
