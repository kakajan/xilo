package model

import (
	"errors"
	"time"

	"github.com/lib/pq"
	authmodel "github.com/xilo-platform/xilo/internal/auth/model"
)

type Post struct {
	ID           string     `json:"id" db:"id"`
	AuthorID     string     `json:"author_id" db:"author_id"`
	Title        string     `json:"title" db:"title"`
	Slug         string     `json:"slug" db:"slug"`
	Excerpt      string     `json:"excerpt" db:"excerpt"`
	Content      string     `json:"content" db:"content"`
	ContentMD    string     `json:"content_md" db:"content_md"`
	CoverImageURL *string   `json:"cover_image_url,omitempty" db:"cover_image_url"`
	Category     *string    `json:"category,omitempty" db:"category"`
	Tags         pq.StringArray `json:"tags" db:"tags"`
	Status       string     `json:"status" db:"status"`
	IsPremium    bool       `json:"is_premium" db:"is_premium"`
	WordCount    int        `json:"word_count" db:"word_count"`
	ReadingTime  int        `json:"reading_time" db:"reading_time"`
	Language     string     `json:"language" db:"language"`
	ViewCount    int64      `json:"view_count" db:"view_count"`
	ScheduledAt  *time.Time `json:"scheduled_at,omitempty" db:"scheduled_at"`
	PublishedAt  *time.Time `json:"published_at,omitempty" db:"published_at"`
	CreatedAt    time.Time  `json:"created_at" db:"created_at"`
	UpdatedAt    time.Time  `json:"updated_at" db:"updated_at"`
	DeletedAt    *time.Time `json:"deleted_at,omitempty" db:"deleted_at"`

	Author           *authmodel.User   `json:"author,omitempty" db:"-"`
	CommentCount     int               `json:"comment_count" db:"-"`
	RepostCount      int               `json:"repost_count" db:"-"`
	Reactions        map[string]int    `json:"reactions,omitempty" db:"-"`
	ViewerReactions  []string          `json:"viewer_reactions,omitempty" db:"-"`
	IsBookmarked     bool              `json:"is_bookmarked" db:"-"`
	IsReposted       bool              `json:"is_reposted" db:"-"`
}

var ErrInvalidViewSession = errors.New("session_id is required for anonymous views")

type RecordViewRequest struct {
	SessionID string `json:"session_id"`
}

type RecordViewResult struct {
	Counted   bool   `json:"counted"`
	ViewCount int64  `json:"view_count"`
	Slug      string `json:"-"`
	AuthorID  string `json:"-"`
}

type PostVersion struct {
	ID        string    `json:"id" db:"id"`
	PostID    string    `json:"post_id" db:"post_id"`
	Title     string    `json:"title" db:"title"`
	Content   string    `json:"content" db:"content"`
	ContentMD string    `json:"content_md" db:"content_md"`
	Version   int       `json:"version" db:"version"`
	CreatedAt time.Time `json:"created_at" db:"created_at"`
}

type CreatePostRequest struct {
	Title        string   `json:"title"`
	Slug         string   `json:"slug"`
	Excerpt      string   `json:"excerpt"`
	Content      string   `json:"content"`
	ContentMD    string   `json:"content_md"`
	CoverImageURL string  `json:"cover_image_url"`
	Category     string   `json:"category"`
	Tags         []string `json:"tags"`
	Status       string   `json:"status"`
	IsPremium    bool     `json:"is_premium"`
	Language     string   `json:"language"`
	ScheduledAt  *time.Time `json:"scheduled_at"`
}

type UpdatePostRequest struct {
	Title        *string   `json:"title"`
	Slug         *string   `json:"slug"`
	Excerpt      *string   `json:"excerpt"`
	Content      *string   `json:"content"`
	ContentMD    *string   `json:"content_md"`
	CoverImageURL *string  `json:"cover_image_url"`
	Category     *string   `json:"category"`
	Tags         *[]string `json:"tags"`
	Status       *string   `json:"status"`
	IsPremium    *bool     `json:"is_premium"`
	Language     *string   `json:"language"`
	ScheduledAt  *time.Time `json:"scheduled_at"`
}

type PostListParams struct {
	Cursor   string
	Limit    int
	Category string
	Tag      string
	Author    string
	Status    string
	Language  string
	MediaOnly bool
	ViewerID  string
}

// TagSuggestion is a hashtag/tag used for autocomplete and trending.
type TagSuggestion struct {
	Tag   string `json:"tag" db:"tag"`
	Count int64  `json:"count" db:"count"`
}
