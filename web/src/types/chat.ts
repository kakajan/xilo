export interface CursorPage<T> {
  data: T[];
  next_cursor: string;
  has_more: boolean;
}

export interface ChatMember {
  chat_id: string;
  user_id: string;
  role: string;
  username: string;
  display_name: string;
  avatar_url?: string | null;
  joined_at: string;
  last_read_at?: string | null;
  is_muted?: boolean;
  is_archived?: boolean;
}

export interface MessageReaction {
  reaction: string;
  count: number;
  reacted: boolean;
}

export interface ChatMessage {
  id: string;
  chat_id: string;
  sender_id: string;
  type: "text" | "image" | "video" | "file";
  sender_name?: string | null;
  sender_avatar?: string | null;
  content?: string | null;
  media_url?: string | null;
  reply_to_id?: string | null;
  is_edited?: boolean;
  is_deleted?: boolean;
  created_at: string;
  updated_at: string;
  reactions?: MessageReaction[];
}

export interface Chat {
  id: string;
  type: string;
  name?: string | null;
  avatar_url?: string | null;
  created_at: string;
  updated_at: string;
  last_message_at?: string | null;
  members?: ChatMember[];
  last_message?: ChatMessage | null;
  unread_count?: number;
  is_muted?: boolean;
  is_archived?: boolean;
  current_role?: string;
}

export interface ChatFolder {
  id: string;
  name: string;
  sort_order?: number;
  chat_ids?: string[];
  created_at?: string | null;
}

export interface Session {
  id: string;
  family?: string;
  device_name?: string | null;
  platform?: string | null;
  user_agent?: string | null;
  ip?: string | null;
  last_seen_at?: string | null;
  created_at?: string | null;
  is_current?: boolean;
}

export interface BookmarkedComment {
  id: string;
  comment_id: string;
  post_id: string;
  post_slug?: string;
  post_title?: string;
  author_username?: string;
  content: string;
  created_at: string;
  author?: {
    username: string;
    display_name: string;
    avatar_url?: string;
  };
}

export interface PublicProfile {
  id: string;
  username: string;
  display_name: string;
  avatar_url?: string;
  bio?: string;
  banner_url?: string;
  is_verified?: boolean;
  /** Nested stats from GET /api/users/:username */
  stats?: {
    posts?: number;
    followers?: number;
    following?: number;
  };
  /** Legacy flat counts (prefer stats.*) */
  post_count?: number;
  follower_count?: number;
  following_count?: number;
  is_following?: boolean;
  created_at?: string;
}

export interface FollowListUser {
  id: string;
  username: string;
  display_name: string;
  avatar_url?: string;
  bio?: string;
  is_following?: boolean;
  is_verified?: boolean;
}

export interface ContactUser extends FollowListUser {
  from_contacts: boolean;
}
