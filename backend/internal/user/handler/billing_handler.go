package handler

import (
	"github.com/gofiber/fiber/v2"
	"github.com/jmoiron/sqlx"
)

type BillingHandler struct {
	db *sqlx.DB
}

func NewBillingHandler(db *sqlx.DB) *BillingHandler {
	return &BillingHandler{db: db}
}

type SubscriptionPlan struct {
	ID         string  `json:"id" db:"id"`
	Name       string  `json:"name" db:"name"`
	Slug       string  `json:"slug" db:"slug"`
	PriceCents int     `json:"price_cents" db:"price_cents"`
	Currency   string  `json:"currency" db:"currency"`
	Interval   string  `json:"interval" db:"interval"`
	Features   string  `json:"features" db:"features"`
	IsActive   bool    `json:"is_active" db:"is_active"`
}

func (h *BillingHandler) ListPlans(c *fiber.Ctx) error {
	var plans []SubscriptionPlan
	err := h.db.Select(&plans, `
		SELECT id, name, slug, price_cents, currency, interval, features::text, is_active
		FROM subscription_plans WHERE is_active = TRUE
		ORDER BY price_cents ASC
	`)
	if err != nil {
		return c.Status(fiber.StatusInternalServerError).JSON(fiber.Map{"error": "failed"})
	}
	if plans == nil {
		plans = []SubscriptionPlan{}
	}
	return c.JSON(fiber.Map{"data": plans})
}

func (h *BillingHandler) GetPlan(c *fiber.Ctx) error {
	var plan SubscriptionPlan
	err := h.db.Get(&plan, `
		SELECT id, name, slug, price_cents, currency, interval, features::text, is_active
		FROM subscription_plans WHERE slug = $1
	`, c.Params("slug"))
	if err != nil {
		return c.Status(fiber.StatusNotFound).JSON(fiber.Map{"error": "plan not found"})
	}
	return c.JSON(plan)
}

func (h *BillingHandler) CreatePlan(c *fiber.Ctx) error {
	var req struct {
		Name       string `json:"name"`
		Slug       string `json:"slug"`
		PriceCents int    `json:"price_cents"`
		Currency   string `json:"currency"`
		Interval   string `json:"interval"`
	}
	if err := c.BodyParser(&req); err != nil {
		return c.Status(fiber.StatusBadRequest).JSON(fiber.Map{"error": "invalid body"})
	}

	var plan SubscriptionPlan
	err := h.db.Get(&plan, `
		INSERT INTO subscription_plans (name, slug, price_cents, currency, interval)
		VALUES ($1, $2, $3, COALESCE($4, 'USD'), $5)
		RETURNING id, name, slug, price_cents, currency, interval, is_active
	`, req.Name, req.Slug, req.PriceCents, req.Currency, req.Interval)
	if err != nil {
		return c.Status(fiber.StatusBadRequest).JSON(fiber.Map{"error": err.Error()})
	}
	return c.Status(fiber.StatusCreated).JSON(plan)
}

func (h *BillingHandler) UpdatePlan(c *fiber.Ctx) error {
	var req struct {
		Name       *string `json:"name"`
		PriceCents *int    `json:"price_cents"`
		IsActive   *bool   `json:"is_active"`
	}
	if err := c.BodyParser(&req); err != nil {
		return c.Status(fiber.StatusBadRequest).JSON(fiber.Map{"error": "invalid body"})
	}

	var plan SubscriptionPlan
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

func (h *BillingHandler) Subscribe(c *fiber.Ctx) error {
	userID := c.Locals("userID").(string)

	var req struct {
		PlanID string `json:"plan_id"`
	}
	if err := c.BodyParser(&req); err != nil {
		return c.Status(fiber.StatusBadRequest).JSON(fiber.Map{"error": "invalid body"})
	}

	expiresAt := "NOW() + INTERVAL '30 days'"

	_, err := h.db.Exec(`
		INSERT INTO user_subscriptions (user_id, plan_id, status, expires_at)
		VALUES ($1, $2, 'active', `+expiresAt+`)
	`, userID, req.PlanID)
	if err != nil {
		return c.Status(fiber.StatusBadRequest).JSON(fiber.Map{"error": "subscription failed"})
	}

	_, _ = h.db.Exec(`
		INSERT INTO invoices (user_id, amount_cents, currency, status, payment_method)
		SELECT $1, price_cents, currency, 'paid', 'card'
		FROM subscription_plans WHERE id = $2
	`, userID, req.PlanID)

	return c.JSON(fiber.Map{"message": "subscribed"})
}

func (h *BillingHandler) MySubscription(c *fiber.Ctx) error {
	userID := c.Locals("userID").(string)

	var sub struct {
		ID     string `db:"id"`
		PlanID string `db:"plan_id"`
		Status string `db:"status"`
	}
	err := h.db.Get(&sub, `
		SELECT id, plan_id, status FROM user_subscriptions
		WHERE user_id = $1 AND status = 'active'
		ORDER BY started_at DESC LIMIT 1
	`, userID)
	if err != nil {
		return c.JSON(fiber.Map{"active": false})
	}
	return c.JSON(fiber.Map{"active": true, "subscription": sub})
}

func (h *BillingHandler) MyInvoices(c *fiber.Ctx) error {
	userID := c.Locals("userID").(string)

	var invoices []struct {
		ID           string `db:"id" json:"id"`
		AmountCents  int    `db:"amount_cents" json:"amount_cents"`
		Currency     string `db:"currency" json:"currency"`
		Status       string `db:"status" json:"status"`
		PaidAt       string `db:"paid_at" json:"paid_at"`
	}
	err := h.db.Select(&invoices, `
		SELECT id, amount_cents, currency, status, paid_at
		FROM invoices WHERE user_id = $1 ORDER BY paid_at DESC LIMIT 20
	`, userID)
	if err != nil {
		return c.Status(fiber.StatusInternalServerError).JSON(fiber.Map{"error": "failed"})
	}
	if invoices == nil {
		invoices = []struct {
			ID           string `db:"id" json:"id"`
			AmountCents  int    `db:"amount_cents" json:"amount_cents"`
			Currency     string `db:"currency" json:"currency"`
			Status       string `db:"status" json:"status"`
			PaidAt       string `db:"paid_at" json:"paid_at"`
		}{}
	}
	return c.JSON(fiber.Map{"data": invoices})
}
