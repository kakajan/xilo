package handler

import (
	"context"
	"errors"
	"strings"
	"time"

	"github.com/gofiber/fiber/v2"
	"github.com/jmoiron/sqlx"
	"github.com/lib/pq"
	userutil "github.com/xilo-platform/xilo/internal/user/util"
	"github.com/xilo-platform/xilo/pkg/contacthash"
)

const MaxCombinedHashes = 500

var ErrTooManyHashes = errors.New("combined phone_hashes and email_hashes exceed 500")

// ContactHandler serves privacy-preserving contact match endpoints.
type ContactHandler struct {
	db     *sqlx.DB
	pepper string
}

// NewContactHandler constructs a handler. Pass pepper from contacthash.ResolvePepper()
// (or CONTACT_MATCH_PEPPER). Empty pepper fails closed on Match in all environments;
// ResolvePepper already fails closed in production and supplies a dev default otherwise.
func NewContactHandler(db *sqlx.DB, pepper string) *ContactHandler {
	return &ContactHandler{db: db, pepper: strings.TrimSpace(pepper)}
}

type matchRequest struct {
	PhoneHashes []string `json:"phone_hashes"`
	EmailHashes []string `json:"email_hashes"`
}

type matchUser struct {
	ID          string `db:"id" json:"id"`
	Username    string `db:"username" json:"username"`
	DisplayName string `db:"display_name" json:"display_name"`
	AvatarURL   string `db:"avatar_url" json:"avatar_url"`
}

type matchResponseItem struct {
	ID               string `json:"id"`
	Username         string `json:"username"`
	DisplayName      string `json:"display_name"`
	AvatarURL        string `json:"avatar_url"`
	AlreadyFollowing bool   `json:"already_following"`
}

type contactListItem struct {
	ID           string `json:"id" db:"id"`
	Username     string `json:"username" db:"username"`
	DisplayName  string `json:"display_name" db:"display_name"`
	AvatarURL    string `json:"avatar_url" db:"avatar_url"`
	Role         string `json:"-" db:"role"`
	IsVerified   bool   `json:"is_verified"`
	IsFollowing  bool   `json:"is_following"`
	FromContacts bool   `json:"from_contacts" db:"from_contacts"`
}

// ValidateMatchLimits returns ErrTooManyHashes when combined client hashes exceed MaxCombinedHashes.
func ValidateMatchLimits(phoneHashes, emailHashes []string) error {
	if len(phoneHashes)+len(emailHashes) > MaxCombinedHashes {
		return ErrTooManyHashes
	}
	return nil
}

// Match handles POST /api/contacts/match.
//
// @Summary      Match contacts by hashed phone/email
// @Tags         contacts
// @Accept       json
// @Produce      json
// @Security     BearerAuth
// @Param        request body matchRequest true "Client SHA-256 hex hashes"
// @Success      200 {object} map[string]interface{}
// @Failure      400 {object} map[string]string
// @Failure      401 {object} map[string]string
// @Failure      503 {object} map[string]string
// @Router       /contacts/match [post]
func (h *ContactHandler) Match(c *fiber.Ctx) error {
	if h.pepper == "" {
		return c.Status(fiber.StatusServiceUnavailable).JSON(fiber.Map{
			"error": "contact match unavailable",
		})
	}

	userID, ok := c.Locals("userID").(string)
	if !ok || userID == "" {
		return c.Status(fiber.StatusUnauthorized).JSON(fiber.Map{"error": "unauthorized"})
	}

	var req matchRequest
	if err := c.BodyParser(&req); err != nil {
		return c.Status(fiber.StatusBadRequest).JSON(fiber.Map{"error": "invalid request body"})
	}
	if req.PhoneHashes == nil {
		req.PhoneHashes = []string{}
	}
	if req.EmailHashes == nil {
		req.EmailHashes = []string{}
	}

	if err := ValidateMatchLimits(req.PhoneHashes, req.EmailHashes); err != nil {
		return c.Status(fiber.StatusBadRequest).JSON(fiber.Map{
			"error": "too many hashes: combined phone_hashes and email_hashes must be <= 500",
		})
	}

	if len(req.PhoneHashes) == 0 && len(req.EmailHashes) == 0 {
		return c.JSON(fiber.Map{"matches": []matchResponseItem{}})
	}

	phoneServer := contacthash.HMACClientHashes(h.pepper, req.PhoneHashes)
	emailServer := contacthash.HMACClientHashes(h.pepper, req.EmailHashes)
	if len(phoneServer) == 0 && len(emailServer) == 0 {
		return c.JSON(fiber.Map{"matches": []matchResponseItem{}})
	}

	var users []matchUser
	err := h.db.SelectContext(c.UserContext(), &users, `
		SELECT DISTINCT u.id, u.username,
		       COALESCE(u.display_name, '') AS display_name,
		       COALESCE(u.avatar_url, '') AS avatar_url
		FROM users u
		WHERE u.deleted_at IS NULL
		  AND u.id <> $1
		  AND (
		    (cardinality($2::text[]) > 0 AND u.phone_hash = ANY($2))
		    OR (cardinality($3::text[]) > 0 AND u.email_hash = ANY($3))
		  )
		ORDER BY u.username
	`, userID, pq.Array(phoneServer), pq.Array(emailServer))
	if err != nil {
		return c.Status(fiber.StatusInternalServerError).JSON(fiber.Map{"error": "match failed"})
	}

	seen := make(map[string]struct{}, len(users))
	deduped := make([]matchUser, 0, len(users))
	for _, u := range users {
		if _, ok := seen[u.ID]; ok {
			continue
		}
		seen[u.ID] = struct{}{}
		deduped = append(deduped, u)
	}

	if err := h.upsertMatches(c.UserContext(), userID, deduped); err != nil {
		return c.Status(fiber.StatusInternalServerError).JSON(fiber.Map{"error": "match failed"})
	}

	following := h.followingSet(c.UserContext(), userID, deduped)

	out := make([]matchResponseItem, 0, len(deduped))
	for _, u := range deduped {
		out = append(out, matchResponseItem{
			ID:               u.ID,
			Username:         u.Username,
			DisplayName:      u.DisplayName,
			AvatarURL:        u.AvatarURL,
			AlreadyFollowing: following[u.ID],
		})
	}

	return c.JSON(fiber.Map{"matches": out})
}

// List handles GET /api/contacts — followings enriched with from_contacts.
//
// @Summary      List contacts (followings with sync badge)
// @Tags         contacts
// @Produce      json
// @Security     BearerAuth
// @Success      200 {object} map[string]interface{}
// @Failure      401 {object} map[string]string
// @Router       /contacts [get]
func (h *ContactHandler) List(c *fiber.Ctx) error {
	userID, ok := c.Locals("userID").(string)
	if !ok || userID == "" {
		return c.Status(fiber.StatusUnauthorized).JSON(fiber.Map{"error": "unauthorized"})
	}

	rows := make([]contactListItem, 0)
	err := h.db.SelectContext(c.UserContext(), &rows, `
		SELECT u.id, u.username,
		       COALESCE(u.display_name, '') AS display_name,
		       COALESCE(u.avatar_url, '') AS avatar_url,
		       u.role,
		       EXISTS (
		         SELECT 1 FROM user_contact_matches m
		         WHERE m.user_id = $1 AND m.matched_user_id = u.id
		       ) AS from_contacts
		FROM follows f
		JOIN users u ON u.id = f.following_id
		WHERE f.follower_id = $1 AND u.deleted_at IS NULL
		ORDER BY f.created_at DESC, u.id DESC
	`, userID)
	if err != nil {
		return c.Status(fiber.StatusInternalServerError).JSON(fiber.Map{"error": "failed to list contacts"})
	}

	for i := range rows {
		rows[i].IsVerified = userutil.IsVerifiedWriter(rows[i].Role)
		rows[i].IsFollowing = true
	}

	return c.JSON(fiber.Map{"data": rows})
}

func (h *ContactHandler) upsertMatches(ctx context.Context, userID string, users []matchUser) error {
	if userID == "" || len(users) == 0 {
		return nil
	}
	ids := make([]string, 0, len(users))
	for _, u := range users {
		if u.ID == "" || u.ID == userID {
			continue
		}
		ids = append(ids, u.ID)
	}
	if len(ids) == 0 {
		return nil
	}
	now := time.Now().UTC()
	_, err := h.db.ExecContext(ctx, `
		INSERT INTO user_contact_matches (user_id, matched_user_id, matched_at)
		SELECT $1, x.matched_user_id, $2
		FROM UNNEST($3::uuid[]) AS x(matched_user_id)
		ON CONFLICT (user_id, matched_user_id) DO UPDATE
		SET matched_at = EXCLUDED.matched_at
	`, userID, now, pq.Array(ids))
	return err
}

func (h *ContactHandler) followingSet(ctx context.Context, followerID string, users []matchUser) map[string]bool {
	out := make(map[string]bool)
	if followerID == "" || len(users) == 0 {
		return out
	}
	ids := make([]string, len(users))
	for i := range users {
		ids[i] = users[i].ID
	}
	var followed []string
	_ = h.db.SelectContext(ctx, &followed, `
		SELECT following_id FROM follows
		WHERE follower_id = $1 AND following_id = ANY($2)
	`, followerID, pq.Array(ids))
	for _, id := range followed {
		out[id] = true
	}
	return out
}
