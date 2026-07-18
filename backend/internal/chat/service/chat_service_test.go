package service

import (
	"context"
	"errors"
	"fmt"
	"net/http"
	"strings"
	"testing"
	"time"

	"github.com/xilo-platform/xilo/internal/chat/model"
	"github.com/xilo-platform/xilo/internal/chat/repository"
	pkgidempotency "github.com/xilo-platform/xilo/pkg/idempotency"
)

const (
	testActor  = "11111111-1111-4111-8111-111111111111"
	testOther  = "22222222-2222-4222-8222-222222222222"
	testThird  = "33333333-3333-4333-8333-333333333333"
	testChat   = "44444444-4444-4444-8444-444444444444"
	testMsg    = "55555555-5555-4555-8555-555555555555"
	testIdempotencyKey = "123e4567-e89b-42d3-a456-426614174000"
)

var testReceivedAt = time.Now().UTC()

type fakeRepository struct {
	ChatRepository

	chat             *model.Chat
	member           *model.ChatMember
	message          *model.Message
	createChatCalled bool
	createChatErr    error
	chatMutation     *pkgidempotency.MutationResult[model.Chat]
	createMsgCalled  bool
	updateMsgCalled  bool
	createdMessage   *model.CreateMessageRequest
	createMessageErr error
	messageMutation  *pkgidempotency.MutationResult[model.Message]
	idempotency      pkgidempotency.Request
	membershipCalls  int
	markReadErr      error
	listChats        []*model.Chat
	listChatsCursor  string
	listChatsErr     error
	listChatsCalled  bool
}

func (f *fakeRepository) CreateChat(
	_ context.Context,
	_ string,
	idempotencyRequest pkgidempotency.Request,
	_ *model.CreateChatRequest,
	_ []string,
	_ string,
	_ string,
) (*pkgidempotency.MutationResult[model.Chat], error) {
	f.createChatCalled = true
	f.idempotency = idempotencyRequest
	if f.createChatErr != nil {
		return nil, f.createChatErr
	}
	if f.chatMutation != nil {
		return f.chatMutation, nil
	}
	return &pkgidempotency.MutationResult[model.Chat]{
		Value:          f.chat,
		Outcome:        pkgidempotency.OutcomeNew,
		ResponseStatus: http.StatusCreated,
	}, nil
}

func (f *fakeRepository) ListChats(
	_ context.Context,
	_ string,
	_ model.ListParams,
) ([]*model.Chat, string, error) {
	f.listChatsCalled = true
	if f.listChatsErr != nil {
		return nil, "", f.listChatsErr
	}
	return f.listChats, f.listChatsCursor, nil
}

func (f *fakeRepository) GetChat(
	_ context.Context,
	_ string,
	_ string,
) (*model.Chat, error) {
	if f.chat == nil {
		return nil, repository.ErrChatNotFound
	}
	return f.chat, nil
}

func (f *fakeRepository) GetMembership(
	_ context.Context,
	_ string,
	_ string,
) (*model.ChatMember, error) {
	f.membershipCalls++
	if f.member == nil {
		return nil, repository.ErrForbidden
	}
	return f.member, nil
}

func (f *fakeRepository) CreateMessage(
	_ context.Context,
	_ string,
	_ string,
	idempotencyRequest pkgidempotency.Request,
	req *model.CreateMessageRequest,
) (*pkgidempotency.MutationResult[model.Message], error) {
	f.createMsgCalled = true
	f.idempotency = idempotencyRequest
	copy := *req
	f.createdMessage = &copy
	if f.createMessageErr != nil {
		return nil, f.createMessageErr
	}
	if f.messageMutation != nil {
		return f.messageMutation, nil
	}
	return &pkgidempotency.MutationResult[model.Message]{
		Value:          f.message,
		Outcome:        pkgidempotency.OutcomeNew,
		ResponseStatus: http.StatusCreated,
	}, nil
}

func (f *fakeRepository) GetMessage(
	_ context.Context,
	_ string,
) (*model.Message, error) {
	if f.message == nil {
		return nil, repository.ErrMessageNotFound
	}
	return f.message, nil
}

func (f *fakeRepository) UpdateMessage(
	_ context.Context,
	_ string,
	_ string,
	content string,
) (*model.Message, error) {
	f.updateMsgCalled = true
	updated := *f.message
	updated.Content = &content
	updated.IsEdited = true
	return &updated, nil
}

func (f *fakeRepository) MarkRead(
	_ context.Context,
	_ string,
	_ string,
) (*model.Read, error) {
	if f.markReadErr != nil {
		return nil, f.markReadErr
	}
	return &model.Read{UserID: testActor}, nil
}

func TestCreateDirectChatRejectsSelf(t *testing.T) {
	repo := &fakeRepository{}
	svc := NewChatService(repo)

	_, err := svc.CreateChat(
		context.Background(),
		testActor,
		testIdempotencyKey,
		testReceivedAt,
		&model.CreateChatRequest{
		Type:      model.ChatTypeDirect,
		MemberIDs: []string{testActor},
		},
	)

	assertServiceCode(t, err, CodeValidation)
	if repo.createChatCalled {
		t.Fatal("repository must not be called for a self-chat")
	}
}

func TestCreateGroupRequiresTwoOtherMembers(t *testing.T) {
	repo := &fakeRepository{}
	svc := NewChatService(repo)
	name := "team"

	_, err := svc.CreateChat(
		context.Background(),
		testActor,
		testIdempotencyKey,
		testReceivedAt,
		&model.CreateChatRequest{
		Type:      model.ChatTypeGroup,
		Name:      &name,
		MemberIDs: []string{testOther},
		},
	)

	assertServiceCode(t, err, CodeValidation)
	if repo.createChatCalled {
		t.Fatal("repository must not be called for an undersized group")
	}
}

func TestCreateChatRequiresValidIdempotencyKey(t *testing.T) {
	tests := []struct {
		name string
		key  string
		code string
	}{
		{name: "missing", key: "", code: CodeIdempotencyNeeded},
		{name: "malformed", key: "not-a-uuid", code: CodeIdempotencyInvalid},
		{
			name: "wrong UUID version",
			key:  "123e4567-e89b-12d3-a456-426614174000",
			code: CodeIdempotencyInvalid,
		},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			repo := &fakeRepository{}
			svc := NewChatService(repo)
			_, err := svc.CreateChat(context.Background(), testActor, tt.key, testReceivedAt, &model.CreateChatRequest{
				Type:      model.ChatTypeDirect,
				MemberIDs: []string{testOther},
			})
			assertServiceCode(t, err, tt.code)
			if repo.createChatCalled {
				t.Fatal("repository must not be called with an invalid idempotency key")
			}
		})
	}
}

func TestCanonicalChatHashIgnoresMemberOrder(t *testing.T) {
	repo := &fakeRepository{}
	svc := NewChatService(repo)
	name := "team"

	_, err := svc.CreateChat(context.Background(), testActor, testIdempotencyKey, testReceivedAt, &model.CreateChatRequest{
		Type:      model.ChatTypeGroup,
		Name:      &name,
		MemberIDs: []string{testThird, testOther},
	})
	if err != nil {
		t.Fatalf("first CreateChat returned error: %v", err)
	}
	firstHash := repo.idempotency.RequestHash

	_, err = svc.CreateChat(context.Background(), testActor, testIdempotencyKey, testReceivedAt, &model.CreateChatRequest{
		Type:      model.ChatTypeGroup,
		Name:      &name,
		MemberIDs: []string{testOther, testThird},
	})
	if err != nil {
		t.Fatalf("second CreateChat returned error: %v", err)
	}
	if firstHash == "" || firstHash != repo.idempotency.RequestHash {
		t.Fatalf("semantic hashes differ: %q != %q", firstHash, repo.idempotency.RequestHash)
	}
	if repo.idempotency.Operation != OperationCreateChat {
		t.Fatalf("operation = %q, want %q", repo.idempotency.Operation, OperationCreateChat)
	}
}

func TestIdempotencyPayloadConflictMapsToStableCode(t *testing.T) {
	repo := &fakeRepository{createChatErr: pkgidempotency.ErrPayloadConflict}
	svc := NewChatService(repo)

	_, err := svc.CreateChat(context.Background(), testActor, testIdempotencyKey, testReceivedAt, &model.CreateChatRequest{
		Type:      model.ChatTypeDirect,
		MemberIDs: []string{testOther},
	})

	assertServiceCode(t, err, CodeIdempotencyConflict)
}

func TestGroupMetadataRequiresAdmin(t *testing.T) {
	repo := &fakeRepository{
		chat: &model.Chat{
			ID:          testChat,
			Type:        model.ChatTypeGroup,
			CurrentRole: model.MemberRoleMember,
		},
	}
	svc := NewChatService(repo)
	name := "renamed"

	_, err := svc.UpdateChat(context.Background(), testActor, testChat, &model.UpdateChatRequest{
		Name: &name,
	})

	assertServiceCode(t, err, CodeForbidden)
}

func TestNonMemberCannotReadMessageHistory(t *testing.T) {
	repo := &fakeRepository{}
	svc := NewChatService(repo)

	_, _, err := svc.ListMessages(
		context.Background(),
		testActor,
		testChat,
		model.ListParams{},
	)

	assertServiceCode(t, err, CodeNotFound)
}

func TestCreateMessageValidation(t *testing.T) {
	tests := []struct {
		name string
		req  model.CreateMessageRequest
	}{
		{
			name: "empty payload",
			req:  model.CreateMessageRequest{Type: model.MessageTypeText},
		},
		{
			name: "unsupported type",
			req: model.CreateMessageRequest{
				Type:    "audio",
				Content: stringPointer("hello"),
			},
		},
		{
			name: "media type without URL",
			req: model.CreateMessageRequest{
				Type:    model.MessageTypeImage,
				Content: stringPointer("caption"),
			},
		},
		{
			name: "text type with media URL",
			req: model.CreateMessageRequest{
				Type:     model.MessageTypeText,
				Content:  stringPointer("caption"),
				MediaURL: stringPointer("https://storage.example/image.png"),
			},
		},
		{
			name: "oversized text",
			req: model.CreateMessageRequest{
				Type:    model.MessageTypeText,
				Content: stringPointer(strings.Repeat("a", 10001)),
			},
		},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			repo := &fakeRepository{
				member: &model.ChatMember{ChatID: testChat, UserID: testActor},
			}
			svc := NewChatService(repo)
			_, err := svc.CreateMessage(
				context.Background(),
				testActor,
				testChat,
				testIdempotencyKey,
				testReceivedAt,
				&tt.req,
			)
			assertServiceCode(t, err, CodeValidation)
			if repo.createMsgCalled {
				t.Fatal("repository must not receive an invalid message")
			}
		})
	}
}

func TestCreateMediaMessageDelegatesTransactionalMediaValidation(t *testing.T) {
	repo := &fakeRepository{
		member:  &model.ChatMember{ChatID: testChat, UserID: testActor},
		message: &model.Message{ID: testMsg, ChatID: testChat, SenderID: testActor},
	}
	svc := NewChatService(repo)
	mediaURL := "https://storage.example/xilo/user/media/image.png"

	_, err := svc.CreateMessage(
		context.Background(),
		testActor,
		testChat,
		testIdempotencyKey,
		testReceivedAt,
		&model.CreateMessageRequest{
			Type:     model.MessageTypeImage,
			MediaURL: &mediaURL,
		},
	)

	if err != nil {
		t.Fatalf("expected valid media message, got %v", err)
	}
	if !repo.createMsgCalled || repo.createdMessage == nil {
		t.Fatal("expected validated media message to reach repository")
	}
	if repo.createdMessage.MediaID != nil {
		t.Fatal("service must not trust a client-derived media ID")
	}
}

func TestCreateMediaMessageRejectsOversizedUpload(t *testing.T) {
	repo := &fakeRepository{
		member:           &model.ChatMember{ChatID: testChat, UserID: testActor},
		createMessageErr: repository.ErrMediaTooLarge,
	}
	svc := NewChatService(repo)
	mediaURL := "https://storage.example/xilo/user/media/video.mp4"

	_, err := svc.CreateMessage(
		context.Background(),
		testActor,
		testChat,
		testIdempotencyKey,
		testReceivedAt,
		&model.CreateMessageRequest{
			Type:     model.MessageTypeVideo,
			MediaURL: &mediaURL,
		},
	)

	assertServiceCode(t, err, CodeValidation)
	if !repo.createMsgCalled {
		t.Fatal("repository must enforce media size inside the domain transaction")
	}
}

func TestCreateMessageReplayDoesNotRequireCurrentMembership(t *testing.T) {
	legacyJSON := []byte(
		`{"id":"` + testMsg + `","chat_id":"` + testChat +
			`","legacy_delivery_state":"accepted"}`,
	)
	repo := &fakeRepository{
		member: nil,
		messageMutation: &pkgidempotency.MutationResult[model.Message]{
			Outcome:        pkgidempotency.OutcomeReplay,
			ResponseStatus: http.StatusCreated,
			ReplayJSON:     legacyJSON,
		},
	}
	svc := NewChatService(repo)
	content := "hello"

	result, err := svc.CreateMessage(
		context.Background(),
		testActor,
		testChat,
		testIdempotencyKey,
		testReceivedAt,
		&model.CreateMessageRequest{
			Type:    model.MessageTypeText,
			Content: &content,
		},
	)
	if err != nil {
		t.Fatalf("CreateMessage replay returned error: %v", err)
	}
	if repo.membershipCalls != 0 {
		t.Fatalf("service performed %d membership checks before replay acquisition", repo.membershipCalls)
	}
	if result == nil || !result.IsReplay() || string(result.ReplayJSON) != string(legacyJSON) {
		t.Fatalf("unexpected replay result: %+v", result)
	}
	if !repo.idempotency.ReceivedAt.Equal(testReceivedAt) {
		t.Fatalf("received_at = %v, want %v", repo.idempotency.ReceivedAt, testReceivedAt)
	}
}

func TestOnlySenderMayEditMessage(t *testing.T) {
	repo := &fakeRepository{
		member: &model.ChatMember{ChatID: testChat, UserID: testActor},
		message: &model.Message{
			ID:        testMsg,
			ChatID:    testChat,
			SenderID:  testOther,
			CreatedAt: time.Now().UTC(),
		},
	}
	svc := NewChatService(repo)

	_, err := svc.UpdateMessage(
		context.Background(),
		testActor,
		testMsg,
		&model.UpdateMessageRequest{Content: "changed"},
	)

	assertServiceCode(t, err, CodeForbidden)
	if repo.updateMsgCalled {
		t.Fatal("repository must not update another sender's message")
	}
}

func TestMessageEditWindowIsEnforced(t *testing.T) {
	now := time.Date(2026, 7, 16, 12, 0, 0, 0, time.UTC)
	repo := &fakeRepository{
		member: &model.ChatMember{ChatID: testChat, UserID: testActor},
		message: &model.Message{
			ID:        testMsg,
			ChatID:    testChat,
			SenderID:  testActor,
			CreatedAt: now.Add(-48 * time.Hour),
		},
	}
	svc := NewChatService(repo)
	svc.clock = func() time.Time { return now }

	_, err := svc.UpdateMessage(
		context.Background(),
		testActor,
		testMsg,
		&model.UpdateMessageRequest{Content: "changed"},
	)

	assertServiceCode(t, err, CodeEditWindowExpired)
	if repo.updateMsgCalled {
		t.Fatal("repository must not update a message at or after the 48-hour boundary")
	}
}

func TestUnsupportedReactionIsRejected(t *testing.T) {
	svc := NewChatService(&fakeRepository{})

	_, err := svc.ToggleReaction(
		context.Background(),
		testActor,
		testMsg,
		&model.ReactionRequest{Reaction: "❌"},
	)

	assertServiceCode(t, err, CodeValidation)
}

func TestRetryableRepositoryErrorMapsToStableServiceCode(t *testing.T) {
	svc := NewChatService(&fakeRepository{markReadErr: repository.ErrRetryable})

	_, err := svc.MarkRead(context.Background(), testActor, testMsg)

	assertServiceCode(t, err, CodeRetryable)
	var serviceErr *Error
	if !errors.As(err, &serviceErr) {
		t.Fatalf("expected service error, got %T", err)
	}
	if strings.Contains(serviceErr.Message, "40001") || strings.Contains(serviceErr.Message, "deadlock") {
		t.Fatalf("service error leaked database details: %q", serviceErr.Message)
	}
}

func TestListChatsEmptyReturnsNonNilSlice(t *testing.T) {
	svc := NewChatService(&fakeRepository{listChats: nil})

	chats, cursor, err := svc.ListChats(context.Background(), testActor, model.ListParams{})
	if err != nil {
		t.Fatalf("ListChats: %v", err)
	}
	if chats == nil {
		t.Fatal("expected non-nil empty chat slice")
	}
	if len(chats) != 0 {
		t.Fatalf("len(chats) = %d, want 0", len(chats))
	}
	if cursor != "" {
		t.Fatalf("cursor = %q, want empty", cursor)
	}
}

func TestListChatsPreservesWrappedRepositoryCause(t *testing.T) {
	tests := []struct {
		name    string
		cause   error
		wantSub string
	}{
		{
			name:    "list chats",
			cause:   fmt.Errorf("list chats: %w", errors.New("db unavailable")),
			wantSub: "list chats",
		},
		{
			name:    "load chat members",
			cause:   fmt.Errorf("load chat members: %w", errors.New("db unavailable")),
			wantSub: "load chat members",
		},
		{
			name:    "load last chat messages",
			cause:   fmt.Errorf("load last chat messages: %w", errors.New("db unavailable")),
			wantSub: "load last chat messages",
		},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			svc := NewChatService(&fakeRepository{listChatsErr: tt.cause})
			_, _, err := svc.ListChats(context.Background(), testActor, model.ListParams{})
			assertServiceCode(t, err, CodeInternal)

			var serviceErr *Error
			if !errors.As(err, &serviceErr) {
				t.Fatalf("expected *Error, got %T", err)
			}
			if serviceErr.Cause == nil {
				t.Fatal("expected Cause to preserve repository wrap")
			}
			if !strings.Contains(serviceErr.Cause.Error(), tt.wantSub) {
				t.Fatalf("cause = %q, want substring %q", serviceErr.Cause.Error(), tt.wantSub)
			}
			if !strings.Contains(err.Error(), tt.wantSub) {
				t.Fatalf("Error() = %q, want substring %q", err.Error(), tt.wantSub)
			}
			if serviceErr.Message != "internal server error" {
				t.Fatalf("Message = %q, want opaque internal message", serviceErr.Message)
			}
		})
	}
}

func assertServiceCode(t *testing.T, err error, want string) {
	t.Helper()
	if err == nil {
		t.Fatalf("expected service error %q", want)
	}
	var serviceErr *Error
	if !errors.As(err, &serviceErr) {
		t.Fatalf("expected *service.Error, got %T: %v", err, err)
	}
	if serviceErr.Code != want {
		t.Fatalf("expected code %q, got %q (%v)", want, serviceErr.Code, err)
	}
}

func stringPointer(value string) *string {
	return &value
}
