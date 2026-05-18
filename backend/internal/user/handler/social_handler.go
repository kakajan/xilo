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

	type Post struct {
		ID            string   `json:"id" db:"id"`
		AuthorID      string   `json:"author_id" db:"author_id"`
		Title         string   `json:"title" db:"title"`
		Slug          string   `json:"slug" db:"slug"`
		Excerpt       string   `json:"excerpt" db:"excerpt"`
		CoverImageURL string   `json:"cover_image_url" db:"cover_image_url"`
		Category      string   `json:"category" db:"category"`
		Tags          []string `json:"tags" db:"tags"`
		PublishedAt   *string  `json:"published_at" db:"published_at"`
		ReadingTime   int      `json:"reading_time" db:"reading_time"`
		AuthorName    string   `json:"author_name" db:"author_name"`
		AuthorUsername string  `json:"author_username" db:"author_username"`
	}

	var posts []Post
	err := h.db.Select(&posts, `
		SELECT p.id, p.author_id, p.title, p.slug, p.excerpt, p.cover_image_url,
		       p.category, p.tags, p.published_at, p.reading_time
		FROM bookmarks b
		JOIN posts p ON b.post_id = p.id
		WHERE b.user_id = $1 AND p.deleted_at IS NULL
		ORDER BY b.created_at DESC
		LIMIT 50
	`, userID)
	if err != nil {
		return c.Status(fiber.StatusInternalServerError).JSON(fiber.Map{"error": "failed"})
	}

	if posts == nil {
		posts = []Post{}
	}

	return c.JSON(fiber.Map{"data": posts})
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
