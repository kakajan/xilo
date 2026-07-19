package repository

import (
	"context"
	"database/sql"
	"encoding/json"
	"errors"
	"fmt"
	"strings"
	"time"

	"github.com/xilo-platform/xilo/internal/post/model"
)

const viewDedupWindow = 24 * time.Hour

// RecordView increments posts.view_count once per viewer_key within 24 hours.
// Authenticated viewers use user ID; anonymous viewers use session_id.
// Authors viewing their own posts are not counted.
func (r *PostRepo) RecordView(ctx context.Context, postID, userID, sessionID string) (*model.RecordViewResult, error) {
	viewerKey, err := viewerKey(userID, sessionID)
	if err != nil {
		return nil, err
	}

	tx, err := r.db.BeginTxx(ctx, nil)
	if err != nil {
		return nil, fmt.Errorf("begin view tx: %w", err)
	}
	defer func() { _ = tx.Rollback() }()

	var (
		authorID  string
		viewCount int64
		status    string
		slug      string
	)
	err = tx.QueryRowxContext(ctx, `
		SELECT author_id, view_count, status, slug
		FROM posts
		WHERE id = $1 AND deleted_at IS NULL
		FOR UPDATE
	`, postID).Scan(&authorID, &viewCount, &status, &slug)
	if err != nil {
		if errors.Is(err, sql.ErrNoRows) {
			return nil, ErrPostNotFound
		}
		return nil, fmt.Errorf("lock post for view: %w", err)
	}
	if status != "published" {
		return nil, ErrPostNotFound
	}

	// Authors should not inflate their own counters.
	if userID != "" && userID == authorID {
		return &model.RecordViewResult{
			Counted:   false,
			ViewCount: viewCount,
			Slug:      slug,
			AuthorID:  authorID,
		}, nil
	}

	var lastViewed time.Time
	err = tx.QueryRowxContext(ctx, `
		SELECT last_viewed_at
		FROM post_view_dedup
		WHERE post_id = $1 AND viewer_key = $2
		FOR UPDATE
	`, postID, viewerKey).Scan(&lastViewed)

	now := time.Now().UTC()
	shouldCount := false
	if errors.Is(err, sql.ErrNoRows) {
		shouldCount = true
	} else if err != nil {
		return nil, fmt.Errorf("load view dedup: %w", err)
	} else if now.Sub(lastViewed) >= viewDedupWindow {
		shouldCount = true
	}

	if !shouldCount {
		if err := tx.Commit(); err != nil {
			return nil, fmt.Errorf("commit view tx: %w", err)
		}
		return &model.RecordViewResult{
			Counted:   false,
			ViewCount: viewCount,
			Slug:      slug,
			AuthorID:  authorID,
		}, nil
	}

	var uid interface{}
	if userID != "" {
		uid = userID
	}

	_, err = tx.ExecContext(ctx, `
		INSERT INTO post_view_dedup (post_id, viewer_key, user_id, session_id, last_viewed_at)
		VALUES ($1, $2, $3, $4, $5)
		ON CONFLICT (post_id, viewer_key) DO UPDATE
		SET user_id = EXCLUDED.user_id,
		    session_id = EXCLUDED.session_id,
		    last_viewed_at = EXCLUDED.last_viewed_at
	`, postID, viewerKey, uid, sessionID, now)
	if err != nil {
		return nil, fmt.Errorf("upsert view dedup: %w", err)
	}

	err = tx.QueryRowxContext(ctx, `
		UPDATE posts
		SET view_count = view_count + 1
		WHERE id = $1
		RETURNING view_count
	`, postID).Scan(&viewCount)
	if err != nil {
		return nil, fmt.Errorf("increment view_count: %w", err)
	}

	props, _ := json.Marshal(map[string]string{
		"post_id":   postID,
		"author_id": authorID,
	})
	_, err = tx.ExecContext(ctx, `
		INSERT INTO analytics_events (event_type, user_id, session_id, properties)
		VALUES ('post_view', $1, $2, $3)
	`, uid, sessionID, props)
	if err != nil {
		return nil, fmt.Errorf("insert post_view analytics: %w", err)
	}

	if err := tx.Commit(); err != nil {
		return nil, fmt.Errorf("commit view tx: %w", err)
	}

	return &model.RecordViewResult{
		Counted:   true,
		ViewCount: viewCount,
		Slug:      slug,
		AuthorID:  authorID,
	}, nil
}

func viewerKey(userID, sessionID string) (string, error) {
	if userID != "" {
		return "u:" + userID, nil
	}
	sid := strings.TrimSpace(sessionID)
	if len(sid) < 16 {
		return "", model.ErrInvalidViewSession
	}
	return "s:" + sid, nil
}
