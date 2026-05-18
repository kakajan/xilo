package middleware

import (
	"strconv"
	"time"

	"github.com/gofiber/fiber/v2"
)

type MetricsMiddleware struct {
	start time.Time
}

func Metrics() fiber.Handler {
	return func(c *fiber.Ctx) error {
		start := time.Now()
		err := c.Next()
		duration := time.Since(start).Seconds()

		status := c.Response().StatusCode()
		if err != nil {
			status = 500
		}

		c.Locals("latency", duration)
		c.Locals("status_code", status)

		return err
	}
}

func PrometheusMetrics() fiber.Handler {
	return func(c *fiber.Ctx) error {
		err := c.Next()

		method := c.Method()
		path := c.Path()
		status := c.Response().StatusCode()
		latency := time.Since(time.Now()).Seconds()

		_ = method
		_ = path
		_ = status
		_ = latency
		_ = strconv.Itoa(status)

		return err
	}
}
