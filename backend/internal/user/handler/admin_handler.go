package handler

import (
	"database/sql"
	"errors"
	"strings"
	"time"

	"github.com/gofiber/fiber/v2"
	"github.com/jmoiron/sqlx"
)

var assignableRoles = map[string]bool{
	"reader":     true,
	"author":     true,
	"editor":     true,
	"admin":      true,
	"superadmin": true,
}

type AdminHandler struct {
	db *sqlx.DB
}

func NewAdminHandler(db *sqlx.DB) *AdminHandler {
	return &AdminHandler{db: db}
}

type adminUserItem struct {
	ID          string    `json:"id" db:"id"`
	Email       string    `json:"email" db:"email"`
	Username    string    `json:"username" db:"username"`
	DisplayName string    `json:"display_name" db:"display_name"`
	AvatarURL   string    `json:"avatar_url" db:"avatar_url"`
	Role        string    `json:"role" db:"role"`
	CreatedAt   time.Time `json:"created_at" db:"created_at"`
}

// ListUsers searches users by username, email, or display name (admin only).
func (h *AdminHandler) ListUsers(c *fiber.Ctx) error {
	q := strings.TrimSpace(c.Query("q"))
	limit := c.QueryInt("limit", 20)
	if limit < 1 {
		limit = 20
	}
	if limit > 50 {
		limit = 50
	}

	ctx := c.UserContext()
	var users []adminUserItem
	var err error

	if q == "" {
		err = h.db.SelectContext(ctx, &users, `
			SELECT id, email, username,
			       COALESCE(display_name, '') AS display_name,
			       COALESCE(avatar_url, '') AS avatar_url,
			       role, created_at
			FROM users
			WHERE deleted_at IS NULL
			ORDER BY created_at DESC
			LIMIT $1
		`, limit)
	} else {
		pattern := "%" + escapeLike(q) + "%"
		err = h.db.SelectContext(ctx, &users, `
			SELECT id, email, username,
			       COALESCE(display_name, '') AS display_name,
			       COALESCE(avatar_url, '') AS avatar_url,
			       role, created_at
			FROM users
			WHERE deleted_at IS NULL
			  AND (
			    username ILIKE $1 ESCAPE '\'
			    OR email ILIKE $1 ESCAPE '\'
			    OR COALESCE(display_name, '') ILIKE $1 ESCAPE '\'
			  )
			ORDER BY username ASC
			LIMIT $2
		`, pattern, limit)
	}
	if err != nil {
		return c.Status(fiber.StatusInternalServerError).JSON(fiber.Map{
			"error": "failed to list users",
			"code":  "internal_error",
		})
	}
	if users == nil {
		users = []adminUserItem{}
	}
	return c.JSON(fiber.Map{"users": users})
}

type updateRoleRequest struct {
	Role string `json:"role"`
}

// UpdateUserRole sets a user's platform role (admin only).
func (h *AdminHandler) UpdateUserRole(c *fiber.Ctx) error {
	userID := c.Params("id")
	if userID == "" {
		return c.Status(fiber.StatusBadRequest).JSON(fiber.Map{
			"error": "user id is required",
			"code":  "bad_request",
		})
	}

	var req updateRoleRequest
	if err := c.BodyParser(&req); err != nil {
		return c.Status(fiber.StatusBadRequest).JSON(fiber.Map{
			"error": "invalid request body",
			"code":  "bad_request",
		})
	}
	role := strings.TrimSpace(strings.ToLower(req.Role))
	if !assignableRoles[role] {
		return c.Status(fiber.StatusBadRequest).JSON(fiber.Map{
			"error": "invalid role",
			"code":  "bad_request",
		})
	}

	actorID, _ := c.Locals("userID").(string)
	if actorID != "" && actorID == userID && role != "admin" && role != "superadmin" {
		return c.Status(fiber.StatusBadRequest).JSON(fiber.Map{
			"error": "cannot demote your own admin role",
			"code":  "bad_request",
		})
	}

	ctx := c.UserContext()
	var currentRole string
	err := h.db.GetContext(ctx, &currentRole, `
		SELECT role FROM users WHERE id = $1 AND deleted_at IS NULL
	`, userID)
	if err != nil {
		if errors.Is(err, sql.ErrNoRows) {
			return c.Status(fiber.StatusNotFound).JSON(fiber.Map{
				"error": "user not found",
				"code":  "not_found",
			})
		}
		return c.Status(fiber.StatusInternalServerError).JSON(fiber.Map{
			"error": "failed to load user",
			"code":  "internal_error",
		})
	}

	if currentRole == "superadmin" && role != "superadmin" {
		var count int
		if err := h.db.GetContext(ctx, &count, `
			SELECT COUNT(*) FROM users
			WHERE role = 'superadmin' AND deleted_at IS NULL
		`); err != nil {
			return c.Status(fiber.StatusInternalServerError).JSON(fiber.Map{
				"error": "failed to check superadmin count",
				"code":  "internal_error",
			})
		}
		if count <= 1 {
			return c.Status(fiber.StatusBadRequest).JSON(fiber.Map{
				"error": "cannot demote the last superadmin",
				"code":  "bad_request",
			})
		}
	}

	var user adminUserItem
	err = h.db.GetContext(ctx, &user, `
		UPDATE users
		SET role = $2, updated_at = NOW()
		WHERE id = $1 AND deleted_at IS NULL
		RETURNING id, email, username,
		          COALESCE(display_name, '') AS display_name,
		          COALESCE(avatar_url, '') AS avatar_url,
		          role, created_at
	`, userID, role)
	if err != nil {
		if errors.Is(err, sql.ErrNoRows) {
			return c.Status(fiber.StatusNotFound).JSON(fiber.Map{
				"error": "user not found",
				"code":  "not_found",
			})
		}
		return c.Status(fiber.StatusInternalServerError).JSON(fiber.Map{
			"error": "failed to update role",
			"code":  "internal_error",
		})
	}
	return c.JSON(user)
}

func escapeLike(s string) string {
	s = strings.ReplaceAll(s, `\`, `\\`)
	s = strings.ReplaceAll(s, `%`, `\%`)
	s = strings.ReplaceAll(s, `_`, `\_`)
	return s
}
