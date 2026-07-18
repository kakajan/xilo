package handler

import (
	"bytes"
	"context"
	"encoding/json"
	"errors"
	"io"
	"log/slog"
	"net/http"
	"net/http/httptest"
	"strings"
	"testing"
	"time"

	"github.com/gofiber/fiber/v2"
	"github.com/xilo-platform/xilo/internal/chat/model"
	"github.com/xilo-platform/xilo/internal/chat/service"
	pkgidempotency "github.com/xilo-platform/xilo/pkg/idempotency"
)

const (
	handlerActor = "11111111-1111-4111-8111-111111111111"
	handlerOther = "22222222-2222-4222-8222-222222222222"
	handlerChat  = "44444444-4444-4444-8444-444444444444"
	handlerKey   = "123e4567-e89b-42d3-a456-426614174000"
)

type handlerReplayRepository struct {
	service.ChatRepository

	chatResult    *pkgidempotency.MutationResult[model.Chat]
	messageResult *pkgidempotency.MutationResult[model.Message]
	receivedAt    time.Time
}

func (r *handlerReplayRepository) CreateChat(
	_ context.Context,
	_ string,
	request pkgidempotency.Request,
	_ *model.CreateChatRequest,
	_ []string,
	_ string,
	_ string,
) (*pkgidempotency.MutationResult[model.Chat], error) {
	r.receivedAt = request.ReceivedAt
	return r.chatResult, nil
}

func (r *handlerReplayRepository) CreateMessage(
	_ context.Context,
	_ string,
	_ string,
	request pkgidempotency.Request,
	_ *model.CreateMessageRequest,
) (*pkgidempotency.MutationResult[model.Message], error) {
	r.receivedAt = request.ReceivedAt
	return r.messageResult, nil
}

func TestWriteErrorLogsCauseAndRawForInternalFailures(t *testing.T) {
	var buf bytes.Buffer
	prev := slog.Default()
	slog.SetDefault(slog.New(slog.NewTextHandler(&buf, &slog.HandlerOptions{Level: slog.LevelError})))
	t.Cleanup(func() { slog.SetDefault(prev) })

	cause := errors.New("list chats: db unavailable")
	app := fiber.New()
	app.Get("/with-cause", func(c *fiber.Ctx) error {
		return writeError(c, &service.Error{
			Code:    service.CodeInternal,
			Message: "internal server error",
			Cause:   cause,
		})
	})
	app.Get("/nil-cause", func(c *fiber.Ctx) error {
		return writeError(c, &service.Error{
			Code:    service.CodeInternal,
			Message: "internal server error",
		})
	})

	resp, err := app.Test(httptest.NewRequest(http.MethodGet, "/with-cause", nil))
	if err != nil {
		t.Fatalf("with-cause request: %v", err)
	}
	_ = resp.Body.Close()
	if !strings.Contains(buf.String(), "chat request failed") {
		t.Fatalf("missing chat request failed log: %s", buf.String())
	}
	if !strings.Contains(buf.String(), "list chats") {
		t.Fatalf("missing cause in log: %s", buf.String())
	}

	buf.Reset()
	resp, err = app.Test(httptest.NewRequest(http.MethodGet, "/nil-cause", nil))
	if err != nil {
		t.Fatalf("nil-cause request: %v", err)
	}
	_ = resp.Body.Close()
	if !strings.Contains(buf.String(), "chat request failed") {
		t.Fatalf("missing chat request failed log for nil Cause: %s", buf.String())
	}
	if !strings.Contains(buf.String(), "raw=") {
		t.Fatalf("expected raw= attr when Cause is nil: %s", buf.String())
	}
}

func TestListChatsEmptyReturnsJSONArray(t *testing.T) {
	repo := &listChatsRepository{chats: nil}
	handler := NewChatHandler(service.NewChatService(repo))
	app := fiber.New()
	app.Use(func(c *fiber.Ctx) error {
		c.Locals("userID", handlerActor)
		return c.Next()
	})
	app.Get("/api/chats", handler.ListChats)

	response, err := app.Test(httptest.NewRequest(http.MethodGet, "/api/chats", nil))
	if err != nil {
		t.Fatalf("perform request: %v", err)
	}
	defer response.Body.Close()

	if response.StatusCode != fiber.StatusOK {
		t.Fatalf("status = %d, want %d", response.StatusCode, fiber.StatusOK)
	}
	body, err := io.ReadAll(response.Body)
	if err != nil {
		t.Fatalf("read body: %v", err)
	}
	if !strings.Contains(string(body), `"data":[]`) {
		t.Fatalf("body = %s, want data:[]", body)
	}
}

type listChatsRepository struct {
	service.ChatRepository
	chats []*model.Chat
}

func (r *listChatsRepository) ListChats(
	_ context.Context,
	_ string,
	_ model.ListParams,
) ([]*model.Chat, string, error) {
	return r.chats, "", nil
}

func TestRetryableErrorReturnsServiceUnavailable(t *testing.T) {
	app := fiber.New()
	app.Get("/", func(c *fiber.Ctx) error {
		return writeError(c, &service.Error{
			Code:    service.CodeRetryable,
			Message: "service temporarily unavailable; retry shortly",
		})
	})

	response, err := app.Test(httptest.NewRequest("GET", "/", nil))
	if err != nil {
		t.Fatalf("perform request: %v", err)
	}
	defer response.Body.Close()

	if response.StatusCode != fiber.StatusServiceUnavailable {
		t.Fatalf("status = %d, want %d", response.StatusCode, fiber.StatusServiceUnavailable)
	}
	if got := response.Header.Get("Retry-After"); got != "1" {
		t.Fatalf("Retry-After = %q, want %q", got, "1")
	}

	var payload model.ErrorResponse
	if err := json.NewDecoder(response.Body).Decode(&payload); err != nil {
		t.Fatalf("decode response: %v", err)
	}
	if payload.Code != service.CodeRetryable {
		t.Fatalf("code = %q, want %q", payload.Code, service.CodeRetryable)
	}
}

func TestRequiredIdempotencyKeyRejectsMissingAndMalformedHeaders(t *testing.T) {
	tests := []struct {
		name string
		key  string
		code string
	}{
		{name: "missing", code: service.CodeIdempotencyNeeded},
		{name: "malformed", key: "not-a-uuid", code: service.CodeIdempotencyInvalid},
		{
			name: "not UUIDv4",
			key:  "123e4567-e89b-12d3-a456-426614174000",
			code: service.CodeIdempotencyInvalid,
		},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			app := fiber.New()
			app.Post("/", func(c *fiber.Ctx) error {
				_, err := requiredIdempotencyKey(c)
				if err != nil {
					return writeError(c, err)
				}
				return c.SendStatus(fiber.StatusNoContent)
			})

			request := httptest.NewRequest(http.MethodPost, "/", nil)
			if tt.key != "" {
				request.Header.Set("Idempotency-Key", tt.key)
			}
			response, err := app.Test(request)
			if err != nil {
				t.Fatalf("perform request: %v", err)
			}
			defer response.Body.Close()

			if response.StatusCode != fiber.StatusBadRequest {
				t.Fatalf("status = %d, want %d", response.StatusCode, fiber.StatusBadRequest)
			}
			var payload model.ErrorResponse
			if err := json.NewDecoder(response.Body).Decode(&payload); err != nil {
				t.Fatalf("decode response: %v", err)
			}
			if payload.Code != tt.code {
				t.Fatalf("code = %q, want %q", payload.Code, tt.code)
			}
		})
	}
}

func TestIdempotencyConflictReturnsConflictStatus(t *testing.T) {
	app := fiber.New()
	app.Post("/", func(c *fiber.Ctx) error {
		return writeError(c, &service.Error{
			Code:    service.CodeIdempotencyConflict,
			Message: "Idempotency-Key was already used with a different payload",
		})
	})

	response, err := app.Test(httptest.NewRequest(http.MethodPost, "/", nil))
	if err != nil {
		t.Fatalf("perform request: %v", err)
	}
	defer response.Body.Close()

	if response.StatusCode != fiber.StatusConflict {
		t.Fatalf("status = %d, want %d", response.StatusCode, fiber.StatusConflict)
	}
	var payload model.ErrorResponse
	if err := json.NewDecoder(response.Body).Decode(&payload); err != nil {
		t.Fatalf("decode response: %v", err)
	}
	if payload.Code != service.CodeIdempotencyConflict {
		t.Fatalf("code = %q, want %q", payload.Code, service.CodeIdempotencyConflict)
	}
}

func TestCreateChatReplayPreservesStoredJSONAndReceiptTime(t *testing.T) {
	receivedAt := time.Now().UTC().Add(-time.Second)
	storedJSON := []byte(
		`{"id":"` + handlerChat + `","type":"direct","legacy_chat_field":"preserved"}`,
	)
	repo := &handlerReplayRepository{
		chatResult: &pkgidempotency.MutationResult[model.Chat]{
			Outcome:        pkgidempotency.OutcomeReplay,
			ResponseStatus: fiber.StatusCreated,
			ReplayJSON:     storedJSON,
		},
	}
	handler := NewChatHandler(service.NewChatService(repo))
	handler.clock = func() time.Time { return receivedAt }
	app := fiber.New()
	app.Use(func(c *fiber.Ctx) error {
		c.Locals("userID", handlerActor)
		return c.Next()
	})
	app.Post("/api/chats", handler.CreateChat)

	body := `{"type":"direct","member_ids":["` + handlerOther + `"]}`
	request := httptest.NewRequest(http.MethodPost, "/api/chats", strings.NewReader(body))
	request.Header.Set("Content-Type", "application/json")
	request.Header.Set("Idempotency-Key", handlerKey)
	response, err := app.Test(request)
	if err != nil {
		t.Fatalf("perform request: %v", err)
	}
	defer response.Body.Close()

	assertRawReplayResponse(t, response, storedJSON)
	if !repo.receivedAt.Equal(receivedAt) {
		t.Fatalf("received_at = %v, want %v", repo.receivedAt, receivedAt)
	}
}

func TestCreateMessageReplayPreservesStoredJSONAndReceiptTime(t *testing.T) {
	receivedAt := time.Now().UTC().Add(-time.Second)
	storedJSON := []byte(
		`{"id":"55555555-5555-4555-8555-555555555555","chat_id":"` +
			handlerChat + `","legacy_message_field":"preserved"}`,
	)
	repo := &handlerReplayRepository{
		messageResult: &pkgidempotency.MutationResult[model.Message]{
			Outcome:        pkgidempotency.OutcomeReplay,
			ResponseStatus: fiber.StatusCreated,
			ReplayJSON:     storedJSON,
		},
	}
	handler := NewChatHandler(service.NewChatService(repo))
	handler.clock = func() time.Time { return receivedAt }
	app := fiber.New()
	app.Use(func(c *fiber.Ctx) error {
		c.Locals("userID", handlerActor)
		return c.Next()
	})
	app.Post("/api/chats/:id/messages", handler.CreateMessage)

	body := `{"type":"text","content":"hello"}`
	request := httptest.NewRequest(
		http.MethodPost,
		"/api/chats/"+handlerChat+"/messages",
		strings.NewReader(body),
	)
	request.Header.Set("Content-Type", "application/json")
	request.Header.Set("Idempotency-Key", handlerKey)
	response, err := app.Test(request)
	if err != nil {
		t.Fatalf("perform request: %v", err)
	}
	defer response.Body.Close()

	assertRawReplayResponse(t, response, storedJSON)
	if !repo.receivedAt.Equal(receivedAt) {
		t.Fatalf("received_at = %v, want %v", repo.receivedAt, receivedAt)
	}
}

func assertRawReplayResponse(t *testing.T, response *http.Response, want []byte) {
	t.Helper()
	if response.StatusCode != fiber.StatusCreated {
		t.Fatalf("status = %d, want %d", response.StatusCode, fiber.StatusCreated)
	}
	body, err := io.ReadAll(response.Body)
	if err != nil {
		t.Fatalf("read response: %v", err)
	}
	if string(body) != string(want) {
		t.Fatalf("raw replay changed:\n got: %s\nwant: %s", body, want)
	}
	if got := response.Header.Get("Content-Type"); !strings.HasPrefix(got, "application/json") {
		t.Fatalf("Content-Type = %q, want application/json", got)
	}
}
