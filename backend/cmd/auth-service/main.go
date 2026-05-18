package main

import (
	"log/slog"
	"os"
	"time"

	"github.com/gofiber/fiber/v2"
	"github.com/gofiber/fiber/v2/middleware/logger"
	"github.com/jmoiron/sqlx"
	_ "github.com/lib/pq"

	authhandler "github.com/xilo-platform/xilo/internal/auth/handler"
	authmw "github.com/xilo-platform/xilo/internal/auth/middleware"
	authrepo "github.com/xilo-platform/xilo/internal/auth/repository"
	authsvc "github.com/xilo-platform/xilo/internal/auth/service"
	"github.com/xilo-platform/xilo/pkg/jwt"
	pkgredis "github.com/xilo-platform/xilo/pkg/redis"
)

func main() {
	db := connectDB()
	_ = connectRedis()

	jwtMgr := initJWT()

	repo := authrepo.NewUserRepo(db)
	svc := authsvc.NewAuthService(repo, jwtMgr)
	h := authhandler.NewAuthHandler(svc)

	app := fiber.New()
	app.Use(logger.New())

	app.Post("/register", h.Register)
	app.Post("/login", h.Login)
	app.Post("/refresh", h.Refresh)
	app.Post("/logout", authmw.AuthRequired(jwtMgr), h.Logout)
	app.Get("/me", authmw.AuthRequired(jwtMgr), h.Me)
	app.Patch("/me", authmw.AuthRequired(jwtMgr), h.UpdateProfile)

	app.Get("/health", func(c *fiber.Ctx) error {
		return c.JSON(fiber.Map{"status": "ok"})
	})

	port := env("PORT", "8010")
	slog.Info("auth-service starting", "port", port)
	if err := app.Listen(":" + port); err != nil {
		slog.Error("auth-service failed", "error", err)
		os.Exit(1)
	}
}

func initJWT() *jwt.Manager {
	mgr, err := jwt.NewManager(
		env("JWT_PRIVATE_KEY_PATH", "private.pem"),
		env("JWT_PUBLIC_KEY_PATH", "public.pem"),
		env("JWT_ISSUER", "xilo"),
		15*time.Minute,
		7*24*time.Hour,
	)
	if err != nil {
		slog.Error("jwt init failed", "error", err)
		os.Exit(1)
	}
	return mgr
}

func connectDB() *sqlx.DB {
	dsn := env("DATABASE_URL", "postgres://xilo:xilo@localhost:5432/xilo?sslmode=disable")
	db, err := sqlx.Connect("postgres", dsn)
	if err != nil {
		slog.Error("db connect failed", "error", err)
		os.Exit(1)
	}
	db.SetMaxOpenConns(25)
	db.SetMaxIdleConns(5)
	return db
}

func connectRedis() *pkgredis.Client {
	addr := env("REDIS_URL", "localhost:6379")
	rdb, err := pkgredis.NewClient(addr, "", 0)
	if err != nil {
		slog.Error("redis connect failed", "error", err)
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
