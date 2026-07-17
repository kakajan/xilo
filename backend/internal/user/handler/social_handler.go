package handler

import (
	"github.com/gofiber/fiber/v2"
	"github.com/jmoiron/sqlx"
)

type SocialHandler struct {
	db *sqlx.DB
}

func NewSocialHandler(db *sqlx.DB) *SocialHandler {
	return &SocialHandler{db: db}
}

// @Summary      Toggle bookmark on a post
// @Tags         social
// @Produce      json
// @Security     BearerAuth
// @Param        id   path string true "Post ID"
// @Success      200  {object}  map[string]interface{}
// @Router       /posts/{id}/bookmark [post]
// @Router       /posts/{id}/bookmark [delete]
func (h *SocialHandler) ToggleBookmark(c *fiber.Ctx) error {
	userID := c.Locals("userID").(string)
	postID := c.Params("id")

	method := c.Method()
	if method == "DELETE" {
		_, err := h.db.Exec(`
			DELETE FROM bookmarks WHERE user_id = $1 AND post_id = $2
		`, userID, postID)
		if err != nil {
			return c.Status(fiber.StatusInternalServerError).JSON(fiber.Map{"error": "failed"})
		}
		return c.JSON(fiber.Map{"bookmarked": false})
	}

	_, err := h.db.Exec(`
		INSERT INTO bookmarks (user_id, post_id) VALUES ($1, $2)
		ON CONFLICT (user_id, post_id) DO NOTHING
	`, userID, postID)
	if err != nil {
		return c.Status(fiber.StatusInternalServerError).JSON(fiber.Map{"error": "failed"})
	}
	return c.JSON(fiber.Map{"bookmarked": true})
}

// @Summary      List bookmarked posts
// @Tags         social
// @Produce      json
// @Security     BearerAuth
// @Success      200  {object}  map[string]interface{}
// @Router       /bookmarks [get]
func (h *SocialHandler) ListBookmarks(c *fiber.Ctx) error {
	userID := c.Locals("userID").(string)

	type Author struct {
		ID          string `json:"id"`
		Username    string `json:"username"`
		DisplayName string `json:"display_name"`
		AvatarURL   string `json:"avatar_url,omitempty"`
	}

	type PostRow struct {
		ID             string  `db:"id"`
		AuthorID       string  `db:"author_id"`
		Title          string  `db:"title"`
		Slug           string  `db:"slug"`
		Excerpt        string  `db:"excerpt"`
		ContentMD      string  `db:"content_md"`
		CoverImageURL  string  `db:"cover_image_url"`
		Category       string  `db:"category"`
		PublishedAt    *string `db:"published_at"`
		CreatedAt      string  `db:"created_at"`
		ReadingTime    int     `db:"reading_time"`
		AuthorUsername string  `db:"author_username"`
		AuthorName     string  `db:"author_name"`
		AuthorAvatar   string  `db:"author_avatar"`
	}

	var rows []PostRow
	err := h.db.Select(&rows, `
		SELECT p.id, p.author_id, p.title, p.slug, COALESCE(p.excerpt, '') AS excerpt,
		       COALESCE(p.content_md, '') AS content_md,
		       COALESCE(p.cover_image_url, '') AS cover_image_url,
		       COALESCE(p.category, '') AS category,
		       p.published_at::text AS published_at,
		       p.created_at::text AS created_at,
		       p.reading_time,
		       u.username AS author_username,
		       COALESCE(u.display_name, u.username) AS author_name,
		       COALESCE(u.avatar_url, '') AS author_avatar
		FROM bookmarks b
		JOIN posts p ON b.post_id = p.id
		JOIN users u ON p.author_id = u.id
		WHERE b.user_id = $1 AND p.deleted_at IS NULL
		ORDER BY b.created_at DESC
		LIMIT 50
	`, userID)
	if err != nil {
		return c.Status(fiber.StatusInternalServerError).JSON(fiber.Map{"error": "failed"})
	}

	type PostOut struct {
		ID            string  `json:"id"`
		AuthorID      string  `json:"author_id"`
		Author        Author  `json:"author"`
		Title         string  `json:"title"`
		Slug          string  `json:"slug"`
		Content       string  `json:"content"`
		ContentMD     string  `json:"content_md"`
		Excerpt       string  `json:"excerpt"`
		CoverImageURL string  `json:"cover_image_url"`
		Category      string  `json:"category"`
		IsBookmarked  bool    `json:"is_bookmarked"`
		ReadingTime   int     `json:"reading_time"`
		CreatedAt     string  `json:"created_at"`
		PublishedAt   *string `json:"published_at"`
	}

	posts := make([]PostOut, 0, len(rows))
	for _, row := range rows {
		content := row.ContentMD
		if content == "" {
			content = row.Excerpt
		}
		posts = append(posts, PostOut{
			ID:       row.ID,
			AuthorID: row.AuthorID,
			Author: Author{
				ID:          row.AuthorID,
				Username:    row.AuthorUsername,
				DisplayName: row.AuthorName,
				AvatarURL:   row.AuthorAvatar,
			},
			Title:         row.Title,
			Slug:          row.Slug,
			Content:       content,
			ContentMD:     row.ContentMD,
			Excerpt:       row.Excerpt,
			CoverImageURL: row.CoverImageURL,
			Category:      row.Category,
			IsBookmarked:  true,
			ReadingTime:   row.ReadingTime,
			CreatedAt:     row.CreatedAt,
			PublishedAt:   row.PublishedAt,
		})
	}

	return c.JSON(fiber.Map{"data": posts})
}

// @Summary      Toggle bookmark on a comment
// @Tags         social
// @Produce      json
// @Security     BearerAuth
// @Param        id   path string true "Comment ID"
// @Success      200  {object}  map[string]interface{}
// @Router       /comments/{id}/bookmark [post]
// @Router       /comments/{id}/bookmark [delete]
func (h *SocialHandler) ToggleCommentBookmark(c *fiber.Ctx) error {
	userID := c.Locals("userID").(string)
	commentID := c.Params("id")

	var exists bool
	if err := h.db.Get(&exists, `
		SELECT EXISTS(
			SELECT 1 FROM comments
			WHERE id = $1 AND deleted_at IS NULL
		)
	`, commentID); err != nil {
		return c.Status(fiber.StatusInternalServerError).JSON(fiber.Map{"error": "failed"})
	}
	if !exists {
		return c.Status(fiber.StatusNotFound).JSON(fiber.Map{"error": "comment not found"})
	}

	method := c.Method()
	if method == "DELETE" {
		_, err := h.db.Exec(`
			DELETE FROM comment_bookmarks WHERE user_id = $1 AND comment_id = $2
		`, userID, commentID)
		if err != nil {
			return c.Status(fiber.StatusInternalServerError).JSON(fiber.Map{"error": "failed"})
		}
		return c.JSON(fiber.Map{"bookmarked": false})
	}

	_, err := h.db.Exec(`
		INSERT INTO comment_bookmarks (user_id, comment_id) VALUES ($1, $2)
		ON CONFLICT (user_id, comment_id) DO NOTHING
	`, userID, commentID)
	if err != nil {
		return c.Status(fiber.StatusInternalServerError).JSON(fiber.Map{"error": "failed"})
	}
	return c.JSON(fiber.Map{"bookmarked": true})
}

// @Summary      List bookmarked comments
// @Tags         social
// @Produce      json
// @Security     BearerAuth
// @Success      200  {object}  map[string]interface{}
// @Router       /bookmarks/comments [get]
func (h *SocialHandler) ListCommentBookmarks(c *fiber.Ctx) error {
	userID := c.Locals("userID").(string)

	type Row struct {
		ID             string  `db:"id"`
		PostID         string  `db:"post_id"`
		PostSlug       string  `db:"post_slug"`
		PostTitle      string  `db:"post_title"`
		AuthorID       string  `db:"author_id"`
		ParentID       *string `db:"parent_id"`
		RootID         *string `db:"root_id"`
		Depth          int     `db:"depth"`
		Content        string  `db:"content"`
		IsPinned       bool    `db:"is_pinned"`
		CreatedAt      string  `db:"created_at"`
		AuthorUsername string  `db:"author_username"`
		AuthorName     string  `db:"author_name"`
		AuthorAvatar   string  `db:"author_avatar"`
	}

	var rows []Row
	err := h.db.Select(&rows, `
		SELECT c.id, c.post_id, p.slug AS post_slug, p.title AS post_title,
		       c.author_id, c.parent_id, c.root_id, c.depth, c.content, c.is_pinned,
		       c.created_at::text AS created_at,
		       u.username AS author_username,
		       COALESCE(u.display_name, u.username) AS author_name,
		       COALESCE(u.avatar_url, '') AS author_avatar
		FROM comment_bookmarks b
		JOIN comments c ON b.comment_id = c.id
		JOIN posts p ON c.post_id = p.id
		JOIN users u ON c.author_id = u.id
		WHERE b.user_id = $1 AND c.deleted_at IS NULL AND p.deleted_at IS NULL
		ORDER BY b.created_at DESC
		LIMIT 50
	`, userID)
	if err != nil {
		return c.Status(fiber.StatusInternalServerError).JSON(fiber.Map{"error": "failed"})
	}

	type Author struct {
		ID          string `json:"id"`
		Username    string `json:"username"`
		DisplayName string `json:"display_name"`
		AvatarURL   string `json:"avatar_url,omitempty"`
	}
	type PostRef struct {
		ID    string `json:"id"`
		Title string `json:"title"`
		Slug  string `json:"slug"`
	}
	type CommentOut struct {
		ID           string   `json:"id"`
		PostID       string   `json:"post_id"`
		AuthorID     string   `json:"author_id"`
		Author       Author   `json:"author"`
		ParentID     *string  `json:"parent_id,omitempty"`
		RootID       *string  `json:"root_id,omitempty"`
		Depth        int      `json:"depth"`
		Content      string   `json:"content"`
		IsPinned     bool     `json:"is_pinned"`
		IsBookmarked bool     `json:"is_bookmarked"`
		CreatedAt    string   `json:"created_at"`
		Post         PostRef  `json:"post"`
	}

	out := make([]CommentOut, 0, len(rows))
	for _, row := range rows {
		out = append(out, CommentOut{
			ID:       row.ID,
			PostID:   row.PostID,
			AuthorID: row.AuthorID,
			Author: Author{
				ID:          row.AuthorID,
				Username:    row.AuthorUsername,
				DisplayName: row.AuthorName,
				AvatarURL:   row.AuthorAvatar,
			},
			ParentID:     row.ParentID,
			RootID:       row.RootID,
			Depth:        row.Depth,
			Content:      row.Content,
			IsPinned:     row.IsPinned,
			IsBookmarked: true,
			CreatedAt:    row.CreatedAt,
			Post: PostRef{
				ID:    row.PostID,
				Title: row.PostTitle,
				Slug:  row.PostSlug,
			},
		})
	}

	return c.JSON(fiber.Map{"data": out})
}

// @Summary      Toggle repost on a post
// @Tags         social
// @Produce      json
// @Security     BearerAuth
// @Param        id   path string true "Post ID"
// @Success      200  {object}  map[string]interface{}
// @Failure      404  {object}  map[string]string
// @Router       /posts/{id}/repost [post]
// @Router       /posts/{id}/repost [delete]
func (h *SocialHandler) ToggleRepost(c *fiber.Ctx) error {
	userID := c.Locals("userID").(string)
	postID := c.Params("id")

	var exists bool
	if err := h.db.Get(&exists, `
		SELECT EXISTS(
			SELECT 1 FROM posts
			WHERE id = $1 AND deleted_at IS NULL
		)
	`, postID); err != nil {
		return c.Status(fiber.StatusInternalServerError).JSON(fiber.Map{"error": "failed"})
	}
	if !exists {
		return c.Status(fiber.StatusNotFound).JSON(fiber.Map{"error": "post not found"})
	}

	method := c.Method()
	if method == "DELETE" {
		if _, err := h.db.Exec(`
			DELETE FROM reposts WHERE user_id = $1 AND post_id = $2
		`, userID, postID); err != nil {
			return c.Status(fiber.StatusInternalServerError).JSON(fiber.Map{"error": "failed"})
		}
		return c.JSON(fiber.Map{
			"reposted":     false,
			"repost_count": h.repostCount(postID),
		})
	}

	if _, err := h.db.Exec(`
		INSERT INTO reposts (user_id, post_id) VALUES ($1, $2)
		ON CONFLICT (user_id, post_id) DO NOTHING
	`, userID, postID); err != nil {
		return c.Status(fiber.StatusInternalServerError).JSON(fiber.Map{"error": "failed"})
	}
	return c.JSON(fiber.Map{
		"reposted":     true,
		"repost_count": h.repostCount(postID),
	})
}

func (h *SocialHandler) repostCount(postID string) int {
	var count int
	_ = h.db.Get(&count, `SELECT COUNT(*)::int FROM reposts WHERE post_id = $1`, postID)
	return count
}

// @Summary      Toggle follow a user
// @Tags         social
// @Produce      json
// @Security     BearerAuth
// @Param        username path string true "Username to follow/unfollow"
// @Success      200  {object}  map[string]interface{}
// @Failure      404  {object}  map[string]string
// @Router       /users/{username}/follow [post]
// @Router       /users/{username}/follow [delete]
func (h *SocialHandler) ToggleFollow(c *fiber.Ctx) error {
	followerID := c.Locals("userID").(string)
	followingUsername := c.Params("username")

	var followingID string
	err := h.db.Get(&followingID, `SELECT id FROM users WHERE username = $1`, followingUsername)
	if err != nil {
		return c.Status(fiber.StatusNotFound).JSON(fiber.Map{"error": "user not found"})
	}

	method := c.Method()
	if method == "DELETE" {
		_, err := h.db.Exec(`
			DELETE FROM follows WHERE follower_id = $1 AND following_id = $2
		`, followerID, followingID)
		if err != nil {
			return c.Status(fiber.StatusInternalServerError).JSON(fiber.Map{"error": "failed"})
		}
		return c.JSON(fiber.Map{"following": false})
	}

	_, err = h.db.Exec(`
		INSERT INTO follows (follower_id, following_id) VALUES ($1, $2)
		ON CONFLICT (follower_id, following_id) DO NOTHING
	`, followerID, followingID)
	if err != nil {
		return c.Status(fiber.StatusInternalServerError).JSON(fiber.Map{"error": "failed"})
	}
	return c.JSON(fiber.Map{"following": true})
}
