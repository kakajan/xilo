package handler

import (
	"strconv"

	"github.com/gofiber/fiber/v2"
	"github.com/xilo-platform/xilo/internal/notification/repository"
)

type NotificationHandler struct {
	repo *repository.NotificationRepo
}

func NewNotificationHandler(repo *repository.NotificationRepo) *NotificationHandler {
	return &NotificationHandler{repo: repo}
}

// List godoc
// @Summary      List notifications
// @Tags         notifications
// @Produce      json
// @Security     BearerAuth
// @Param        limit  query    int     false  "Items per page"  default(20)
// @Success      200    {object} map[string]interface{}
// @Router       /notifications [get]
func (h *NotificationHandler) List(c *fiber.Ctx) error {
	userID := c.Locals("userID").(string)
	limit, _ := strconv.Atoi(c.Query("limit", "20"))

	items, err := h.repo.List(c.UserContext(), userID, limit)
	if err != nil {
		return c.Status(fiber.StatusInternalServerError).JSON(fiber.Map{"error": "failed to list notifications"})
	}

	return c.JSON(fiber.Map{"data": items})
}

// MarkRead godoc
// @Summary      Mark notification as read
// @Tags         notifications
// @Produce      json
// @Security     BearerAuth
// @Param        id   path     string  true   "Notification ID"
// @Success      200  {object} map[string]string
// @Router       /notifications/{id}/read [post]
func (h *NotificationHandler) MarkRead(c *fiber.Ctx) error {
	userID := c.Locals("userID").(string)
	id := c.Params("id")
	if err := h.repo.MarkRead(c.UserContext(), id, userID); err != nil {
		return c.Status(fiber.StatusInternalServerError).JSON(fiber.Map{"error": "failed"})
	}
	return c.JSON(fiber.Map{"message": "ok"})
}

// MarkAllRead godoc
// @Summary      Mark all notifications as read
// @Tags         notifications
// @Produce      json
// @Security     BearerAuth
// @Success      200  {object} map[string]string
// @Router       /notifications/read-all [post]
func (h *NotificationHandler) MarkAllRead(c *fiber.Ctx) error {
	userID := c.Locals("userID").(string)
	if err := h.repo.MarkAllRead(c.UserContext(), userID); err != nil {
		return c.Status(fiber.StatusInternalServerError).JSON(fiber.Map{"error": "failed"})
	}
	return c.JSON(fiber.Map{"message": "ok"})
}
