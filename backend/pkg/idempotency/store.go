package idempotency

import (
	"context"
	"database/sql"
	"encoding/hex"
	"encoding/json"
	"errors"
	"fmt"
	"strings"
	"time"

	"github.com/google/uuid"
	"github.com/jmoiron/sqlx"
)

func Acquire(ctx context.Context, tx *sqlx.Tx, req Request) (*Acquisition, error) {
	if err := validateRequest(req); err != nil {
		return nil, err
	}

	var recordID string
	receivedAt := req.ReceivedAt.UTC()
	expiresAt := receivedAt.Add(30 * 24 * time.Hour)
	err := tx.GetContext(ctx, &recordID, `
		INSERT INTO idempotency_records (
		    principal_id, operation, idempotency_key, request_hash,
		    created_at, updated_at, expires_at
		)
		VALUES ($1, $2, $3, $4, $5, $5, $6)
		ON CONFLICT (principal_id, operation, idempotency_key) DO NOTHING
		RETURNING id
	`, req.PrincipalID, req.Operation, req.Key, req.RequestHash, receivedAt, expiresAt)
	if err == nil {
		return &Acquisition{
			ID:      recordID,
			Outcome: OutcomeNew,
		}, nil
	}
	if !errors.Is(err, sql.ErrNoRows) {
		return nil, fmt.Errorf("acquire idempotency record: %w", err)
	}

	var existing struct {
		ID             string  `db:"id"`
		RequestHash    string  `db:"request_hash"`
		State          string  `db:"state"`
		ResourceType   *string `db:"resource_type"`
		ResourceID     *string `db:"resource_id"`
		ResponseStatus *int    `db:"response_status"`
		ResultJSON     []byte  `db:"result_json"`
	}
	err = tx.GetContext(ctx, &existing, `
		SELECT id, request_hash, state, resource_type, resource_id,
		       response_status, result_json
		FROM idempotency_records
		WHERE principal_id = $1
		  AND operation = $2
		  AND idempotency_key = $3
		FOR UPDATE
	`, req.PrincipalID, req.Operation, req.Key)
	if err != nil {
		return nil, fmt.Errorf("load idempotency record: %w", err)
	}
	if existing.RequestHash != req.RequestHash {
		return nil, ErrPayloadConflict
	}
	if existing.State != "completed" ||
		existing.ResourceType == nil ||
		existing.ResourceID == nil ||
		existing.ResponseStatus == nil ||
		len(existing.ResultJSON) == 0 {
		return nil, ErrIncomplete
	}

	return &Acquisition{
		ID:             existing.ID,
		Outcome:        OutcomeReplay,
		ResourceType:   *existing.ResourceType,
		ResourceID:     *existing.ResourceID,
		ResponseStatus: *existing.ResponseStatus,
		ResultJSON:     json.RawMessage(existing.ResultJSON),
	}, nil
}

func Complete(
	ctx context.Context,
	tx *sqlx.Tx,
	recordID string,
	resourceType string,
	resourceID string,
	responseStatus int,
	result any,
) error {
	resultJSON, err := json.Marshal(result)
	if err != nil {
		return fmt.Errorf("marshal idempotency result: %w", err)
	}
	execResult, err := tx.ExecContext(ctx, `
		UPDATE idempotency_records
		SET state = 'completed',
		    resource_type = $2,
		    resource_id = $3,
		    response_status = $4,
		    result_json = $5::jsonb,
		    updated_at = NOW()
		WHERE id = $1 AND state = 'in_progress'
	`, recordID, resourceType, resourceID, responseStatus, string(resultJSON))
	if err != nil {
		return fmt.Errorf("complete idempotency record: %w", err)
	}
	affected, err := execResult.RowsAffected()
	if err != nil {
		return fmt.Errorf("inspect idempotency completion: %w", err)
	}
	if affected != 1 {
		return ErrIncomplete
	}
	return nil
}

// CleanupExpired is an explicit maintenance hook. Deployments must schedule it
// periodically; this package intentionally does not start a background process.
func CleanupExpired(ctx context.Context, db *sqlx.DB, limit int) (int, error) {
	if limit <= 0 || limit > 10000 {
		return 0, fmt.Errorf("cleanup limit must be between 1 and 10000")
	}
	var deleted int
	if err := db.GetContext(
		ctx,
		&deleted,
		`SELECT cleanup_expired_idempotency_records($1)`,
		limit,
	); err != nil {
		return 0, fmt.Errorf("cleanup expired idempotency records: %w", err)
	}
	return deleted, nil
}

func validateRequest(req Request) error {
	if _, err := uuid.Parse(req.PrincipalID); err != nil {
		return fmt.Errorf("invalid idempotency principal: %w", err)
	}
	operation := strings.TrimSpace(req.Operation)
	if operation == "" || len(operation) > 100 {
		return fmt.Errorf("invalid idempotency operation")
	}
	if len(req.RequestHash) != 64 {
		return fmt.Errorf("invalid idempotency request hash")
	}
	decoded, err := hex.DecodeString(req.RequestHash)
	if err != nil || len(decoded) != sha256Size {
		return fmt.Errorf("invalid idempotency request hash")
	}
	if req.Key == uuid.Nil || req.Key.Version() != 4 || req.Key.Variant() != uuid.RFC4122 {
		return ErrInvalidKey
	}
	now := time.Now().UTC()
	receivedAt := req.ReceivedAt.UTC()
	if req.ReceivedAt.IsZero() ||
		receivedAt.Before(now.Add(-24*time.Hour)) ||
		receivedAt.After(now.Add(5*time.Minute)) {
		return ErrInvalidReceivedAt
	}
	return nil
}

const sha256Size = 32
