package idempotency

import (
	"context"
	"database/sql"
	"encoding/json"
	"fmt"
	"os"
	"strings"
	"testing"
	"time"

	"github.com/google/uuid"
	"github.com/jmoiron/sqlx"
	_ "github.com/lib/pq"
)

func TestPostgresConcurrentReplayAndRollbackReacquire(t *testing.T) {
	dsn := strings.TrimSpace(os.Getenv("TEST_DATABASE_URL"))
	if dsn == "" {
		t.Skip("TEST_DATABASE_URL is not set; skipping real PostgreSQL idempotency integration")
	}

	ctx, cancel := context.WithTimeout(context.Background(), 20*time.Second)
	defer cancel()
	firstDB, err := sqlx.ConnectContext(ctx, "postgres", dsn)
	if err != nil {
		t.Fatalf("connect first PostgreSQL session: %v", err)
	}
	defer firstDB.Close()
	secondDB, err := sqlx.ConnectContext(ctx, "postgres", dsn)
	if err != nil {
		t.Fatalf("connect second PostgreSQL session: %v", err)
	}
	defer secondDB.Close()

	var table sql.NullString
	if err := firstDB.GetContext(
		ctx,
		&table,
		`SELECT to_regclass('public.idempotency_records')::text`,
	); err != nil {
		t.Fatalf("check idempotency migration: %v", err)
	}
	if !table.Valid {
		t.Fatal("idempotency_records is missing; apply backend migrations before this integration test")
	}

	suffix := strings.ReplaceAll(uuid.NewString(), "-", "")[:12]
	principalID := uuid.NewString()
	_, err = firstDB.ExecContext(ctx, `
		INSERT INTO users (id, email, username, password_hash, display_name)
		VALUES ($1, $2, $3, $4, $5)
	`, principalID, fmt.Sprintf("idem-%s@example.test", suffix), "idem_"+suffix,
		"integration-test-not-a-real-hash", "Idempotency Integration")
	if err != nil {
		t.Fatalf("insert integration principal: %v", err)
	}
	defer func() {
		cleanupCtx, cleanupCancel := context.WithTimeout(context.Background(), 5*time.Second)
		defer cleanupCancel()
		_, _ = firstDB.ExecContext(cleanupCtx, `DELETE FROM users WHERE id = $1`, principalID)
	}()

	requestHash, err := HashPayload(map[string]string{"content": "concurrent"})
	if err != nil {
		t.Fatalf("hash integration payload: %v", err)
	}
	request := Request{
		PrincipalID: principalID,
		Operation:   "integration.idempotency.v1",
		Key:         uuid.New(),
		RequestHash: requestHash,
		ReceivedAt:  time.Now().UTC(),
	}
	resourceID := uuid.NewString()
	storedJSON := json.RawMessage(
		`{"id":"` + resourceID + `","legacy_integration_field":"preserved"}`,
	)

	firstTx, err := firstDB.BeginTxx(ctx, nil)
	if err != nil {
		t.Fatalf("begin first concurrent transaction: %v", err)
	}
	firstAcquisition, err := Acquire(ctx, firstTx, request)
	if err != nil {
		_ = firstTx.Rollback()
		t.Fatalf("acquire first concurrent request: %v", err)
	}
	if firstAcquisition.Outcome != OutcomeNew {
		_ = firstTx.Rollback()
		t.Fatalf("first outcome = %q, want %q", firstAcquisition.Outcome, OutcomeNew)
	}

	type concurrentResult struct {
		acquisition *Acquisition
		err         error
	}
	started := make(chan struct{})
	resultCh := make(chan concurrentResult, 1)
	go func() {
		secondTx, beginErr := secondDB.BeginTxx(ctx, nil)
		if beginErr != nil {
			resultCh <- concurrentResult{err: beginErr}
			return
		}
		close(started)
		acquisition, acquireErr := Acquire(ctx, secondTx, request)
		if acquireErr != nil {
			_ = secondTx.Rollback()
			resultCh <- concurrentResult{err: acquireErr}
			return
		}
		if commitErr := secondTx.Commit(); commitErr != nil {
			resultCh <- concurrentResult{err: commitErr}
			return
		}
		resultCh <- concurrentResult{acquisition: acquisition}
	}()
	<-started

	select {
	case early := <-resultCh:
		_ = firstTx.Rollback()
		t.Fatalf("concurrent duplicate completed before owner commit: %+v", early)
	case <-time.After(150 * time.Millisecond):
	}

	if err := Complete(
		ctx,
		firstTx,
		firstAcquisition.ID,
		"integration_resource",
		resourceID,
		201,
		storedJSON,
	); err != nil {
		_ = firstTx.Rollback()
		t.Fatalf("complete first concurrent request: %v", err)
	}
	if err := firstTx.Commit(); err != nil {
		t.Fatalf("commit first concurrent request: %v", err)
	}

	var concurrent concurrentResult
	select {
	case concurrent = <-resultCh:
	case <-ctx.Done():
		t.Fatal("timed out waiting for concurrent duplicate replay")
	}
	if concurrent.err != nil {
		t.Fatalf("concurrent duplicate failed: %v", concurrent.err)
	}
	if concurrent.acquisition == nil || concurrent.acquisition.Outcome != OutcomeReplay {
		t.Fatalf("concurrent outcome = %+v, want replay", concurrent.acquisition)
	}
	if string(concurrent.acquisition.ResultJSON) != string(storedJSON) {
		t.Fatalf("concurrent replay JSON changed: %s", concurrent.acquisition.ResultJSON)
	}

	rollbackRequest := request
	rollbackRequest.Key = uuid.New()
	rollbackRequest.ReceivedAt = time.Now().UTC()
	rollbackTx, err := firstDB.BeginTxx(ctx, nil)
	if err != nil {
		t.Fatalf("begin rollback transaction: %v", err)
	}
	rolledBackAcquisition, err := Acquire(ctx, rollbackTx, rollbackRequest)
	if err != nil {
		_ = rollbackTx.Rollback()
		t.Fatalf("acquire rollback request: %v", err)
	}
	if rolledBackAcquisition.Outcome != OutcomeNew {
		_ = rollbackTx.Rollback()
		t.Fatalf("rollback acquisition outcome = %q, want new", rolledBackAcquisition.Outcome)
	}
	if err := rollbackTx.Rollback(); err != nil {
		t.Fatalf("rollback acquisition: %v", err)
	}

	reacquireTx, err := secondDB.BeginTxx(ctx, nil)
	if err != nil {
		t.Fatalf("begin reacquire transaction: %v", err)
	}
	reacquired, err := Acquire(ctx, reacquireTx, rollbackRequest)
	if err != nil {
		_ = reacquireTx.Rollback()
		t.Fatalf("reacquire rolled-back key: %v", err)
	}
	if reacquired.Outcome != OutcomeNew {
		_ = reacquireTx.Rollback()
		t.Fatalf("reacquired outcome = %q, want new", reacquired.Outcome)
	}
	if err := Complete(
		ctx,
		reacquireTx,
		reacquired.ID,
		"integration_resource",
		uuid.NewString(),
		201,
		map[string]string{"status": "reacquired"},
	); err != nil {
		_ = reacquireTx.Rollback()
		t.Fatalf("complete reacquired key: %v", err)
	}
	if err := reacquireTx.Commit(); err != nil {
		t.Fatalf("commit reacquired key: %v", err)
	}
}
