package main

import (
	"log/slog"
	"os"

	"github.com/gofiber/fiber/v2"
	"github.com/gofiber/fiber/v2/middleware/logger"
	"github.com/jmoiron/sqlx"
	_ "github.com/lib/pq"

	mediahandler "github.com/xilo-platform/xilo/internal/media/handler"
	mediarepo "github.com/xilo-platform/xilo/internal/media/repository"
	mediasvc "github.com/xilo-platform/xilo/internal/media/service"
	storagefactory "github.com/xilo-platform/xilo/pkg/storage/factory"
)

func main() {
	db := connectDB()
	storageDriver, err := storagefactory.New()
	if err != nil {
		slog.Error("storage driver init failed", "error", err)
		os.Exit(1)
	}

	repo := mediarepo.NewMediaRepo(db)
	svc := mediasvc.NewMediaService(repo, storageDriver)
	h := mediahandler.NewMediaHandler(svc)

	app := fiber.New()
	app.Use(logger.New())

	app.Post("/upload", h.Upload)
	app.Get("/", h.List)
	app.Get("/:id", h.Get)
	app.Delete("/:id", h.Delete)

	app.Get("/health", func(c *fiber.Ctx) error {
		return c.JSON(fiber.Map{"status": "ok"})
	})

	port := env("PORT", "8070")
	slog.Info("media-service starting", "port", port)
	if err := app.Listen(":" + port); err != nil {
		slog.Error("media-service failed", "error", err)
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
