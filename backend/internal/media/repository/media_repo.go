package repository

import (
	"context"
	"fmt"

	"github.com/jmoiron/sqlx"
	"github.com/xilo-platform/xilo/internal/media/model"
)

type MediaRepo struct {
	db *sqlx.DB
}

func NewMediaRepo(db *sqlx.DB) *MediaRepo {
	return &MediaRepo{db: db}
}

func (r *MediaRepo) Create(ctx context.Context, media *model.Media) error {
	_, err := r.db.ExecContext(ctx, `
		INSERT INTO media (id, user_id, filename, original_name, mime_type, size_bytes, width, height, variants)
		VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9)
	`, media.ID, media.UserID, media.Filename, media.OriginalName,
		media.MimeType, media.SizeBytes, media.Width, media.Height, media.Variants)
	if err != nil {
		return fmt.Errorf("insert media: %w", err)
	}
	return nil
}

func (r *MediaRepo) GetByID(ctx context.Context, id string) (*model.Media, error) {
	var media model.Media
	err := r.db.GetContext(ctx, &media, `
		SELECT id, user_id, filename, original_name, mime_type, size_bytes, width, height, variants, created_at
		FROM media WHERE id = $1
	`, id)
	if err != nil {
		return nil, fmt.Errorf("get media: %w", err)
	}
	return &media, nil
}

func (r *MediaRepo) ListByUser(ctx context.Context, userID string, cursor string, limit int) ([]*model.Media, string, error) {
	if limit == 0 {
		limit = 20
	}

	query := `
		SELECT id, user_id, filename, original_name, mime_type, size_bytes, width, height, variants, created_at
		FROM media
		WHERE user_id = $1
	`
	args := []interface{}{userID}

	if cursor != "" {
		query += ` AND created_at < (SELECT created_at FROM media WHERE id = $` + fmt.Sprint(len(args)+1) + `)`
		args = append(args, cursor)
	}

	query += ` ORDER BY created_at DESC LIMIT $` + fmt.Sprint(len(args)+1)
	args = append(args, limit+1)

	var items []*model.Media
	err := r.db.SelectContext(ctx, &items, query, args...)
	if err != nil {
		return nil, "", fmt.Errorf("list media: %w", err)
	}

	var nextCursor string
	if len(items) > limit {
		items = items[:limit]
		nextCursor = items[len(items)-1].ID
	}

	return items, nextCursor, nil
}

func (r *MediaRepo) Delete(ctx context.Context, id string) error {
	_, err := r.db.ExecContext(ctx, `DELETE FROM media WHERE id = $1`, id)
	return err
}
