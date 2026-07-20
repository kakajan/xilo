package service

import (
	"context"
	"sync"
	"testing"
	"time"

	"github.com/DATA-DOG/go-sqlmock"
	"github.com/jmoiron/sqlx"
	"github.com/xilo-platform/xilo/internal/comment/model"
	"github.com/xilo-platform/xilo/internal/comment/repository"
	notifrepo "github.com/xilo-platform/xilo/internal/notification/repository"
	notifsvc "github.com/xilo-platform/xilo/internal/notification/service"
)

type captureNotifier struct {
	mu   sync.Mutex
	reqs []notifsvc.NotifyRequest
}

func (c *captureNotifier) Notify(_ context.Context, req notifsvc.NotifyRequest) (*notifrepo.Notification, error) {
	c.mu.Lock()
	defer c.mu.Unlock()
	c.reqs = append(c.reqs, req)
	return &notifrepo.Notification{ID: "n1", UserID: req.RecipientID, Type: req.Type}, nil
}

func (c *captureNotifier) last() *notifsvc.NotifyRequest {
	c.mu.Lock()
	defer c.mu.Unlock()
	if len(c.reqs) == 0 {
		return nil
	}
	r := c.reqs[len(c.reqs)-1]
	return &r
}

func TestNotifyPostComment_NotifiesPostAuthor(t *testing.T) {
	db, mock, err := sqlmock.New()
	if err != nil {
		t.Fatal(err)
	}
	defer db.Close()
	sqlxDB := sqlx.NewDb(db, "sqlmock")
	repo := repository.NewCommentRepo(sqlxDB)
	svc := NewCommentService(repo)
	cap := &captureNotifier{}
	svc.SetNotifier(cap)

	postID := "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"
	actorID := "bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb"
	authorID := "cccccccc-cccc-cccc-cccc-cccccccccccc"
	commentID := "dddddddd-dddd-dddd-dddd-dddddddddddd"

	mock.ExpectQuery(`SELECT p.author_id, u.username, p.slug`).
		WithArgs(postID).
		WillReturnRows(sqlmock.NewRows([]string{"author_id", "username", "slug"}).
			AddRow(authorID, "usher", "hello-world"))

	comment := &model.Comment{
		ID:       commentID,
		PostID:   postID,
		AuthorID: actorID,
		Content:  "سلام از مخاطب",
	}
	svc.notifyPostComment(context.Background(), comment, actorID)

	req := cap.last()
	if req == nil {
		t.Fatal("expected Notify call")
	}
	if req.Type != notifsvc.TypePostComment {
		t.Fatalf("type: got %q want %q", req.Type, notifsvc.TypePostComment)
	}
	if req.RecipientID != authorID {
		t.Fatalf("recipient: got %q want %q", req.RecipientID, authorID)
	}
	if req.ActorID != actorID {
		t.Fatalf("actor: got %q want %q", req.ActorID, actorID)
	}
	if req.Data["slug"] != "hello-world" {
		t.Fatalf("slug data: %v", req.Data["slug"])
	}
	if req.Data["post_author_username"] != "usher" {
		t.Fatalf("post_author_username: %v", req.Data["post_author_username"])
	}
	if req.Data["comment_id"] != commentID {
		t.Fatalf("comment_id data: %v", req.Data["comment_id"])
	}
	if err := mock.ExpectationsWereMet(); err != nil {
		t.Fatal(err)
	}
}

func TestNotifyAfterCreate_TopLevelUsesPostComment(t *testing.T) {
	db, mock, err := sqlmock.New()
	if err != nil {
		t.Fatal(err)
	}
	defer db.Close()
	sqlxDB := sqlx.NewDb(db, "sqlmock")
	repo := repository.NewCommentRepo(sqlxDB)
	svc := NewCommentService(repo)
	cap := &captureNotifier{}
	svc.SetNotifier(cap)

	postID := "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"
	actorID := "bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb"
	authorID := "cccccccc-cccc-cccc-cccc-cccccccccccc"

	mock.ExpectQuery(`SELECT p.author_id, u.username, p.slug`).
		WithArgs(postID).
		WillReturnRows(sqlmock.NewRows([]string{"author_id", "username", "slug"}).
			AddRow(authorID, "usher", "post-slug"))

	svc.notifyAfterCreate(context.Background(), &model.Comment{
		ID:       "c1",
		PostID:   postID,
		AuthorID: actorID,
		Content:  "top level",
	}, actorID)

	req := cap.last()
	if req == nil || req.Type != notifsvc.TypePostComment {
		t.Fatalf("expected post_comment notify, got %+v", req)
	}
	if err := mock.ExpectationsWereMet(); err != nil {
		t.Fatal(err)
	}
}

func TestNotifyAfterCreate_ReplyUsesCommentReply(t *testing.T) {
	db, mock, err := sqlmock.New()
	if err != nil {
		t.Fatal(err)
	}
	defer db.Close()
	sqlxDB := sqlx.NewDb(db, "sqlmock")
	repo := repository.NewCommentRepo(sqlxDB)
	svc := NewCommentService(repo)
	cap := &captureNotifier{}
	svc.SetNotifier(cap)

	parentID := "eeeeeeee-eeee-eeee-eeee-eeeeeeeeeeee"
	parentAuthor := "ffffffff-ffff-ffff-ffff-ffffffffffff"
	actorID := "bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb"
	postID := "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"

	now := time.Now().UTC()
	mock.ExpectQuery(`SELECT c.id, c.post_id, c.author_id`).
		WithArgs(parentID).
		WillReturnRows(sqlmock.NewRows([]string{
			"id", "post_id", "author_id", "parent_id", "root_id", "depth",
			"content", "content_html", "media_url", "is_pinned", "is_spam",
			"created_at", "updated_at",
			"user_id", "username", "display_name", "avatar_url",
		}).AddRow(
			parentID, postID, parentAuthor, nil, nil, 0,
			"parent", "parent", "", false, false,
			now, now,
			parentAuthor, "parentuser", "Parent", "",
		))
	mock.ExpectQuery(`SELECT p.author_id, u.username, p.slug`).
		WithArgs(postID).
		WillReturnRows(sqlmock.NewRows([]string{"author_id", "username", "slug"}).
			AddRow("post-author", "usher", "hello-world"))

	parent := parentID
	svc.notifyAfterCreate(context.Background(), &model.Comment{
		ID:       "c2",
		PostID:   postID,
		AuthorID: actorID,
		ParentID: &parent,
		Content:  "reply body",
	}, actorID)

	req := cap.last()
	if req == nil || req.Type != notifsvc.TypeCommentReply {
		t.Fatalf("expected comment_reply notify, got %+v", req)
	}
	if req.RecipientID != parentAuthor {
		t.Fatalf("recipient: got %q want %q", req.RecipientID, parentAuthor)
	}
	if req.Data["post_author_username"] != "usher" {
		t.Fatalf("post_author_username: %v", req.Data["post_author_username"])
	}
	if err := mock.ExpectationsWereMet(); err != nil {
		t.Fatal(err)
	}
}
