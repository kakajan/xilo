export interface Post {
  id: string;
  author_id: string;
  title: string;
  slug: string;
  excerpt: string;
  content: string;
  content_md: string;
  cover_image_url: string;
  category: string;
  tags: string[];
  status: "draft" | "scheduled" | "published" | "archived" | "deleted";
  is_premium: boolean;
  word_count: number;
  reading_time: number;
  view_count?: number;
  scheduled_at: string | null;
  published_at: string | null;
  created_at: string;
  updated_at: string;
  author?: User;
  comment_count?: number;
  repost_count?: number;
  reactions?: Record<string, number>;
  viewer_reactions?: string[];
  is_bookmarked?: boolean;
  is_reposted?: boolean;
}

import { User } from "./user";

export interface PostListParams {
  cursor?: string;
  limit?: number;
  category?: string;
  tag?: string;
  author?: string;
}

export interface PostListResponse {
  data: Post[];
  next_cursor: string;
  has_more: boolean;
}

export interface CreatePostRequest {
  title: string;
  slug?: string;
  excerpt?: string;
  content: string;
  content_md?: string;
  cover_image_url?: string;
  category?: string;
  tags?: string[];
  status?: string;
  is_premium?: boolean;
}
