package norobots

import (
	"io"
	"net/http/httptest"
	"strings"
	"testing"

	"github.com/gofiber/fiber/v2"
)

func TestRobotsDisallowsAll(t *testing.T) {
	app := fiber.New()
	app.Use(Middleware)
	app.Get("/robots.txt", Robots)
	app.Get("/", func(c *fiber.Ctx) error {
		return c.Status(fiber.StatusNotFound).JSON(fiber.Map{"error": "not found"})
	})

	req := httptest.NewRequest("GET", "/robots.txt", nil)
	resp, err := app.Test(req)
	if err != nil {
		t.Fatalf("robots.txt: %v", err)
	}
	body, _ := io.ReadAll(resp.Body)
	if resp.StatusCode != fiber.StatusOK {
		t.Fatalf("status=%d body=%s", resp.StatusCode, body)
	}
	if resp.Header.Get("X-Robots-Tag") != HeaderValue {
		t.Fatalf("X-Robots-Tag=%q", resp.Header.Get("X-Robots-Tag"))
	}
	if !strings.Contains(string(body), "Disallow: /") {
		t.Fatalf("robots body=%s", body)
	}

	root, err := app.Test(httptest.NewRequest("GET", "/", nil))
	if err != nil {
		t.Fatalf("GET /: %v", err)
	}
	if root.Header.Get("X-Robots-Tag") != HeaderValue {
		t.Fatalf("root X-Robots-Tag=%q", root.Header.Get("X-Robots-Tag"))
	}
}
