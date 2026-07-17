package realtime

import (
	"context"
	"fmt"
)

type MutationAck struct {
	ResourceType string
	ResourceID   string
	Replayed     bool
}

type ChatGateway interface {
	AuthorizeChat(ctx context.Context, userID string, chatID string) error
	SendMessage(
		ctx context.Context,
		userID string,
		operationKey string,
		payload MessageSendPayload,
	) (MutationAck, error)
	EditMessage(
		ctx context.Context,
		userID string,
		payload MessageEditCommand,
	) (MutationAck, error)
	DeleteMessage(
		ctx context.Context,
		userID string,
		payload MessageDeleteCommand,
	) (MutationAck, error)
	ReadMessage(
		ctx context.Context,
		userID string,
		payload MessageReadCommand,
	) (MutationAck, error)
	ReactToMessage(
		ctx context.Context,
		userID string,
		payload MessageReactionCommand,
	) (MutationAck, error)
}

type GatewayError struct {
	Code      string
	Message   string
	Retryable bool
	Cause     error
}

func (e *GatewayError) Error() string {
	return e.Message
}

func (e *GatewayError) Unwrap() error {
	return e.Cause
}

func NewGatewayError(code string, message string, retryable bool, cause error) error {
	if code == "" || message == "" {
		return fmt.Errorf("invalid realtime gateway error: %w", cause)
	}
	return &GatewayError{
		Code:      code,
		Message:   message,
		Retryable: retryable,
		Cause:     cause,
	}
}
