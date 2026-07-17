package service

import (
	"context"
	"encoding/json"
	"net/http"
	"sync"
	"testing"
	"time"

	"github.com/xilo-platform/xilo/internal/chat/model"
	"github.com/xilo-platform/xilo/internal/chat/repository"
	pkgidempotency "github.com/xilo-platform/xilo/pkg/idempotency"
	"github.com/xilo-platform/xilo/pkg/realtime"
)

type publicationRepository struct {
	ChatRepository
	message        *model.Message
	createResult   *pkgidempotency.MutationResult[model.Message]
	createErr      error
	updateResult   *model.Message
	deleteErr      error
	readResult     *model.Read
	reactionResult *model.ReactionResult
}

func (r *publicationRepository) CreateMessage(
	context.Context,
	string,
	string,
	pkgidempotency.Request,
	*model.CreateMessageRequest,
) (*pkgidempotency.MutationResult[model.Message], error) {
	return r.createResult, r.createErr
}

func (r *publicationRepository) GetMessage(context.Context, string) (*model.Message, error) {
	if r.message == nil {
		return nil, repository.ErrMessageNotFound
	}
	copy := *r.message
	return &copy, nil
}

func (r *publicationRepository) GetMembership(
	context.Context,
	string,
	string,
) (*model.ChatMember, error) {
	return &model.ChatMember{ChatID: testChat, UserID: testActor}, nil
}

func (r *publicationRepository) UpdateMessage(
	context.Context,
	string,
	string,
	string,
) (*model.Message, error) {
	return r.updateResult, nil
}

func (r *publicationRepository) DeleteMessage(context.Context, string, string) error {
	return r.deleteErr
}

func (r *publicationRepository) MarkRead(
	context.Context,
	string,
	string,
) (*model.Read, error) {
	return r.readResult, nil
}

func (r *publicationRepository) ToggleReaction(
	context.Context,
	string,
	string,
	string,
) (*model.ReactionResult, error) {
	return r.reactionResult, nil
}

type recordingPublisher struct {
	mu         sync.Mutex
	deliveries []realtime.Delivery
}

func (p *recordingPublisher) Publish(
	_ context.Context,
	delivery realtime.Delivery,
) error {
	p.mu.Lock()
	defer p.mu.Unlock()
	p.deliveries = append(p.deliveries, delivery)
	return nil
}

func (p *recordingPublisher) one(t *testing.T) realtime.Delivery {
	t.Helper()
	p.mu.Lock()
	defer p.mu.Unlock()
	if len(p.deliveries) != 1 {
		t.Fatalf("published %d events, want 1", len(p.deliveries))
	}
	return p.deliveries[0]
}

func (p *recordingPublisher) count() int {
	p.mu.Lock()
	defer p.mu.Unlock()
	return len(p.deliveries)
}

func TestCommittedMessageMutationsPublishRealtimeEvents(t *testing.T) {
	now := time.Now().UTC()
	baseMessage := &model.Message{
		ID:        testMsg,
		ChatID:    testChat,
		SenderID:  testActor,
		Type:      model.MessageTypeText,
		CreatedAt: now.Add(-time.Hour),
		UpdatedAt: now,
		Reactions: []model.Reaction{},
		ReadBy:    []model.Read{},
	}

	tests := []struct {
		name      string
		wantEvent string
		mutate    func(*ChatService, *publicationRepository) error
	}{
		{
			name:      "create",
			wantEvent: realtime.EventMessageReceive,
			mutate: func(svc *ChatService, repo *publicationRepository) error {
				repo.createResult = &pkgidempotency.MutationResult[model.Message]{
					Value:          baseMessage,
					Outcome:        pkgidempotency.OutcomeNew,
					ResponseStatus: http.StatusCreated,
				}
				content := "created"
				_, err := svc.CreateMessage(
					context.Background(),
					testActor,
					testChat,
					testIdempotencyKey,
					now,
					&model.CreateMessageRequest{Type: model.MessageTypeText, Content: &content},
				)
				return err
			},
		},
		{
			name:      "edit",
			wantEvent: realtime.EventMessageEdit,
			mutate: func(svc *ChatService, repo *publicationRepository) error {
				content := "edited"
				editedAt := now
				updated := *baseMessage
				updated.Content = &content
				updated.EditedAt = &editedAt
				updated.UpdatedAt = now
				repo.updateResult = &updated
				_, err := svc.UpdateMessage(
					context.Background(),
					testActor,
					testMsg,
					&model.UpdateMessageRequest{Content: content},
				)
				return err
			},
		},
		{
			name:      "delete",
			wantEvent: realtime.EventMessageDelete,
			mutate: func(svc *ChatService, _ *publicationRepository) error {
				return svc.DeleteMessage(context.Background(), testActor, testMsg)
			},
		},
		{
			name:      "read",
			wantEvent: realtime.EventMessageRead,
			mutate: func(svc *ChatService, repo *publicationRepository) error {
				repo.readResult = &model.Read{UserID: testActor, ReadAt: now}
				_, err := svc.MarkRead(context.Background(), testActor, testMsg)
				return err
			},
		},
		{
			name:      "reaction",
			wantEvent: realtime.EventMessageReaction,
			mutate: func(svc *ChatService, repo *publicationRepository) error {
				repo.reactionResult = &model.ReactionResult{
					Reaction: "👍",
					Active:   true,
					Count:    1,
				}
				_, err := svc.ToggleReaction(
					context.Background(),
					testActor,
					testMsg,
					&model.ReactionRequest{Reaction: "👍"},
				)
				return err
			},
		},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			repo := &publicationRepository{message: baseMessage}
			publisher := &recordingPublisher{}
			svc := NewChatServiceWithPublisher(repo, publisher)
			svc.clock = func() time.Time { return now }

			if err := tt.mutate(svc, repo); err != nil {
				t.Fatalf("mutation returned error: %v", err)
			}
			delivery := publisher.one(t)
			if delivery.Channel != "chat:"+testChat {
				t.Fatalf("channel = %q, want chat channel", delivery.Channel)
			}
			if delivery.Envelope.Event != tt.wantEvent {
				t.Fatalf("event = %q, want %q", delivery.Envelope.Event, tt.wantEvent)
			}
			if tt.wantEvent == realtime.EventMessageReceive {
				if delivery.Envelope.OperationKey != testIdempotencyKey {
					t.Fatalf(
						"operation key = %q, want %q",
						delivery.Envelope.OperationKey,
						testIdempotencyKey,
					)
				}
				var payload realtime.MessagePayload
				if err := json.Unmarshal(delivery.Envelope.Data, &payload); err != nil {
					t.Fatalf("decode message.receive payload: %v", err)
				}
				if payload.ID != testMsg || payload.ChatID != testChat {
					t.Fatalf("unexpected message.receive payload: %+v", payload)
				}
			}
			if delivery.Envelope.EventID == "" || delivery.Envelope.Sequence == 0 {
				t.Fatalf("event is missing reconciliation metadata: %+v", delivery.Envelope)
			}
			if !json.Valid(delivery.Envelope.Data) {
				t.Fatal("event payload is not valid JSON")
			}
		})
	}
}

func TestFailedOrReplayedMessageCreateDoesNotBroadcast(t *testing.T) {
	t.Run("failure", func(t *testing.T) {
		publisher := &recordingPublisher{}
		svc := NewChatServiceWithPublisher(
			&publicationRepository{createErr: repository.ErrRetryable},
			publisher,
		)
		content := "hello"
		_, err := svc.CreateMessage(
			context.Background(),
			testActor,
			testChat,
			testIdempotencyKey,
			testReceivedAt,
			&model.CreateMessageRequest{Type: model.MessageTypeText, Content: &content},
		)
		if err == nil {
			t.Fatal("expected create failure")
		}
		if publisher.count() != 0 {
			t.Fatal("failed mutation was broadcast")
		}
	})

	t.Run("idempotent replay", func(t *testing.T) {
		publisher := &recordingPublisher{}
		replayJSON := []byte(`{"id":"` + testMsg + `","chat_id":"` + testChat + `"}`)
		svc := NewChatServiceWithPublisher(
			&publicationRepository{
				createResult: &pkgidempotency.MutationResult[model.Message]{
					Outcome:        pkgidempotency.OutcomeReplay,
					ResponseStatus: http.StatusCreated,
					ReplayJSON:     replayJSON,
				},
			},
			publisher,
		)
		content := "hello"
		result, err := svc.CreateMessage(
			context.Background(),
			testActor,
			testChat,
			testIdempotencyKey,
			testReceivedAt,
			&model.CreateMessageRequest{Type: model.MessageTypeText, Content: &content},
		)
		if err != nil {
			t.Fatalf("replay returned error: %v", err)
		}
		if !result.IsReplay() {
			t.Fatal("expected replay result")
		}
		if publisher.count() != 0 {
			t.Fatal("replayed mutation was broadcast again")
		}
	})
}
