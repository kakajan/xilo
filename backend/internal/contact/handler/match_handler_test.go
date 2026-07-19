package handler

import (
	"bytes"
	"encoding/json"
	"net/http/httptest"
	"strings"
	"testing"

	"github.com/DATA-DOG/go-sqlmock"
	"github.com/gofiber/fiber/v2"
	"github.com/jmoiron/sqlx"
	"github.com/lib/pq"
	"github.com/xilo-platform/xilo/pkg/contacthash"
)

func TestValidateMatchLimits(t *testing.T) {
	if err := ValidateMatchLimits(nil, nil); err != nil {
		t.Fatalf("empty should be ok: %v", err)
	}
	phones := make([]string, 300)
	emails := make([]string, 200)
	if err := ValidateMatchLimits(phones, emails); err != nil {
		t.Fatalf("exactly 500 should be ok: %v", err)
	}
	emails = append(emails, "x")
	if err := ValidateMatchLimits(phones, emails); err != ErrTooManyHashes {
		t.Fatalf("501 should be ErrTooManyHashes, got %v", err)
	}
}

func TestMatch_RejectsOverLimit(t *testing.T) {
	db, _, err := sqlmock.New()
	if err != nil {
		t.Fatalf("sqlmock: %v", err)
	}
	defer db.Close()

	h := NewContactHandler(sqlx.NewDb(db, "sqlmock"), "test-pepper")
	app := fiber.New()
	app.Post("/api/contacts/match", func(c *fiber.Ctx) error {
		c.Locals("userID", "user-self")
		return h.Match(c)
	})

	hashes := make([]string, MaxCombinedHashes+1)
	for i := range hashes {
		hashes[i] = strings.Repeat("a", 64)
	}
	body, _ := json.Marshal(matchRequest{PhoneHashes: hashes})
	req := httptest.NewRequest("POST", "/api/contacts/match", bytes.NewReader(body))
	req.Header.Set("Content-Type", "application/json")
	resp, err := app.Test(req)
	if err != nil {
		t.Fatalf("request: %v", err)
	}
	if resp.StatusCode != fiber.StatusBadRequest {
		t.Fatalf("status = %d, want 400", resp.StatusCode)
	}
}

func TestMatch_FailClosedWithoutPepper(t *testing.T) {
	db, _, err := sqlmock.New()
	if err != nil {
		t.Fatalf("sqlmock: %v", err)
	}
	defer db.Close()

	h := NewContactHandler(sqlx.NewDb(db, "sqlmock"), "")
	app := fiber.New()
	app.Post("/api/contacts/match", func(c *fiber.Ctx) error {
		c.Locals("userID", "user-self")
		return h.Match(c)
	})

	body, _ := json.Marshal(matchRequest{PhoneHashes: []string{"ab"}})
	req := httptest.NewRequest("POST", "/api/contacts/match", bytes.NewReader(body))
	req.Header.Set("Content-Type", "application/json")
	resp, err := app.Test(req)
	if err != nil {
		t.Fatalf("request: %v", err)
	}
	if resp.StatusCode != fiber.StatusServiceUnavailable {
		t.Fatalf("status = %d, want 503", resp.StatusCode)
	}
}

func TestMatch_ReturnsMatchesWithFollowState(t *testing.T) {
	db, mock, err := sqlmock.New()
	if err != nil {
		t.Fatalf("sqlmock: %v", err)
	}
	defer db.Close()

	pepper := "test-pepper"
	h := NewContactHandler(sqlx.NewDb(db, "sqlmock"), pepper)
	app := fiber.New()
	app.Post("/api/contacts/match", func(c *fiber.Ctx) error {
		c.Locals("userID", "self-id")
		return h.Match(c)
	})

	clientPhone := contacthash.ClientSHA256Hex(contacthash.NormalizePhone("09121234567"))
	serverPhone := contacthash.ServerHMACHex(pepper, clientPhone)

	rows := sqlmock.NewRows([]string{"id", "username", "display_name", "avatar_url"}).
		AddRow("u1", "alice", "Alice", "https://img/a").
		AddRow("u2", "bob", "Bob", "")
	mock.ExpectQuery(`SELECT DISTINCT u.id`).
		WithArgs("self-id", pq.Array([]string{serverPhone}), pq.Array([]string{})).
		WillReturnRows(rows)

	mock.ExpectExec(`INSERT INTO user_contact_matches`).
		WithArgs("self-id", sqlmock.AnyArg(), pq.Array([]string{"u1", "u2"})).
		WillReturnResult(sqlmock.NewResult(0, 2))

	mock.ExpectQuery(`SELECT following_id FROM follows`).
		WithArgs("self-id", pq.Array([]string{"u1", "u2"})).
		WillReturnRows(sqlmock.NewRows([]string{"following_id"}).AddRow("u1"))

	body, _ := json.Marshal(matchRequest{PhoneHashes: []string{clientPhone}})
	req := httptest.NewRequest("POST", "/api/contacts/match", bytes.NewReader(body))
	req.Header.Set("Content-Type", "application/json")
	resp, err := app.Test(req)
	if err != nil {
		t.Fatalf("request: %v", err)
	}
	if resp.StatusCode != fiber.StatusOK {
		t.Fatalf("status = %d, want 200", resp.StatusCode)
	}

	var got struct {
		Matches []matchResponseItem `json:"matches"`
	}
	if err := json.NewDecoder(resp.Body).Decode(&got); err != nil {
		t.Fatalf("decode: %v", err)
	}
	if len(got.Matches) != 2 {
		t.Fatalf("matches len = %d, want 2", len(got.Matches))
	}
	if !got.Matches[0].AlreadyFollowing {
		t.Fatal("alice should be already_following")
	}
	if got.Matches[1].AlreadyFollowing {
		t.Fatal("bob should not be already_following")
	}
	if err := mock.ExpectationsWereMet(); err != nil {
		t.Fatalf("unmet expectations: %v", err)
	}
}

func TestList_ReturnsFollowingsWithFromContacts(t *testing.T) {
	db, mock, err := sqlmock.New()
	if err != nil {
		t.Fatalf("sqlmock: %v", err)
	}
	defer db.Close()

	h := NewContactHandler(sqlx.NewDb(db, "sqlmock"), "test-pepper")
	app := fiber.New()
	app.Get("/api/contacts", func(c *fiber.Ctx) error {
		c.Locals("userID", "self-id")
		return h.List(c)
	})

	rows := sqlmock.NewRows([]string{
		"id", "username", "display_name", "avatar_url", "role", "from_contacts",
	}).
		AddRow("u1", "alice", "Alice", "https://img/a", "writer", true).
		AddRow("u2", "bob", "Bob", "", "reader", false)
	mock.ExpectQuery(`SELECT u.id, u.username`).
		WithArgs("self-id").
		WillReturnRows(rows)

	req := httptest.NewRequest("GET", "/api/contacts", nil)
	resp, err := app.Test(req)
	if err != nil {
		t.Fatalf("request: %v", err)
	}
	if resp.StatusCode != fiber.StatusOK {
		t.Fatalf("status = %d, want 200", resp.StatusCode)
	}

	var got struct {
		Data []contactListItem `json:"data"`
	}
	if err := json.NewDecoder(resp.Body).Decode(&got); err != nil {
		t.Fatalf("decode: %v", err)
	}
	if len(got.Data) != 2 {
		t.Fatalf("data len = %d, want 2", len(got.Data))
	}
	if !got.Data[0].FromContacts || !got.Data[0].IsFollowing || !got.Data[0].IsVerified {
		t.Fatalf("alice flags unexpected: %+v", got.Data[0])
	}
	if got.Data[1].FromContacts || !got.Data[1].IsFollowing || got.Data[1].IsVerified {
		t.Fatalf("bob flags unexpected: %+v", got.Data[1])
	}
	if err := mock.ExpectationsWereMet(); err != nil {
		t.Fatalf("unmet expectations: %v", err)
	}
}

func TestMatch_EmptyArrays(t *testing.T) {
	db, _, err := sqlmock.New()
	if err != nil {
		t.Fatalf("sqlmock: %v", err)
	}
	defer db.Close()

	h := NewContactHandler(sqlx.NewDb(db, "sqlmock"), "test-pepper")
	app := fiber.New()
	app.Post("/api/contacts/match", func(c *fiber.Ctx) error {
		c.Locals("userID", "self-id")
		return h.Match(c)
	})

	body := []byte(`{}`)
	req := httptest.NewRequest("POST", "/api/contacts/match", bytes.NewReader(body))
	req.Header.Set("Content-Type", "application/json")
	resp, err := app.Test(req)
	if err != nil {
		t.Fatalf("request: %v", err)
	}
	if resp.StatusCode != fiber.StatusOK {
		t.Fatalf("status = %d, want 200", resp.StatusCode)
	}
}
