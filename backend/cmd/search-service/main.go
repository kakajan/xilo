package main

import (
	"log/slog"
	"os"

	"github.com/gofiber/fiber/v2"
	"github.com/gofiber/fiber/v2/middleware/logger"

	searchhandler "github.com/xilo-platform/xilo/internal/search/handler"
	searchrepo "github.com/xilo-platform/xilo/internal/search/repository"
	searchsvc "github.com/xilo-platform/xilo/internal/search/service"
)

func main() {
	repo, err := searchrepo.NewSearchRepo(
		env("MEILISEARCH_URL", "http://localhost:7700"),
		env("MEILISEARCH_KEY", "xilo-meili-key"),
	)
	if err != nil {
		slog.Error("search repo init failed", "error", err)
		os.Exit(1)
	}

	svc := searchsvc.NewSearchService(repo)
	h := searchhandler.NewSearchHandler(svc)

	app := fiber.New()
	app.Use(logger.New())

	app.Get("/posts", h.SearchPosts)
	app.Get("/suggest", h.Suggest)

	app.Get("/health", func(c *fiber.Ctx) error {
		return c.JSON(fiber.Map{"status": "ok"})
	})

	port := env("PORT", "8060")
	slog.Info("search-service starting", "port", port)
	if err := app.Listen(":" + port); err != nil {
		slog.Error("search-service failed", "error", err)
		os.Exit(1)
	}
}

func env(key, fallback string) string {
	if v := os.Getenv(key); v != "" {
		return v
	}
	return fallback
}
