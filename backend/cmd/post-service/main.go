package main

import (
	"log/slog"
	"os"

	"github.com/gofiber/fiber/v2"
	"github.com/gofiber/fiber/v2/middleware/logger"
	"github.com/jmoiron/sqlx"
	_ "github.com/lib/pq"

	posthandler "github.com/xilo-platform/xilo/internal/post/handler"
	postrepo "github.com/xilo-platform/xilo/internal/post/repository"
	postsvc "github.com/xilo-platform/xilo/internal/post/service"
	pkgredis "github.com/xilo-platform/xilo/pkg/redis"
)

func main() {
	db := connectDB()
	rdb := connectRedis()

	repo := postrepo.NewPostRepo(db)
	svc := postsvc.NewPostService(repo, rdb)
	h := posthandler.NewPostHandler(svc)

	app := fiber.New()
	app.Use(logger.New())

	app.Get("/", h.List)
	app.Get("/tags/suggest", h.SuggestTags)
	app.Get("/tags/trending", h.TrendingTags)
	app.Post("/:id/view", h.RecordView)
	app.Get("/:slug", h.GetBySlug)
	app.Post("/", h.Create)
	app.Patch("/:id", h.Update)
	app.Delete("/:id", h.Delete)

	app.Get("/health", func(c *fiber.Ctx) error {
		return c.JSON(fiber.Map{"status": "ok"})
	})

	port := env("PORT", "8030")
	slog.Info("post-service starting", "port", port)
	if err := app.Listen(":" + port); err != nil {
		slog.Error("post-service failed", "error", err)
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

func connectRedis() *pkgredis.Client {
	rdb, err := pkgredis.NewClient(env("REDIS_URL", "localhost:6379"), "", 0)
	if err != nil {
		os.Exit(1)
	}
	return rdb
}

func env(key, fallback string) string {
	if v := os.Getenv(key); v != "" {
		return v
	}
	return fallback
}
