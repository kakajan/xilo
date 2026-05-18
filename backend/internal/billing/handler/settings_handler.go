package handler

import (
	"github.com/gofiber/fiber/v2"
	"github.com/jmoiron/sqlx"
)

type SettingsHandler struct {
	db *sqlx.DB
}

func NewSettingsHandler(db *sqlx.DB) *SettingsHandler {
	return &SettingsHandler{db: db}
}

type paymentGatewayConfigRow struct {
	ID         string `json:"id" db:"id"`
	Gateway    string `json:"gateway" db:"gateway"`
	MerchantID string `json:"merchant_id" db:"merchant_id"`
	Sandbox    bool   `json:"sandbox" db:"sandbox"`
	IsActive   bool   `json:"is_active" db:"is_active"`
}

// GetPaymentGatewayConfig godoc
// @Summary      Get payment gateway config
// @Tags         billing
// @Produce      json
// @Security     BearerAuth
// @Success      200  {object} map[string]interface{}
// @Router       /billing/settings [get]
func (h *SettingsHandler) GetPaymentGatewayConfig(c *fiber.Ctx) error {
	var config paymentGatewayConfigRow
	err := h.db.Get(&config, `
		SELECT id, gateway, merchant_id, sandbox, is_active
		FROM payment_gateway_config
		WHERE gateway = 'zarinpal'
		ORDER BY created_at DESC LIMIT 1
	`)
	if err != nil {
		return c.JSON(fiber.Map{
			"gateway":     "zarinpal",
			"merchant_id": "",
			"sandbox":     true,
			"is_active":   false,
			"configured":  false,
		})
	}

	merchantMasked := config.MerchantID
	if len(config.MerchantID) > 8 {
		merchantMasked = config.MerchantID[:4] + "****" + config.MerchantID[len(config.MerchantID)-4:]
	}

	return c.JSON(fiber.Map{
		"id":          config.ID,
		"gateway":     config.Gateway,
		"merchant_id": merchantMasked,
		"sandbox":     config.Sandbox,
		"is_active":   config.IsActive,
		"configured":  config.MerchantID != "",
	})
}

type updatePaymentGatewayConfigRequest struct {
	MerchantID string `json:"merchant_id"`
	Sandbox    *bool  `json:"sandbox"`
	IsActive   *bool  `json:"is_active"`
}

// UpdatePaymentGatewayConfig godoc
// @Summary      Update payment gateway config
// @Tags         billing
// @Accept       json
// @Produce      json
// @Security     BearerAuth
// @Param        body  body      object  true   "Config data"
// @Success      200   {object} map[string]string
// @Failure      400   {object} map[string]string
// @Router       /billing/settings [patch]
func (h *SettingsHandler) UpdatePaymentGatewayConfig(c *fiber.Ctx) error {
	var req updatePaymentGatewayConfigRequest
	if err := c.BodyParser(&req); err != nil {
		return c.Status(fiber.StatusBadRequest).JSON(fiber.Map{"error": "invalid body"})
	}

	sandbox := true
	if req.Sandbox != nil {
		sandbox = *req.Sandbox
	}

	isActive := false
	if req.IsActive != nil {
		isActive = *req.IsActive
	}

	_, err := h.db.Exec(`
		UPDATE payment_gateway_config
		SET merchant_id = CASE WHEN $2 != '' THEN $2 ELSE merchant_id END,
		    sandbox = $3, is_active = $4, updated_at = NOW()
		WHERE gateway = $1
	`, "zarinpal", req.MerchantID, sandbox, isActive)
	if err != nil {
		return c.Status(fiber.StatusInternalServerError).JSON(fiber.Map{"error": "update failed"})
	}

	return c.JSON(fiber.Map{"message": "updated"})
}
