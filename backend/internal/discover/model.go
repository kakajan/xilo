package discover

import (
	"database/sql"
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
	ID             string   `json:"id"`
	Title          string   `json:"title"`
	Slug           string   `json:"slug"`
	AuthorUsername string   `json:"author_username,omitempty"`
	Category       string   `json:"category,omitempty"`
	Tags           []string `json:"tags,omitempty"`
}

// Comment is a Discover feed item (shape aligned with Android DiscoverCommentDto).
type Comment struct {
	ID              string         `json:"id"`
	PostID          string         `json:"post_id"`
	AuthorID        string         `json:"author_id"`
	ParentID        *string        `json:"parent_id,omitempty"`
	RootID          *string        `json:"root_id,omitempty"`
	Depth           int            `json:"depth"`
	Content         string         `json:"content"`
	CreatedAt       time.Time      `json:"created_at"`
	LikeCount       int            `json:"like_count"`
	ReplyCount      int            `json:"reply_count"`
	RepostCount     int            `json:"repost_count"`
	IsReposted      bool           `json:"is_reposted,omitempty"`
	IsBookmarked    bool           `json:"is_bookmarked,omitempty"`
	Reactions       map[string]int `json:"reactions,omitempty"`
	ViewerReactions []string       `json:"viewer_reactions,omitempty"`
	Author          Author         `json:"author"`
	Post            PostContext    `json:"post"`
	Parent          *ParentContext `json:"parent,omitempty"`
}

// CommentsResponse is the JSON body for GET /api/discover/comments.
type CommentsResponse struct {
	Data []Comment `json:"data"`
}

type commentRow struct {
	ID                     string         `db:"id"`
	Content                string         `db:"content"`
	CreatedAt              time.Time      `db:"created_at"`
	LikesCount             int            `db:"likes_count"`
	RepliesCount           int            `db:"replies_count"`
	RepostCount            int            `db:"repost_count"`
	AuthorID               string         `db:"author_id"`
	Username               string         `db:"username"`
	DisplayName            string         `db:"display_name"`
	AvatarURL              string         `db:"avatar_url"`
	PostID                 string         `db:"post_id"`
	PostTitle              string         `db:"post_title"`
	PostSlug               string         `db:"post_slug"`
	PostAuthorUsername     string         `db:"post_author_username"`
	PostCategory           string         `db:"post_category"`
	PostTags               pq.StringArray `db:"post_tags"`
	ParentID               sql.NullString `db:"parent_id"`
	RootID                 sql.NullString `db:"root_id"`
	Depth                  int            `db:"depth"`
	ParentCommentID        sql.NullString `db:"parent_comment_id"`
	ParentContent          sql.NullString `db:"parent_content"`
	ParentAuthorUsername   sql.NullString `db:"parent_author_username"`
	ParentAuthorDisplayName sql.NullString `db:"parent_author_display_name"`
}

func (row commentRow) toComment() Comment {
	tags := []string(row.PostTags)
	if tags == nil {
		tags = []string{}
	}
	c := Comment{
		ID:          row.ID,
		PostID:      row.PostID,
		AuthorID:    row.AuthorID,
		Depth:       row.Depth,
		Content:     row.Content,
		CreatedAt:   row.CreatedAt,
		LikeCount:   row.LikesCount,
		ReplyCount:  row.RepliesCount,
		RepostCount: row.RepostCount,
		Author: Author{
			ID:          row.AuthorID,
			Username:    row.Username,
			DisplayName: row.DisplayName,
			AvatarURL:   row.AvatarURL,
		},
		Post: PostContext{
			ID:             row.PostID,
			Title:          row.PostTitle,
			Slug:           row.PostSlug,
			AuthorUsername: row.PostAuthorUsername,
			Category:       row.PostCategory,
			Tags:           tags,
		},
	}
	if row.ParentID.Valid && row.ParentID.String != "" {
		id := row.ParentID.String
		c.ParentID = &id
	}
	if row.RootID.Valid && row.RootID.String != "" {
		id := row.RootID.String
		c.RootID = &id
	}
	c.Parent = buildParentContext(
		nullString(row.ParentCommentID),
		nullString(row.ParentAuthorUsername),
		nullString(row.ParentAuthorDisplayName),
		nullString(row.ParentContent),
	)
	return c
}

func nullString(ns sql.NullString) string {
	if !ns.Valid {
		return ""
	}
	return ns.String
}
