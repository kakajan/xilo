package repository

import (
	"context"
	"database/sql"
	"encoding/json"
	"errors"
	"fmt"
	"time"

	"github.com/jmoiron/sqlx"
)

// JSONObject scans PostgreSQL JSON/JSONB (bytes or string) into json.RawMessage.
type JSONObject json.RawMessage

func (j JSONObject) MarshalJSON() ([]byte, error) {
	if len(j) == 0 {
		return []byte("{}"), nil
	}
	return []byte(j), nil
}

func (j *JSONObject) UnmarshalJSON(b []byte) error {
	if j == nil {
		return errors.New("JSONObject: UnmarshalJSON on nil pointer")
	}
	*j = append((*j)[0:0], b...)
	return nil
}

func (j *JSONObject) Scan(src any) error {
	if src == nil {
		*j = JSONObject([]byte("{}"))
		return nil
	}
	switch v := src.(type) {
	case []byte:
		*j = append(JSONObject(nil), v...)
	case string:
		*j = JSONObject(v)
	default:
		return fmt.Errorf("JSONObject: cannot scan %T", src)
	}
	if len(*j) == 0 {
		*j = JSONObject([]byte("{}"))
	}
	return nil
}

type Notification struct {
	ID        string     `json:"id" db:"id"`
	UserID    string     `json:"user_id" db:"user_id"`
	Type      string     `json:"type" db:"type"`
	Title     string     `json:"title" db:"title"`
	Body      string     `json:"body" db:"body"`
	Data      JSONObject `json:"data" db:"data"`
	IsRead    bool       `json:"is_read" db:"is_read"`
	CreatedAt time.Time  `json:"created_at" db:"created_at"`
}

type NotificationRepo struct {
	db *sqlx.DB
}

func NewNotificationRepo(db *sqlx.DB) *NotificationRepo {
	return &NotificationRepo{db: db}
}

func (r *NotificationRepo) Create(ctx context.Context, userID, notifType, title, body, data string) (*Notification, error) {
	var n Notification
	err := r.db.GetContext(ctx, &n, `
		INSERT INTO notifications (user_id, type, title, body, data)
		VALUES ($1, $2, $3, $4, $5)
		RETURNING id, user_id, type, title, body, data, is_read, created_at
	`, userID, notifType, title, body, data)
	if err != nil {
		return nil, fmt.Errorf("create notification: %w", err)
	}
	return &n, nil
}

func (r *NotificationRepo) List(ctx context.Context, userID string, limit int) ([]*Notification, error) {
	if limit == 0 {
		limit = 20
	}
	var items []*Notification
	err := r.db.SelectContext(ctx, &items, `
		SELECT id, user_id, type, title, body, data, is_read, created_at
		FROM notifications
		WHERE user_id = $1
		ORDER BY created_at DESC
		LIMIT $2
	`, userID, limit)
	if err != nil {
		return nil, fmt.Errorf("list notifications: %w", err)
	}
	return items, nil
}

func (r *NotificationRepo) MarkRead(ctx context.Context, id, userID string) error {
	_, err := r.db.ExecContext(ctx, `
		UPDATE notifications SET is_read = TRUE WHERE id = $1 AND user_id = $2
	`, id, userID)
	return err
}

// MarkReadReturningWasUnread marks a notification read and reports whether it was unread.
func (r *NotificationRepo) MarkReadReturningWasUnread(ctx context.Context, id, userID string) (bool, error) {
	var wasUnread bool
	err := r.db.QueryRowContext(ctx, `
		UPDATE notifications
		SET is_read = TRUE
		WHERE id = $1 AND user_id = $2 AND NOT is_read
		RETURNING TRUE
	`, id, userID).Scan(&wasUnread)
	if err != nil {
		if errors.Is(err, sql.ErrNoRows) {
			_ = r.MarkRead(ctx, id, userID)
			return false, nil
		}
		return false, err
	}
	return wasUnread, nil
}

func (r *NotificationRepo) MarkAllRead(ctx context.Context, userID string) error {
	_, err := r.db.ExecContext(ctx, `
		UPDATE notifications SET is_read = TRUE WHERE user_id = $1
	`, userID)
	return err
}

func (r *NotificationRepo) UnreadCount(ctx context.Context, userID string) (int, error) {
	var count int
	err := r.db.GetContext(ctx, &count, `
		SELECT COUNT(*) FROM notifications WHERE user_id = $1 AND NOT is_read
	`, userID)
	return count, err
}
