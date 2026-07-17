package handler

import (
	"net/http/httptest"
	"testing"

	"github.com/DATA-DOG/go-sqlmock"
	"github.com/gofiber/fiber/v2"
	"github.com/jmoiron/sqlx"
)

func TestToggleRepost_CreateAndRemove(t *testing.T) {
	db, mock, err := sqlmock.New()
	if err != nil {
		t.Fatalf("sqlmock: %v", err)
	}
	defer db.Close()

	sqlxDB := sqlx.NewDb(db, "sqlmock")
	h := NewSocialHandler(sqlxDB)

	app := fiber.New()
	app.Post("/posts/:id/repost", func(c *fiber.Ctx) error {
		c.Locals("userID", "user-1")
		return h.ToggleRepost(c)
	})
	app.Delete("/posts/:id/repost", func(c *fiber.Ctx) error {
		c.Locals("userID", "user-1")
		return h.ToggleRepost(c)
	})

	postID := "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa"

	mock.ExpectQuery(`SELECT EXISTS`).
		WithArgs(postID).
		WillReturnRows(sqlmock.NewRows([]string{"exists"}).AddRow(true))
	mock.ExpectExec(`INSERT INTO reposts`).
		WithArgs("user-1", postID).
		WillReturnResult(sqlmock.NewResult(1, 1))
	mock.ExpectQuery(`SELECT COUNT`).
		WithArgs(postID).
		WillReturnRows(sqlmock.NewRows([]string{"count"}).AddRow(3))

	req := httptest.NewRequest("POST", "/posts/"+postID+"/repost", nil)
	resp, err := app.Test(req)
	if err != nil {
		t.Fatalf("POST: %v", err)
	}
	if resp.StatusCode != fiber.StatusOK {
		t.Fatalf("POST status = %d", resp.StatusCode)
	}

	mock.ExpectQuery(`SELECT EXISTS`).
		WithArgs(postID).
		WillReturnRows(sqlmock.NewRows([]string{"exists"}).AddRow(true))
	mock.ExpectExec(`DELETE FROM reposts`).
		WithArgs("user-1", postID).
		WillReturnResult(sqlmock.NewResult(0, 1))
	mock.ExpectQuery(`SELECT COUNT`).
		WithArgs(postID).
		WillReturnRows(sqlmock.NewRows([]string{"count"}).AddRow(2))

	req = httptest.NewRequest("DELETE", "/posts/"+postID+"/repost", nil)
	resp, err = app.Test(req)
	if err != nil {
		t.Fatalf("DELETE: %v", err)
	}
	if resp.StatusCode != fiber.StatusOK {
		t.Fatalf("DELETE status = %d", resp.StatusCode)
	}

	if err := mock.ExpectationsWereMet(); err != nil {
		t.Fatalf("unmet expectations: %v", err)
	}
}

func TestToggleCommentBookmark_CreateAndRemove(t *testing.T) {
	db, mock, err := sqlmock.New()
	if err != nil {
		t.Fatalf("sqlmock: %v", err)
	}
	defer db.Close()

	sqlxDB := sqlx.NewDb(db, "sqlmock")
	h := NewSocialHandler(sqlxDB)

	app := fiber.New()
	app.Post("/comments/:id/bookmark", func(c *fiber.Ctx) error {
		c.Locals("userID", "user-1")
		return h.ToggleCommentBookmark(c)
	})
	app.Delete("/comments/:id/bookmark", func(c *fiber.Ctx) error {
		c.Locals("userID", "user-1")
		return h.ToggleCommentBookmark(c)
	})

	commentID := "cccccccc-cccc-4ccc-8ccc-cccccccccccc"

	mock.ExpectQuery(`SELECT EXISTS`).
		WithArgs(commentID).
		WillReturnRows(sqlmock.NewRows([]string{"exists"}).AddRow(true))
	mock.ExpectExec(`INSERT INTO comment_bookmarks`).
		WithArgs("user-1", commentID).
		WillReturnResult(sqlmock.NewResult(1, 1))

	req := httptest.NewRequest("POST", "/comments/"+commentID+"/bookmark", nil)
	resp, err := app.Test(req)
	if err != nil {
		t.Fatalf("POST: %v", err)
	}
	if resp.StatusCode != fiber.StatusOK {
		t.Fatalf("POST status = %d", resp.StatusCode)
	}

	mock.ExpectQuery(`SELECT EXISTS`).
		WithArgs(commentID).
		WillReturnRows(sqlmock.NewRows([]string{"exists"}).AddRow(true))
	mock.ExpectExec(`DELETE FROM comment_bookmarks`).
		WithArgs("user-1", commentID).
		WillReturnResult(sqlmock.NewResult(0, 1))

	req = httptest.NewRequest("DELETE", "/comments/"+commentID+"/bookmark", nil)
	resp, err = app.Test(req)
	if err != nil {
		t.Fatalf("DELETE: %v", err)
	}
	if resp.StatusCode != fiber.StatusOK {
		t.Fatalf("DELETE status = %d", resp.StatusCode)
	}

	if err := mock.ExpectationsWereMet(); err != nil {
		t.Fatalf("unmet expectations: %v", err)
	}
}

func TestListCommentBookmarks_Empty(t *testing.T) {
	db, mock, err := sqlmock.New()
	if err != nil {
		t.Fatalf("sqlmock: %v", err)
	}
	defer db.Close()

	sqlxDB := sqlx.NewDb(db, "sqlmock")
	h := NewSocialHandler(sqlxDB)

	app := fiber.New()
	app.Get("/bookmarks/comments", func(c *fiber.Ctx) error {
		c.Locals("userID", "user-1")
		return h.ListCommentBookmarks(c)
	})

	mock.ExpectQuery(`FROM comment_bookmarks`).
		WithArgs("user-1").
		WillReturnRows(sqlmock.NewRows([]string{
			"id", "post_id", "post_slug", "post_title", "author_id", "parent_id", "root_id",
			"depth", "content", "is_pinned", "created_at", "author_username", "author_name", "author_avatar",
		}))

	req := httptest.NewRequest("GET", "/bookmarks/comments", nil)
	resp, err := app.Test(req)
	if err != nil {
		t.Fatalf("GET: %v", err)
	}
	if resp.StatusCode != fiber.StatusOK {
		t.Fatalf("status = %d", resp.StatusCode)
	}
	if err := mock.ExpectationsWereMet(); err != nil {
		t.Fatalf("unmet expectations: %v", err)
	}
}

func TestToggleRepost_PostNotFound(t *testing.T) {
	db, mock, err := sqlmock.New()
	if err != nil {
		t.Fatalf("sqlmock: %v", err)
	}
	defer db.Close()

	sqlxDB := sqlx.NewDb(db, "sqlmock")
	h := NewSocialHandler(sqlxDB)

	app := fiber.New()
	app.Post("/posts/:id/repost", func(c *fiber.Ctx) error {
		c.Locals("userID", "user-1")
		return h.ToggleRepost(c)
	})

	postID := "bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb"
	mock.ExpectQuery(`SELECT EXISTS`).
		WithArgs(postID).
		WillReturnRows(sqlmock.NewRows([]string{"exists"}).AddRow(false))

	req := httptest.NewRequest("POST", "/posts/"+postID+"/repost", nil)
	resp, err := app.Test(req)
	if err != nil {
		t.Fatalf("POST: %v", err)
	}
	if resp.StatusCode != fiber.StatusNotFound {
		t.Fatalf("status = %d, want 404", resp.StatusCode)
	}
	if err := mock.ExpectationsWereMet(); err != nil {
		t.Fatalf("unmet expectations: %v", err)
	}
}
