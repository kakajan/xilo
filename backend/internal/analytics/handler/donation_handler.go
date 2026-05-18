package handler

import (
	"github.com/gofiber/fiber/v2"
	"github.com/jmoiron/sqlx"
)

type DonationHandler struct {
	db *sqlx.DB
}

func NewDonationHandler(db *sqlx.DB) *DonationHandler {
	return &DonationHandler{db: db}
}

// @Summary      Set donation wallet address
// @Tags         donations
// @Accept       json
// @Produce      json
// @Security     BearerAuth
// @Param        request body object true "Wallet data"
// @Success      200  {object}  map[string]string
// @Failure      400  {object}  map[string]string
// @Router       /donations/wallet [post]
func (h *DonationHandler) SetWalletAddress(c *fiber.Ctx) error {
	userID := c.Locals("userID").(string)

	var req struct {
		Currency string `json:"currency"`
		Address  string `json:"address"`
	}
	if err := c.BodyParser(&req); err != nil {
		return c.Status(fiber.StatusBadRequest).JSON(fiber.Map{"error": "invalid body"})
	}

	_, err := h.db.Exec(`
		INSERT INTO donation_wallets (user_id, currency, address)
		VALUES ($1, $2, $3)
		ON CONFLICT (user_id, currency) DO UPDATE SET address = $3
	`, userID, req.Currency, req.Address)
	if err != nil {
		return c.Status(fiber.StatusInternalServerError).JSON(fiber.Map{"error": "failed"})
	}

	return c.JSON(fiber.Map{"message": "wallet updated"})
}

// @Summary      Get user wallet addresses
// @Tags         donations
// @Produce      json
// @Param        username path string true "Username"
// @Success      200  {object}  map[string]interface{}
// @Failure      404  {object}  map[string]string
// @Router       /donations/wallet/{username} [get]
func (h *DonationHandler) GetWalletAddresses(c *fiber.Ctx) error {
	username := c.Params("username")

	var userID string
	err := h.db.Get(&userID, `SELECT id FROM users WHERE username = $1`, username)
	if err != nil {
		return c.Status(fiber.StatusNotFound).JSON(fiber.Map{"error": "user not found"})
	}

	type Wallet struct {
		Currency string `db:"currency" json:"currency"`
		Address  string `db:"address" json:"address"`
	}
	var wallets []Wallet
	err = h.db.Select(&wallets, `
		SELECT currency, address FROM donation_wallets WHERE user_id = $1
	`, userID)
	if err != nil {
		return c.Status(fiber.StatusInternalServerError).JSON(fiber.Map{"error": "failed"})
	}
	if wallets == nil {
		wallets = []Wallet{}
	}
	return c.JSON(fiber.Map{"data": wallets})
}

// @Summary      Record a donation
// @Tags         donations
// @Accept       json
// @Produce      json
// @Param        request body object true "Donation data"
// @Success      200  {object}  map[string]string
// @Failure      400  {object}  map[string]string
// @Router       /donations/record [post]
func (h *DonationHandler) RecordDonation(c *fiber.Ctx) error {
	var req struct {
		ReceiverID string  `json:"receiver_id"`
		Currency   string  `json:"currency"`
		Amount     float64 `json:"amount"`
		TxHash     string  `json:"tx_hash"`
	}
	if err := c.BodyParser(&req); err != nil {
		return c.Status(fiber.StatusBadRequest).JSON(fiber.Map{"error": "invalid body"})
	}

	donorID := c.Locals("userID")
	var donorUID interface{} = nil
	if donorID != nil {
		donorUID = donorID.(string)
	}

	_, err := h.db.Exec(`
		INSERT INTO donations (donor_id, receiver_id, currency, amount, tx_hash)
		VALUES ($1, $2, $3, $4, $5)
	`, donorUID, req.ReceiverID, req.Currency, req.Amount, req.TxHash)
	if err != nil {
		return c.Status(fiber.StatusInternalServerError).JSON(fiber.Map{"error": "failed"})
	}

	return c.JSON(fiber.Map{"message": "donation recorded"})
}
