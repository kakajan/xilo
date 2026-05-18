package handler

import (
	"errors"
	"strconv"

	"github.com/gofiber/fiber/v2"
	"github.com/xilo-platform/xilo/internal/post/model"
	"github.com/xilo-platform/xilo/internal/post/repository"
	"github.com/xilo-platform/xilo/internal/post/service"
)

type PostHandler struct {
	svc *service.PostService
}

func NewPostHandler(svc *service.PostService) *PostHandler {
	return &PostHandler{svc: svc}
}

// @Summary      Create a new post
// @Description  Create a new blog post
// @Tags         posts
// @Accept       json
// @Produce      json
// @Security     BearerAuth
// @Param        request body model.CreatePostRequest true "Post data"
// @Success      201  {object}  model.Post
// @Failure      400  {object}  map[string]string
// @Router       /posts [post]
func (h *PostHandler) Create(c *fiber.Ctx) error {
	userID := c.Locals("userID").(string)

	var req model.CreatePostRequest
	if err := c.BodyParser(&req); err != nil {
		return c.Status(fiber.StatusBadRequest).JSON(fiber.Map{
			"error": "invalid request body",
		})
	}

	post, err := h.svc.Create(c.UserContext(), userID, &req)
	if err != nil {
		return c.Status(fiber.StatusBadRequest).JSON(fiber.Map{
			"error": err.Error(),
		})
	}

	return c.Status(fiber.StatusCreated).JSON(post)
}

// @Summary      Get post by slug
// @Description  Get a post by its slug
// @Tags         posts
// @Produce      json
// @Param        slug path string true "Post slug"
// @Success      200  {object}  model.Post
// @Failure      404  {object}  map[string]string
// @Router       /posts/{slug} [get]
func (h *PostHandler) GetBySlug(c *fiber.Ctx) error {
	slug := c.Params("slug")
	post, err := h.svc.GetBySlug(c.UserContext(), slug)
	if err != nil {
		if errors.Is(err, repository.ErrPostNotFound) {
			return c.Status(fiber.StatusNotFound).JSON(fiber.Map{
				"error": "post not found",
			})
		}
		return c.Status(fiber.StatusInternalServerError).JSON(fiber.Map{
			"error": "internal server error",
		})
	}
	return c.JSON(post)
}

// @Summary      Update a post
// @Description  Update an existing blog post
// @Tags         posts
// @Accept       json
// @Produce      json
// @Security     BearerAuth
// @Param        id path string true "Post ID"
// @Param        request body model.UpdatePostRequest true "Updated post data"
// @Success      200  {object}  model.Post
// @Failure      400  {object}  map[string]string
// @Router       /posts/{id} [patch]
func (h *PostHandler) Update(c *fiber.Ctx) error {
	userID := c.Locals("userID").(string)
	id := c.Params("id")

	var req model.UpdatePostRequest
	if err := c.BodyParser(&req); err != nil {
		return c.Status(fiber.StatusBadRequest).JSON(fiber.Map{
			"error": "invalid request body",
		})
	}

	post, err := h.svc.Update(c.UserContext(), id, userID, &req)
	if err != nil {
		return c.Status(fiber.StatusBadRequest).JSON(fiber.Map{
			"error": err.Error(),
		})
	}

	return c.JSON(post)
}

// @Summary      Delete a post
// @Description  Delete a blog post
// @Tags         posts
// @Produce      json
// @Security     BearerAuth
// @Param        id path string true "Post ID"
// @Success      200  {object}  map[string]string
// @Failure      400  {object}  map[string]string
// @Router       /posts/{id} [delete]
func (h *PostHandler) Delete(c *fiber.Ctx) error {
	userID := c.Locals("userID").(string)
	id := c.Params("id")

	if err := h.svc.Delete(c.UserContext(), id, userID); err != nil {
		return c.Status(fiber.StatusBadRequest).JSON(fiber.Map{
			"error": err.Error(),
		})
	}

	return c.JSON(fiber.Map{
		"message": "post deleted",
	})
}

// @Summary      List published posts
// @Description  List published blog posts with pagination and filters
// @Tags         posts
// @Produce      json
// @Param        cursor query string false "Pagination cursor"
// @Param        limit query int false "Items per page" default(10)
// @Param        category query string false "Filter by category"
// @Param        tag query string false "Filter by tag"
// @Param        author query string false "Filter by author"
// @Param        language query string false "Filter by language"
// @Success      200  {object}  map[string]interface{}
// @Router       /posts [get]
func (h *PostHandler) List(c *fiber.Ctx) error {
	cursor := c.Query("cursor")
	limit, _ := strconv.Atoi(c.Query("limit", "10"))
	category := c.Query("category")
	tag := c.Query("tag")
	author := c.Query("author")
	language := c.Query("language")

	posts, nextCursor, err := h.svc.List(c.UserContext(), model.PostListParams{
		Cursor:   cursor,
		Limit:    limit,
		Category: category,
		Tag:      tag,
		Author:   author,
		Language: language,
	})
	if err != nil {
		return c.Status(fiber.StatusInternalServerError).JSON(fiber.Map{
			"error": "failed to list posts",
			"detail": err.Error(),
		})
	}

	return c.JSON(fiber.Map{
		"data":        posts,
		"next_cursor": nextCursor,
		"has_more":    nextCursor != "",
	})
}
