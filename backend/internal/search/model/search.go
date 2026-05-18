package model

import "time"

type PostDocument struct {
	ID            string    `json:"id"`
	AuthorID      string    `json:"author_id"`
	AuthorName    string    `json:"author_name"`
	Title         string    `json:"title"`
	Slug          string    `json:"slug"`
	Excerpt       string    `json:"excerpt"`
	Content       string    `json:"content"`
	ContentMD     string    `json:"content_md"`
	CoverImageURL string    `json:"cover_image_url"`
	Category      string    `json:"category"`
	Tags          []string  `json:"tags"`
	Status        string    `json:"status"`
	Language      string    `json:"_language"`
	WordCount     int       `json:"word_count"`
	ReadingTime   int       `json:"reading_time"`
	PublishedAt   time.Time `json:"published_at"`
}

type SearchResult struct {
	ID              string    `json:"id"`
	Title           string    `json:"title"`
	Slug            string    `json:"slug"`
	Excerpt         string    `json:"excerpt"`
	CoverImageURL   string    `json:"cover_image_url"`
	Category        string    `json:"category"`
	Tags            []string  `json:"tags"`
	Language        string    `json:"language"`
	AuthorName      string    `json:"author_name"`
	AuthorUsername  string    `json:"author_username"`
	PublishedAt     time.Time `json:"published_at"`
	WordCount       int       `json:"word_count"`
	ReadingTime     int       `json:"reading_time"`
	Formatted       *Snippet  `json:"_formatted,omitempty"`
}

type Snippet struct {
	Title   string `json:"title"`
	Excerpt string `json:"excerpt"`
	Content string `json:"content_md"`
}

type SearchParams struct {
	Query     string
	Category  string
	Tag       string
	Author    string
	Language  string
	After     string
	Before    string
	Limit     int
	Offset    int
}

type SuggestItem struct {
	Text  string `json:"text"`
	Type  string `json:"type"`
	Slug  string `json:"slug,omitempty"`
}
