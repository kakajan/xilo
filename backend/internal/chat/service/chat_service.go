package service

import (
	"context"
	"errors"
	"fmt"
	"log/slog"
	"net/url"
	"sort"
	"strings"
	"time"
	"unicode/utf8"

	"github.com/google/uuid"
	"github.com/xilo-platform/xilo/internal/chat/model"
	"github.com/xilo-platform/xilo/internal/chat/repository"
	pkgidempotency "github.com/xilo-platform/xilo/pkg/idempotency"
	"github.com/xilo-platform/xilo/pkg/realtime"
)

const (
	CodeValidation          = "validation_error"
	CodeForbidden           = "forbidden"
	CodeNotFound            = "not_found"
	CodeConflict            = "conflict"
	CodeInvalidCursor       = "invalid_cursor"
	CodeEditWindowExpired   = "edit_window_expired"
	CodeRetryable           = "retryable_error"
	CodeIdempotencyNeeded   = "idempotency_key_required"
	CodeIdempotencyInvalid  = "invalid_idempotency_key"
	CodeIdempotencyConflict = "idempotency_conflict"
	CodeInternal            = "internal_error"
)

const (
	OperationCreateChat    = "chat.create.v1"
	OperationCreateMessage = "chat.message.create.v1"
)

type Error struct {
	Code    string
	Message string
	Cause   error
}

func (e *Error) Error() string {
	if e == nil {
		return ""
	}
	if e.Cause != nil {
		return fmt.Sprintf("%s: %v", e.Message, e.Cause)
	}
	return e.Message
}

func (e *Error) Unwrap() error {
	return e.Cause
}

type ChatRepository interface {
	CreateChat(
		ctx context.Context,
		actorID string,
		idempotencyRequest pkgidempotency.Request,
		req *model.CreateChatRequest,
		memberIDs []string,
		directLow string,
		directHigh string,
	) (*pkgidempotency.MutationResult[model.Chat], error)
	ListChats(ctx context.Context, userID string, params model.ListParams) ([]*model.Chat, string, error)
	GetChat(ctx context.Context, userID string, chatID string) (*model.Chat, error)
	GetMembership(ctx context.Context, chatID string, userID string) (*model.ChatMember, error)
	UpdateChat(
		ctx context.Context,
		chatID string,
		userID string,
		name *string,
		avatarURL *string,
		isMuted *bool,
		isArchived *bool,
	) error
	AddMembers(ctx context.Context, chatID string, actorID string, userIDs []string) error
	RemoveMember(ctx context.Context, chatID string, actorID string, targetID string) error
	LeaveChat(ctx context.Context, chatID string, userID string) error
	EnsureSavedMessagesChat(ctx context.Context, userID string) (*model.Chat, error)
	ListMessages(
		ctx context.Context,
		chatID string,
		userID string,
		params model.ListParams,
	) ([]*model.Message, string, error)
	SearchMessages(
		ctx context.Context,
		chatID string,
		userID string,
		query string,
		params model.ListParams,
	) ([]*model.Message, string, error)
	CreateMessage(
		ctx context.Context,
		chatID string,
		senderID string,
		idempotencyRequest pkgidempotency.Request,
		req *model.CreateMessageRequest,
	) (*pkgidempotency.MutationResult[model.Message], error)
	GetMessage(ctx context.Context, messageID string) (*model.Message, error)
	UpdateMessage(
		ctx context.Context,
		messageID string,
		senderID string,
		content string,
	) (*model.Message, error)
	DeleteMessage(ctx context.Context, messageID string, senderID string) error
	MarkRead(ctx context.Context, messageID string, userID string) (*model.Read, error)
	ToggleReaction(
		ctx context.Context,
		messageID string,
		userID string,
		reaction string,
	) (*model.ReactionResult, error)
}

type ChatService struct {
	repo      ChatRepository
	publisher realtime.Publisher
	clock     func() time.Time
}

func NewChatService(repo ChatRepository) *ChatService {
	return NewChatServiceWithPublisher(repo, realtime.NopPublisher{})
}

func NewChatServiceWithPublisher(
	repo ChatRepository,
	publisher realtime.Publisher,
) *ChatService {
	if publisher == nil {
		publisher = realtime.NopPublisher{}
	}
	return &ChatService{
		repo:      repo,
		publisher: publisher,
		clock:     func() time.Time { return time.Now().UTC() },
	}
}

func (s *ChatService) CreateChat(
	ctx context.Context,
	actorID string,
	idempotencyKey string,
	receivedAt time.Time,
	req *model.CreateChatRequest,
) (*pkgidempotency.MutationResult[model.Chat], error) {
	key, err := parseIdempotencyKey(idempotencyKey)
	if err != nil {
		return nil, err
	}
	actorID, err = canonicalID(actorID)
	if err != nil {
		return nil, forbidden("invalid authenticated user", err)
	}
	req.Type = strings.ToLower(strings.TrimSpace(req.Type))
	if req.Type != model.ChatTypeDirect && req.Type != model.ChatTypeGroup {
		return nil, validation("type must be direct or group")
	}
	if req.AvatarURL != nil {
		normalized, err := normalizeURL(*req.AvatarURL)
		if err != nil {
			return nil, validation("avatar_url must be a valid HTTP(S) URL")
		}
		req.AvatarURL = normalized
	}

	otherIDs, err := normalizeMemberIDs(actorID, req.MemberIDs)
	if err != nil {
		return nil, err
	}
	memberIDs := append([]string{actorID}, otherIDs...)

	var directLow string
	var directHigh string
	switch req.Type {
	case model.ChatTypeDirect:
		if len(otherIDs) != 1 {
			return nil, validation("direct chat requires exactly one other member")
		}
		req.Name = nil
		req.AvatarURL = nil
		pair := []string{actorID, otherIDs[0]}
		sort.Strings(pair)
		directLow, directHigh = pair[0], pair[1]
	case model.ChatTypeGroup:
		if len(otherIDs) < 2 {
			return nil, validation("group chat requires at least two other members")
		}
		if req.Name == nil {
			return nil, validation("group chat name is required")
		}
		name := strings.TrimSpace(*req.Name)
		if utf8.RuneCountInString(name) == 0 || utf8.RuneCountInString(name) > 100 {
			return nil, validation("group chat name must be between 1 and 100 characters")
		}
		req.Name = &name
	}

	requestHash, err := pkgidempotency.HashPayload(struct {
		Type      string   `json:"type"`
		Name      *string  `json:"name,omitempty"`
		AvatarURL *string  `json:"avatar_url,omitempty"`
		MemberIDs []string `json:"member_ids"`
	}{
		Type:      req.Type,
		Name:      req.Name,
		AvatarURL: req.AvatarURL,
		MemberIDs: otherIDs,
	})
	if err != nil {
		return nil, internal(err)
	}
	result, err := s.repo.CreateChat(
		ctx,
		actorID,
		pkgidempotency.Request{
			PrincipalID: actorID,
			Operation:   OperationCreateChat,
			Key:         key,
			RequestHash: requestHash,
			ReceivedAt:  receivedAt,
		},
		req,
		memberIDs,
		directLow,
		directHigh,
	)
	if err != nil {
		return nil, translateRepositoryError(err, "chat")
	}
	return result, nil
}

func (s *ChatService) ListChats(
	ctx context.Context,
	userID string,
	params model.ListParams,
) ([]*model.Chat, string, error) {
	if _, err := canonicalID(userID); err != nil {
		return nil, "", forbidden("invalid authenticated user", err)
	}
	chats, cursor, err := s.repo.ListChats(ctx, userID, params)
	if err != nil {
		return nil, "", translateRepositoryError(err, "chat")
	}
	if chats == nil {
		chats = []*model.Chat{}
	}
	return chats, cursor, nil
}

func (s *ChatService) GetChat(
	ctx context.Context,
	userID string,
	chatID string,
) (*model.Chat, error) {
	if err := validateIDs(userID, chatID); err != nil {
		return nil, err
	}
	chat, err := s.repo.GetChat(ctx, userID, chatID)
	if err != nil {
		return nil, translateRepositoryError(err, "chat")
	}
	return chat, nil
}

func (s *ChatService) UpdateChat(
	ctx context.Context,
	userID string,
	chatID string,
	req *model.UpdateChatRequest,
) (*model.Chat, error) {
	if err := validateIDs(userID, chatID); err != nil {
		return nil, err
	}
	chat, err := s.repo.GetChat(ctx, userID, chatID)
	if err != nil {
		return nil, translateRepositoryError(err, "chat")
	}

	metadataRequested := req.Name != nil || req.AvatarURL != nil
	if metadataRequested {
		if chat.Type != model.ChatTypeGroup || chat.CurrentRole != model.MemberRoleAdmin {
			return nil, forbidden("only group admins may update group metadata", nil)
		}
		if req.Name != nil {
			name := strings.TrimSpace(*req.Name)
			if utf8.RuneCountInString(name) == 0 || utf8.RuneCountInString(name) > 100 {
				return nil, validation("group chat name must be between 1 and 100 characters")
			}
			req.Name = &name
		}
		if req.AvatarURL != nil {
			normalized, err := normalizeOptionalURL(*req.AvatarURL)
			if err != nil {
				return nil, validation("avatar_url must be empty or a valid HTTP(S) URL")
			}
			req.AvatarURL = normalized
		}
	}
	if !metadataRequested && req.IsMuted == nil && req.IsArchived == nil {
		return nil, validation("at least one updatable field is required")
	}
	if err := s.repo.UpdateChat(
		ctx,
		chatID,
		userID,
		req.Name,
		req.AvatarURL,
		req.IsMuted,
		req.IsArchived,
	); err != nil {
		return nil, translateRepositoryError(err, "chat")
	}
	return s.GetChat(ctx, userID, chatID)
}

func (s *ChatService) EnsureSavedMessagesChat(
	ctx context.Context,
	userID string,
) (*model.Chat, error) {
	userID, err := canonicalID(userID)
	if err != nil {
		return nil, forbidden("invalid authenticated user", err)
	}
	chat, err := s.repo.EnsureSavedMessagesChat(ctx, userID)
	if err != nil {
		return nil, translateRepositoryError(err, "chat")
	}
	return chat, nil
}

func (s *ChatService) LeaveChat(ctx context.Context, userID string, chatID string) error {
	if err := validateIDs(userID, chatID); err != nil {
		return err
	}
	chat, err := s.repo.GetChat(ctx, userID, chatID)
	if err != nil {
		return translateRepositoryError(err, "chat")
	}
	if chat.Type == model.ChatTypeSaved {
		return forbidden("saved messages chat cannot be left", nil)
	}
	if _, err := s.requireMember(ctx, chatID, userID); err != nil {
		return err
	}
	if err := s.repo.LeaveChat(ctx, chatID, userID); err != nil {
		return translateRepositoryError(err, "chat")
	}
	return nil
}

func (s *ChatService) AddMembers(
	ctx context.Context,
	actorID string,
	chatID string,
	req *model.AddMembersRequest,
) (*model.Chat, error) {
	if err := validateIDs(actorID, chatID); err != nil {
		return nil, err
	}
	chat, err := s.repo.GetChat(ctx, actorID, chatID)
	if err != nil {
		return nil, translateRepositoryError(err, "chat")
	}
	if chat.Type != model.ChatTypeGroup || chat.CurrentRole != model.MemberRoleAdmin {
		return nil, forbidden("only group admins may add members", nil)
	}
	userIDs, err := normalizeMemberIDs(actorID, req.UserIDs)
	if err != nil {
		return nil, err
	}
	if len(userIDs) == 0 {
		return nil, validation("at least one new member is required")
	}
	if err := s.repo.AddMembers(ctx, chatID, actorID, userIDs); err != nil {
		return nil, translateRepositoryError(err, "chat")
	}
	return s.GetChat(ctx, actorID, chatID)
}

func (s *ChatService) RemoveMember(
	ctx context.Context,
	actorID string,
	chatID string,
	targetID string,
) error {
	if err := validateIDs(actorID, chatID, targetID); err != nil {
		return err
	}
	chat, err := s.repo.GetChat(ctx, actorID, chatID)
	if err != nil {
		return translateRepositoryError(err, "chat")
	}
	if chat.Type != model.ChatTypeGroup {
		return forbidden("direct chat membership cannot be changed", nil)
	}
	if actorID != targetID && chat.CurrentRole != model.MemberRoleAdmin {
		return forbidden("only group admins may remove other members", nil)
	}
	if err := s.repo.RemoveMember(ctx, chatID, actorID, targetID); err != nil {
		return translateRepositoryError(err, "chat")
	}
	return nil
}

func (s *ChatService) ListMessages(
	ctx context.Context,
	userID string,
	chatID string,
	params model.ListParams,
) ([]*model.Message, string, error) {
	if err := validateIDs(userID, chatID); err != nil {
		return nil, "", err
	}
	if _, err := s.requireMember(ctx, chatID, userID); err != nil {
		return nil, "", err
	}
	messages, cursor, err := s.repo.ListMessages(ctx, chatID, userID, params)
	if err != nil {
		return nil, "", translateRepositoryError(err, "message")
	}
	return messages, cursor, nil
}

func (s *ChatService) CreateMessage(
	ctx context.Context,
	userID string,
	chatID string,
	idempotencyKey string,
	receivedAt time.Time,
	req *model.CreateMessageRequest,
) (*pkgidempotency.MutationResult[model.Message], error) {
	key, err := parseIdempotencyKey(idempotencyKey)
	if err != nil {
		return nil, err
	}
	userID, err = canonicalID(userID)
	if err != nil {
		return nil, forbidden("invalid authenticated user", err)
	}
	chatID, err = canonicalID(chatID)
	if err != nil {
		return nil, validation("invalid resource identifier")
	}
	if err := normalizeMessageRequest(req); err != nil {
		return nil, err
	}
	if req.ReplyToID != nil {
		replyID, err := canonicalID(*req.ReplyToID)
		if err != nil {
			return nil, validation("reply_to_id must be a valid UUID")
		}
		req.ReplyToID = &replyID
	}

	requestHash, err := pkgidempotency.HashPayload(struct {
		ChatID   string  `json:"chat_id"`
		Type     string  `json:"type"`
		Content  *string `json:"content,omitempty"`
		MediaURL *string `json:"media_url,omitempty"`
		ReplyTo  *string `json:"reply_to_id,omitempty"`
	}{
		ChatID:   chatID,
		Type:     req.Type,
		Content:  req.Content,
		MediaURL: req.MediaURL,
		ReplyTo:  req.ReplyToID,
	})
	if err != nil {
		return nil, internal(err)
	}
	result, err := s.repo.CreateMessage(
		ctx,
		chatID,
		userID,
		pkgidempotency.Request{
			PrincipalID: userID,
			Operation:   OperationCreateMessage,
			Key:         key,
			RequestHash: requestHash,
			ReceivedAt:  receivedAt,
		},
		req,
	)
	if err != nil {
		if errors.Is(err, repository.ErrMediaTooLarge) {
			return nil, validation("media exceeds the size limit for this message type")
		}
		if errors.Is(err, repository.ErrMediaNotFound) ||
			errors.Is(err, repository.ErrMediaURL) ||
			errors.Is(err, repository.ErrMediaType) {
			return nil, validation("media_url must reference an owned upload compatible with the message type")
		}
		return nil, translateRepositoryError(err, "message")
	}
	if !result.IsReplay() && result.Value != nil {
		s.publish(
			ctx,
			"chat:"+result.Value.ChatID,
			realtime.EventMessageReceive,
			toRealtimeMessage(result.Value),
			key.String(),
		)
	}
	return result, nil
}

func (s *ChatService) UpdateMessage(
	ctx context.Context,
	userID string,
	messageID string,
	req *model.UpdateMessageRequest,
) (*model.Message, error) {
	if err := validateIDs(userID, messageID); err != nil {
		return nil, err
	}
	content := strings.TrimSpace(req.Content)
	if content == "" || utf8.RuneCountInString(content) > 10000 {
		return nil, validation("content must be between 1 and 10000 characters")
	}
	message, err := s.authorizeMessageSender(ctx, userID, messageID)
	if err != nil {
		return nil, err
	}
	if message.IsDeleted {
		return nil, conflict("deleted messages cannot be edited", nil)
	}
	if !s.clock().Before(message.CreatedAt.Add(48 * time.Hour)) {
		return nil, &Error{
			Code:    CodeEditWindowExpired,
			Message: "message edit window has expired",
		}
	}
	updated, err := s.repo.UpdateMessage(ctx, messageID, userID, content)
	if err != nil {
		return nil, translateRepositoryError(err, "message")
	}
	s.publish(
		ctx,
		"chat:"+updated.ChatID,
		realtime.EventMessageEdit,
		realtime.MessageEditPayload{
			MessageID: updated.ID,
			ChatID:    updated.ChatID,
			Content:   content,
			EditedAt:  updated.EditedAt,
			UpdatedAt: updated.UpdatedAt,
		},
		"",
	)
	return updated, nil
}

func (s *ChatService) DeleteMessage(
	ctx context.Context,
	userID string,
	messageID string,
) error {
	if err := validateIDs(userID, messageID); err != nil {
		return err
	}
	message, err := s.authorizeMessageSender(ctx, userID, messageID)
	if err != nil {
		return err
	}
	if message.IsDeleted {
		return conflict("message is already deleted", nil)
	}
	if err := s.repo.DeleteMessage(ctx, messageID, userID); err != nil {
		return translateRepositoryError(err, "message")
	}
	s.publish(
		ctx,
		"chat:"+message.ChatID,
		realtime.EventMessageDelete,
		realtime.MessageDeletePayload{
			MessageID: message.ID,
			ChatID:    message.ChatID,
		},
		"",
	)
	return nil
}

func (s *ChatService) MarkRead(
	ctx context.Context,
	userID string,
	messageID string,
) (*model.Read, error) {
	if err := validateIDs(userID, messageID); err != nil {
		return nil, err
	}
	read, err := s.repo.MarkRead(ctx, messageID, userID)
	if err != nil {
		if errors.Is(err, repository.ErrForbidden) {
			return nil, notFound("message not found", err)
		}
		return nil, translateRepositoryError(err, "message")
	}
	lookupCtx, cancelLookup := context.WithTimeout(context.WithoutCancel(ctx), 2*time.Second)
	message, lookupErr := s.repo.GetMessage(lookupCtx, messageID)
	cancelLookup()
	if lookupErr != nil {
		slog.Error("resolve committed message read chat", "message_id", messageID, "error", lookupErr)
	} else {
		s.publish(
			ctx,
			"chat:"+message.ChatID,
			realtime.EventMessageRead,
			realtime.MessageReadPayload{
				MessageID: messageID,
				ChatID:    message.ChatID,
				UserID:    read.UserID,
				ReadAt:    read.ReadAt,
			},
			"",
		)
	}
	return read, nil
}

func (s *ChatService) ToggleReaction(
	ctx context.Context,
	userID string,
	messageID string,
	req *model.ReactionRequest,
) (*model.ReactionResult, error) {
	if err := validateIDs(userID, messageID); err != nil {
		return nil, err
	}
	if _, ok := supportedReactions[req.Reaction]; !ok {
		return nil, validation("unsupported reaction")
	}
	result, err := s.repo.ToggleReaction(ctx, messageID, userID, req.Reaction)
	if err != nil {
		if errors.Is(err, repository.ErrForbidden) {
			return nil, notFound("message not found", err)
		}
		return nil, translateRepositoryError(err, "message")
	}
	lookupCtx, cancelLookup := context.WithTimeout(context.WithoutCancel(ctx), 2*time.Second)
	message, lookupErr := s.repo.GetMessage(lookupCtx, messageID)
	cancelLookup()
	if lookupErr != nil {
		slog.Error("resolve committed message reaction chat", "message_id", messageID, "error", lookupErr)
	} else {
		s.publish(
			ctx,
			"chat:"+message.ChatID,
			realtime.EventMessageReaction,
			realtime.MessageReactionPayload{
				MessageID: messageID,
				ChatID:    message.ChatID,
				UserID:    userID,
				Reaction:  result.Reaction,
				Active:    result.Active,
				Count:     result.Count,
			},
			"",
		)
	}
	return result, nil
}

func (s *ChatService) SearchMessages(
	ctx context.Context,
	userID string,
	chatID string,
	query string,
	params model.ListParams,
) ([]*model.Message, string, error) {
	if err := validateIDs(userID, chatID); err != nil {
		return nil, "", err
	}
	query = strings.TrimSpace(query)
	if query == "" || utf8.RuneCountInString(query) > 200 {
		return nil, "", validation("q must be between 1 and 200 characters")
	}
	if _, err := s.requireMember(ctx, chatID, userID); err != nil {
		return nil, "", err
	}
	messages, cursor, err := s.repo.SearchMessages(ctx, chatID, userID, query, params)
	if err != nil {
		return nil, "", translateRepositoryError(err, "message")
	}
	return messages, cursor, nil
}

func (s *ChatService) requireMember(
	ctx context.Context,
	chatID string,
	userID string,
) (*model.ChatMember, error) {
	member, err := s.repo.GetMembership(ctx, chatID, userID)
	if err != nil {
		if repository.IsRetryable(err) {
			return nil, translateRepositoryError(err, "chat")
		}
		if errors.Is(err, repository.ErrForbidden) || errors.Is(err, repository.ErrChatNotFound) {
			return nil, notFound("chat not found", err)
		}
		return nil, internal(err)
	}
	return member, nil
}

// AuthorizeMembership exposes the same non-enumerating active-membership check
// used by REST to the WebSocket gateway adapter.
func (s *ChatService) AuthorizeMembership(
	ctx context.Context,
	userID string,
	chatID string,
) error {
	if err := validateIDs(userID, chatID); err != nil {
		return notFound("chat not found", err)
	}
	_, err := s.requireMember(ctx, chatID, userID)
	return err
}

func (s *ChatService) publish(
	ctx context.Context,
	channel string,
	event string,
	payload any,
	operationKey string,
) {
	envelope, err := realtime.NewEnvelope(event, payload)
	if err != nil {
		slog.Error("build chat realtime event", "event", event, "error", err)
		return
	}
	envelope.OperationKey = operationKey
	publishCtx, cancel := context.WithTimeout(context.WithoutCancel(ctx), 2*time.Second)
	defer cancel()
	if err := s.publisher.Publish(publishCtx, realtime.Delivery{
		Channel:  channel,
		Envelope: envelope,
	}); err != nil {
		slog.Error("publish chat realtime event", "event", event, "error", err)
	}
}

func toRealtimeMessage(message *model.Message) realtime.MessagePayload {
	reactions := make([]realtime.MessageReactionSummary, 0, len(message.Reactions))
	for _, reaction := range message.Reactions {
		reactions = append(reactions, realtime.MessageReactionSummary{
			Reaction: reaction.Reaction,
			Count:    reaction.Count,
			Reacted:  reaction.Reacted,
		})
	}
	reads := make([]realtime.MessageReadSummary, 0, len(message.ReadBy))
	for _, read := range message.ReadBy {
		reads = append(reads, realtime.MessageReadSummary{
			UserID: read.UserID,
			ReadAt: read.ReadAt,
		})
	}
	return realtime.MessagePayload{
		ID:        message.ID,
		ChatID:    message.ChatID,
		SenderID:  message.SenderID,
		Type:      message.Type,
		Content:   message.Content,
		MediaID:   message.MediaID,
		MediaURL:  message.MediaURL,
		ReplyToID: message.ReplyToID,
		IsEdited:  message.IsEdited,
		IsDeleted: message.IsDeleted,
		CreatedAt: message.CreatedAt,
		UpdatedAt: message.UpdatedAt,
		EditedAt:  message.EditedAt,
		DeletedAt: message.DeletedAt,
		Reactions: reactions,
		ReadBy:    reads,
	}
}

func (s *ChatService) authorizeMessageSender(
	ctx context.Context,
	userID string,
	messageID string,
) (*model.Message, error) {
	message, err := s.repo.GetMessage(ctx, messageID)
	if err != nil {
		return nil, translateRepositoryError(err, "message")
	}
	if _, err := s.requireMember(ctx, message.ChatID, userID); err != nil {
		return nil, notFound("message not found", err)
	}
	if message.SenderID != userID {
		return nil, forbidden("only the sender may change this message", nil)
	}
	return message, nil
}

func normalizeMessageRequest(req *model.CreateMessageRequest) error {
	req.MediaID = nil
	req.Type = strings.ToLower(strings.TrimSpace(req.Type))
	if req.Type == "" {
		req.Type = model.MessageTypeText
	}
	switch req.Type {
	case model.MessageTypeText, model.MessageTypeImage, model.MessageTypeVideo, model.MessageTypeFile:
	default:
		return validation("unsupported message type")
	}
	if req.Content != nil {
		content := strings.TrimSpace(*req.Content)
		if content == "" {
			req.Content = nil
		} else {
			if utf8.RuneCountInString(content) > 10000 {
				return validation("content must not exceed 10000 characters")
			}
			req.Content = &content
		}
	}
	if req.MediaURL != nil {
		mediaURL, err := normalizeURL(*req.MediaURL)
		if err != nil {
			return validation("media_url must be a valid HTTP(S) URL")
		}
		req.MediaURL = mediaURL
	}
	if req.Type == model.MessageTypeText {
		if req.Content == nil {
			return validation("text messages require content")
		}
		if req.MediaURL != nil {
			return validation("text messages cannot include media_url; use a media message type")
		}
		return nil
	}
	if req.MediaURL == nil {
		return validation("media message types require media_url")
	}
	return nil
}

func normalizeMemberIDs(actorID string, rawIDs []string) ([]string, error) {
	seen := map[string]struct{}{actorID: {}}
	ids := make([]string, 0, len(rawIDs))
	for _, rawID := range rawIDs {
		id, err := canonicalID(rawID)
		if err != nil {
			return nil, validation("member_ids must contain valid UUIDs")
		}
		if id == actorID {
			if len(rawIDs) == 1 {
				return nil, validation("cannot create a chat with yourself")
			}
			continue
		}
		if _, exists := seen[id]; exists {
			continue
		}
		seen[id] = struct{}{}
		ids = append(ids, id)
	}
	sort.Strings(ids)
	return ids, nil
}

func validateIDs(ids ...string) error {
	for _, id := range ids {
		if _, err := canonicalID(id); err != nil {
			return validation("invalid resource identifier")
		}
	}
	return nil
}

func canonicalID(value string) (string, error) {
	id, err := uuid.Parse(strings.TrimSpace(value))
	if err != nil {
		return "", err
	}
	return id.String(), nil
}

func parseIdempotencyKey(value string) (uuid.UUID, error) {
	if strings.TrimSpace(value) == "" {
		return uuid.Nil, &Error{
			Code:    CodeIdempotencyNeeded,
			Message: "Idempotency-Key header is required",
		}
	}
	key, err := pkgidempotency.ParseKey(value)
	if err != nil {
		return uuid.Nil, &Error{
			Code:    CodeIdempotencyInvalid,
			Message: "Idempotency-Key must be a UUIDv4",
			Cause:   err,
		}
	}
	return key, nil
}

func normalizeURL(value string) (*string, error) {
	value = strings.TrimSpace(value)
	if value == "" || len(value) > 500 {
		return nil, errors.New("invalid URL")
	}
	parsed, err := url.ParseRequestURI(value)
	if err != nil || parsed.Host == "" || (parsed.Scheme != "http" && parsed.Scheme != "https") {
		return nil, errors.New("invalid URL")
	}
	return &value, nil
}

func normalizeOptionalURL(value string) (*string, error) {
	if strings.TrimSpace(value) == "" {
		empty := ""
		return &empty, nil
	}
	return normalizeURL(value)
}

func translateRepositoryError(err error, resource string) error {
	switch {
	case errors.Is(err, pkgidempotency.ErrPayloadConflict):
		return &Error{
			Code:    CodeIdempotencyConflict,
			Message: "Idempotency-Key was already used with a different payload",
			Cause:   err,
		}
	case errors.Is(err, pkgidempotency.ErrIncomplete):
		return &Error{
			Code:    CodeRetryable,
			Message: "service temporarily unavailable; retry shortly",
			Cause:   err,
		}
	case repository.IsRetryable(err):
		return &Error{
			Code:    CodeRetryable,
			Message: "service temporarily unavailable; retry shortly",
			Cause:   err,
		}
	case errors.Is(err, repository.ErrInvalidCursor):
		return &Error{Code: CodeInvalidCursor, Message: "invalid pagination cursor", Cause: err}
	case errors.Is(err, repository.ErrChatNotFound):
		return notFound(resource+" not found", err)
	case errors.Is(err, repository.ErrMessageNotFound):
		return notFound(resource+" not found", err)
	case errors.Is(err, repository.ErrForbidden):
		return forbidden("insufficient permissions", err)
	case errors.Is(err, repository.ErrConflict):
		return conflict(resource+" state conflicts with this operation", err)
	default:
		return internal(err)
	}
}

func validation(message string) *Error {
	return &Error{Code: CodeValidation, Message: message}
}

func forbidden(message string, cause error) *Error {
	return &Error{Code: CodeForbidden, Message: message, Cause: cause}
}

func notFound(message string, cause error) *Error {
	return &Error{Code: CodeNotFound, Message: message, Cause: cause}
}

func conflict(message string, cause error) *Error {
	return &Error{Code: CodeConflict, Message: message, Cause: cause}
}

func internal(cause error) *Error {
	return &Error{Code: CodeInternal, Message: "internal server error", Cause: cause}
}

var supportedReactions = map[string]struct{}{
	"👍":  {},
	"❤️": {},
	"😄":  {},
	"😮":  {},
	"😢":  {},
	"😡":  {},
	"👏":  {},
	"🎉":  {},
	"💡":  {},
	"🔥":  {},
}
