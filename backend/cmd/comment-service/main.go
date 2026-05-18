package main

import (
	"log/slog"
	"os"

	"github.com/gofiber/fiber/v2"
	"github.com/gofiber/fiber/v2/middleware/logger"
	"github.com/jmoiron/sqlx"
	_ "github.com/lib/pq"

	commenthandler "github.com/xilo-platform/xilo/internal/comment/handler"
	commentrepo "github.com/xilo-platform/xilo/internal/comment/repository"
	commentsvc "github.com/xilo-platform/xilo/internal/comment/service"
)

func main() {
	db := connectDB()

	repo := commentrepo.NewCommentRepo(db)
	svc := commentsvc.NewCommentService(repo)
	h := commenthandler.NewCommentHandler(svc)

	app := fiber.New()
	app.Use(logger.New())

	app.Get("/posts/:postId/comments", h.List)
	app.Post("/posts/:postId/comments", h.Create)
	app.Patch("/comments/:id", h.Update)
	app.Delete("/comments/:id", h.Delete)
	app.Post("/:type/:id/reactions", h.ToggleReaction)
	app.Post("/comments/:id/pin", h.Pin)

	app.Get("/health", func(c *fiber.Ctx) error {
		return c.JSON(fiber.Map{"status": "ok"})
	})

	port := env("PORT", "8040")
	slog.Info("comment-service starting", "port", port)
	if err := app.Listen(":" + port); err != nil {
		slog.Error("comment-service failed", "error", err)
		os.Exit(1)
	}
}

func connectDB() *sqlx.DB {
	dsn := env("DATABASE_URL", "postgres://xilo:xilo@localhost:5432/xilo?sslmode=disable")
	db, err := sqlx.Connect("postgres", dsn)
	if err != nil {
		os.Exit(1)
	}
	db.SetMaxOpenConns(25)
	db.SetMaxIdleConns(5)
	return db
}

func env(key, fallback string) string {
	if v := os.Getenv(key); v != "" {
		return v
	}
	return fallback
}
