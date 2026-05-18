package handler

import (
	"strconv"
	"strings"

	"github.com/gofiber/fiber/v2"
	"github.com/jmoiron/sqlx"
)

type NotificationPrefsHandler struct {
	db *sqlx.DB
}

func NewNotificationPrefsHandler(db *sqlx.DB) *NotificationPrefsHandler {
	return &NotificationPrefsHandler{db: db}
}

var prefsColumns = map[string]string{
	"comment_reply_web":        "comment_reply_web",
	"comment_reply_email":      "comment_reply_email",
	"comment_reply_sms":        "comment_reply_sms",
	"comment_mention_web":      "comment_mention_web",
	"comment_mention_email":    "comment_mention_email",
	"comment_mention_sms":      "comment_mention_sms",
	"post_reaction_web":        "post_reaction_web",
	"new_follower_web":         "new_follower_web",
	"post_published_web":       "post_published_web",
	"post_published_email":     "post_published_email",
	"post_published_sms":       "post_published_sms",
	"system_announcement_sms":  "system_announcement_sms",
}

// GetPreferences godoc
// @Summary      Get notification preferences
// @Tags         notifications
// @Produce      json
// @Security     BearerAuth
// @Success      200  {object} map[string]interface{}
// @Router       /notifications/preferences [get]
func (h *NotificationPrefsHandler) GetPreferences(c *fiber.Ctx) error {
	userID := c.Locals("userID").(string)

	var prefs struct {
		CommentReplyWeb        bool `json:"comment_reply_web" db:"comment_reply_web"`
		CommentReplyEmail      bool `json:"comment_reply_email" db:"comment_reply_email"`
		CommentReplySMS        bool `json:"comment_reply_sms" db:"comment_reply_sms"`
		CommentMentionWeb      bool `json:"comment_mention_web" db:"comment_mention_web"`
		CommentMentionEmail    bool `json:"comment_mention_email" db:"comment_mention_email"`
		CommentMentionSMS      bool `json:"comment_mention_sms" db:"comment_mention_sms"`
		PostReactionWeb        bool `json:"post_reaction_web" db:"post_reaction_web"`
		NewFollowerWeb         bool `json:"new_follower_web" db:"new_follower_web"`
		PostPublishedWeb       bool `json:"post_published_web" db:"post_published_web"`
		PostPublishedEmail     bool `json:"post_published_email" db:"post_published_email"`
		PostPublishedSMS       bool `json:"post_published_sms" db:"post_published_sms"`
		SystemAnnouncementSMS  bool `json:"system_announcement_sms" db:"system_announcement_sms"`
	}

	err := h.db.Get(&prefs, `
		SELECT comment_reply_web, comment_reply_email, comment_reply_sms,
		       comment_mention_web, comment_mention_email, comment_mention_sms,
		       post_reaction_web, new_follower_web,
		       post_published_web, post_published_email, post_published_sms,
		       system_announcement_sms
		FROM notification_preferences WHERE user_id = $1
	`, userID)
	if err != nil {
		h.db.Exec(`INSERT INTO notification_preferences (user_id) VALUES ($1) ON CONFLICT DO NOTHING`, userID)
		return c.JSON(prefs)
	}

	return c.JSON(prefs)
}

// UpdatePreferences godoc
// @Summary      Update notification preferences
// @Tags         notifications
// @Accept       json
// @Produce      json
// @Security     BearerAuth
// @Param        body  body      object  true   "Preferences map"
// @Success      200   {object} map[string]string
// @Failure      400   {object} map[string]string
// @Router       /notifications/preferences [patch]
func (h *NotificationPrefsHandler) UpdatePreferences(c *fiber.Ctx) error {
	userID := c.Locals("userID").(string)

	var req map[string]bool
	if err := c.BodyParser(&req); err != nil {
		return c.Status(fiber.StatusBadRequest).JSON(fiber.Map{"error": "invalid body"})
	}

	setClauses := []string{}
	args := []interface{}{userID}
	argIdx := 2

	for field, value := range req {
		col, ok := prefsColumns[field]
		if !ok {
			continue
		}
		setClauses = append(setClauses, col+` = $`+strconv.Itoa(argIdx))
		args = append(args, value)
		argIdx++
	}

	if len(setClauses) == 0 {
		return c.JSON(fiber.Map{"message": "no valid fields"})
	}

	query := `UPDATE notification_preferences SET ` + strings.Join(setClauses, `, `) + ` WHERE user_id = $1`
	_, err := h.db.Exec(query, args...)
	if err != nil {
		return c.Status(fiber.StatusInternalServerError).JSON(fiber.Map{"error": "update failed"})
	}

	return c.JSON(fiber.Map{"message": "preferences updated"})
}
