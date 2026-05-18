package handler

import (
	"log/slog"

	"github.com/gofiber/fiber/v2"
	"github.com/jmoiron/sqlx"
	"github.com/xilo-platform/xilo/pkg/sms"
)

type SMSNotificationHandler struct {
	db        *sqlx.DB
	smsDriver sms.Driver
}

func NewSMSNotificationHandler(db *sqlx.DB, smsDriver sms.Driver) *SMSNotificationHandler {
	return &SMSNotificationHandler{db: db, smsDriver: smsDriver}
}

// SendSMS godoc
// @Summary      Send SMS notification
// @Tags         notifications
// @Accept       json
// @Produce      json
// @Security     BearerAuth
// @Param        body  body      object  true   "SMS data"
// @Success      200   {object} map[string]interface{}
// @Failure      400   {object} map[string]string
// @Failure      503   {object} map[string]string
// @Router       /notifications/sms/send [post]
func (h *SMSNotificationHandler) SendSMS(c *fiber.Ctx) error {
	if h.smsDriver == nil {
		return c.Status(fiber.StatusServiceUnavailable).JSON(fiber.Map{"error": "sms service is not configured"})
	}

	var req struct {
		Recipients []string          `json:"recipients"`
		Pattern    string            `json:"pattern"`
		Params     map[string]string `json:"params"`
	}
	if err := c.BodyParser(&req); err != nil {
		return c.Status(fiber.StatusBadRequest).JSON(fiber.Map{"error": "invalid request body"})
	}

	if len(req.Recipients) == 0 {
		return c.Status(fiber.StatusBadRequest).JSON(fiber.Map{"error": "at least one recipient is required"})
	}

	type result struct {
		Phone   string `json:"phone"`
		Success bool   `json:"success"`
		Error   string `json:"error,omitempty"`
	}

	results := make([]result, 0, len(req.Recipients))

	for _, phone := range req.Recipients {
		res := result{Phone: phone}

		var sendErr error
		if req.Pattern != "" {
			_, sendErr = h.smsDriver.SendPattern(c.UserContext(), phone, req.Pattern, req.Params)
		} else if msg, ok := req.Params["message"]; ok {
			sendErr = h.smsDriver.Send(c.UserContext(), phone, msg)
		} else {
			res.Error = "no pattern or message provided"
			results = append(results, res)
			continue
		}

		if sendErr != nil {
			slog.Error("sms send failed", "phone", phone, "error", sendErr)
			res.Error = sendErr.Error()
		} else {
			res.Success = true
		}

		results = append(results, res)
	}

	return c.JSON(fiber.Map{
		"results": results,
	})
}

// BroadcastToAll godoc
// @Summary      Broadcast SMS to all users
// @Tags         notifications
// @Accept       json
// @Produce      json
// @Security     BearerAuth
// @Param        body  body      object  true   "Broadcast data"
// @Success      200   {object} map[string]interface{}
// @Failure      400   {object} map[string]string
// @Failure      503   {object} map[string]string
// @Router       /notifications/sms/broadcast [post]
func (h *SMSNotificationHandler) BroadcastToAll(c *fiber.Ctx) error {
	if h.smsDriver == nil {
		return c.Status(fiber.StatusServiceUnavailable).JSON(fiber.Map{"error": "sms service is not configured"})
	}

	var req struct {
		Pattern string            `json:"pattern"`
		Params  map[string]string `json:"params"`
	}
	if err := c.BodyParser(&req); err != nil {
		return c.Status(fiber.StatusBadRequest).JSON(fiber.Map{"error": "invalid request body"})
	}

	rows, err := h.db.Query(`
		SELECT phone FROM users
		WHERE phone IS NOT NULL AND deleted_at IS NULL
	`)
	if err != nil {
		slog.Error("query phones failed", "error", err)
		return c.Status(fiber.StatusInternalServerError).JSON(fiber.Map{"error": "failed to fetch users"})
	}
	defer rows.Close()

	var phones []string
	for rows.Next() {
		var phone string
		if err := rows.Scan(&phone); err != nil {
			continue
		}
		phones = append(phones, phone)
	}

	sent := 0
	failed := 0

	for _, phone := range phones {
		if req.Pattern != "" {
			if _, err := h.smsDriver.SendPattern(c.UserContext(), phone, req.Pattern, req.Params); err != nil {
				slog.Error("broadcast sms failed", "phone", phone, "error", err)
				failed++
				continue
			}
		} else if msg, ok := req.Params["message"]; ok {
			if err := h.smsDriver.Send(c.UserContext(), phone, msg); err != nil {
				slog.Error("broadcast sms failed", "phone", phone, "error", err)
				failed++
				continue
			}
		}
		sent++
	}

	return c.JSON(fiber.Map{
		"total":  len(phones),
		"sent":   sent,
		"failed": failed,
	})
}
