package repository

import (
	"context"
	"testing"
	"time"

	"github.com/DATA-DOG/go-sqlmock"
	"github.com/jmoiron/sqlx"
	"github.com/lib/pq"
)

func TestListByPost_AllowsEmptyAuthorDisplayAndAvatar(t *testing.T) {
	db, mock, err := sqlmock.New()
	if err != nil {
		t.Fatalf("sqlmock: %v", err)
	}
	defer db.Close()

	sqlxDB := sqlx.NewDb(db, "sqlmock")
	repo := NewCommentRepo(sqlxDB)

	postID := "f2bc2945-d31b-49d0-9c3f-f6bf6785323a"
	commentID := "ec2642d8-daad-482e-8225-79f7a4264377"
	authorID := "ed7a4901-bab9-4a50-a590-98f5c71661fd"
	now := time.Now().UTC()

	// COALESCE in SQL yields empty strings for NULL display_name / avatar_url.
	mock.ExpectQuery(`SELECT c.id, c.post_id, c.author_id`).
		WithArgs(postID).
		WillReturnRows(sqlmock.NewRows([]string{
			"id", "post_id", "author_id", "parent_id", "root_id", "depth",
			"content", "content_html", "media_url", "is_pinned", "is_spam",
			"created_at", "updated_at",
			"user_id", "username", "display_name", "avatar_url",
		}).AddRow(
			commentID, postID, authorID, nil, nil, 0,
			".", ".", "", false, false,
			now, now,
			authorID, "sabi", "", "",
		))

	mock.ExpectQuery(`SELECT target_id, reaction, COUNT`).
		WithArgs(pq.Array([]string{commentID})).
		WillReturnRows(sqlmock.NewRows([]string{"target_id", "reaction", "count"}))

	comments, _, err := repo.ListByPost(context.Background(), postID, "", 20, "newest", "")
	if err != nil {
		t.Fatalf("ListByPost: %v", err)
	}
	if len(comments) != 1 {
		t.Fatalf("got %d comments, want 1", len(comments))
	}
	if comments[0].Author == nil {
		t.Fatal("author nil")
	}
	if comments[0].Author.Username != "sabi" {
		t.Fatalf("username = %q", comments[0].Author.Username)
	}
	if comments[0].Author.DisplayName != "" || comments[0].Author.AvatarURL != "" {
		t.Fatalf("expected empty display/avatar, got %q / %q",
			comments[0].Author.DisplayName, comments[0].Author.AvatarURL)
	}
	if err := mock.ExpectationsWereMet(); err != nil {
		t.Fatalf("unmet expectations: %v", err)
	}
}
