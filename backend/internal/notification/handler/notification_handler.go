package handler

import (
	"strconv"

	"github.com/gofiber/fiber/v2"
	"github.com/xilo-platform/xilo/internal/notification/service"
)

type NotificationHandler struct {
	svc *service.NotificationService
}

func NewNotificationHandler(svc *service.NotificationService) *NotificationHandler {
	return &NotificationHandler{svc: svc}
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

	items, err := h.svc.List(c.UserContext(), userID, limit)
	if err != nil {
		return c.Status(fiber.StatusInternalServerError).JSON(fiber.Map{"error": "failed to list notifications"})
	}

	return c.JSON(fiber.Map{"data": items})
}

// UnreadCount godoc
// @Summary      Get unread notification count
// @Tags         notifications
// @Produce      json
// @Security     BearerAuth
// @Success      200  {object} map[string]int
// @Router       /notifications/unread-count [get]
func (h *NotificationHandler) UnreadCount(c *fiber.Ctx) error {
	userID := c.Locals("userID").(string)
	count, err := h.svc.UnreadCount(c.UserContext(), userID)
	if err != nil {
		return c.Status(fiber.StatusInternalServerError).JSON(fiber.Map{"error": "failed to get unread count"})
	}
	return c.JSON(fiber.Map{"unread": count})
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
	if err := h.svc.MarkRead(c.UserContext(), id, userID); err != nil {
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
	if err := h.svc.MarkAllRead(c.UserContext(), userID); err != nil {
		return c.Status(fiber.StatusInternalServerError).JSON(fiber.Map{"error": "failed"})
	}
	return c.JSON(fiber.Map{"message": "ok"})
}
