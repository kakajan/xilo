package middleware

import (
	"strings"

	"github.com/gofiber/fiber/v2"
	"github.com/xilo-platform/xilo/pkg/jwt"
)

func AuthRequired(jwtMgr *jwt.Manager) fiber.Handler {
	return func(c *fiber.Ctx) error {
		token := extractToken(c)
		if token == "" {
			return c.Status(fiber.StatusUnauthorized).JSON(fiber.Map{
				"error": "missing authorization token",
				"code":  "unauthorized",
			})
		}

		claims, err := jwtMgr.ValidateToken(token)
		if err != nil {
			return c.Status(fiber.StatusUnauthorized).JSON(fiber.Map{
				"error": "invalid or expired token",
				"code":  "unauthorized",
			})
		}

		c.Locals("userID", claims.UserID)
		c.Locals("username", claims.Username)
		c.Locals("role", claims.Role)

		return c.Next()
	}
}

func RoleRequired(roles ...string) fiber.Handler {
	return func(c *fiber.Ctx) error {
		roleVal := c.Locals("role")
		if roleVal == nil {
			return c.Status(fiber.StatusForbidden).JSON(fiber.Map{
				"error": "insufficient permissions",
				"code":  "forbidden",
			})
		}

		userRole, ok := roleVal.(string)
		if !ok {
			return c.Status(fiber.StatusForbidden).JSON(fiber.Map{
				"error": "insufficient permissions",
				"code":  "forbidden",
			})
		}

		for _, allowed := range roles {
			if userRole == allowed {
				return c.Next()
			}
		}
		return c.Status(fiber.StatusForbidden).JSON(fiber.Map{
			"error": "insufficient permissions",
			"code":  "forbidden",
		})
	}
}

func OptionalAuth(jwtMgr *jwt.Manager) fiber.Handler {
	return func(c *fiber.Ctx) error {
		token := extractToken(c)
		if token != "" {
			claims, err := jwtMgr.ValidateToken(token)
			if err == nil {
				c.Locals("userID", claims.UserID)
				c.Locals("username", claims.Username)
				c.Locals("role", claims.Role)
			}
		}
		return c.Next()
	}
}

func extractToken(c *fiber.Ctx) string {
	header := c.Get("Authorization")
	if header != "" {
		parts := strings.SplitN(header, " ", 2)
		if len(parts) == 2 && strings.ToLower(parts[0]) == "bearer" {
			return parts[1]
		}
	}

	cookie := c.Cookies("xilo_access_token")
	if cookie != "" {
		return cookie
	}

	return ""
}
