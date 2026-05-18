package handler

import (
	"fmt"
	"log/slog"

	"github.com/gofiber/fiber/v2"
	"github.com/google/uuid"
	"github.com/jmoiron/sqlx"
	"github.com/xilo-platform/xilo/pkg/payment"
)

type PaymentHandler struct {
	db      *sqlx.DB
	gateway payment.Driver
	baseURL string
}

func NewPaymentHandler(db *sqlx.DB, gateway payment.Driver, baseURL string) *PaymentHandler {
	return &PaymentHandler{db: db, gateway: gateway, baseURL: baseURL}
}

type subscribeRequest struct {
	PlanSlug string `json:"plan_slug"`
	Mobile   string `json:"mobile,omitempty"`
	Email    string `json:"email,omitempty"`
}

// Subscribe godoc
// @Summary      Subscribe to a plan
// @Tags         billing
// @Accept       json
// @Produce      json
// @Security     BearerAuth
// @Param        body  body      object  true   "Subscription request"
// @Success      200   {object} map[string]interface{}
// @Failure      400   {object} map[string]string
// @Failure      404   {object} map[string]string
// @Failure      409   {object} map[string]string
// @Failure      502   {object} map[string]string
// @Router       /billing/subscribe [post]
func (h *PaymentHandler) Subscribe(c *fiber.Ctx) error {
	if !h.gateway.IsAvailable() {
		return c.Status(fiber.StatusServiceUnavailable).JSON(fiber.Map{
			"error": "payment gateway not configured",
		})
	}

	userID := c.Locals("userID").(string)

	var req subscribeRequest
	if err := c.BodyParser(&req); err != nil {
		return c.Status(fiber.StatusBadRequest).JSON(fiber.Map{"error": "invalid body"})
	}

	var activeCheck int
	h.db.Get(&activeCheck, `SELECT 1 FROM user_subscriptions WHERE user_id = $1 AND status = 'active' AND expires_at > NOW()`, userID)
	if activeCheck == 1 {
		return c.Status(fiber.StatusConflict).JSON(fiber.Map{"error": "already have an active subscription"})
	}

	var plan struct {
		ID         string `db:"id"`
		Name       string `db:"name"`
		PriceCents int    `db:"price_cents"`
		Currency   string `db:"currency"`
	}
	err := h.db.Get(&plan, `
		SELECT id, name, price_cents, currency FROM subscription_plans
		WHERE slug = $1 AND is_active = TRUE
	`, req.PlanSlug)
	if err != nil {
		return c.Status(fiber.StatusNotFound).JSON(fiber.Map{"error": "plan not found"})
	}

	tx, err := h.db.Beginx()
	if err != nil {
		slog.Error("begin tx failed", "error", err)
		return c.Status(fiber.StatusInternalServerError).JSON(fiber.Map{"error": "internal error"})
	}
	defer tx.Rollback()

	subID := uuid.New().String()
	invID := uuid.New().String()

	_, err = tx.Exec(`
		INSERT INTO user_subscriptions (id, user_id, plan_id, status)
		VALUES ($1, $2, $3, 'pending')
	`, subID, userID, plan.ID)
	if err != nil {
		slog.Error("insert subscription failed", "error", err)
		return c.Status(fiber.StatusInternalServerError).JSON(fiber.Map{"error": "subscription failed"})
	}

	_, err = tx.Exec(`
		INSERT INTO invoices (id, user_id, subscription_id, amount_cents, currency, status)
		VALUES ($1, $2, $3, $4, $5, 'pending')
	`, invID, userID, subID, plan.PriceCents, plan.Currency)
	if err != nil {
		slog.Error("insert invoice failed", "error", err)
		return c.Status(fiber.StatusInternalServerError).JSON(fiber.Map{"error": "invoice failed"})
	}

	callbackURL := h.baseURL + "/api/billing/callback"
	paymentResp, err := h.gateway.RequestPayment(c.UserContext(), &payment.PaymentRequest{
		Amount:      int64(plan.PriceCents),
		Currency:    plan.Currency,
		Description: plan.Name,
		CallbackURL: callbackURL,
		Metadata: &payment.PaymentMetadata{
			Mobile:  req.Mobile,
			Email:   req.Email,
			OrderID: invID,
		},
	})
	if err != nil {
		slog.Error("payment request failed", "error", err)
		return c.Status(fiber.StatusBadGateway).JSON(fiber.Map{
			"error":   "payment gateway error",
			"details": err.Error(),
		})
	}

	_, err = tx.Exec(`
		UPDATE user_subscriptions SET authority = $1, updated_at = NOW() WHERE id = $2
	`, paymentResp.Authority, subID)
	if err != nil {
		slog.Error("update authority failed", "error", err)
		return c.Status(fiber.StatusInternalServerError).JSON(fiber.Map{"error": "update failed"})
	}

	_, err = tx.Exec(`
		UPDATE invoices SET authority = $1, updated_at = NOW() WHERE id = $2
	`, paymentResp.Authority, invID)
	if err != nil {
		slog.Error("update invoice authority failed", "error", err)
		return c.Status(fiber.StatusInternalServerError).JSON(fiber.Map{"error": "update failed"})
	}

	if err := tx.Commit(); err != nil {
		slog.Error("commit tx failed", "error", err)
		return c.Status(fiber.StatusInternalServerError).JSON(fiber.Map{"error": "commit failed"})
	}

	return c.JSON(fiber.Map{
		"gateway_url": paymentResp.GatewayURL,
		"authority":   paymentResp.Authority,
		"invoice_id":  invID,
	})
}

// Callback godoc
// @Summary      Payment gateway callback
// @Tags         billing
// @Produce      json
// @Param        Authority  query    string  true   "Payment authority"
// @Param        Status     query    string  true   "Payment status"
// @Success      302        {string} string  "Redirect"
// @Router       /billing/callback [get]
func (h *PaymentHandler) Callback(c *fiber.Ctx) error {
	authority := c.Query("Authority")
	status := c.Query("Status")

	if status != "OK" {
		return c.Redirect(h.baseURL + "/billing?status=failed&reason=cancelled")
	}

	if authority == "" {
		return c.Redirect(h.baseURL + "/billing?status=failed&reason=missing_authority")
	}

	tx, err := h.db.Beginx()
	if err != nil {
		slog.Error("callback begin tx failed", "error", err)
		return c.Redirect(h.baseURL + "/billing?status=failed&reason=internal_error")
	}
	defer tx.Rollback()

	var invoice struct {
		ID           string `db:"id"`
		AmountCents  int    `db:"amount_cents"`
		SubscriptionID string `db:"subscription_id"`
	}
	err = tx.Get(&invoice, `
		SELECT id, amount_cents, subscription_id FROM invoices
		WHERE authority = $1 AND status = 'pending'
	`, authority)
	if err != nil {
		slog.Error("callback invoice not found", "authority", authority, "error", err)
		return c.Redirect(h.baseURL + "/billing?status=failed&reason=invoice_not_found")
	}

	verifyResp, err := h.gateway.VerifyPayment(c.UserContext(), &payment.VerifyRequest{
		Amount:    int64(invoice.AmountCents),
		Authority: authority,
	})
	if err != nil {
		slog.Error("payment verify failed", "authority", authority, "error", err)
		return c.Redirect(h.baseURL + "/billing?status=failed&reason=verify_failed")
	}

	if !payment.IsSuccess(verifyResp.Code) {
		slog.Error("payment verify unsuccessful", "code", verifyResp.Code, "message", verifyResp.Message)
		return c.Redirect(h.baseURL + "/billing?status=failed&reason=verify_rejected")
	}

	if verifyResp.Code == 101 {
		slog.Warn("payment already verified (duplicate callback)", "authority", authority)
		return c.Redirect(h.baseURL + "/billing?status=success&ref_id=" + c.Query("ref_id"))
	}

	_, err = tx.Exec(`
		UPDATE user_subscriptions
		SET status = 'active', ref_id = $2, started_at = NOW(),
		    expires_at = NOW() + INTERVAL '30 days', updated_at = NOW()
		WHERE id = $1
	`, invoice.SubscriptionID, verifyResp.RefID)
	if err != nil {
		slog.Error("update subscription failed", "error", err)
		return c.Redirect(h.baseURL + "/billing?status=failed&reason=update_failed")
	}

	_, err = tx.Exec(`
		UPDATE invoices
		SET status = 'paid', ref_id = $2, card_pan = $3, paid_at = NOW(), updated_at = NOW()
		WHERE id = $1
	`, invoice.ID, verifyResp.RefID, verifyResp.CardPAN)
	if err != nil {
		slog.Error("update invoice failed", "error", err)
		return c.Redirect(h.baseURL + "/billing?status=failed&reason=update_failed")
	}

	if err := tx.Commit(); err != nil {
		slog.Error("callback commit tx failed", "error", err)
		return c.Redirect(h.baseURL + "/billing?status=failed&reason=internal_error")
	}

	return c.Redirect(h.baseURL + "/billing?status=success&ref_id=" + fmt.Sprint(verifyResp.RefID))
}
