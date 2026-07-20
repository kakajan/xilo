package handler

import (
	"encoding/json"
	"io"
	"net/http/httptest"
	"testing"
	"time"

	"github.com/DATA-DOG/go-sqlmock"
	"github.com/gofiber/fiber/v2"
	"github.com/jmoiron/sqlx"
)

func TestListUserFollowers_AllowsNullDisplayNameAndRole(t *testing.T) {
	db, mock, err := sqlmock.New()
	if err != nil {
		t.Fatalf("sqlmock: %v", err)
	}
	defer db.Close()

	sqlxDB := sqlx.NewDb(db, "sqlmock")
	h := NewProfileHandler(sqlxDB, nil, nil, nil)

	app := fiber.New()
	app.Get("/users/:username/followers", h.ListUserFollowers)

	targetID := "008d7930-1093-4019-b986-a2108596118c"
	followerID := "6c57729a-084b-4d7e-946e-32b8dd79eb00"
	now := time.Now().UTC()

	mock.ExpectQuery(`SELECT id FROM users WHERE username`).
		WithArgs("usher").
		WillReturnRows(sqlmock.NewRows([]string{"id"}).AddRow(targetID))

	mock.ExpectQuery(`SELECT u.id, u.username`).
		WithArgs(targetID, 21).
		WillReturnRows(sqlmock.NewRows([]string{
			"id", "username", "display_name", "avatar_url", "role", "created_at",
		}).AddRow(followerID, "mohammad", "", "https://example.com/a.png", "reader", now))

	req := httptest.NewRequest("GET", "/users/usher/followers?limit=20", nil)
	resp, err := app.Test(req)
	if err != nil {
		t.Fatalf("request: %v", err)
	}
	if resp.StatusCode != fiber.StatusOK {
		body, _ := io.ReadAll(resp.Body)
		t.Fatalf("status = %d body=%s", resp.StatusCode, body)
	}
	var payload struct {
		Data []struct {
			Username    string `json:"username"`
			DisplayName string `json:"display_name"`
		} `json:"data"`
	}
	if err := json.NewDecoder(resp.Body).Decode(&payload); err != nil {
		t.Fatalf("decode: %v", err)
	}
	if len(payload.Data) != 1 || payload.Data[0].Username != "mohammad" {
		t.Fatalf("unexpected payload: %+v", payload.Data)
	}
	if err := mock.ExpectationsWereMet(); err != nil {
		t.Fatalf("unmet expectations: %v", err)
	}
}
