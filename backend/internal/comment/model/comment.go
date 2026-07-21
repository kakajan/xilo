package model

import (
	"time"

	authmodel "github.com/xilo-platform/xilo/internal/auth/model"
)

type Comment struct {
	ID          string     `json:"id" db:"id"`
	PostID      string     `json:"post_id" db:"post_id"`
	AuthorID    string     `json:"author_id" db:"author_id"`
	ParentID    *string    `json:"parent_id,omitempty" db:"parent_id"`
	RootID      *string    `json:"root_id,omitempty" db:"root_id"`
	Depth       int        `json:"depth" db:"depth"`
	Content     string     `json:"content" db:"content"`
	ContentHTML string     `json:"content_html" db:"content_html"`
	MediaURL    string     `json:"media_url,omitempty" db:"media_url"`
	IsPinned    bool       `json:"is_pinned" db:"is_pinned"`
	IsSpam      bool       `json:"is_spam" db:"is_spam"`
	CreatedAt   time.Time  `json:"created_at" db:"created_at"`
	UpdatedAt   time.Time  `json:"updated_at" db:"updated_at"`
	DeletedAt   *time.Time `json:"deleted_at,omitempty" db:"deleted_at"`
	// IsDeleted is true when DeletedAt is set (tombstone or soft-deleted row).
	IsDeleted bool `json:"is_deleted" db:"-"`

	RepostCount int `json:"repost_count" db:"repost_count"`

	Author          *authmodel.User  `json:"author,omitempty" db:"-"`
	Replies         []*Comment       `json:"replies,omitempty" db:"-"`
	Reactions       map[string]int   `json:"reactions,omitempty" db:"-"`
	ViewerReactions []string         `json:"viewer_reactions,omitempty" db:"-"`
	IsBookmarked    bool             `json:"is_bookmarked" db:"-"`
	IsReposted      bool             `json:"is_reposted" db:"-"`
	Post            *PostRef         `json:"post,omitempty" db:"-"`
}

type CreateCommentRequest struct {
	Content  string  `json:"content"`
	ParentID *string `json:"parent_id"`
	RootID   *string `json:"root_id"`
	MediaURL string  `json:"media_url"`
}

type Reaction struct {
	ID         string    `json:"id" db:"id"`
	UserID     string    `json:"user_id" db:"user_id"`
	TargetType string    `json:"target_type" db:"target_type"`
	TargetID   string    `json:"target_id" db:"target_id"`
	Reaction   string    `json:"reaction" db:"reaction"`
	CreatedAt  time.Time `json:"created_at" db:"created_at"`
}
