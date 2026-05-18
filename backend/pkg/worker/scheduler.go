package worker

import (
	"context"
	"log/slog"
	"time"

	"github.com/google/uuid"
	"github.com/jmoiron/sqlx"
	"github.com/redis/go-redis/v9"
)

type Scheduler struct {
	db  *sqlx.DB
	rdb *redis.Client
}

func NewScheduler(db *sqlx.DB, rdb *redis.Client) *Scheduler {
	return &Scheduler{db: db, rdb: rdb}
}

func (s *Scheduler) Run(ctx context.Context, interval time.Duration) {
	ticker := time.NewTicker(interval)
	defer ticker.Stop()

	slog.Info("scheduler started", "interval", interval)

	for {
		select {
		case <-ctx.Done():
			slog.Info("scheduler stopped")
			return
		case <-ticker.C:
			s.publishScheduledPosts(ctx)
		}
	}
}

func (s *Scheduler) publishScheduledPosts(ctx context.Context) {
	lockKey := "scheduler:publish_lock"
	lockValue := uuid.New().String()

	locked, err := s.rdb.SetNX(ctx, lockKey, lockValue, 30*time.Second).Result()
	if err != nil || !locked {
		return
	}

	defer s.rdb.Eval(ctx, `
		if redis.call("GET", KEYS[1]) == ARGV[1] then
			return redis.call("DEL", KEYS[1])
		end
		return 0
	`, []string{lockKey}, lockValue)

	type ScheduledPost struct {
		ID string `db:"id"`
	}

	var posts []ScheduledPost
	err = s.db.SelectContext(ctx, &posts, `
		SELECT id FROM posts
		WHERE status = 'scheduled'
		AND scheduled_at <= NOW()
		AND deleted_at IS NULL
		LIMIT 50
	`)
	if err != nil {
		slog.Error("scheduler: query failed", "error", err)
		return
	}

	for _, post := range posts {
		result, err := s.db.ExecContext(ctx, `
			UPDATE posts
			SET status = 'published', published_at = NOW(), updated_at = NOW()
			WHERE id = $1 AND status = 'scheduled'
		`, post.ID)
		if err != nil {
			slog.Error("scheduler: publish failed", "postId", post.ID, "error", err)
			continue
		}
		if n, _ := result.RowsAffected(); n > 0 {
			slog.Info("scheduler: post published", "postId", post.ID)
			s.invalidatePostCaches(ctx, post.ID)
		}
	}
}

func (s *Scheduler) invalidatePostCaches(ctx context.Context, postID string) {
	var keys []string
	iter := s.rdb.Scan(ctx, 0, "cache:/api/posts*", 0).Iterator()
	for iter.Next(ctx) {
		keys = append(keys, iter.Val())
	}
	if len(keys) > 0 {
		s.rdb.Del(ctx, keys...)
	}
}
