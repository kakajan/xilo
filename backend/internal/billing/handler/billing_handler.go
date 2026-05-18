package handler

import (
	"log/slog"

	"github.com/gofiber/fiber/v2"
	"github.com/jmoiron/sqlx"
	"github.com/xilo-platform/xilo/pkg/payment"
)

type BillingHandler struct {
	db      *sqlx.DB
	gateway payment.Driver
	baseURL string
}

func NewBillingHandler(db *sqlx.DB, gateway payment.Driver, baseURL string) *BillingHandler {
	return &BillingHandler{db: db, gateway: gateway, baseURL: baseURL}
}

type subscriptionPlanRow struct {
	ID         string `json:"id" db:"id"`
	Name       string `json:"name" db:"name"`
	Slug       string `json:"slug" db:"slug"`
	PriceCents int    `json:"price_cents" db:"price_cents"`
	Currency   string `json:"currency" db:"currency"`
	Interval   string `json:"interval" db:"interval"`
	Features   string `json:"features" db:"features"`
	IsActive   bool   `json:"is_active" db:"is_active"`
}

// ListPlans godoc
// @Summary      List subscription plans
// @Tags         billing
// @Produce      json
// @Success      200  {object} map[string]interface{}
// @Router       /billing/plans [get]
func (h *BillingHandler) ListPlans(c *fiber.Ctx) error {
	var plans []subscriptionPlanRow
	err := h.db.Select(&plans, `
		SELECT id, name, slug, price_cents, currency, interval, features::text, is_active
		FROM subscription_plans WHERE is_active = TRUE
		ORDER BY price_cents ASC
	`)
	if err != nil {
		return c.Status(fiber.StatusInternalServerError).JSON(fiber.Map{"error": "failed"})
	}
	if plans == nil {
		plans = []subscriptionPlanRow{}
	}
	return c.JSON(fiber.Map{"data": plans})
}

// GetPlan godoc
// @Summary      Get plan by slug
// @Tags         billing
// @Produce      json
// @Param        slug  path     string  true   "Plan slug"
// @Success      200   {object} map[string]interface{}
// @Failure      404   {object} map[string]string
// @Router       /billing/plans/{slug} [get]
func (h *BillingHandler) GetPlan(c *fiber.Ctx) error {
	var plan subscriptionPlanRow
	err := h.db.Get(&plan, `
		SELECT id, name, slug, price_cents, currency, interval, features::text, is_active
		FROM subscription_plans WHERE slug = $1
	`, c.Params("slug"))
	if err != nil {
		return c.Status(fiber.StatusNotFound).JSON(fiber.Map{"error": "plan not found"})
	}
	return c.JSON(plan)
}

type createPlanRequest struct {
	Name       string `json:"name"`
	Slug       string `json:"slug"`
	PriceCents int    `json:"price_cents"`
	Currency   string `json:"currency"`
	Interval   string `json:"interval"`
}

// CreatePlan godoc
// @Summary      Create a subscription plan
// @Tags         billing
// @Accept       json
// @Produce      json
// @Security     BearerAuth
// @Param        body  body      object  true   "Plan data"
// @Success      201   {object} map[string]interface{}
// @Failure      400   {object} map[string]string
// @Router       /billing/plans [post]
func (h *BillingHandler) CreatePlan(c *fiber.Ctx) error {
	var req createPlanRequest
	if err := c.BodyParser(&req); err != nil {
		return c.Status(fiber.StatusBadRequest).JSON(fiber.Map{"error": "invalid body"})
	}

	var plan subscriptionPlanRow
	err := h.db.Get(&plan, `
		INSERT INTO subscription_plans (name, slug, price_cents, currency, interval)
		VALUES ($1, $2, $3, COALESCE(NULLIF($4, ''), 'IRR'), $5)
		RETURNING id, name, slug, price_cents, currency, interval, is_active
	`, req.Name, req.Slug, req.PriceCents, req.Currency, req.Interval)
	if err != nil {
		return c.Status(fiber.StatusBadRequest).JSON(fiber.Map{"error": err.Error()})
	}
	return c.Status(fiber.StatusCreated).JSON(plan)
}

type updatePlanRequest struct {
	Name       *string `json:"name"`
	PriceCents *int    `json:"price_cents"`
	IsActive   *bool   `json:"is_active"`
}

// UpdatePlan godoc
// @Summary      Update a subscription plan
// @Tags         billing
// @Accept       json
// @Produce      json
// @Security     BearerAuth
// @Param        id    path     string  true   "Plan ID"
// @Param        body  body      object  true   "Updated plan data"
// @Success      200   {object} map[string]interface{}
// @Failure      400   {object} map[string]string
// @Failure      404   {object} map[string]string
// @Router       /billing/plans/{id} [patch]
func (h *BillingHandler) UpdatePlan(c *fiber.Ctx) error {
	var req updatePlanRequest
	if err := c.BodyParser(&req); err != nil {
		return c.Status(fiber.StatusBadRequest).JSON(fiber.Map{"error": "invalid body"})
	}

	var plan subscriptionPlanRow
	err := h.db.Get(&plan, `
		UPDATE subscription_plans
		SET name = COALESCE($2, name),
		    price_cents = COALESCE($3, price_cents),
		    is_active = COALESCE($4, is_active)
		WHERE id = $1
		RETURNING id, name, slug, price_cents, currency, interval, is_active
	`, c.Params("id"), req.Name, req.PriceCents, req.IsActive)
	if err != nil {
		return c.Status(fiber.StatusNotFound).JSON(fiber.Map{"error": "plan not found"})
	}
	return c.JSON(plan)
}

// MySubscription godoc
// @Summary      Get current user subscription
// @Tags         billing
// @Produce      json
// @Security     BearerAuth
// @Success      200  {object} map[string]interface{}
// @Router       /billing/my-subscription [get]
func (h *BillingHandler) MySubscription(c *fiber.Ctx) error {
	userID := c.Locals("userID").(string)

	var sub struct {
		ID        string  `json:"id" db:"id"`
		PlanID    string  `json:"plan_id" db:"plan_id"`
		PlanName  string  `json:"plan_name" db:"plan_name"`
		Status    string  `json:"status" db:"status"`
		ExpiresAt *string `json:"expires_at" db:"expires_at"`
	}
	err := h.db.Get(&sub, `
		SELECT us.id, us.plan_id, sp.name AS plan_name, us.status,
		       TO_CHAR(us.expires_at, 'YYYY-MM-DD"T"HH24:MI:SS"Z"') AS expires_at
		FROM user_subscriptions us
		JOIN subscription_plans sp ON sp.id = us.plan_id
		WHERE us.user_id = $1 AND us.status = 'active' AND us.expires_at > NOW()
		ORDER BY us.started_at DESC LIMIT 1
	`, userID)
	if err != nil {
		return c.JSON(fiber.Map{"active": false, "subscription": nil})
	}
	return c.JSON(fiber.Map{"active": true, "subscription": sub})
}

// CancelSubscription godoc
// @Summary      Cancel current subscription
// @Tags         billing
// @Produce      json
// @Security     BearerAuth
// @Success      200  {object} map[string]string
// @Failure      404  {object} map[string]string
// @Router       /billing/subscription [delete]
func (h *BillingHandler) CancelSubscription(c *fiber.Ctx) error {
	userID := c.Locals("userID").(string)

	result, err := h.db.Exec(`
		UPDATE user_subscriptions
		SET status = 'cancelled', cancelled_at = NOW(), updated_at = NOW()
		WHERE user_id = $1 AND status = 'active'
	`, userID)
	if err != nil {
		slog.Error("cancel subscription failed", "error", err)
		return c.Status(fiber.StatusInternalServerError).JSON(fiber.Map{"error": "failed"})
	}

	n, _ := result.RowsAffected()
	if n == 0 {
		return c.Status(fiber.StatusNotFound).JSON(fiber.Map{"error": "no active subscription"})
	}

	return c.JSON(fiber.Map{"message": "cancelled"})
}

// MyInvoices godoc
// @Summary      List user invoices
// @Tags         billing
// @Produce      json
// @Security     BearerAuth
// @Success      200  {object} map[string]interface{}
// @Router       /billing/invoices [get]
func (h *BillingHandler) MyInvoices(c *fiber.Ctx) error {
	userID := c.Locals("userID").(string)

	var invoices []struct {
		ID            string  `db:"id" json:"id"`
		AmountCents   int     `db:"amount_cents" json:"amount_cents"`
		Currency      string  `db:"currency" json:"currency"`
		Status        string  `db:"status" json:"status"`
		PaymentMethod string  `db:"payment_method" json:"payment_method"`
		PaymentGateway string `db:"payment_gateway" json:"payment_gateway"`
		RefID         *int64  `db:"ref_id" json:"ref_id"`
		PaidAt        *string `db:"paid_at" json:"paid_at"`
		CreatedAt     string  `db:"created_at" json:"created_at"`
	}
	err := h.db.Select(&invoices, `
		SELECT id, amount_cents, currency, status, payment_method, payment_gateway, ref_id,
		       TO_CHAR(paid_at, 'YYYY-MM-DD"T"HH24:MI:SS"Z"') AS paid_at,
		       TO_CHAR(created_at, 'YYYY-MM-DD"T"HH24:MI:SS"Z"') AS created_at
		FROM invoices WHERE user_id = $1 ORDER BY created_at DESC LIMIT 20
	`, userID)
	if err != nil {
		return c.Status(fiber.StatusInternalServerError).JSON(fiber.Map{"error": "failed"})
	}
	if invoices == nil {
		invoices = []struct {
			ID             string  `db:"id" json:"id"`
			AmountCents    int     `db:"amount_cents" json:"amount_cents"`
			Currency       string  `db:"currency" json:"currency"`
			Status         string  `db:"status" json:"status"`
			PaymentMethod  string  `db:"payment_method" json:"payment_method"`
			PaymentGateway string  `db:"payment_gateway" json:"payment_gateway"`
			RefID          *int64  `db:"ref_id" json:"ref_id"`
			PaidAt         *string `db:"paid_at" json:"paid_at"`
			CreatedAt      string  `db:"created_at" json:"created_at"`
		}{}
	}
	return c.JSON(fiber.Map{"data": invoices})
}
