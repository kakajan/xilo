package repository

import (
	"context"
	"testing"
	"time"

	"github.com/DATA-DOG/go-sqlmock"
	"github.com/jmoiron/sqlx"
	"github.com/lib/pq"
)

func TestDelete_ClearsContentAndSetsDeletedAt(t *testing.T) {
	db, mock, err := sqlmock.New()
	if err != nil {
		t.Fatalf("sqlmock: %v", err)
	}
	defer db.Close()

	sqlxDB := sqlx.NewDb(db, "sqlmock")
	repo := NewCommentRepo(sqlxDB)

	commentID := "ec2642d8-daad-482e-8225-79f7a4264377"
	mock.ExpectExec(`UPDATE comments\s+SET deleted_at = NOW\(\),\s+content = '',\s+content_html = '',\s+media_url = NULL`).
		WithArgs(commentID).
		WillReturnResult(sqlmock.NewResult(0, 1))

	if err := repo.Delete(context.Background(), commentID); err != nil {
		t.Fatalf("Delete: %v", err)
	}
	if err := mock.ExpectationsWereMet(); err != nil {
		t.Fatalf("unmet expectations: %v", err)
	}
}

func TestListByPost_IncludesDeletedParentWithLiveReply(t *testing.T) {
	db, mock, err := sqlmock.New()
	if err != nil {
		t.Fatalf("sqlmock: %v", err)
	}
	defer db.Close()

	sqlxDB := sqlx.NewDb(db, "sqlmock")
	repo := NewCommentRepo(sqlxDB)

	postID := "f2bc2945-d31b-49d0-9c3f-f6bf6785323a"
	parentID := "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"
	childID := "bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb"
	authorID := "ed7a4901-bab9-4a50-a590-98f5c71661fd"
	now := time.Now().UTC()
	deletedAt := now.Add(-time.Minute)

	mock.ExpectQuery(`SELECT c.id, c.post_id, c.author_id`).
		WithArgs(postID).
		WillReturnRows(sqlmock.NewRows([]string{
			"id", "post_id", "author_id", "parent_id", "root_id", "depth",
			"content", "content_html", "media_url", "is_pinned", "is_spam",
			"repost_count", "created_at", "updated_at", "deleted_at",
			"user_id", "username", "display_name", "avatar_url",
		}).AddRow(
			parentID, postID, authorID, nil, nil, 0,
			"", "", "", false, false,
			0, now.Add(-2*time.Minute), now, deletedAt,
			authorID, "sabi", "Sabi", "",
		).AddRow(
			childID, postID, authorID, parentID, parentID, 1,
			"still here", "still here", "", false, false,
			0, now, now, nil,
			authorID, "sabi", "Sabi", "",
		))

	ids := pq.Array([]string{parentID, childID})
	mock.ExpectQuery(`SELECT target_id, reaction, COUNT`).
		WithArgs(ids).
		WillReturnRows(sqlmock.NewRows([]string{"target_id", "reaction", "count"}))

	comments, _, err := repo.ListByPost(context.Background(), postID, "", 20, "oldest", "")
	if err != nil {
		t.Fatalf("ListByPost: %v", err)
	}
	if len(comments) != 1 {
		t.Fatalf("got %d roots, want 1", len(comments))
	}
	parent := comments[0]
	if !parent.IsDeleted || parent.DeletedAt == nil {
		t.Fatalf("parent should be deleted tombstone, IsDeleted=%v DeletedAt=%v", parent.IsDeleted, parent.DeletedAt)
	}
	if parent.Content != "" {
		t.Fatalf("tombstone content = %q, want empty", parent.Content)
	}
	if len(parent.Replies) != 1 || parent.Replies[0].ID != childID {
		t.Fatalf("replies = %#v, want child %s nested under parent", parent.Replies, childID)
	}
	if parent.Replies[0].IsDeleted {
		t.Fatal("live reply should not be marked deleted")
	}
	if err := mock.ExpectationsWereMet(); err != nil {
		t.Fatalf("unmet expectations: %v", err)
	}
}

func TestListByPost_OmitsDeletedCommentWithoutLiveReplies(t *testing.T) {
	db, mock, err := sqlmock.New()
	if err != nil {
		t.Fatalf("sqlmock: %v", err)
	}
	defer db.Close()

	sqlxDB := sqlx.NewDb(db, "sqlmock")
	repo := NewCommentRepo(sqlxDB)

	postID := "f2bc2945-d31b-49d0-9c3f-f6bf6785323a"
	liveID := "cccccccc-cccc-cccc-cccc-cccccccccccc"
	authorID := "ed7a4901-bab9-4a50-a590-98f5c71661fd"
	now := time.Now().UTC()

	// SQL filters out deleted parents with no live children; mock returns only the live row.
	mock.ExpectQuery(`SELECT c.id, c.post_id, c.author_id`).
		WithArgs(postID).
		WillReturnRows(sqlmock.NewRows([]string{
			"id", "post_id", "author_id", "parent_id", "root_id", "depth",
			"content", "content_html", "media_url", "is_pinned", "is_spam",
			"repost_count", "created_at", "updated_at", "deleted_at",
			"user_id", "username", "display_name", "avatar_url",
		}).AddRow(
			liveID, postID, authorID, nil, nil, 0,
			"alive", "alive", "", false, false,
			0, now, now, nil,
			authorID, "sabi", "Sabi", "",
		))

	mock.ExpectQuery(`SELECT target_id, reaction, COUNT`).
		WithArgs(pq.Array([]string{liveID})).
		WillReturnRows(sqlmock.NewRows([]string{"target_id", "reaction", "count"}))

	comments, _, err := repo.ListByPost(context.Background(), postID, "", 20, "newest", "")
	if err != nil {
		t.Fatalf("ListByPost: %v", err)
	}
	if len(comments) != 1 || comments[0].ID != liveID {
		t.Fatalf("got %#v, want only live comment", comments)
	}
	if comments[0].IsDeleted {
		t.Fatal("live comment marked deleted")
	}
	if err := mock.ExpectationsWereMet(); err != nil {
		t.Fatalf("unmet expectations: %v", err)
	}
}
