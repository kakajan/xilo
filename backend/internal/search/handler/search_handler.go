package handler

import (
	"strconv"

	"github.com/gofiber/fiber/v2"
	"github.com/xilo-platform/xilo/internal/search/model"
	"github.com/xilo-platform/xilo/internal/search/service"
)

type SearchHandler struct {
	svc *service.SearchService
}

func NewSearchHandler(svc *service.SearchService) *SearchHandler {
	return &SearchHandler{svc: svc}
}

// SearchPosts godoc
// @Summary      Search posts
// @Tags         search
// @Produce      json
// @Param        q         query    string  false  "Search query"
// @Param        category  query    string  false  "Filter by category"
// @Param        tag       query    string  false  "Filter by tag"
// @Param        author    query    string  false  "Filter by author"
// @Param        language  query    string  false  "Filter by language"
// @Param        after     query    string  false  "Filter after date"
// @Param        before    query    string  false  "Filter before date"
// @Param        limit     query    int     false  "Items per page"  default(20)
// @Param        offset    query    int     false  "Pagination offset"  default(0)
// @Success      200       {object} map[string]interface{}
// @Failure      400       {object} map[string]string
// @Router       /search/posts [get]
func (h *SearchHandler) SearchPosts(c *fiber.Ctx) error {
	query := c.Query("q")
	category := c.Query("category")
	tag := c.Query("tag")
	author := c.Query("author")
	language := c.Query("language")
	after := c.Query("after")
	before := c.Query("before")
	limit, _ := strconv.Atoi(c.Query("limit", "20"))
	offset, _ := strconv.Atoi(c.Query("offset", "0"))

	if query == "" && category == "" && tag == "" {
		return c.Status(fiber.StatusBadRequest).JSON(fiber.Map{
			"error": "at least one search parameter is required",
		})
	}

	results, total, err := h.svc.Search(c.UserContext(), &model.SearchParams{
		Query:    query,
		Category: category,
		Tag:      tag,
		Author:   author,
		Language: language,
		After:    after,
		Before:   before,
		Limit:    limit,
		Offset:   offset,
	})
	if err != nil {
		return c.Status(fiber.StatusInternalServerError).JSON(fiber.Map{
			"error": "search failed",
		})
	}

	return c.JSON(fiber.Map{
		"data":   results,
		"total":  total,
		"limit":  limit,
		"offset": offset,
	})
}

// Suggest godoc
// @Summary      Search suggestions
// @Tags         search
// @Produce      json
// @Param        q    query    string  true   "Search query"
// @Success      200  {object} map[string]interface{}
// @Router       /search/suggest [get]
func (h *SearchHandler) Suggest(c *fiber.Ctx) error {
	query := c.Query("q")
	if len(query) < 2 {
		return c.JSON(fiber.Map{"data": []interface{}{}})
	}

	items, err := h.svc.Suggest(c.UserContext(), query)
	if err != nil {
		return c.Status(fiber.StatusInternalServerError).JSON(fiber.Map{
			"error": "suggest failed",
		})
	}

	return c.JSON(fiber.Map{"data": items})
}
