package middleware

import (
	"context"
	"encoding/json"
	"time"

	"github.com/gofiber/fiber/v2"
	"github.com/redis/go-redis/v9"
)

const maxCachedBodySize = 100 * 1024

func Cache(rdb *redis.Client, ttl time.Duration) fiber.Handler {
	return func(c *fiber.Ctx) error {
		if c.Method() != "GET" {
			return c.Next()
		}

		key := "cache:" + c.OriginalURL()
		cached, err := rdb.Get(c.UserContext(), key).Result()
		if err == nil {
			c.Set("X-Cache", "HIT")
			c.Set("Content-Type", "application/json")
			return c.SendString(cached)
		}

		c.Set("X-Cache", "MISS")
		err = c.Next()
		if err != nil {
			return err
		}

		body := c.Response().Body()
		if len(body) > 0 && len(body) < maxCachedBodySize {
			var data interface{}
			if json.Unmarshal(body, &data) == nil {
				rdb.Set(c.UserContext(), key, body, ttl)
			}
		}

		return nil
	}
}

func InvalidateCache(rdb *redis.Client, patterns ...string) {
	ctx, cancel := context.WithTimeout(context.Background(), 10*time.Second)
	defer cancel()

	for _, pattern := range patterns {
		var keys []string
		iter := rdb.Scan(ctx, 0, pattern, 0).Iterator()
		for iter.Next(ctx) {
			keys = append(keys, iter.Val())
		}

		if len(keys) > 0 {
			rdb.Del(ctx, keys...)
		}
	}
}
