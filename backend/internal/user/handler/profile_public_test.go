package handler

import (
	"database/sql"
	"encoding/json"
	"io"
	"net/http/httptest"
	"testing"
	"time"

	"github.com/DATA-DOG/go-sqlmock"
	"github.com/gofiber/fiber/v2"
	"github.com/jmoiron/sqlx"
)

func TestGetPublicProfile_AllowsNullDisplayNameAndRole(t *testing.T) {
	db, mock, err := sqlmock.New()
	if err != nil {
		t.Fatalf("sqlmock: %v", err)
	}
	defer db.Close()

	sqlxDB := sqlx.NewDb(db, "sqlmock")
	h := NewProfileHandler(sqlxDB, nil, nil, nil)

	app := fiber.New()
	app.Get("/users/:username", h.GetPublicProfile)

	userID := "008d7930-1093-4019-b986-a2108596118c"
	now := time.Now().UTC()

	mock.ExpectQuery(`SELECT id, username`).
		WithArgs("mosen").
		WillReturnRows(sqlmock.NewRows([]string{
			"id", "username", "display_name", "avatar_url", "bio", "role", "created_at",
		}).AddRow(userID, "mosen", "", "", "", "reader", now))

	mock.ExpectQuery(`SELECT COUNT\(\*\) FROM posts`).
		WithArgs(userID).
		WillReturnRows(sqlmock.NewRows([]string{"count"}).AddRow(2))
	mock.ExpectQuery(`SELECT COUNT\(\*\) FROM follows WHERE following_id`).
		WithArgs(userID).
		WillReturnRows(sqlmock.NewRows([]string{"count"}).AddRow(1))
	mock.ExpectQuery(`SELECT COUNT\(\*\) FROM follows WHERE follower_id`).
		WithArgs(userID).
		WillReturnRows(sqlmock.NewRows([]string{"count"}).AddRow(3))

	req := httptest.NewRequest("GET", "/users/mosen", nil)
	resp, err := app.Test(req)
	if err != nil {
		t.Fatalf("request: %v", err)
	}
	body, _ := io.ReadAll(resp.Body)
	if resp.StatusCode != fiber.StatusOK {
		t.Fatalf("status = %d body=%s", resp.StatusCode, body)
	}

	var payload struct {
		Username    string `json:"username"`
		DisplayName string `json:"display_name"`
		Stats       struct {
			Posts     int `json:"posts"`
			Followers int `json:"followers"`
			Following int `json:"following"`
		} `json:"stats"`
	}
	if err := json.Unmarshal(body, &payload); err != nil {
		t.Fatalf("decode: %v", err)
	}
	if payload.Username != "mosen" {
		t.Fatalf("username = %q", payload.Username)
	}
	if payload.DisplayName != "" {
		t.Fatalf("display_name = %q, want empty", payload.DisplayName)
	}
	if payload.Stats.Posts != 2 || payload.Stats.Followers != 1 || payload.Stats.Following != 3 {
		t.Fatalf("unexpected stats: %+v", payload.Stats)
	}
	if err := mock.ExpectationsWereMet(); err != nil {
		t.Fatalf("unmet expectations: %v", err)
	}
}

func TestGetPublicProfile_NotFound(t *testing.T) {
	db, mock, err := sqlmock.New()
	if err != nil {
		t.Fatalf("sqlmock: %v", err)
	}
	defer db.Close()

	sqlxDB := sqlx.NewDb(db, "sqlmock")
	h := NewProfileHandler(sqlxDB, nil, nil, nil)

	app := fiber.New()
	app.Get("/users/:username", h.GetPublicProfile)

	mock.ExpectQuery(`SELECT id, username`).
		WithArgs("nouser").
		WillReturnError(sql.ErrNoRows)

	req := httptest.NewRequest("GET", "/users/nouser", nil)
	resp, err := app.Test(req)
	if err != nil {
		t.Fatalf("request: %v", err)
	}
	if resp.StatusCode != fiber.StatusNotFound {
		body, _ := io.ReadAll(resp.Body)
		t.Fatalf("status = %d body=%s", resp.StatusCode, body)
	}
	if err := mock.ExpectationsWereMet(); err != nil {
		t.Fatalf("unmet expectations: %v", err)
	}
}
