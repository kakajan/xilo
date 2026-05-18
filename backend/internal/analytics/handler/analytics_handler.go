package handler

import (
	"github.com/gofiber/fiber/v2"
	"github.com/jmoiron/sqlx"
)

type AnalyticsHandler struct {
	db *sqlx.DB
}

func NewAnalyticsHandler(db *sqlx.DB) *AnalyticsHandler {
	return &AnalyticsHandler{db: db}
}

// @Summary      Ingest analytics events
// @Tags         analytics
// @Accept       json
// @Produce      json
// @Param        request body object true "Array of events"
// @Success      200  {object}  map[string]interface{}
// @Failure      400  {object}  map[string]string
// @Router       /analytics/events [post]
func (h *AnalyticsHandler) Ingest(c *fiber.Ctx) error {
	var events []struct {
		EventType string                 `json:"event_type"`
		SessionID string                 `json:"session_id"`
		Properties map[string]interface{} `json:"properties"`
	}
	if err := c.BodyParser(&events); err != nil {
		return c.Status(fiber.StatusBadRequest).JSON(fiber.Map{"error": "invalid body"})
	}

	for _, e := range events {
		userID := c.Locals("userID")
		var uid interface{} = nil
		if userID != nil {
			uid = userID.(string)
		}

		_, err := h.db.Exec(`
			INSERT INTO analytics_events (event_type, user_id, session_id, properties)
			VALUES ($1, $2, $3, $4)
		`, e.EventType, uid, e.SessionID, e.Properties)
		if err != nil {
			return c.Status(fiber.StatusInternalServerError).JSON(fiber.Map{"error": "ingest failed"})
		}
	}

	return c.JSON(fiber.Map{"ingested": len(events)})
}
