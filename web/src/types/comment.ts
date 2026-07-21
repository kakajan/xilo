/** Short parent summary on Discover reply hits. */
export interface CommentParentSummary {
  id: string;
  author_username: string;
  author_display_name: string;
  content_preview: string;
}

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
  viewer_reactions?: string[];
  is_bookmarked?: boolean;
  is_reposted?: boolean;
  repost_count?: number;
  /** Present on discover / denormalized payloads. */
  like_count?: number;
  reply_count?: number;
  post?: {
    id: string;
    title: string;
    slug: string;
    author_username?: string;
  };
  /** Present on Discover when this comment replies to another. */
  parent?: CommentParentSummary | null;
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
