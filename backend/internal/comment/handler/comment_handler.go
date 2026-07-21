package handler

import (
	"strconv"

	"github.com/gofiber/fiber/v2"
	"github.com/xilo-platform/xilo/internal/comment/model"
	"github.com/xilo-platform/xilo/internal/comment/service"
)

type CommentHandler struct {
	svc *service.CommentService
}

func NewCommentHandler(svc *service.CommentService) *CommentHandler {
	return &CommentHandler{svc: svc}
}

// @Summary      List comments for a post
// @Tags         comments
// @Produce      json
// @Param        postId path string true "Post ID"
// @Param        cursor query string false "Pagination cursor"
// @Param        limit query int false "Items per page" default(20)
// @Param        sort query string false "Sort order (newest/oldest)" default(newest)
// @Success      200  {object}  map[string]interface{}
// @Router       /posts/{postId}/comments [get]
func (h *CommentHandler) List(c *fiber.Ctx) error {
	postID := c.Params("postId")
	cursor := c.Query("cursor")
	limit, _ := strconv.Atoi(c.Query("limit", "20"))
	sort := c.Query("sort", "newest")
	viewerID, _ := c.Locals("userID").(string)

	comments, nextCursor, err := h.svc.List(c.UserContext(), postID, cursor, limit, sort, viewerID)
	if err != nil {
		return c.Status(fiber.StatusInternalServerError).JSON(fiber.Map{
			"error": "failed to list comments",
		})
	}

	return c.JSON(fiber.Map{
		"data":        comments,
		"next_cursor": nextCursor,
		"has_more":    nextCursor != "",
	})
}

// @Summary      Get a comment by ID
// @Tags         comments
// @Produce      json
// @Param        id path string true "Comment ID"
// @Success      200  {object}  model.Comment
// @Failure      404  {object}  map[string]string
// @Router       /comments/{id} [get]
func (h *CommentHandler) GetByID(c *fiber.Ctx) error {
	comment, err := h.svc.GetByID(c.UserContext(), c.Params("id"))
	if err != nil {
		return c.Status(fiber.StatusNotFound).JSON(fiber.Map{"error": "comment not found"})
	}
	return c.JSON(comment)
}

// @Summary      Create a comment
// @Tags         comments
// @Accept       json
// @Produce      json
// @Security     BearerAuth
// @Param        postId path string true "Post ID"
// @Param        body body model.CreateCommentRequest true "Comment data"
// @Success      201  {object}  model.Comment
// @Failure      400  {object}  map[string]string
// @Router       /posts/{postId}/comments [post]
func (h *CommentHandler) Create(c *fiber.Ctx) error {
	postID := c.Params("postId")
	userID := c.Locals("userID").(string)

	var req model.CreateCommentRequest
	if err := c.BodyParser(&req); err != nil {
		return c.Status(fiber.StatusBadRequest).JSON(fiber.Map{
			"error": "invalid request body",
		})
	}

	comment, err := h.svc.Create(c.UserContext(), postID, userID, &req)
	if err != nil {
		return c.Status(fiber.StatusBadRequest).JSON(fiber.Map{
			"error": err.Error(),
		})
	}

	return c.Status(fiber.StatusCreated).JSON(comment)
}

// @Summary      Update a comment
// @Tags         comments
// @Accept       json
// @Produce      json
// @Security     BearerAuth
// @Param        postId path string true "Post ID"
// @Param        id path string true "Comment ID"
// @Param        body body object true "Updated comment" example({"content":"updated text"})
// @Success      200  {object}  model.Comment
// @Failure      400  {object}  map[string]string
// @Router       /posts/{postId}/comments/{id} [patch]
func (h *CommentHandler) Update(c *fiber.Ctx) error {
	userID := c.Locals("userID").(string)
	commentID := c.Params("id")

	var req struct {
		Content string `json:"content"`
	}
	if err := c.BodyParser(&req); err != nil {
		return c.Status(fiber.StatusBadRequest).JSON(fiber.Map{
			"error": "invalid request body",
		})
	}

	comment, err := h.svc.Update(c.UserContext(), commentID, userID, req.Content)
	if err != nil {
		return c.Status(fiber.StatusBadRequest).JSON(fiber.Map{
			"error": err.Error(),
		})
	}

	return c.JSON(comment)
}

// @Summary      Delete a comment
// @Tags         comments
// @Produce      json
// @Security     BearerAuth
// @Param        postId path string true "Post ID"
// @Param        id path string true "Comment ID"
// @Success      200  {object}  map[string]string
// @Failure      400  {object}  map[string]string
// @Router       /posts/{postId}/comments/{id} [delete]
func (h *CommentHandler) Delete(c *fiber.Ctx) error {
	userID := c.Locals("userID").(string)
	commentID := c.Params("id")

	if err := h.svc.Delete(c.UserContext(), commentID, userID); err != nil {
		return c.Status(fiber.StatusBadRequest).JSON(fiber.Map{
			"error": err.Error(),
		})
	}

	return c.JSON(fiber.Map{"message": "comment deleted"})
}

// @Summary      Pin/unpin a comment
// @Tags         comments
// @Accept       json
// @Produce      json
// @Security     BearerAuth
// @Param        id path string true "Comment ID"
// @Param        body body object true "Pin status" example({"pin":true})
// @Success      200  {object}  map[string]string
// @Failure      400  {object}  map[string]string
// @Router       /comments/{id}/pin [post]
func (h *CommentHandler) Pin(c *fiber.Ctx) error {
	commentID := c.Params("id")

	var req struct {
		Pin bool `json:"pin"`
	}
	if err := c.BodyParser(&req); err != nil {
		return c.Status(fiber.StatusBadRequest).JSON(fiber.Map{
			"error": "invalid request body",
		})
	}

	if err := h.svc.Pin(c.UserContext(), commentID, req.Pin); err != nil {
		return c.Status(fiber.StatusBadRequest).JSON(fiber.Map{
			"error": err.Error(),
		})
	}

	return c.JSON(fiber.Map{"message": "pin status updated"})
}

// @Summary      Toggle reaction on a target
// @Tags         reactions
// @Accept       json
// @Produce      json
// @Security     BearerAuth
// @Param        type path string true "Target type (post/comment)"
// @Param        id path string true "Target ID"
// @Param        body body object true "Reaction data" example({"reaction":"like"})
// @Success      200  {object}  map[string]interface{}
// @Failure      400  {object}  map[string]string
// @Router       /{type}/{id}/reactions [post]
func (h *CommentHandler) ToggleReaction(c *fiber.Ctx) error {
	userID := c.Locals("userID").(string)
	targetType := c.Params("type")
	targetID := c.Params("id")

	var req struct {
		Reaction string `json:"reaction"`
	}
	if err := c.BodyParser(&req); err != nil {
		return c.Status(fiber.StatusBadRequest).JSON(fiber.Map{
			"error": "invalid request body",
		})
	}

	count, err := h.svc.ToggleReaction(c.UserContext(), userID, targetType, targetID, req.Reaction)
	if err != nil {
		return c.Status(fiber.StatusBadRequest).JSON(fiber.Map{
			"error": err.Error(),
		})
	}

	return c.JSON(fiber.Map{
		"target_type": targetType,
		"target_id":   targetID,
		"reaction":    req.Reaction,
		"count":       count,
	})
}
