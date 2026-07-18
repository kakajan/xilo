package handler

import (
	"github.com/gofiber/fiber/v2"
	"github.com/jmoiron/sqlx"
)

type AnalyticsDashboardHandler struct {
	db *sqlx.DB
}

func NewDashboardHandler(db *sqlx.DB) *AnalyticsDashboardHandler {
	return &AnalyticsDashboardHandler{db: db}
}

// @Summary      Get author analytics dashboard
// @Tags         analytics
// @Produce      json
// @Security     BearerAuth
// @Param        days query int false "Days to look back" default(30)
// @Success      200  {object}  map[string]interface{}
// @Router       /analytics/author-dashboard [get]
func (h *AnalyticsDashboardHandler) AuthorDashboard(c *fiber.Ctx) error {
	userID := c.Locals("userID").(string)
	days := c.QueryInt("days", 30)

	var totalViews int64
	h.db.Get(&totalViews, `
		SELECT COUNT(*) FROM analytics_events
		WHERE event_type = 'post_view' AND properties->>'author_id' = $1
		AND created_at > NOW() - INTERVAL '1 day' * $2
	`, userID, days)

	var totalReads int64
	h.db.Get(&totalReads, `
		SELECT COUNT(*) FROM analytics_events
		WHERE event_type = 'post_read' AND properties->>'author_id' = $1
		AND created_at > NOW() - INTERVAL '1 day' * $2
	`, userID, days)

	type DailyStat struct {
		Day   string `db:"day" json:"day"`
		Views int64  `db:"views" json:"views"`
		Reads int64  `db:"reads" json:"reads"`
	}
	dailyStats := make([]DailyStat, 0)
	h.db.Select(&dailyStats, `
		SELECT
			TO_CHAR(created_at, 'YYYY-MM-DD') as day,
			COUNT(*) FILTER (WHERE event_type = 'post_view') as views,
			COUNT(*) FILTER (WHERE event_type = 'post_read') as reads
		FROM analytics_events
		WHERE properties->>'author_id' = $1
		AND created_at > NOW() - INTERVAL '1 day' * $2
		GROUP BY day
		ORDER BY day ASC
	`, userID, days)

	type TopPost struct {
		ID    string `db:"post_id" json:"post_id"`
		Title string `db:"title" json:"title"`
		Views int64  `db:"views" json:"views"`
	}
	topPosts := make([]TopPost, 0)
	h.db.Select(&topPosts, `
		SELECT
			properties->>'post_id' as post_id,
			COUNT(*) as views
		FROM analytics_events
		WHERE event_type = 'post_view'
		AND properties->>'author_id' = $1
		AND created_at > NOW() - INTERVAL '1 day' * $2
		GROUP BY post_id
		ORDER BY views DESC
		LIMIT 10
	`, userID, days)

	return c.JSON(fiber.Map{
		"total_views":  totalViews,
		"total_reads":  totalReads,
		"daily_stats":  dailyStats,
		"top_posts":    topPosts,
	})
}

// @Summary      Get admin analytics dashboard
// @Tags         analytics
// @Produce      json
// @Security     BearerAuth
// @Param        days query int false "Days to look back" default(30)
// @Success      200  {object}  map[string]interface{}
// @Router       /analytics/admin-dashboard [get]
func (h *AnalyticsDashboardHandler) AdminDashboard(c *fiber.Ctx) error {
	days := c.QueryInt("days", 30)

	var dau, wau, mau int64
	h.db.Get(&dau, `SELECT COUNT(DISTINCT user_id) FROM analytics_events WHERE created_at > NOW() - INTERVAL '1 day'`)
	h.db.Get(&wau, `SELECT COUNT(DISTINCT user_id) FROM analytics_events WHERE created_at > NOW() - INTERVAL '7 days'`)
	h.db.Get(&mau, `SELECT COUNT(DISTINCT user_id) FROM analytics_events WHERE created_at > NOW() - INTERVAL '30 days'`)

	type DailyActive struct {
		Day   string `db:"day" json:"day"`
		Users int64  `db:"users" json:"users"`
	}
	active := make([]DailyActive, 0)
	h.db.Select(&active, `
		SELECT TO_CHAR(created_at, 'YYYY-MM-DD') as day, COUNT(DISTINCT user_id) as users
		FROM analytics_events
		WHERE created_at > NOW() - INTERVAL '1 day' * $1
		GROUP BY day ORDER BY day ASC
	`, days)

	var totalPosts int64
	h.db.Get(&totalPosts, `SELECT COUNT(*) FROM posts WHERE deleted_at IS NULL`)

	var totalUsers int64
	h.db.Get(&totalUsers, `SELECT COUNT(*) FROM users WHERE deleted_at IS NULL`)

	var totalComments int64
	h.db.Get(&totalComments, `SELECT COUNT(*) FROM comments WHERE deleted_at IS NULL`)

	var revenue int64
	h.db.Get(&revenue, `SELECT COALESCE(SUM(amount_cents), 0) FROM invoices WHERE status = 'paid'`)

	return c.JSON(fiber.Map{
		"dau":            dau,
		"wau":            wau,
		"mau":            mau,
		"daily_active":   active,
		"total_posts":    totalPosts,
		"total_users":    totalUsers,
		"total_comments": totalComments,
		"revenue_cents":  revenue,
	})
}
