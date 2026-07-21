package repository

import (
	"context"
	"database/sql"
	"errors"
	"net/http"
	"testing"
	"time"

	"github.com/DATA-DOG/go-sqlmock"
	"github.com/google/uuid"
	"github.com/jmoiron/sqlx"
	"github.com/lib/pq"
	"github.com/xilo-platform/xilo/internal/chat/model"
	pkgidempotency "github.com/xilo-platform/xilo/pkg/idempotency"
)

// TODO(chat-postgres-integration): Run the transaction and ILIKE ESCAPE
// invariants, plus true concurrent idempotency contention, against PostgreSQL
// in CI when a disposable TEST_DATABASE_URL is available.

const (
	repoTestActor     = "11111111-1111-4111-8111-111111111111"
	repoTestRecord    = "22222222-2222-4222-8222-222222222222"
	repoTestChat      = "44444444-4444-4444-8444-444444444444"
	repoTestMsg       = "55555555-5555-4555-8555-555555555555"
	repoTestRequestHash = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
)

func TestEscapeLikePattern(t *testing.T) {
	tests := []struct {
		name  string
		input string
		want  string
	}{
		{name: "plain", input: "hello", want: "hello"},
		{name: "percent", input: "100%", want: `100\%`},
		{name: "underscore", input: "user_name", want: `user\_name`},
		{name: "backslash", input: `a\b`, want: `a\\b`},
		{name: "combined", input: `50%_off\sale`, want: `50\%\_off\\sale`},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			if got := escapeLikePattern(tt.input); got != tt.want {
				t.Fatalf("escapeLikePattern(%q) = %q, want %q", tt.input, got, tt.want)
			}
		})
	}
}

func TestMarkReadUsesMessageCreatedAtAsMonotonicWatermark(t *testing.T) {
	repo, mock, cleanup := newMockChatRepo(t)
	defer cleanup()

	targetCreatedAt := time.Date(2026, 7, 16, 10, 0, 0, 0, time.UTC)
	receiptAt := targetCreatedAt.Add(2 * time.Hour)

	mock.ExpectBegin()
	mock.ExpectQuery(`SELECT m\.chat_id, m\.created_at.+FROM messages m.+FOR SHARE OF m, cm`).
		WithArgs(repoTestMsg, repoTestActor).
		WillReturnRows(sqlmock.NewRows([]string{"chat_id", "created_at"}).
			AddRow(repoTestChat, targetCreatedAt))
	mock.ExpectQuery(`INSERT INTO message_reads.+VALUES \(\$1, \$2, \$3, NOW\(\)\).+RETURNING read_at`).
		WithArgs(repoTestMsg, repoTestChat, repoTestActor).
		WillReturnRows(sqlmock.NewRows([]string{"read_at"}).AddRow(receiptAt))
	mock.ExpectExec(
		`UPDATE chat_members.+last_read_at = GREATEST\(COALESCE\(last_read_at, joined_at\), \$3\)`,
	).
		WithArgs(repoTestChat, repoTestActor, targetCreatedAt).
		WillReturnResult(sqlmock.NewResult(0, 1))
	mock.ExpectCommit()

	read, err := repo.MarkRead(context.Background(), repoTestMsg, repoTestActor)
	if err != nil {
		t.Fatalf("MarkRead returned error: %v", err)
	}
	if !read.ReadAt.Equal(receiptAt) {
		t.Fatalf("read receipt = %v, want %v", read.ReadAt, receiptAt)
	}
	if err := mock.ExpectationsWereMet(); err != nil {
		t.Fatalf("unmet SQL expectation: %v", err)
	}
}

func TestCreateMessageAdvancesLastMessageAtMonotonically(t *testing.T) {
	repo, mock, cleanup := newMockChatRepo(t)
	defer cleanup()

	createdAt := time.Date(2026, 7, 16, 10, 0, 0, 0, time.UTC)
	content := "hello"
	idempotencyRequest := repoIdempotencyRequest()

	mock.ExpectBegin()
	mock.ExpectQuery(`INSERT INTO idempotency_records.+RETURNING id`).
		WithArgs(
			repoTestActor,
			idempotencyRequest.Operation,
			idempotencyRequest.Key,
			idempotencyRequest.RequestHash,
			idempotencyRequest.ReceivedAt.UTC(),
			idempotencyRequest.ReceivedAt.UTC().Add(30*24*time.Hour),
		).
		WillReturnRows(sqlmock.NewRows([]string{"id"}).AddRow(repoTestRecord))
	mock.ExpectQuery(`SELECT id.+FROM chats.+FOR KEY SHARE`).
		WithArgs(repoTestChat).
		WillReturnRows(sqlmock.NewRows([]string{"id"}).AddRow(repoTestChat))
	mock.ExpectQuery(`SELECT TRUE.+FROM chat_members.+FOR SHARE`).
		WithArgs(repoTestChat, repoTestActor).
		WillReturnRows(sqlmock.NewRows([]string{"active"}).AddRow(true))
	mock.ExpectQuery(`INSERT INTO messages.+RETURNING id, chat_id, sender_id`).
		WithArgs(
			repoTestChat,
			repoTestActor,
			model.MessageTypeText,
			&content,
			nil,
			nil,
			nil,
		).
		WillReturnRows(sqlmock.NewRows([]string{
			"id",
			"chat_id",
			"sender_id",
			"type",
			"content",
			"media_id",
			"media_url",
			"reply_to_id",
			"is_edited",
			"is_deleted",
			"created_at",
			"updated_at",
			"edited_at",
			"deleted_at",
		}).AddRow(
			repoTestMsg,
			repoTestChat,
			repoTestActor,
			model.MessageTypeText,
			content,
			nil,
			nil,
			nil,
			false,
			false,
			createdAt,
			createdAt,
			nil,
			nil,
		))
	mock.ExpectQuery(`SELECT COALESCE\(NULLIF\(TRIM\(display_name\), ''\), username\) AS sender_name`).
		WithArgs(repoTestActor).
		WillReturnRows(sqlmock.NewRows([]string{"sender_name", "sender_avatar"}).
			AddRow("Actor", nil))
	mock.ExpectExec(
		`UPDATE chats.+last_message_at = GREATEST\(COALESCE\(last_message_at, \$2\), \$2\)`,
	).
		WithArgs(repoTestChat, createdAt).
		WillReturnResult(sqlmock.NewResult(0, 1))
	mock.ExpectExec(`UPDATE idempotency_records.+state = 'completed'`).
		WithArgs(
			repoTestRecord,
			"message",
			repoTestMsg,
			http.StatusCreated,
			sqlmock.AnyArg(),
		).
		WillReturnResult(sqlmock.NewResult(0, 1))
	mock.ExpectCommit()

	result, err := repo.CreateMessage(
		context.Background(),
		repoTestChat,
		repoTestActor,
		idempotencyRequest,
		&model.CreateMessageRequest{
			Type:    model.MessageTypeText,
			Content: &content,
		},
	)
	if err != nil {
		t.Fatalf("CreateMessage returned error: %v", err)
	}
	if result.ResponseStatus != http.StatusCreated || result.Outcome != pkgidempotency.OutcomeNew {
		t.Fatalf("unexpected mutation metadata: status=%d outcome=%q",
			result.ResponseStatus, result.Outcome)
	}
	if result.Value == nil || result.Value.ID != repoTestMsg {
		t.Fatalf("unexpected typed mutation result: %+v", result.Value)
	}
	if err := mock.ExpectationsWereMet(); err != nil {
		t.Fatalf("unmet SQL expectation: %v", err)
	}
}

func TestCreateMessageReplaysStoredSemanticResultWithoutDuplicateWrite(t *testing.T) {
	repo, mock, cleanup := newMockChatRepo(t)
	defer cleanup()
	idempotencyRequest := repoIdempotencyRequest()
	content := "hello"
	resultJSON := []byte(
		`{"id":"` + repoTestMsg + `","chat_id":"` + repoTestChat +
			`","legacy_delivery_state":"accepted"}`,
	)

	mock.ExpectBegin()
	mock.ExpectQuery(`INSERT INTO idempotency_records.+RETURNING id`).
		WithArgs(
			repoTestActor,
			idempotencyRequest.Operation,
			idempotencyRequest.Key,
			idempotencyRequest.RequestHash,
			idempotencyRequest.ReceivedAt.UTC(),
			idempotencyRequest.ReceivedAt.UTC().Add(30*24*time.Hour),
		).
		WillReturnError(sql.ErrNoRows)
	mock.ExpectQuery(`SELECT id, request_hash, state, resource_type, resource_id,.+FOR UPDATE`).
		WithArgs(repoTestActor, idempotencyRequest.Operation, idempotencyRequest.Key).
		WillReturnRows(completedIdempotencyRows(resultJSON, idempotencyRequest.RequestHash))
	mock.ExpectCommit()

	result, err := repo.CreateMessage(
		context.Background(),
		repoTestChat,
		repoTestActor,
		idempotencyRequest,
		&model.CreateMessageRequest{
			Type:    model.MessageTypeText,
			Content: &content,
		},
	)
	if err != nil {
		t.Fatalf("CreateMessage replay returned error: %v", err)
	}
	if result.Outcome != pkgidempotency.OutcomeReplay ||
		result.ResponseStatus != http.StatusCreated {
		t.Fatalf("unexpected replay metadata: status=%d outcome=%q",
			result.ResponseStatus, result.Outcome)
	}
	if string(result.ReplayJSON) != string(resultJSON) {
		t.Fatalf("replay JSON changed:\n got: %s\nwant: %s", result.ReplayJSON, resultJSON)
	}
	if result.Value != nil {
		t.Fatalf("replay must not deserialize into current model: %+v", result.Value)
	}
	if err := mock.ExpectationsWereMet(); err != nil {
		t.Fatalf("unmet SQL expectation: %v", err)
	}
}

func TestCreateMessageRejectsIdempotencyPayloadConflict(t *testing.T) {
	repo, mock, cleanup := newMockChatRepo(t)
	defer cleanup()
	idempotencyRequest := repoIdempotencyRequest()
	content := "changed"

	mock.ExpectBegin()
	mock.ExpectQuery(`INSERT INTO idempotency_records.+RETURNING id`).
		WithArgs(
			repoTestActor,
			idempotencyRequest.Operation,
			idempotencyRequest.Key,
			idempotencyRequest.RequestHash,
			idempotencyRequest.ReceivedAt.UTC(),
			idempotencyRequest.ReceivedAt.UTC().Add(30*24*time.Hour),
		).
		WillReturnError(sql.ErrNoRows)
	mock.ExpectQuery(`SELECT id, request_hash, state, resource_type, resource_id,.+FOR UPDATE`).
		WithArgs(repoTestActor, idempotencyRequest.Operation, idempotencyRequest.Key).
		WillReturnRows(completedIdempotencyRows(
			[]byte(`{"id":"`+repoTestMsg+`"}`),
			"bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb",
		))
	mock.ExpectRollback()

	_, err := repo.CreateMessage(
		context.Background(),
		repoTestChat,
		repoTestActor,
		idempotencyRequest,
		&model.CreateMessageRequest{
			Type:    model.MessageTypeText,
			Content: &content,
		},
	)
	if !errors.Is(err, pkgidempotency.ErrPayloadConflict) {
		t.Fatalf("CreateMessage error = %v, want ErrPayloadConflict", err)
	}
	if err := mock.ExpectationsWereMet(); err != nil {
		t.Fatalf("unmet SQL expectation: %v", err)
	}
}

func TestCreateMessageRollbackDoesNotCompleteIdempotencyRecord(t *testing.T) {
	repo, mock, cleanup := newMockChatRepo(t)
	defer cleanup()
	idempotencyRequest := repoIdempotencyRequest()
	content := "hello"

	mock.ExpectBegin()
	mock.ExpectQuery(`INSERT INTO idempotency_records.+RETURNING id`).
		WithArgs(
			repoTestActor,
			idempotencyRequest.Operation,
			idempotencyRequest.Key,
			idempotencyRequest.RequestHash,
			idempotencyRequest.ReceivedAt.UTC(),
			idempotencyRequest.ReceivedAt.UTC().Add(30*24*time.Hour),
		).
		WillReturnRows(sqlmock.NewRows([]string{"id"}).AddRow(repoTestRecord))
	mock.ExpectQuery(`SELECT id.+FROM chats.+FOR KEY SHARE`).
		WithArgs(repoTestChat).
		WillReturnRows(sqlmock.NewRows([]string{"id"}).AddRow(repoTestChat))
	mock.ExpectQuery(`SELECT TRUE.+FROM chat_members.+FOR SHARE`).
		WithArgs(repoTestChat, repoTestActor).
		WillReturnError(errors.New("forced domain write failure"))
	mock.ExpectRollback()

	_, err := repo.CreateMessage(
		context.Background(),
		repoTestChat,
		repoTestActor,
		idempotencyRequest,
		&model.CreateMessageRequest{
			Type:    model.MessageTypeText,
			Content: &content,
		},
	)
	if err == nil {
		t.Fatal("expected forced domain write failure")
	}
	if err := mock.ExpectationsWereMet(); err != nil {
		t.Fatalf("unmet SQL expectation: %v", err)
	}
}

func TestRetryablePostgresErrorsAreNotDomainConflicts(t *testing.T) {
	for _, code := range []pq.ErrorCode{"40001", "40P01"} {
		t.Run(string(code), func(t *testing.T) {
			mapped := mapWriteError("commit transaction", &pq.Error{Code: code})
			if !errors.Is(mapped, ErrRetryable) {
				t.Fatalf("expected ErrRetryable, got %v", mapped)
			}
			if errors.Is(mapped, ErrConflict) {
				t.Fatalf("retryable database error must not map to ErrConflict")
			}
			if !IsRetryable(mapped) {
				t.Fatalf("IsRetryable must recognize mapped error")
			}
		})
	}

	conflict := mapWriteError("insert row", &pq.Error{Code: "23505"})
	if !errors.Is(conflict, ErrConflict) {
		t.Fatalf("expected unique violation to remain a domain conflict, got %v", conflict)
	}
	if IsRetryable(conflict) {
		t.Fatal("domain conflict must not be retryable")
	}
}

func newMockChatRepo(t *testing.T) (*ChatRepo, sqlmock.Sqlmock, func()) {
	t.Helper()
	db, mock, err := sqlmock.New(sqlmock.QueryMatcherOption(sqlmock.QueryMatcherRegexp))
	if err != nil {
		t.Fatalf("create SQL mock: %v", err)
	}
	sqlxDB := sqlx.NewDb(db, "sqlmock")
	return NewChatRepo(sqlxDB), mock, func() {
		_ = sqlxDB.Close()
	}
}

func repoIdempotencyRequest() pkgidempotency.Request {
	return pkgidempotency.Request{
		PrincipalID: repoTestActor,
		Operation:   "chat.message.create.v1",
		Key:         uuid.MustParse("123e4567-e89b-42d3-a456-426614174000"),
		RequestHash: repoTestRequestHash,
		ReceivedAt:  time.Now().UTC(),
	}
}

func completedIdempotencyRows(resultJSON []byte, requestHash string) *sqlmock.Rows {
	return sqlmock.NewRows([]string{
		"id",
		"request_hash",
		"state",
		"resource_type",
		"resource_id",
		"response_status",
		"result_json",
	}).AddRow(
		repoTestRecord,
		requestHash,
		"completed",
		"message",
		repoTestMsg,
		http.StatusCreated,
		resultJSON,
	)
}
