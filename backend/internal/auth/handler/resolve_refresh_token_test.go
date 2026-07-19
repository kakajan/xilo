package handler

import (
	"io"
	"net/http"
	"net/http/httptest"
	"strings"
	"testing"

	"github.com/gofiber/fiber/v2"
)

func TestResolveRefreshToken(t *testing.T) {
	app := fiber.New()
	app.Post("/refresh", func(c *fiber.Ctx) error {
		return c.SendString(resolveRefreshToken(c))
	})

	t.Run("body wins over cookie and header", func(t *testing.T) {
		req := httptest.NewRequest(http.MethodPost, "/refresh", strings.NewReader(`{"refresh_token":"from-body"}`))
		req.Header.Set("Content-Type", "application/json")
		req.Header.Set("X-Refresh-Token", "from-header")
		req.AddCookie(&http.Cookie{Name: cookieRefresh, Value: "from-cookie"})

		resp, err := app.Test(req)
		if err != nil {
			t.Fatal(err)
		}
		defer resp.Body.Close()
		got, _ := io.ReadAll(resp.Body)
		if string(got) != "from-body" {
			t.Fatalf("got %q, want from-body", got)
		}
	})

	t.Run("empty json body falls back to cookie", func(t *testing.T) {
		req := httptest.NewRequest(http.MethodPost, "/refresh", strings.NewReader(`{}`))
		req.Header.Set("Content-Type", "application/json")
		req.AddCookie(&http.Cookie{Name: cookieRefresh, Value: "cookie-token"})

		resp, err := app.Test(req)
		if err != nil {
			t.Fatal(err)
		}
		defer resp.Body.Close()
		got, _ := io.ReadAll(resp.Body)
		if string(got) != "cookie-token" {
			t.Fatalf("got %q, want cookie-token", got)
		}
	})

	t.Run("header used when body empty", func(t *testing.T) {
		req := httptest.NewRequest(http.MethodPost, "/refresh", strings.NewReader(`{}`))
		req.Header.Set("Content-Type", "application/json")
		req.Header.Set("X-Refresh-Token", "header-token")

		resp, err := app.Test(req)
		if err != nil {
			t.Fatal(err)
		}
		defer resp.Body.Close()
		got, _ := io.ReadAll(resp.Body)
		if string(got) != "header-token" {
			t.Fatalf("got %q, want header-token", got)
		}
	})

	t.Run("missing token returns empty", func(t *testing.T) {
		req := httptest.NewRequest(http.MethodPost, "/refresh", strings.NewReader(`{}`))
		req.Header.Set("Content-Type", "application/json")

		resp, err := app.Test(req)
		if err != nil {
			t.Fatal(err)
		}
		defer resp.Body.Close()
		got, _ := io.ReadAll(resp.Body)
		if string(got) != "" {
			t.Fatalf("got %q, want empty", got)
		}
	})
}
