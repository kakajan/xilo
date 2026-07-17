package idempotency

import (
	"context"
	"database/sql"
	"errors"
	"testing"
	"time"

	"github.com/DATA-DOG/go-sqlmock"
	"github.com/google/uuid"
	"github.com/jmoiron/sqlx"
)

const (
	storePrincipal = "11111111-1111-4111-8111-111111111111"
	storeRecord    = "22222222-2222-4222-8222-222222222222"
	storeResource  = "33333333-3333-4333-8333-333333333333"
	storeHash      = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
)

var storeKey = uuid.MustParse("123e4567-e89b-42d3-a456-426614174000")

func TestAcquireReturnsNewThenReplay(t *testing.T) {
	db, mock, cleanup := newIdempotencyMock(t)
	defer cleanup()
	req := Request{
		PrincipalID: storePrincipal,
		Operation:   "chat.create.v1",
		Key:         storeKey,
		RequestHash: storeHash,
		ReceivedAt:  time.Now().UTC(),
	}

	mock.ExpectBegin()
	mock.ExpectQuery(`INSERT INTO idempotency_records.+RETURNING id`).
		WithArgs(
			storePrincipal,
			req.Operation,
			storeKey,
			storeHash,
			req.ReceivedAt.UTC(),
			req.ReceivedAt.UTC().Add(30*24*time.Hour),
		).
		WillReturnRows(sqlmock.NewRows([]string{"id"}).AddRow(storeRecord))
	firstTx, err := db.BeginTxx(context.Background(), nil)
	if err != nil {
		t.Fatalf("begin first transaction: %v", err)
	}
	first, err := Acquire(context.Background(), firstTx, req)
	if err != nil {
		t.Fatalf("acquire new record: %v", err)
	}
	if first.Outcome != OutcomeNew {
		t.Fatalf("first outcome = %q, want %q", first.Outcome, OutcomeNew)
	}
	mock.ExpectExec(`UPDATE idempotency_records.+state = 'completed'`).
		WithArgs(storeRecord, "chat", storeResource, 201, sqlmock.AnyArg()).
		WillReturnResult(sqlmock.NewResult(0, 1))
	if err := Complete(
		context.Background(),
		firstTx,
		storeRecord,
		"chat",
		storeResource,
		201,
		map[string]string{"id": storeResource},
	); err != nil {
		t.Fatalf("complete first acquisition: %v", err)
	}
	mock.ExpectCommit()
	if err := firstTx.Commit(); err != nil {
		t.Fatalf("commit first transaction: %v", err)
	}

	resultJSON := `{"id":"` + storeResource + `"}`
	mock.ExpectBegin()
	mock.ExpectQuery(`INSERT INTO idempotency_records.+RETURNING id`).
		WithArgs(
			storePrincipal,
			req.Operation,
			storeKey,
			storeHash,
			req.ReceivedAt.UTC(),
			req.ReceivedAt.UTC().Add(30*24*time.Hour),
		).
		WillReturnError(sql.ErrNoRows)
	mock.ExpectQuery(`SELECT id, request_hash, state, resource_type, resource_id,.+FOR UPDATE`).
		WithArgs(storePrincipal, req.Operation, storeKey).
		WillReturnRows(sqlmock.NewRows([]string{
			"id",
			"request_hash",
			"state",
			"resource_type",
			"resource_id",
			"response_status",
			"result_json",
		}).AddRow(
			storeRecord,
			storeHash,
			"completed",
			"chat",
			storeResource,
			201,
			[]byte(resultJSON),
		))
	replayTx, err := db.BeginTxx(context.Background(), nil)
	if err != nil {
		t.Fatalf("begin replay transaction: %v", err)
	}
	replay, err := Acquire(context.Background(), replayTx, req)
	if err != nil {
		t.Fatalf("acquire replay record: %v", err)
	}
	if replay.Outcome != OutcomeReplay || replay.ResourceID != storeResource {
		t.Fatalf("unexpected replay acquisition: %+v", replay)
	}
	mock.ExpectCommit()
	if err := replayTx.Commit(); err != nil {
		t.Fatalf("commit replay transaction: %v", err)
	}

	if err := mock.ExpectationsWereMet(); err != nil {
		t.Fatalf("unmet SQL expectation: %v", err)
	}
}

func TestAcquireRejectsChangedPayload(t *testing.T) {
	db, mock, cleanup := newIdempotencyMock(t)
	defer cleanup()
	req := Request{
		PrincipalID: storePrincipal,
		Operation:   "chat.create.v1",
		Key:         storeKey,
		RequestHash: storeHash,
		ReceivedAt:  time.Now().UTC(),
	}

	mock.ExpectBegin()
	mock.ExpectQuery(`INSERT INTO idempotency_records.+RETURNING id`).
		WithArgs(
			storePrincipal,
			req.Operation,
			storeKey,
			storeHash,
			req.ReceivedAt.UTC(),
			req.ReceivedAt.UTC().Add(30*24*time.Hour),
		).
		WillReturnError(sql.ErrNoRows)
	mock.ExpectQuery(`SELECT id, request_hash, state, resource_type, resource_id,.+FOR UPDATE`).
		WithArgs(storePrincipal, req.Operation, storeKey).
		WillReturnRows(sqlmock.NewRows([]string{
			"id",
			"request_hash",
			"state",
			"resource_type",
			"resource_id",
			"response_status",
			"result_json",
		}).AddRow(
			storeRecord,
			"bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb",
			"completed",
			"chat",
			storeResource,
			201,
			[]byte(`{"id":"`+storeResource+`"}`),
		))
	tx, err := db.BeginTxx(context.Background(), nil)
	if err != nil {
		t.Fatalf("begin conflict transaction: %v", err)
	}
	_, err = Acquire(context.Background(), tx, req)
	if !errors.Is(err, ErrPayloadConflict) {
		t.Fatalf("Acquire error = %v, want ErrPayloadConflict", err)
	}
	mock.ExpectRollback()
	if err := tx.Rollback(); err != nil {
		t.Fatalf("rollback conflict transaction: %v", err)
	}
	if err := mock.ExpectationsWereMet(); err != nil {
		t.Fatalf("unmet SQL expectation: %v", err)
	}
}

func TestRolledBackAcquisitionCanBeAcquiredAgain(t *testing.T) {
	db, mock, cleanup := newIdempotencyMock(t)
	defer cleanup()
	req := Request{
		PrincipalID: storePrincipal,
		Operation:   "chat.message.create.v1",
		Key:         storeKey,
		RequestHash: storeHash,
		ReceivedAt:  time.Now().UTC(),
	}

	mock.ExpectBegin()
	mock.ExpectQuery(`INSERT INTO idempotency_records.+RETURNING id`).
		WithArgs(
			storePrincipal,
			req.Operation,
			storeKey,
			storeHash,
			req.ReceivedAt.UTC(),
			req.ReceivedAt.UTC().Add(30*24*time.Hour),
		).
		WillReturnRows(sqlmock.NewRows([]string{"id"}).AddRow(storeRecord))
	firstTx, err := db.BeginTxx(context.Background(), nil)
	if err != nil {
		t.Fatalf("begin first transaction: %v", err)
	}
	if _, err := Acquire(context.Background(), firstTx, req); err != nil {
		t.Fatalf("first acquisition: %v", err)
	}
	mock.ExpectRollback()
	if err := firstTx.Rollback(); err != nil {
		t.Fatalf("rollback first transaction: %v", err)
	}

	mock.ExpectBegin()
	mock.ExpectQuery(`INSERT INTO idempotency_records.+RETURNING id`).
		WithArgs(
			storePrincipal,
			req.Operation,
			storeKey,
			storeHash,
			req.ReceivedAt.UTC(),
			req.ReceivedAt.UTC().Add(30*24*time.Hour),
		).
		WillReturnRows(sqlmock.NewRows([]string{"id"}).AddRow(storeRecord))
	retryTx, err := db.BeginTxx(context.Background(), nil)
	if err != nil {
		t.Fatalf("begin retry transaction: %v", err)
	}
	retry, err := Acquire(context.Background(), retryTx, req)
	if err != nil {
		t.Fatalf("retry acquisition: %v", err)
	}
	if retry.Outcome != OutcomeNew {
		t.Fatalf("retry outcome = %q, want %q", retry.Outcome, OutcomeNew)
	}
	mock.ExpectCommit()
	if err := retryTx.Commit(); err != nil {
		t.Fatalf("commit retry transaction: %v", err)
	}
	if err := mock.ExpectationsWereMet(); err != nil {
		t.Fatalf("unmet SQL expectation: %v", err)
	}
}

func TestValidateRequestRejectsUnreasonableReceiptTimes(t *testing.T) {
	now := time.Now().UTC()
	base := Request{
		PrincipalID: storePrincipal,
		Operation:   "chat.create.v1",
		Key:         storeKey,
		RequestHash: storeHash,
		ReceivedAt:  now,
	}

	tests := []struct {
		name       string
		receivedAt time.Time
	}{
		{name: "zero", receivedAt: time.Time{}},
		{name: "too old", receivedAt: now.Add(-25 * time.Hour)},
		{name: "too far in future", receivedAt: now.Add(6 * time.Minute)},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			req := base
			req.ReceivedAt = tt.receivedAt
			if err := validateRequest(req); !errors.Is(err, ErrInvalidReceivedAt) {
				t.Fatalf("validateRequest error = %v, want ErrInvalidReceivedAt", err)
			}
		})
	}
}

func newIdempotencyMock(t *testing.T) (*sqlx.DB, sqlmock.Sqlmock, func()) {
	t.Helper()
	rawDB, mock, err := sqlmock.New(sqlmock.QueryMatcherOption(sqlmock.QueryMatcherRegexp))
	if err != nil {
		t.Fatalf("create SQL mock: %v", err)
	}
	db := sqlx.NewDb(rawDB, "sqlmock")
	return db, mock, func() {
		_ = db.Close()
	}
}
