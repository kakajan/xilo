package realtime

import (
	"context"
	"net/http"
	"testing"

	"github.com/xilo-platform/xilo/internal/chat/model"
	"github.com/xilo-platform/xilo/internal/chat/service"
	pkgidempotency "github.com/xilo-platform/xilo/pkg/idempotency"
	pkgrealtime "github.com/xilo-platform/xilo/pkg/realtime"
)

const (
	gatewayUser = "11111111-1111-4111-8111-111111111111"
	gatewayChat = "22222222-2222-4222-8222-222222222222"
	gatewayMsg  = "33333333-3333-4333-8333-333333333333"
	gatewayKey  = "44444444-4444-4444-8444-444444444444"
)

type replayRepository struct {
	service.ChatRepository
}

func (replayRepository) CreateMessage(
	context.Context,
	string,
	string,
	pkgidempotency.Request,
	*model.CreateMessageRequest,
) (*pkgidempotency.MutationResult[model.Message], error) {
	return &pkgidempotency.MutationResult[model.Message]{
		Outcome:        pkgidempotency.OutcomeReplay,
		ResponseStatus: http.StatusCreated,
		ReplayJSON: []byte(
			`{"id":"` + gatewayMsg + `","chat_id":"` + gatewayChat + `","legacy":true}`,
		),
	}, nil
}

func TestSendMessageReplayReturnsStoredResourceWithoutRebroadcast(t *testing.T) {
	gateway := NewGateway(service.NewChatService(replayRepository{}))
	content := "hello"

	ack, err := gateway.SendMessage(
		context.Background(),
		gatewayUser,
		gatewayKey,
		pkgrealtime.MessageSendPayload{
			ChatID:  gatewayChat,
			Type:    model.MessageTypeText,
			Content: &content,
		},
	)
	if err != nil {
		t.Fatalf("SendMessage returned error: %v", err)
	}
	if !ack.Replayed || ack.ResourceType != "message" || ack.ResourceID != gatewayMsg {
		t.Fatalf("unexpected replay ack: %+v", ack)
	}
}
