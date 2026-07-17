package realtime

import (
	"context"
	"encoding/json"
	"errors"
	"time"

	"github.com/xilo-platform/xilo/internal/chat/model"
	"github.com/xilo-platform/xilo/internal/chat/service"
	pkgrealtime "github.com/xilo-platform/xilo/pkg/realtime"
)

type Gateway struct {
	service *service.ChatService
	clock   func() time.Time
}

func NewGateway(chatService *service.ChatService) *Gateway {
	return &Gateway{
		service: chatService,
		clock:   func() time.Time { return time.Now().UTC() },
	}
}

func (g *Gateway) AuthorizeChat(
	ctx context.Context,
	userID string,
	chatID string,
) error {
	return translateError(g.service.AuthorizeMembership(ctx, userID, chatID))
}

func (g *Gateway) SendMessage(
	ctx context.Context,
	userID string,
	operationKey string,
	payload pkgrealtime.MessageSendPayload,
) (pkgrealtime.MutationAck, error) {
	result, err := g.service.CreateMessage(
		ctx,
		userID,
		payload.ChatID,
		operationKey,
		g.clock(),
		&model.CreateMessageRequest{
			Type:      payload.Type,
			Content:   payload.Content,
			MediaURL:  payload.MediaURL,
			ReplyToID: payload.ReplyToID,
		},
	)
	if err != nil {
		return pkgrealtime.MutationAck{}, translateError(err)
	}

	var message model.Message
	if result != nil && result.Value != nil {
		message = *result.Value
	} else if result != nil && result.IsReplay() {
		if err := json.Unmarshal(result.ReplayJSON, &message); err != nil {
			return pkgrealtime.MutationAck{}, pkgrealtime.NewGatewayError(
				"internal_error",
				"internal server error",
				false,
				err,
			)
		}
	}
	if message.ID == "" {
		return pkgrealtime.MutationAck{}, pkgrealtime.NewGatewayError(
			"internal_error",
			"internal server error",
			false,
			errors.New("message mutation returned no resource"),
		)
	}
	replayed := result != nil && result.IsReplay()
	return pkgrealtime.MutationAck{
		ResourceType: "message",
		ResourceID:   message.ID,
		Replayed:     replayed,
	}, nil
}

func (g *Gateway) EditMessage(
	ctx context.Context,
	userID string,
	payload pkgrealtime.MessageEditCommand,
) (pkgrealtime.MutationAck, error) {
	message, err := g.service.UpdateMessage(
		ctx,
		userID,
		payload.MessageID,
		&model.UpdateMessageRequest{Content: payload.Content},
	)
	if err != nil {
		return pkgrealtime.MutationAck{}, translateError(err)
	}
	return messageAck(message.ID), nil
}

func (g *Gateway) DeleteMessage(
	ctx context.Context,
	userID string,
	payload pkgrealtime.MessageDeleteCommand,
) (pkgrealtime.MutationAck, error) {
	if err := g.service.DeleteMessage(ctx, userID, payload.MessageID); err != nil {
		return pkgrealtime.MutationAck{}, translateError(err)
	}
	return messageAck(payload.MessageID), nil
}

func (g *Gateway) ReadMessage(
	ctx context.Context,
	userID string,
	payload pkgrealtime.MessageReadCommand,
) (pkgrealtime.MutationAck, error) {
	if _, err := g.service.MarkRead(ctx, userID, payload.MessageID); err != nil {
		return pkgrealtime.MutationAck{}, translateError(err)
	}
	return messageAck(payload.MessageID), nil
}

func (g *Gateway) ReactToMessage(
	ctx context.Context,
	userID string,
	payload pkgrealtime.MessageReactionCommand,
) (pkgrealtime.MutationAck, error) {
	if _, err := g.service.ToggleReaction(
		ctx,
		userID,
		payload.MessageID,
		&model.ReactionRequest{Reaction: payload.Reaction},
	); err != nil {
		return pkgrealtime.MutationAck{}, translateError(err)
	}
	return messageAck(payload.MessageID), nil
}

func messageAck(messageID string) pkgrealtime.MutationAck {
	return pkgrealtime.MutationAck{
		ResourceType: "message",
		ResourceID:   messageID,
	}
}

func translateError(err error) error {
	if err == nil {
		return nil
	}
	var serviceErr *service.Error
	if !errors.As(err, &serviceErr) {
		return pkgrealtime.NewGatewayError(
			"internal_error",
			"internal server error",
			false,
			err,
		)
	}
	switch serviceErr.Code {
	case service.CodeNotFound, service.CodeForbidden:
		return pkgrealtime.NewGatewayError(
			"not_authorized",
			"operation is not authorized",
			false,
			err,
		)
	case service.CodeRetryable:
		return pkgrealtime.NewGatewayError(
			serviceErr.Code,
			serviceErr.Message,
			true,
			err,
		)
	default:
		return pkgrealtime.NewGatewayError(
			serviceErr.Code,
			serviceErr.Message,
			false,
			err,
		)
	}
}
