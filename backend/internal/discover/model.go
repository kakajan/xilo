package discover

import (
	"time"

	"github.com/lib/pq"
)

// Author is the public author fields for a Discover comment card.
type Author struct {
	ID          string `json:"id"`
	Username    string `json:"username"`
	DisplayName string `json:"display_name"`
	AvatarURL   string `json:"avatar_url,omitempty"`
}

// PostContext is the parent post summary for a Discover card.
type PostContext struct {
	ID       string   `json:"id"`
	Title    string   `json:"title"`
	Slug     string   `json:"slug"`
	Category string   `json:"category,omitempty"`
	Tags     []string `json:"tags,omitempty"`
}

// Comment is a Discover feed item (shape aligned with Android DiscoverCommentDto).
type Comment struct {
	ID         string      `json:"id"`
	PostID     string      `json:"post_id"`
	AuthorID   string      `json:"author_id"`
	Content    string      `json:"content"`
	CreatedAt  time.Time   `json:"created_at"`
	LikeCount  int         `json:"like_count"`
	ReplyCount int         `json:"reply_count"`
	Author     Author      `json:"author"`
	Post       PostContext `json:"post"`
}

// CommentsResponse is the JSON body for GET /api/discover/comments.
type CommentsResponse struct {
	Data []Comment `json:"data"`
}

type commentRow struct {
	ID           string         `db:"id"`
	Content      string         `db:"content"`
	CreatedAt    time.Time      `db:"created_at"`
	LikesCount   int            `db:"likes_count"`
	RepliesCount int            `db:"replies_count"`
	AuthorID     string         `db:"author_id"`
	Username     string         `db:"username"`
	DisplayName  string         `db:"display_name"`
	AvatarURL    string         `db:"avatar_url"`
	PostID       string         `db:"post_id"`
	PostTitle    string         `db:"post_title"`
	PostSlug     string         `db:"post_slug"`
	PostCategory string         `db:"post_category"`
	PostTags     pq.StringArray `db:"post_tags"`
}
