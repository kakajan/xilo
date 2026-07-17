package repository

import (
	"context"
	"database/sql"
	"errors"
	"fmt"
	"net/url"
	"strings"

	"github.com/google/uuid"
	"github.com/jmoiron/sqlx"
	"github.com/xilo-platform/xilo/internal/chat/model"
	"github.com/xilo-platform/xilo/pkg/storage"
)

const (
	maxImageBytes = int64(10 * 1024 * 1024)
	maxVideoBytes = int64(100 * 1024 * 1024)
	maxFileBytes  = int64(50 * 1024 * 1024)
)

type MediaValidator struct {
	db      *sqlx.DB
	storage storage.Driver
}

func NewMediaValidator(db *sqlx.DB, storageDriver storage.Driver) *MediaValidator {
	return &MediaValidator{db: db, storage: storageDriver}
}

func (v *MediaValidator) ValidateMedia(
	ctx context.Context,
	userID string,
	rawURL string,
	messageType string,
) (string, error) {
	if v.db == nil {
		return "", errors.New("media validator is not configured")
	}
	return v.validateMedia(ctx, v.db, userID, rawURL, messageType)
}

func (v *MediaValidator) ValidateMediaTx(
	ctx context.Context,
	tx *sqlx.Tx,
	userID string,
	rawURL string,
	messageType string,
) (string, error) {
	if tx == nil {
		return "", errors.New("media transaction is not configured")
	}
	return v.validateMedia(ctx, tx, userID, rawURL, messageType)
}

func (v *MediaValidator) validateMedia(
	ctx context.Context,
	queryer sqlx.QueryerContext,
	userID string,
	rawURL string,
	messageType string,
) (string, error) {
	if v.storage == nil {
		return "", errors.New("media validator is not configured")
	}
	parsed, err := url.ParseRequestURI(rawURL)
	if err != nil {
		return "", ErrMediaURL
	}

	type mediaRecord struct {
		ID        string `db:"id"`
		Filename  string `db:"filename"`
		MimeType  string `db:"mime_type"`
		SizeBytes int64  `db:"size_bytes"`
	}
	var media mediaRecord
	found := false
	for _, segment := range strings.Split(strings.Trim(parsed.Path, "/"), "/") {
		candidate, err := uuid.Parse(segment)
		if err != nil || candidate.String() == userID {
			continue
		}
		err = sqlx.GetContext(ctx, queryer, &media, `
			SELECT id, filename, mime_type, size_bytes
			FROM media
			WHERE id = $1 AND user_id = $2
		`, candidate.String(), userID)
		if err == nil {
			found = true
			break
		}
		if !errors.Is(err, sql.ErrNoRows) {
			return "", fmt.Errorf("look up message media: %w", err)
		}
	}
	if !found {
		return "", ErrMediaNotFound
	}
	if v.storage.GetURL(media.Filename) != rawURL {
		return "", ErrMediaURL
	}

	var maxBytes int64
	switch messageType {
	case model.MessageTypeImage:
		if !strings.HasPrefix(strings.ToLower(media.MimeType), "image/") {
			return "", ErrMediaType
		}
		maxBytes = maxImageBytes
	case model.MessageTypeVideo:
		if !strings.HasPrefix(strings.ToLower(media.MimeType), "video/") {
			return "", ErrMediaType
		}
		maxBytes = maxVideoBytes
	case model.MessageTypeFile:
		maxBytes = maxFileBytes
	default:
		return "", ErrMediaType
	}
	if media.SizeBytes < 0 || media.SizeBytes > maxBytes {
		return "", ErrMediaTooLarge
	}
	return media.ID, nil
}
