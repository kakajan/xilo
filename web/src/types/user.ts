export type PreferredCalendar = "auto" | "jalali" | "gregorian";

export interface User {
  id: string;
  email: string;
  username: string;
  display_name: string;
  avatar_url: string;
  bio: string;
  role: "reader" | "author" | "editor" | "admin" | "superadmin";
  email_verified: boolean;
  preferred_language?: string;
  preferred_calendar?: PreferredCalendar;
  username_pending?: boolean;
  created_at: string;
  updated_at: string;
}

export interface AuthResponse {
  access_token: string;
  refresh_token: string;
  expires_in: number;
  user: User;
}

export interface RegisterRequest {
  email: string;
  password: string;
  username?: string;
}

export interface LoginRequest {
  email: string;
  password: string;
}
