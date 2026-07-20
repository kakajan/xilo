package handler

import (
	"strings"

	"github.com/gofiber/fiber/v2"
	"github.com/xilo-platform/xilo/internal/notification/service"
)

type PushTokenHandler struct {
	svc *service.NotificationService
}

func NewPushTokenHandler(svc *service.NotificationService) *PushTokenHandler {
	return &PushTokenHandler{svc: svc}
}

type pushTokenBody struct {
	Token    string `json:"token"`
	Platform string `json:"platform"`
}

// Register godoc
// @Summary      Register a device push token
// @Tags         devices
// @Accept       json
// @Produce      json
// @Security     BearerAuth
// @Success      200  {object} map[string]string
// @Router       /devices/push-tokens [post]
func (h *PushTokenHandler) Register(c *fiber.Ctx) error {
	userID := c.Locals("userID").(string)
	var body pushTokenBody
	if err := c.BodyParser(&body); err != nil || strings.TrimSpace(body.Token) == "" {
		return c.Status(fiber.StatusBadRequest).JSON(fiber.Map{"error": "token is required"})
	}
	if err := h.svc.UpsertPushToken(c.UserContext(), userID, body.Token, body.Platform); err != nil {
		return c.Status(fiber.StatusInternalServerError).JSON(fiber.Map{"error": "failed to register token"})
	}
	return c.JSON(fiber.Map{"message": "ok"})
}

// Unregister godoc
// @Summary      Unregister a device push token
// @Tags         devices
// @Accept       json
// @Produce      json
// @Security     BearerAuth
// @Success      200  {object} map[string]string
// @Router       /devices/push-tokens [delete]
func (h *PushTokenHandler) Unregister(c *fiber.Ctx) error {
	userID := c.Locals("userID").(string)
	var body pushTokenBody
	if err := c.BodyParser(&body); err != nil || strings.TrimSpace(body.Token) == "" {
		return c.Status(fiber.StatusBadRequest).JSON(fiber.Map{"error": "token is required"})
	}
	if err := h.svc.DeletePushToken(c.UserContext(), userID, body.Token); err != nil {
		return c.Status(fiber.StatusInternalServerError).JSON(fiber.Map{"error": "failed to unregister token"})
	}
	return c.JSON(fiber.Map{"message": "ok"})
}
