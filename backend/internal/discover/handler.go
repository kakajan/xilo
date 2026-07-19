package discover

import (
	"strconv"

	"github.com/gofiber/fiber/v2"
	"github.com/jmoiron/sqlx"
)

// Handler serves Discover feed endpoints.
type Handler struct {
	repo *Repository
}

// NewDiscoverHandler constructs a Discover handler backed by sqlx.
// Mount with OptionalAuth, e.g.:
//
//	discoverH := discover.NewDiscoverHandler(db)
//	app.Get("/api/discover/comments", applyPublicRateLimit, authmw.OptionalAuth(jwtMgr), discoverH.ListComments)
func NewDiscoverHandler(db *sqlx.DB) *Handler {
	return &Handler{repo: NewRepository(db)}
}

// ListComments godoc
// @Summary      Discover comments (interest-aware)
// @Tags         discover
// @Produce      json
// @Param        limit     query int    false "Max items (default 50, max 100)"
// @Param        interest  query string false "Filter to category/tag slug"
// @Success      200 {object} CommentsResponse
// @Failure      500 {object} map[string]string
// @Router       /discover/comments [get]
func (h *Handler) ListComments(c *fiber.Ctx) error {
	userID, _ := c.Locals("userID").(string)
	interest := c.Query("interest")
	limit, _ := strconv.Atoi(c.Query("limit", "50"))

	comments, err := h.repo.ListComments(c.UserContext(), userID, interest, limit)
	if err != nil {
		return c.Status(fiber.StatusInternalServerError).JSON(fiber.Map{
			"error": "failed to load discover comments",
			"code":  "internal_error",
		})
	}
	if comments == nil {
		comments = []Comment{}
	}

	return c.JSON(CommentsResponse{Data: comments})
}
