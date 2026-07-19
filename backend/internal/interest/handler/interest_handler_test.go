package handler

import (
	"bytes"
	"encoding/json"
	"fmt"
	"net/http/httptest"
	"testing"
	"time"

	"github.com/DATA-DOG/go-sqlmock"
	"github.com/gofiber/fiber/v2"
	"github.com/jmoiron/sqlx"
	"github.com/xilo-platform/xilo/internal/interest/repository"
)

func TestPutMyInterests_Max20(t *testing.T) {
	db, mock, err := sqlmock.New()
	if err != nil {
		t.Fatalf("sqlmock: %v", err)
	}
	defer db.Close()

	h := NewInterestHandler(repository.NewInterestRepo(sqlx.NewDb(db, "sqlmock")))
	app := fiber.New()
	app.Put("/me/interests", func(c *fiber.Ctx) error {
		c.Locals("userID", "user-1")
		return h.PutMyInterests(c)
	})

	ids := make([]string, 21)
	for i := range ids {
		ids[i] = fmt.Sprintf("00000000-0000-4000-8000-%012d", i+1)
	}
	body, _ := json.Marshal(map[string]any{"interest_ids": ids})
	req := httptest.NewRequest("PUT", "/me/interests", bytes.NewReader(body))
	req.Header.Set("Content-Type", "application/json")

	resp, err := app.Test(req)
	if err != nil {
		t.Fatalf("request: %v", err)
	}
	if resp.StatusCode != fiber.StatusBadRequest {
		t.Fatalf("status = %d, want 400", resp.StatusCode)
	}
	if err := mock.ExpectationsWereMet(); err != nil {
		t.Fatalf("unexpected DB calls: %v", err)
	}
}

func TestPutMyInterests_Success(t *testing.T) {
	db, mock, err := sqlmock.New(sqlmock.QueryMatcherOption(sqlmock.QueryMatcherRegexp))
	if err != nil {
		t.Fatalf("sqlmock: %v", err)
	}
	defer db.Close()

	h := NewInterestHandler(repository.NewInterestRepo(sqlx.NewDb(db, "sqlmock")))
	app := fiber.New()
	app.Put("/me/interests", func(c *fiber.Ctx) error {
		c.Locals("userID", "user-1")
		return h.PutMyInterests(c)
	})

	id1 := "11111111-1111-4111-8111-111111111111"
	body, _ := json.Marshal(map[string]any{"interest_ids": []string{id1}})
	req := httptest.NewRequest("PUT", "/me/interests", bytes.NewReader(body))
	req.Header.Set("Content-Type", "application/json")

	mock.ExpectQuery(`SELECT COUNT`).
		WithArgs(id1).
		WillReturnRows(sqlmock.NewRows([]string{"count"}).AddRow(1))
	mock.ExpectBegin()
	mock.ExpectExec(`DELETE FROM user_interests`).
		WithArgs("user-1").
		WillReturnResult(sqlmock.NewResult(0, 0))
	mock.ExpectExec(`INSERT INTO user_interests`).
		WithArgs("user-1", id1).
		WillReturnResult(sqlmock.NewResult(1, 1))
	mock.ExpectCommit()
	now := time.Now()
	mock.ExpectQuery(`SELECT i.id`).
		WithArgs("user-1").
		WillReturnRows(sqlmock.NewRows([]string{
			"id", "slug", "labels", "icon", "sort_order", "is_active", "created_at", "updated_at",
		}).AddRow(id1, "music", []byte(`{"en":"Music","fa":"موسیقی"}`), nil, 0, true, now, now))
	mock.ExpectQuery(`SELECT ui.interest_id`).
		WithArgs("user-1").
		WillReturnRows(sqlmock.NewRows([]string{"interest_id"}).AddRow(id1))

	resp, err := app.Test(req)
	if err != nil {
		t.Fatalf("request: %v", err)
	}
	if resp.StatusCode != fiber.StatusOK {
		t.Fatalf("status = %d, want 200", resp.StatusCode)
	}
	if err := mock.ExpectationsWereMet(); err != nil {
		t.Fatalf("unmet expectations: %v", err)
	}
}

func TestReorder_InvalidBody(t *testing.T) {
	db, mock, err := sqlmock.New()
	if err != nil {
		t.Fatalf("sqlmock: %v", err)
	}
	defer db.Close()

	h := NewInterestHandler(repository.NewInterestRepo(sqlx.NewDb(db, "sqlmock")))
	app := fiber.New()
	app.Put("/reorder", h.Reorder)

	body, _ := json.Marshal(map[string]any{"ordered_ids": []string{"not-uuid"}})
	req := httptest.NewRequest("PUT", "/reorder", bytes.NewReader(body))
	req.Header.Set("Content-Type", "application/json")

	resp, err := app.Test(req)
	if err != nil {
		t.Fatalf("request: %v", err)
	}
	if resp.StatusCode != fiber.StatusBadRequest {
		t.Fatalf("status = %d, want 400", resp.StatusCode)
	}
	if err := mock.ExpectationsWereMet(); err != nil {
		t.Fatalf("unexpected DB calls: %v", err)
	}
}

func TestCreate_InvalidSlug(t *testing.T) {
	db, mock, err := sqlmock.New()
	if err != nil {
		t.Fatalf("sqlmock: %v", err)
	}
	defer db.Close()

	h := NewInterestHandler(repository.NewInterestRepo(sqlx.NewDb(db, "sqlmock")))
	app := fiber.New()
	app.Post("/", h.Create)

	body, _ := json.Marshal(map[string]any{
		"slug":   "Bad_Slug",
		"labels": map[string]string{"en": "X", "fa": "ی"},
	})
	req := httptest.NewRequest("POST", "/", bytes.NewReader(body))
	req.Header.Set("Content-Type", "application/json")

	resp, err := app.Test(req)
	if err != nil {
		t.Fatalf("request: %v", err)
	}
	if resp.StatusCode != fiber.StatusBadRequest {
		t.Fatalf("status = %d, want 400", resp.StatusCode)
	}
	if err := mock.ExpectationsWereMet(); err != nil {
		t.Fatalf("unexpected DB calls: %v", err)
	}
}
