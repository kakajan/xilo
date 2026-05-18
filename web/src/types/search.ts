export interface SearchParams {
  q?: string;
  category?: string;
  tag?: string;
  author?: string;
  after?: string;
  before?: string;
  limit?: number;
  offset?: number;
}

export interface SearchResult {
  id: string;
  title: string;
  slug: string;
  excerpt: string;
  cover_image_url: string;
  category: string;
  tags: string[];
  author_name: string;
  author_username: string;
  published_at: string;
  word_count: number;
  reading_time: number;
  _formatted?: {
    title: string;
    excerpt: string;
    content_md: string;
  };
}

export interface SearchResponse {
  data: SearchResult[];
  total: number;
  limit: number;
  offset: number;
}

export interface SuggestItem {
  text: string;
  type: "post" | "author" | "tag";
  slug?: string;
}
