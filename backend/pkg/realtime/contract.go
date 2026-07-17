package realtime

import (
	"context"
	"encoding/json"
	"fmt"
	"time"

	"github.com/google/uuid"
)

const (
	ProtocolVersion = "1"

	EventAck             = "ack"
	EventError           = "error"
	EventChatJoin        = "chat.join"
	EventChatLeave       = "chat.leave"
	EventMessageSend     = "message.send"
	EventMessageReceive  = "message.receive"
	EventMessageEdit     = "message.edit"
	EventMessageDelete   = "message.delete"
	EventMessageRead     = "message.read"
	EventMessageReaction = "message.reaction"
	EventUserTyping      = "user.typing"
	EventUserOnline      = "user.online"
	EventUserOffline     = "user.offline"
)

// Envelope is the versioned wire contract used for all realtime chat events.
// event_id is a UUID suitable for duplicate suppression. sequence is the
// publisher's UTC Unix-microsecond timestamp and must only be used as an
// ordering hint; resource timestamps remain authoritative.
type Envelope struct {
	Version      string          `json:"version"`
	Event        string          `json:"event"`
	EventID      string          `json:"event_id,omitempty"`
	RequestID    string          `json:"request_id,omitempty"`
	OperationKey string          `json:"operation_key,omitempty"`
	OccurredAt   time.Time       `json:"occurred_at,omitempty"`
	Sequence     int64           `json:"sequence,omitempty"`
	Data         json.RawMessage `json:"data,omitempty"`
	Error        *ErrorPayload   `json:"error,omitempty"`
}

type ErrorPayload struct {
	Code      string `json:"code"`
	Message   string `json:"message"`
	Retryable bool   `json:"retryable"`
}

type AckPayload struct {
	ForEvent     string `json:"for_event"`
	OperationKey string `json:"operation_key,omitempty"`
	ResourceType string `json:"resource_type,omitempty"`
	ResourceID   string `json:"resource_id,omitempty"`
	Replayed     bool   `json:"replayed,omitempty"`
	Accepted     bool   `json:"accepted"`
}

type ChatPayload struct {
	ChatID       string `json:"chat_id,omitempty"`
	LegacyChatID string `json:"chatId,omitempty"`
}

type MessageSendPayload struct {
	ChatID             string  `json:"chat_id,omitempty"`
	LegacyChatID       string  `json:"chatId,omitempty"`
	Type               string  `json:"type,omitempty"`
	Content            *string `json:"content,omitempty"`
	MediaURL           *string `json:"media_url,omitempty"`
	LegacyMediaURL     *string `json:"mediaUrl,omitempty"`
	ReplyToID          *string `json:"reply_to_id,omitempty"`
	LegacyReplyToID    *string `json:"replyToId,omitempty"`
	OperationKey       string  `json:"operation_key,omitempty"`
	LegacyOperationKey string  `json:"operationKey,omitempty"`
}

type MessageEditCommand struct {
	MessageID       string `json:"message_id,omitempty"`
	LegacyMessageID string `json:"messageId,omitempty"`
	Content         string `json:"content"`
}

type MessageDeleteCommand struct {
	MessageID       string `json:"message_id,omitempty"`
	LegacyMessageID string `json:"messageId,omitempty"`
}

type MessageReadCommand struct {
	MessageID       string `json:"message_id,omitempty"`
	LegacyMessageID string `json:"messageId,omitempty"`
}

type MessageReactionCommand struct {
	MessageID       string `json:"message_id,omitempty"`
	LegacyMessageID string `json:"messageId,omitempty"`
	Reaction        string `json:"reaction"`
}

// MessagePayload mirrors the authoritative REST message representation so the
// same shape can be written directly into Android Room.
type MessagePayload struct {
	ID        string                   `json:"id"`
	ChatID    string                   `json:"chat_id"`
	SenderID  string                   `json:"sender_id"`
	Type      string                   `json:"type"`
	Content   *string                  `json:"content,omitempty"`
	MediaID   *string                  `json:"media_id,omitempty"`
	MediaURL  *string                  `json:"media_url,omitempty"`
	ReplyToID *string                  `json:"reply_to_id,omitempty"`
	IsEdited  bool                     `json:"is_edited"`
	IsDeleted bool                     `json:"is_deleted"`
	CreatedAt time.Time                `json:"created_at"`
	UpdatedAt time.Time                `json:"updated_at"`
	EditedAt  *time.Time               `json:"edited_at,omitempty"`
	DeletedAt *time.Time               `json:"deleted_at,omitempty"`
	Reactions []MessageReactionSummary `json:"reactions"`
	ReadBy    []MessageReadSummary     `json:"read_by"`
}

type MessageReactionSummary struct {
	Reaction string `json:"reaction"`
	Count    int64  `json:"count"`
	Reacted  bool   `json:"reacted"`
}

type MessageReadSummary struct {
	UserID string    `json:"user_id"`
	ReadAt time.Time `json:"read_at"`
}

type MessageEditPayload struct {
	MessageID string     `json:"message_id"`
	ChatID    string     `json:"chat_id"`
	Content   string     `json:"content"`
	EditedAt  *time.Time `json:"edited_at,omitempty"`
	UpdatedAt time.Time  `json:"updated_at"`
}

type MessageDeletePayload struct {
	MessageID string `json:"message_id"`
	ChatID    string `json:"chat_id"`
}

type MessageReadPayload struct {
	MessageID string    `json:"message_id"`
	ChatID    string    `json:"chat_id"`
	UserID    string    `json:"user_id"`
	ReadAt    time.Time `json:"read_at"`
}

type MessageReactionPayload struct {
	MessageID string `json:"message_id"`
	ChatID    string `json:"chat_id"`
	UserID    string `json:"user_id"`
	Reaction  string `json:"reaction"`
	Active    bool   `json:"active"`
	Count     int64  `json:"count"`
}

type TypingPayload struct {
	ChatID    string     `json:"chat_id"`
	UserID    string     `json:"user_id"`
	Typing    bool       `json:"typing"`
	ExpiresAt *time.Time `json:"expires_at,omitempty"`
}

type PresencePayload struct {
	ChatID    string    `json:"chat_id"`
	UserID    string    `json:"user_id"`
	Online    bool      `json:"online"`
	ChangedAt time.Time `json:"changed_at"`
}

type Delivery struct {
	Channel  string
	Envelope Envelope
}

// Publisher is the post-commit realtime fanout boundary. Implementations must
// not persist ephemeral typing or presence events.
type Publisher interface {
	Publish(ctx context.Context, delivery Delivery) error
}

type NopPublisher struct{}

func (NopPublisher) Publish(context.Context, Delivery) error {
	return nil
}

func NewEnvelope(event string, payload any) (Envelope, error) {
	now := time.Now().UTC()
	return NewEnvelopeAt(event, payload, now)
}

func NewEnvelopeAt(event string, payload any, occurredAt time.Time) (Envelope, error) {
	if event == "" {
		return Envelope{}, fmt.Errorf("realtime event name is required")
	}
	data, err := json.Marshal(payload)
	if err != nil {
		return Envelope{}, fmt.Errorf("marshal realtime payload: %w", err)
	}
	occurredAt = occurredAt.UTC()
	return Envelope{
		Version:    ProtocolVersion,
		Event:      event,
		EventID:    uuid.NewString(),
		OccurredAt: occurredAt,
		Sequence:   occurredAt.UnixMicro(),
		Data:       data,
	}, nil
}

func NewAck(
	requestID string,
	event string,
	operationKey string,
	resourceType string,
	resourceID string,
	replayed bool,
) (Envelope, error) {
	envelope, err := NewEnvelope(EventAck, AckPayload{
		ForEvent:     event,
		OperationKey: operationKey,
		ResourceType: resourceType,
		ResourceID:   resourceID,
		Replayed:     replayed,
		Accepted:     true,
	})
	if err != nil {
		return Envelope{}, err
	}
	envelope.RequestID = requestID
	envelope.OperationKey = operationKey
	return envelope, nil
}

func NewError(requestID string, code string, message string, retryable bool) Envelope {
	now := time.Now().UTC()
	return Envelope{
		Version:    ProtocolVersion,
		Event:      EventError,
		EventID:    uuid.NewString(),
		RequestID:  requestID,
		OccurredAt: now,
		Sequence:   now.UnixMicro(),
		Error: &ErrorPayload{
			Code:      code,
			Message:   message,
			Retryable: retryable,
		},
	}
}
