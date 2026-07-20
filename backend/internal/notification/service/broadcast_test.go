package service

import (
	"context"
	"testing"
	"time"

	"github.com/DATA-DOG/go-sqlmock"
	"github.com/jmoiron/sqlx"
	"github.com/lib/pq"
	"github.com/xilo-platform/xilo/internal/notification/repository"
)

type countingPush struct {
	calls  int
	tokens int
}

func (p *countingPush) Send(_ context.Context, tokens []string, _, _ string, _ map[string]string) error {
	p.calls++
	p.tokens += len(tokens)
	return nil
}

func TestBroadcastToAllUsers(t *testing.T) {
	db, mock, err := sqlmock.New()
	if err != nil {
		t.Fatal(err)
	}
	defer db.Close()
	sqlxDB := sqlx.NewDb(db, "sqlmock")
	pub := &capturePublisher{}
	push := &countingPush{}
	svc := NewNotificationService(repository.NewNotificationRepo(sqlxDB), sqlxDB, nil, pub, push)

	u1 := "11111111-1111-1111-1111-111111111111"
	u2 := "22222222-2222-2222-2222-222222222222"
	now := time.Now().UTC()

	mock.ExpectQuery(`SELECT id FROM users WHERE deleted_at IS NULL`).
		WillReturnRows(sqlmock.NewRows([]string{"id"}).AddRow(u1).AddRow(u2))

	for _, uid := range []string{u1, u2} {
		mock.ExpectQuery(`INSERT INTO notifications`).
			WithArgs(uid, TypeSystemAnnouncement, "Hello", "World", sqlmock.AnyArg()).
			WillReturnRows(sqlmock.NewRows([]string{
				"id", "user_id", "type", "title", "body", "data", "is_read", "created_at",
			}).AddRow("33333333-3333-3333-3333-333333333333", uid, TypeSystemAnnouncement, "Hello", "World", `{}`, false, now))
		mock.ExpectQuery(`SELECT COUNT\(\*\) FROM notifications`).
			WithArgs(uid).
			WillReturnRows(sqlmock.NewRows([]string{"count"}).AddRow(1))
	}

	mock.ExpectQuery(`SELECT token FROM push_tokens WHERE user_id = ANY`).
		WithArgs(pq.Array([]string{u1, u2})).
		WillReturnRows(sqlmock.NewRows([]string{"token"}).AddRow("tok-a").AddRow("tok-b"))

	res, err := svc.BroadcastToAllUsers(context.Background(), BroadcastRequest{
		Title:     "Hello",
		Body:      "World",
		SendInbox: true,
		SendPush:  true,
	})
	if err != nil {
		t.Fatalf("BroadcastToAllUsers: %v", err)
	}
	if res.UsersTotal != 2 || res.InboxCreated != 2 {
		t.Fatalf("unexpected result: %+v", res)
	}
	if res.PushTokens != 2 || push.tokens != 2 {
		t.Fatalf("expected 2 push tokens, got result=%+v push=%+v", res, push)
	}
	if err := mock.ExpectationsWereMet(); err != nil {
		t.Fatal(err)
	}
}

func TestBroadcastRequiresTitle(t *testing.T) {
	db, _, err := sqlmock.New()
	if err != nil {
		t.Fatal(err)
	}
	defer db.Close()
	sqlxDB := sqlx.NewDb(db, "sqlmock")
	svc := NewNotificationService(repository.NewNotificationRepo(sqlxDB), sqlxDB, nil, &capturePublisher{}, NopPushSender{})
	_, err = svc.BroadcastToAllUsers(context.Background(), BroadcastRequest{Body: "x"})
	if err == nil {
		t.Fatal("expected title required error")
	}
}
