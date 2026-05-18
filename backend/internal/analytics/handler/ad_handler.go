package handler

import (
	"github.com/gofiber/fiber/v2"
	"github.com/jmoiron/sqlx"
)

type AdHandler struct {
	db *sqlx.DB
}

func NewAdHandler(db *sqlx.DB) *AdHandler {
	return &AdHandler{db: db}
}

type Ad struct {
	ID             string  `json:"id" db:"id"`
	Title          string  `json:"title" db:"title"`
	ImageURL       string  `json:"image_url" db:"image_url"`
	TargetURL      string  `json:"target_url" db:"target_url"`
	Slot           string  `json:"slot" db:"slot"`
	CategoryFilter string  `json:"category_filter" db:"category_filter"`
	IsActive       bool    `json:"is_active" db:"is_active"`
	StartsAt       *string `json:"starts_at" db:"starts_at"`
	EndsAt         *string `json:"ends_at" db:"ends_at"`
	Impressions    int64   `json:"impressions" db:"impressions"`
	Clicks         int64   `json:"clicks" db:"clicks"`
}

// @Summary      List all ads
// @Tags         ads
// @Produce      json
// @Success      200  {object}  map[string]interface{}
// @Router       /ads [get]
func (h *AdHandler) ListAds(c *fiber.Ctx) error {
	var ads []Ad
	err := h.db.Select(&ads, `
		SELECT id, title, image_url, target_url, slot, category_filter, is_active, starts_at, ends_at, impressions, clicks
		FROM ads ORDER BY created_at DESC
	`)
	if err != nil {
		return c.Status(fiber.StatusInternalServerError).JSON(fiber.Map{"error": "failed"})
	}
	if ads == nil {
		ads = []Ad{}
	}
	return c.JSON(fiber.Map{"data": ads})
}

// @Summary      Create an ad
// @Tags         ads
// @Accept       json
// @Produce      json
// @Security     BearerAuth
// @Param        request body object true "Ad data"
// @Success      201  {object}  map[string]interface{}
// @Failure      400  {object}  map[string]string
// @Router       /ads [post]
func (h *AdHandler) CreateAd(c *fiber.Ctx) error {
	var req struct {
		Title          string `json:"title"`
		ImageURL       string `json:"image_url"`
		TargetURL      string `json:"target_url"`
		Slot           string `json:"slot"`
		CategoryFilter string `json:"category_filter"`
	}
	if err := c.BodyParser(&req); err != nil {
		return c.Status(fiber.StatusBadRequest).JSON(fiber.Map{"error": "invalid body"})
	}

	var ad Ad
	err := h.db.Get(&ad, `
		INSERT INTO ads (title, image_url, target_url, slot, category_filter)
		VALUES ($1, $2, $3, $4, $5)
		RETURNING id, title, image_url, target_url, slot, category_filter, is_active
	`, req.Title, req.ImageURL, req.TargetURL, req.Slot, req.CategoryFilter)
	if err != nil {
		return c.Status(fiber.StatusBadRequest).JSON(fiber.Map{"error": err.Error()})
	}
	return c.Status(fiber.StatusCreated).JSON(ad)
}

// @Summary      Update an ad
// @Tags         ads
// @Accept       json
// @Produce      json
// @Security     BearerAuth
// @Param        id   path string true "Ad ID"
// @Param        request body object true "Updated ad data"
// @Success      200  {object}  map[string]string
// @Failure      400  {object}  map[string]string
// @Failure      404  {object}  map[string]string
// @Router       /ads/{id} [patch]
func (h *AdHandler) UpdateAd(c *fiber.Ctx) error {
	var req struct {
		IsActive *bool `json:"is_active"`
	}
	if err := c.BodyParser(&req); err != nil {
		return c.Status(fiber.StatusBadRequest).JSON(fiber.Map{"error": "invalid body"})
	}

	_, err := h.db.Exec(`UPDATE ads SET is_active = $2 WHERE id = $1`, c.Params("id"), *req.IsActive)
	if err != nil {
		return c.Status(fiber.StatusNotFound).JSON(fiber.Map{"error": "ad not found"})
	}
	return c.JSON(fiber.Map{"message": "updated"})
}

// @Summary      Serve an ad
// @Tags         ads
// @Produce      json
// @Param        slot     query string false "Ad slot" default(feed)
// @Param        category query string false "Filter by category"
// @Success      200  {object}  map[string]interface{}
// @Router       /ads/serve [get]
func (h *AdHandler) ServeAd(c *fiber.Ctx) error {
	slot := c.Query("slot", "feed")
	category := c.Query("category")

	var ad struct {
		ID         string `db:"id" json:"id"`
		Title      string `db:"title" json:"title"`
		ImageURL   string `db:"image_url" json:"image_url"`
		TargetURL  string `db:"target_url" json:"target_url"`
		Slot       string `db:"slot" json:"slot"`
	}

	if category != "" {
		err := h.db.Get(&ad, `
			SELECT id, title, image_url, target_url, slot FROM ads
			WHERE is_active = TRUE AND slot = $1
			AND (category_filter IS NULL OR category_filter = '' OR category_filter = $2)
			ORDER BY impressions ASC LIMIT 1
		`, slot, category)
		if err != nil {
			return c.JSON(fiber.Map{})
		}
	} else {
		err := h.db.Get(&ad, `
			SELECT id, title, image_url, target_url, slot FROM ads
			WHERE is_active = TRUE AND slot = $1
			ORDER BY impressions ASC LIMIT 1
		`, slot)
		if err != nil {
			return c.JSON(fiber.Map{})
		}
	}

	h.db.Exec(`UPDATE ads SET impressions = impressions + 1 WHERE id = $1`, ad.ID)
	return c.JSON(ad)
}

// @Summary      Track ad click
// @Tags         ads
// @Produce      json
// @Param        id   path string true "Ad ID"
// @Success      200  {object}  map[string]string
// @Router       /ads/{id}/click [post]
func (h *AdHandler) TrackClick(c *fiber.Ctx) error {
	id := c.Params("id")
	h.db.Exec(`UPDATE ads SET clicks = clicks + 1 WHERE id = $1`, id)
	return c.JSON(fiber.Map{"message": "clicked"})
}
