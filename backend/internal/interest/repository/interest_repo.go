package repository

import (
	"context"
	"database/sql"
	"errors"
	"fmt"
	"strings"

	"github.com/jmoiron/sqlx"
	"github.com/lib/pq"
	"github.com/xilo-platform/xilo/internal/interest/model"
)

type InterestRepo struct {
	db *sqlx.DB
}

func NewInterestRepo(db *sqlx.DB) *InterestRepo {
	return &InterestRepo{db: db}
}

func (r *InterestRepo) ListActive(ctx context.Context) ([]model.Interest, error) {
	var items []model.Interest
	err := r.db.SelectContext(ctx, &items, `
		SELECT id, slug, labels, icon, sort_order, is_active, created_at, updated_at
		FROM interests
		WHERE is_active = TRUE
		ORDER BY sort_order ASC, slug ASC
	`)
	if err != nil {
		return nil, fmt.Errorf("list active interests: %w", err)
	}
	if items == nil {
		items = []model.Interest{}
	}
	return items, nil
}

func (r *InterestRepo) ListAll(ctx context.Context) ([]model.Interest, error) {
	var items []model.Interest
	err := r.db.SelectContext(ctx, &items, `
		SELECT id, slug, labels, icon, sort_order, is_active, created_at, updated_at
		FROM interests
		ORDER BY sort_order ASC, slug ASC
	`)
	if err != nil {
		return nil, fmt.Errorf("list all interests: %w", err)
	}
	if items == nil {
		items = []model.Interest{}
	}
	return items, nil
}

func (r *InterestRepo) GetByID(ctx context.Context, id string) (*model.Interest, error) {
	var item model.Interest
	err := r.db.GetContext(ctx, &item, `
		SELECT id, slug, labels, icon, sort_order, is_active, created_at, updated_at
		FROM interests
		WHERE id = $1
	`, id)
	if err != nil {
		if errors.Is(err, sql.ErrNoRows) {
			return nil, model.ErrNotFound
		}
		return nil, fmt.Errorf("get interest: %w", err)
	}
	return &item, nil
}

func (r *InterestRepo) Create(ctx context.Context, slug string, labels model.Labels, icon *string, sortOrder int, isActive bool) (*model.Interest, error) {
	var item model.Interest
	err := r.db.GetContext(ctx, &item, `
		INSERT INTO interests (slug, labels, icon, sort_order, is_active)
		VALUES ($1, $2, $3, $4, $5)
		RETURNING id, slug, labels, icon, sort_order, is_active, created_at, updated_at
	`, slug, labels, icon, sortOrder, isActive)
	if err != nil {
		if isUniqueViolation(err) {
			return nil, model.ErrConflict
		}
		return nil, fmt.Errorf("create interest: %w", err)
	}
	return &item, nil
}

func (r *InterestRepo) Patch(ctx context.Context, id string, req model.PatchInterestRequest) (*model.Interest, error) {
	existing, err := r.GetByID(ctx, id)
	if err != nil {
		return nil, err
	}

	slug := existing.Slug
	if req.Slug != nil {
		slug = strings.TrimSpace(*req.Slug)
	}
	labels := existing.Labels
	if req.Labels != nil {
		labels = req.Labels
	}
	icon := existing.Icon
	if req.Icon != nil {
		trimmed := strings.TrimSpace(*req.Icon)
		if trimmed == "" {
			icon = nil
		} else {
			icon = &trimmed
		}
	}
	sortOrder := existing.SortOrder
	if req.SortOrder != nil {
		sortOrder = *req.SortOrder
	}
	isActive := existing.IsActive
	if req.IsActive != nil {
		isActive = *req.IsActive
	}

	var item model.Interest
	err = r.db.GetContext(ctx, &item, `
		UPDATE interests
		SET slug = $2,
		    labels = $3,
		    icon = $4,
		    sort_order = $5,
		    is_active = $6,
		    updated_at = NOW()
		WHERE id = $1
		RETURNING id, slug, labels, icon, sort_order, is_active, created_at, updated_at
	`, id, slug, labels, icon, sortOrder, isActive)
	if err != nil {
		if errors.Is(err, sql.ErrNoRows) {
			return nil, model.ErrNotFound
		}
		if isUniqueViolation(err) {
			return nil, model.ErrConflict
		}
		return nil, fmt.Errorf("patch interest: %w", err)
	}
	return &item, nil
}

// SoftDeactivate sets is_active=false (preferred over hard delete).
func (r *InterestRepo) SoftDeactivate(ctx context.Context, id string) (*model.Interest, error) {
	var item model.Interest
	err := r.db.GetContext(ctx, &item, `
		UPDATE interests
		SET is_active = FALSE, updated_at = NOW()
		WHERE id = $1
		RETURNING id, slug, labels, icon, sort_order, is_active, created_at, updated_at
	`, id)
	if err != nil {
		if errors.Is(err, sql.ErrNoRows) {
			return nil, model.ErrNotFound
		}
		return nil, fmt.Errorf("deactivate interest: %w", err)
	}
	return &item, nil
}

// Reorder updates sort_order for each id to its index in orderedIDs.
func (r *InterestRepo) Reorder(ctx context.Context, orders map[string]int) error {
	tx, err := r.db.BeginTxx(ctx, nil)
	if err != nil {
		return fmt.Errorf("begin reorder tx: %w", err)
	}
	defer func() { _ = tx.Rollback() }()

	for id, sortOrder := range orders {
		res, err := tx.ExecContext(ctx, `
			UPDATE interests
			SET sort_order = $2, updated_at = NOW()
			WHERE id = $1
		`, id, sortOrder)
		if err != nil {
			return fmt.Errorf("reorder interest %s: %w", id, err)
		}
		n, err := res.RowsAffected()
		if err != nil {
			return fmt.Errorf("reorder rows affected: %w", err)
		}
		if n == 0 {
			return model.ErrNotFound
		}
	}

	if err := tx.Commit(); err != nil {
		return fmt.Errorf("commit reorder: %w", err)
	}
	return nil
}

func (r *InterestRepo) CountActiveByIDs(ctx context.Context, ids []string) (int, error) {
	if len(ids) == 0 {
		return 0, nil
	}
	var count int
	query, args, err := sqlx.In(`
		SELECT COUNT(*) FROM interests
		WHERE is_active = TRUE AND id IN (?)
	`, ids)
	if err != nil {
		return 0, fmt.Errorf("count active interests: %w", err)
	}
	query = r.db.Rebind(query)
	if err := r.db.GetContext(ctx, &count, query, args...); err != nil {
		return 0, fmt.Errorf("count active interests: %w", err)
	}
	return count, nil
}

func (r *InterestRepo) ListByIDs(ctx context.Context, ids []string) ([]model.Interest, error) {
	if len(ids) == 0 {
		return []model.Interest{}, nil
	}
	query, args, err := sqlx.In(`
		SELECT id, slug, labels, icon, sort_order, is_active, created_at, updated_at
		FROM interests
		WHERE id IN (?)
		ORDER BY sort_order ASC, slug ASC
	`, ids)
	if err != nil {
		return nil, fmt.Errorf("list interests by ids: %w", err)
	}
	query = r.db.Rebind(query)
	var items []model.Interest
	if err := r.db.SelectContext(ctx, &items, query, args...); err != nil {
		return nil, fmt.Errorf("list interests by ids: %w", err)
	}
	if items == nil {
		items = []model.Interest{}
	}
	return items, nil
}

func (r *InterestRepo) GetUserInterestIDs(ctx context.Context, userID string) ([]string, error) {
	var ids []string
	err := r.db.SelectContext(ctx, &ids, `
		SELECT ui.interest_id
		FROM user_interests ui
		JOIN interests i ON i.id = ui.interest_id
		WHERE ui.user_id = $1 AND i.is_active = TRUE
		ORDER BY i.sort_order ASC, i.slug ASC
	`, userID)
	if err != nil {
		return nil, fmt.Errorf("get user interest ids: %w", err)
	}
	if ids == nil {
		ids = []string{}
	}
	return ids, nil
}

func (r *InterestRepo) GetUserInterests(ctx context.Context, userID string) ([]model.Interest, error) {
	var items []model.Interest
	err := r.db.SelectContext(ctx, &items, `
		SELECT i.id, i.slug, i.labels, i.icon, i.sort_order, i.is_active, i.created_at, i.updated_at
		FROM user_interests ui
		JOIN interests i ON i.id = ui.interest_id
		WHERE ui.user_id = $1 AND i.is_active = TRUE
		ORDER BY i.sort_order ASC, i.slug ASC
	`, userID)
	if err != nil {
		return nil, fmt.Errorf("get user interests: %w", err)
	}
	if items == nil {
		items = []model.Interest{}
	}
	return items, nil
}

func (r *InterestRepo) ReplaceUserInterests(ctx context.Context, userID string, interestIDs []string) error {
	tx, err := r.db.BeginTxx(ctx, nil)
	if err != nil {
		return fmt.Errorf("begin replace user interests: %w", err)
	}
	defer func() { _ = tx.Rollback() }()

	if _, err := tx.ExecContext(ctx, `
		DELETE FROM user_interests WHERE user_id = $1
	`, userID); err != nil {
		return fmt.Errorf("clear user interests: %w", err)
	}

	for _, interestID := range interestIDs {
		if _, err := tx.ExecContext(ctx, `
			INSERT INTO user_interests (user_id, interest_id)
			VALUES ($1, $2)
		`, userID, interestID); err != nil {
			return fmt.Errorf("insert user interest: %w", err)
		}
	}

	if err := tx.Commit(); err != nil {
		return fmt.Errorf("commit replace user interests: %w", err)
	}
	return nil
}

func isUniqueViolation(err error) bool {
	var pqErr *pq.Error
	if errors.As(err, &pqErr) {
		return pqErr.Code == "23505"
	}
	return false
}
