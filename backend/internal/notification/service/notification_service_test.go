package service

import (
	"context"
	"encoding/json"
	"sync"
	"testing"
	"time"

	"github.com/DATA-DOG/go-sqlmock"
	"github.com/jmoiron/sqlx"
	"github.com/xilo-platform/xilo/internal/notification/repository"
	"github.com/xilo-platform/xilo/pkg/realtime"
)

type capturePublisher struct {
	mu       sync.Mutex
	deliveries []realtime.Delivery
}

func (p *capturePublisher) Publish(_ context.Context, d realtime.Delivery) error {
	p.mu.Lock()
	defer p.mu.Unlock()
	p.deliveries = append(p.deliveries, d)
	return nil
}

func (p *capturePublisher) events() []string {
	p.mu.Lock()
	defer p.mu.Unlock()
	out := make([]string, 0, len(p.deliveries))
	for _, d := range p.deliveries {
		out = append(out, d.Envelope.Event)
	}
	return out
}

func TestNotifySkipsSelf(t *testing.T) {
	db, _, err := sqlmock.New()
	if err != nil {
		t.Fatal(err)
	}
	defer db.Close()
	sqlxDB := sqlx.NewDb(db, "sqlmock")
	pub := &capturePublisher{}
	svc := NewNotificationService(repository.NewNotificationRepo(sqlxDB), sqlxDB, nil, pub, NopPushSender{})

	n, err := svc.Notify(context.Background(), NotifyRequest{
		RecipientID: "user-a",
		ActorID:     "user-a",
		Type:        TypeCommentReply,
		Title:       "Reply",
	})
	if err != nil {
		t.Fatalf("unexpected err: %v", err)
	}
	if n != nil {
		t.Fatalf("expected nil notification for self")
	}
	if len(pub.events()) != 0 {
		t.Fatalf("expected no publish")
	}
}

func TestNotifyCreatesAndEmits(t *testing.T) {
	db, mock, err := sqlmock.New()
	if err != nil {
		t.Fatal(err)
	}
	defer db.Close()
	sqlxDB := sqlx.NewDb(db, "sqlmock")
	pub := &capturePublisher{}
	svc := NewNotificationService(repository.NewNotificationRepo(sqlxDB), sqlxDB, nil, pub, NopPushSender{})

	recipient := "11111111-1111-1111-1111-111111111111"
	actor := "22222222-2222-2222-2222-222222222222"
	now := time.Now().UTC()

	mock.ExpectQuery(`SELECT COALESCE\(comment_reply_web`).
		WithArgs(recipient).
		WillReturnRows(sqlmock.NewRows([]string{"web_enabled", "push_enabled"}).AddRow(true, false))

	mock.ExpectQuery(`INSERT INTO notifications`).
		WithArgs(recipient, TypeCommentReply, "New reply", "hello", sqlmock.AnyArg()).
		WillReturnRows(sqlmock.NewRows([]string{
			"id", "user_id", "type", "title", "body", "data", "is_read", "created_at",
		}).AddRow("33333333-3333-3333-3333-333333333333", recipient, TypeCommentReply, "New reply", "hello", `{"post_id":"p1"}`, false, now))

	mock.ExpectQuery(`SELECT COUNT\(\*\) FROM notifications`).
		WithArgs(recipient).
		WillReturnRows(sqlmock.NewRows([]string{"count"}).AddRow(1))

	n, err := svc.Notify(context.Background(), NotifyRequest{
		RecipientID: recipient,
		ActorID:     actor,
		Type:        TypeCommentReply,
		Title:       "New reply",
		Body:        "hello",
		Data:        map[string]any{"post_id": "p1"},
		SkipPush:    true,
	})
	if err != nil {
		t.Fatalf("Notify: %v", err)
	}
	if n == nil || n.ID == "" {
		t.Fatalf("expected notification row")
	}
	events := pub.events()
	if len(events) < 2 {
		t.Fatalf("expected notification.new and notification.count, got %v", events)
	}
	foundNew, foundCount := false, false
	for _, e := range events {
		if e == EventNotificationNew {
			foundNew = true
		}
		if e == EventNotificationCount {
			foundCount = true
		}
	}
	if !foundNew || !foundCount {
		t.Fatalf("missing events: %v", events)
	}
	if err := mock.ExpectationsWereMet(); err != nil {
		t.Fatal(err)
	}
}

func TestNotifyRespectsDisabledWebPref(t *testing.T) {
	db, mock, err := sqlmock.New()
	if err != nil {
		t.Fatal(err)
	}
	defer db.Close()
	sqlxDB := sqlx.NewDb(db, "sqlmock")
	pub := &capturePublisher{}
	svc := NewNotificationService(repository.NewNotificationRepo(sqlxDB), sqlxDB, nil, pub, NopPushSender{})

	recipient := "11111111-1111-1111-1111-111111111111"
	mock.ExpectQuery(`SELECT COALESCE\(comment_reply_web`).
		WithArgs(recipient).
		WillReturnRows(sqlmock.NewRows([]string{"web_enabled", "push_enabled"}).AddRow(false, false))

	n, err := svc.Notify(context.Background(), NotifyRequest{
		RecipientID: recipient,
		ActorID:     "22222222-2222-2222-2222-222222222222",
		Type:        TypeCommentReply,
		Title:       "New reply",
	})
	if err != nil {
		t.Fatalf("Notify: %v", err)
	}
	if n != nil {
		t.Fatalf("expected skip when prefs disabled")
	}
	if len(pub.events()) != 0 {
		t.Fatalf("expected no WS emit")
	}
	_ = json.RawMessage(`{}`)
	if err := mock.ExpectationsWereMet(); err != nil {
		t.Fatal(err)
	}
}
