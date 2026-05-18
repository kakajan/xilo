package middleware

import (
	"fmt"
	"time"

	"github.com/gofiber/fiber/v2"
	"github.com/redis/go-redis/v9"
)

func RateLimit(rdb *redis.Client, limit int, window time.Duration) fiber.Handler {
	return func(c *fiber.Ctx) error {
		ip := c.IP()
		path := c.Path()
		key := fmt.Sprintf("ratelimit:%s:%s", ip, path)

		count, err := rdb.Incr(c.UserContext(), key).Result()
		if err != nil {
			return c.Next()
		}

		if count == 1 {
			rdb.Expire(c.UserContext(), key, window)
		}

		if count > int64(limit) {
			retryAfter := int(window.Seconds())
			c.Set("Retry-After", fmt.Sprintf("%d", retryAfter))
			return c.Status(fiber.StatusTooManyRequests).JSON(fiber.Map{
				"error": "too many requests, please try again later",
			})
		}

		return c.Next()
	}
}
