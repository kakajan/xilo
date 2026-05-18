package main

// @title Xilo Blog Platform API
// @version 1.0
// @description A self-hosted, Telegram-inspired modern blog platform API.
// @termsOfService https://xilo.example.com/terms

// @contact.name Xilo API Support
// @contact.url https://xilo.example.com/support
// @contact.email support@xilo.example.com

// @license.name MIT
// @license.url https://opensource.org/licenses/MIT

// @host localhost:8000
// @BasePath /api
// @schemes http https

// @securityDefinitions.apikey BearerAuth
// @in header
// @name Authorization
// @description Type "Bearer" followed by a space and JWT token.

import (
	"context"
	"log/slog"
	"os"
	"os/signal"
	"syscall"
	"time"

	"github.com/gofiber/fiber/v2"
	"github.com/gofiber/fiber/v2/middleware/limiter"
	"github.com/gofiber/contrib/websocket"
	"github.com/gofiber/fiber/v2/middleware/cors"
	"github.com/gofiber/fiber/v2/middleware/logger"
	"github.com/gofiber/swagger"
	"github.com/jmoiron/sqlx"
	_ "github.com/lib/pq"

	authhandler "github.com/xilo-platform/xilo/internal/auth/handler"
	authmw "github.com/xilo-platform/xilo/internal/auth/middleware"
	authrepo "github.com/xilo-platform/xilo/internal/auth/repository"
	authsvc "github.com/xilo-platform/xilo/internal/auth/service"

	analyticshandler "github.com/xilo-platform/xilo/internal/analytics/handler"

	billinghandler "github.com/xilo-platform/xilo/internal/billing/handler"

	commenthandler "github.com/xilo-platform/xilo/internal/comment/handler"
	commentrepo "github.com/xilo-platform/xilo/internal/comment/repository"
	commentsvc "github.com/xilo-platform/xilo/internal/comment/service"

	mediahandler "github.com/xilo-platform/xilo/internal/media/handler"
	mediarepo "github.com/xilo-platform/xilo/internal/media/repository"
	mediasvc "github.com/xilo-platform/xilo/internal/media/service"

	notifhandler "github.com/xilo-platform/xilo/internal/notification/handler"
	notifrepo "github.com/xilo-platform/xilo/internal/notification/repository"

	posthandler "github.com/xilo-platform/xilo/internal/post/handler"
	postrepo "github.com/xilo-platform/xilo/internal/post/repository"
	postsvc "github.com/xilo-platform/xilo/internal/post/service"

	searchhandler "github.com/xilo-platform/xilo/internal/search/handler"
	searchrepo "github.com/xilo-platform/xilo/internal/search/repository"
	searchsvc "github.com/xilo-platform/xilo/internal/search/service"

	userhandler "github.com/xilo-platform/xilo/internal/user/handler"

	"github.com/xilo-platform/xilo/pkg/jwt"
	"github.com/xilo-platform/xilo/pkg/payment/zarinpal"
	pkgredis "github.com/xilo-platform/xilo/pkg/redis"
	smsfactory "github.com/xilo-platform/xilo/pkg/sms/factory"
	storagefactory "github.com/xilo-platform/xilo/pkg/storage/factory"
	wshub "github.com/xilo-platform/xilo/pkg/websocket"
	"github.com/xilo-platform/xilo/pkg/i18n"

	_ "github.com/xilo-platform/xilo/docs"
)

func main() {
	db := connectDB()
	rdb := connectRedis()

	jwtMgr := initJWT()

	storageDriver, err := storagefactory.New()
	if err != nil {
		slog.Error("failed to init storage driver", "error", err)
		os.Exit(1)
	}

	smsDriver, err := smsfactory.New()
	if err != nil {
		slog.Warn("sms driver not initialized, sms features disabled", "error", err)
	}

	searchRepo, err := searchrepo.NewSearchRepo(
		requireEnvOr("MEILISEARCH_URL", "http://localhost:7700"),
		requireEnvOr("MEILISEARCH_KEY", "xilo-meili-key"),
	)
	if err != nil {
		slog.Error("failed to init search repo", "error", err)
		os.Exit(1)
	}

	hub := wshub.NewHub(rdb.Client, jwtMgr)
	go hub.ListenRedis("post:*")
	go hub.ListenRedis("user:*")

	authRepo := authrepo.NewUserRepo(db)
	authSvc := authsvc.NewAuthServiceWithSMS(authRepo, jwtMgr, smsDriver)
	authH := authhandler.NewAuthHandler(authSvc)

	postRepo := postrepo.NewPostRepo(db)
	postSvc := postsvc.NewPostService(postRepo, rdb)
	postH := posthandler.NewPostHandler(postSvc)

	commentRepo := commentrepo.NewCommentRepo(db)
	commentSvc := commentsvc.NewCommentService(commentRepo)
	commentH := commenthandler.NewCommentHandler(commentSvc)

	mediaRepo := mediarepo.NewMediaRepo(db)
	mediaSvc := mediasvc.NewMediaService(mediaRepo, storageDriver)
	mediaH := mediahandler.NewMediaHandler(mediaSvc)

	searchSvc := searchsvc.NewSearchService(searchRepo)
	searchH := searchhandler.NewSearchHandler(searchSvc)

	notifRepo := notifrepo.NewNotificationRepo(db)
	notifH := notifhandler.NewNotificationHandler(notifRepo)
	prefsH := notifhandler.NewNotificationPrefsHandler(db)
	smsH := notifhandler.NewSMSNotificationHandler(db, smsDriver)

	socialH := userhandler.NewSocialHandler(db)
	analyticsDH := analyticshandler.NewDashboardHandler(db)
	adH := analyticshandler.NewAdHandler(db)
	donationH := analyticshandler.NewDonationHandler(db)
	analyticsH := analyticshandler.NewAnalyticsHandler(db)

	zarinpalDriver := zarinpal.NewDriver(
		env("ZARINPAL_MERCHANT_ID", ""),
		env("ZARINPAL_SANDBOX", "true") == "true",
	)
	baseURL := env("BASE_URL", "http://localhost:3000")

	billingH := billinghandler.NewBillingHandler(db, zarinpalDriver, baseURL)
	paymentH := billinghandler.NewPaymentHandler(db, zarinpalDriver, baseURL)
	settingsH := billinghandler.NewSettingsHandler(db)

	app := fiber.New(fiber.Config{
		ReadTimeout:  10 * time.Second,
		WriteTimeout: 30 * time.Second,
		IdleTimeout:  60 * time.Second,
	})

	app.Use(logger.New())
	app.Use(cors.New(cors.Config{
		AllowOrigins: "http://localhost:3000",
		AllowHeaders: "Origin, Content-Type, Accept, Authorization",
	}))

	app.Get("/api/languages", func(c *fiber.Ctx) error {
		return c.JSON(fiber.Map{
			"languages": i18n.ListLanguages(),
			"default":   i18n.DefaultLanguage,
		})
	})

	applyPublicRateLimit := limiter.New(limiter.Config{Max: 30, Expiration: 1 * time.Minute, KeyGenerator: func(c *fiber.Ctx) string { return c.IP() }})
	applyAuthRateLimit := limiter.New(limiter.Config{Max: 5, Expiration: 1 * time.Minute, KeyGenerator: func(c *fiber.Ctx) string { return c.IP() }})

	auth := app.Group("/api/auth")
	auth.Post("/register", applyAuthRateLimit, authH.Register)
	auth.Post("/login", applyAuthRateLimit, authH.Login)
	auth.Post("/refresh", applyPublicRateLimit, authH.Refresh)
	auth.Post("/logout", authmw.AuthRequired(jwtMgr), authH.Logout)
	auth.Get("/me", authmw.AuthRequired(jwtMgr), authH.Me)
	auth.Patch("/me", authmw.AuthRequired(jwtMgr), authH.UpdateProfile)
	auth.Post("/avatar", authmw.AuthRequired(jwtMgr), mediaH.UploadAvatar)
	auth.Post("/otp/request", applyAuthRateLimit, authH.RequestOTP)
	auth.Post("/otp/verify-login", applyAuthRateLimit, authH.VerifyOTPLogin)
	auth.Post("/otp/verify-register", applyAuthRateLimit, authH.VerifyOTPRegister)

	posts := app.Group("/api/posts")
	posts.Get("/", applyPublicRateLimit, postH.List)
	posts.Get("/:slug", postH.GetBySlug)
	posts.Post("/", authmw.AuthRequired(jwtMgr), authmw.RoleRequired("author", "editor", "admin"), postH.Create)
	posts.Patch("/:id", authmw.AuthRequired(jwtMgr), postH.Update)
	posts.Delete("/:id", authmw.AuthRequired(jwtMgr), postH.Delete)

	comments := app.Group("/api/posts/:postId/comments")
	comments.Get("/", commentH.List)
	comments.Post("/", authmw.AuthRequired(jwtMgr), commentH.Create)
	comments.Patch("/:id", authmw.AuthRequired(jwtMgr), commentH.Update)
	comments.Delete("/:id", authmw.AuthRequired(jwtMgr), commentH.Delete)

	react := app.Group("/api")
	react.Post("/:type/:id/reactions", authmw.AuthRequired(jwtMgr), commentH.ToggleReaction)
	react.Post("/comments/:id/pin", authmw.AuthRequired(jwtMgr), commentH.Pin)

	media := app.Group("/api/media")
	media.Post("/upload", authmw.AuthRequired(jwtMgr), authmw.RoleRequired("author", "editor", "admin"), mediaH.Upload)
	media.Get("/", authmw.AuthRequired(jwtMgr), mediaH.List)
	media.Get("/:id", authmw.AuthRequired(jwtMgr), mediaH.Get)
	media.Delete("/:id", authmw.AuthRequired(jwtMgr), mediaH.Delete)

	search := app.Group("/api/search")
	search.Get("/posts", applyPublicRateLimit, searchH.SearchPosts)
	search.Get("/suggest", applyPublicRateLimit, searchH.Suggest)

	notif := app.Group("/api/notifications")
	notif.Get("/", authmw.AuthRequired(jwtMgr), notifH.List)
	notif.Post("/:id/read", authmw.AuthRequired(jwtMgr), notifH.MarkRead)
	notif.Post("/read-all", authmw.AuthRequired(jwtMgr), notifH.MarkAllRead)
	notif.Get("/preferences", authmw.AuthRequired(jwtMgr), prefsH.GetPreferences)
	notif.Patch("/preferences", authmw.AuthRequired(jwtMgr), prefsH.UpdatePreferences)
	notif.Post("/sms/send", authmw.AuthRequired(jwtMgr), authmw.RoleRequired("admin", "superadmin"), smsH.SendSMS)
	notif.Post("/sms/broadcast", authmw.AuthRequired(jwtMgr), authmw.RoleRequired("admin", "superadmin"), smsH.BroadcastToAll)

	social := app.Group("/api")
	social.Post("/posts/:id/bookmark", authmw.AuthRequired(jwtMgr), socialH.ToggleBookmark)
	social.Delete("/posts/:id/bookmark", authmw.AuthRequired(jwtMgr), socialH.ToggleBookmark)
	social.Get("/bookmarks", authmw.AuthRequired(jwtMgr), socialH.ListBookmarks)
	social.Post("/users/:username/follow", authmw.AuthRequired(jwtMgr), socialH.ToggleFollow)
	social.Delete("/users/:username/follow", authmw.AuthRequired(jwtMgr), socialH.ToggleFollow)

	billing := app.Group("/api/billing")
	billing.Get("/plans", billingH.ListPlans)
	billing.Get("/plans/:slug", billingH.GetPlan)
	billing.Post("/plans", authmw.AuthRequired(jwtMgr), authmw.RoleRequired("admin", "superadmin"), billingH.CreatePlan)
	billing.Patch("/plans/:id", authmw.AuthRequired(jwtMgr), authmw.RoleRequired("admin", "superadmin"), billingH.UpdatePlan)
	billing.Post("/subscribe", authmw.AuthRequired(jwtMgr), paymentH.Subscribe)
	billing.Get("/callback", paymentH.Callback)
	billing.Get("/my-subscription", authmw.AuthRequired(jwtMgr), billingH.MySubscription)
	billing.Delete("/subscription", authmw.AuthRequired(jwtMgr), billingH.CancelSubscription)
	billing.Get("/invoices", authmw.AuthRequired(jwtMgr), billingH.MyInvoices)
	billing.Get("/settings", authmw.AuthRequired(jwtMgr), authmw.RoleRequired("admin", "superadmin"), settingsH.GetPaymentGatewayConfig)
	billing.Patch("/settings", authmw.AuthRequired(jwtMgr), authmw.RoleRequired("admin", "superadmin"), settingsH.UpdatePaymentGatewayConfig)

	analytics := app.Group("/api/analytics")
	analytics.Post("/events", analyticsH.Ingest)
	analytics.Get("/author-dashboard", authmw.AuthRequired(jwtMgr), analyticsDH.AuthorDashboard)
	analytics.Get("/admin-dashboard", authmw.AuthRequired(jwtMgr), authmw.RoleRequired("admin", "superadmin"), analyticsDH.AdminDashboard)

	ads := app.Group("/api/ads")
	ads.Get("/", adH.ListAds)
	ads.Post("/", authmw.AuthRequired(jwtMgr), authmw.RoleRequired("admin", "superadmin"), adH.CreateAd)
	ads.Patch("/:id", authmw.AuthRequired(jwtMgr), authmw.RoleRequired("admin", "superadmin"), adH.UpdateAd)
	ads.Get("/serve", adH.ServeAd)
	ads.Post("/:id/click", adH.TrackClick)

	donations := app.Group("/api/donations")
	donations.Post("/wallet", authmw.AuthRequired(jwtMgr), donationH.SetWalletAddress)
	donations.Get("/wallet/:username", donationH.GetWalletAddresses)
	donations.Post("/record", donationH.RecordDonation)

	app.Get("/ws", websocket.New(func(c *websocket.Conn) {
		hub.HandleWebSocket(c)
	}))

	app.Get("/swagger/*", swagger.HandlerDefault)

	app.Get("/health", func(c *fiber.Ctx) error {
		healthy := true
		checks := map[string]string{}

		if err := db.PingContext(c.UserContext()); err != nil {
			healthy = false
			checks["database"] = "unhealthy"
		} else {
			checks["database"] = "ok"
		}

		ctx, cancel := context.WithTimeout(c.UserContext(), 1*time.Second)
		defer cancel()
		if err := rdb.Ping(ctx).Err(); err != nil {
			checks["redis"] = "unhealthy"
		} else {
			checks["redis"] = "ok"
		}

		if !healthy {
			return c.Status(fiber.StatusServiceUnavailable).JSON(fiber.Map{
				"status": "degraded",
				"checks": checks,
			})
		}

		return c.JSON(fiber.Map{"status": "ok", "checks": checks})
	})

	port := requireEnvOr("PORT", "8000")

	go func() {
		sigCh := make(chan os.Signal, 1)
		signal.Notify(sigCh, syscall.SIGINT, syscall.SIGTERM)
		<-sigCh
		slog.Info("shutting down...")
		ctx, cancel := context.WithTimeout(context.Background(), 10*time.Second)
		defer cancel()
		if err := app.ShutdownWithContext(ctx); err != nil {
			slog.Error("shutdown error", "error", err)
		}
	}()

	slog.Info("api-gateway starting", "port", port)
	if err := app.Listen(":" + port); err != nil {
		slog.Error("server failed", "error", err)
		os.Exit(1)
	}
}

func initJWT() *jwt.Manager {
	privateKey := os.Getenv("JWT_PRIVATE_KEY_PATH")
	publicKey := os.Getenv("JWT_PUBLIC_KEY_PATH")
	if privateKey == "" {
		privateKey = "private.pem"
	}
	if publicKey == "" {
		publicKey = "public.pem"
	}

	mgr, err := jwt.NewManager(
		privateKey,
		publicKey,
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
	dsn := requireEnvOr("DATABASE_URL", "postgres://xilo:xilo@localhost:5432/xilo?sslmode=disable")
	db, err := sqlx.Connect("postgres", dsn)
	if err != nil {
		slog.Error("db connect failed", "error", err)
		os.Exit(1)
	}
	db.SetMaxOpenConns(25)
	db.SetMaxIdleConns(5)
	db.SetConnMaxLifetime(30 * time.Minute)
	db.SetConnMaxIdleTime(5 * time.Minute)
	return db
}

func connectRedis() *pkgredis.Client {
	addr := requireEnvOr("REDIS_URL", "localhost:6379")
	rdb, err := pkgredis.NewClient(addr, "", 0)
	if err != nil {
		slog.Error("redis connect failed", "error", err)
		os.Exit(1)
	}
	return rdb
}

func requireEnvOr(key, fallback string) string {
	v := os.Getenv(key)
	if v != "" {
		return v
	}
	if fallback != "" {
		return fallback
	}
	slog.Error("required environment variable not set", "key", key)
	os.Exit(1)
	return fallback
}

func env(key, fallback string) string {
	if v := os.Getenv(key); v != "" {
		return v
	}
	return fallback
}
