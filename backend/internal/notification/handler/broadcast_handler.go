package handler

import (
	"strings"
	"unicode/utf8"

	"github.com/gofiber/fiber/v2"
	"github.com/xilo-platform/xilo/internal/notification/service"
)

type BroadcastHandler struct {
	svc *service.NotificationService
}

func NewBroadcastHandler(svc *service.NotificationService) *BroadcastHandler {
	return &BroadcastHandler{svc: svc}
}

type broadcastBody struct {
	Title     string `json:"title"`
	Body      string `json:"body"`
	Link      string `json:"link"`
	SendPush  *bool  `json:"send_push"`
	SendInbox *bool  `json:"send_inbox"`
}

// BroadcastPush godoc
// @Summary      Broadcast a custom push/inbox notification to all users
// @Tags         notifications
// @Accept       json
// @Produce      json
// @Security     BearerAuth
// @Param        body  body      broadcastBody  true  "Broadcast payload"
// @Success      200   {object}  service.BroadcastResult
// @Failure      400   {object}  map[string]string
// @Router       /notifications/push/broadcast [post]
func (h *BroadcastHandler) BroadcastPush(c *fiber.Ctx) error {
	var body broadcastBody
	if err := c.BodyParser(&body); err != nil {
		return c.Status(fiber.StatusBadRequest).JSON(fiber.Map{"error": "invalid body"})
	}

	title := strings.TrimSpace(body.Title)
	msg := strings.TrimSpace(body.Body)
	if title == "" {
		return c.Status(fiber.StatusBadRequest).JSON(fiber.Map{"error": "title is required"})
	}
	if utf8.RuneCountInString(title) > 120 {
		return c.Status(fiber.StatusBadRequest).JSON(fiber.Map{"error": "title must be at most 120 characters"})
	}
	if utf8.RuneCountInString(msg) > 1000 {
		return c.Status(fiber.StatusBadRequest).JSON(fiber.Map{"error": "body must be at most 1000 characters"})
	}
	link := strings.TrimSpace(body.Link)
	if link != "" && !(strings.HasPrefix(link, "http://") || strings.HasPrefix(link, "https://") || strings.HasPrefix(link, "/")) {
		return c.Status(fiber.StatusBadRequest).JSON(fiber.Map{"error": "link must be absolute http(s) or a relative path"})
	}

	sendPush := true
	sendInbox := true
	if body.SendPush != nil {
		sendPush = *body.SendPush
	}
	if body.SendInbox != nil {
		sendInbox = *body.SendInbox
	}
	if !sendPush && !sendInbox {
		return c.Status(fiber.StatusBadRequest).JSON(fiber.Map{"error": "at least one of send_push or send_inbox must be true"})
	}

	result, err := h.svc.BroadcastToAllUsers(c.UserContext(), service.BroadcastRequest{
		Title:     title,
		Body:      msg,
		Link:      link,
		SendPush:  sendPush,
		SendInbox: sendInbox,
	})
	if err != nil {
		return c.Status(fiber.StatusInternalServerError).JSON(fiber.Map{"error": err.Error()})
	}
	return c.JSON(result)
}
