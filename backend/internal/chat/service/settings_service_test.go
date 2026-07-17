package service

import (
	"context"
	"testing"

	"github.com/xilo-platform/xilo/internal/chat/model"
)

type savedMessagesRepo struct {
	fakeRepository
	ensureCalls int
	savedChat   *model.Chat
}

func (f *savedMessagesRepo) EnsureSavedMessagesChat(
	_ context.Context,
	userID string,
) (*model.Chat, error) {
	f.ensureCalls++
	if f.savedChat != nil {
		return f.savedChat, nil
	}
	f.savedChat = &model.Chat{
		ID:          testChat,
		Type:        model.ChatTypeSaved,
		CurrentRole: model.MemberRoleAdmin,
		Members: []model.ChatMember{
			{ChatID: testChat, UserID: userID, Role: model.MemberRoleAdmin},
		},
	}
	return f.savedChat, nil
}

func TestEnsureSavedMessagesChat_Idempotent(t *testing.T) {
	repo := &savedMessagesRepo{}
	svc := NewChatService(repo)

	first, err := svc.EnsureSavedMessagesChat(context.Background(), testActor)
	if err != nil {
		t.Fatalf("first call failed: %v", err)
	}
	second, err := svc.EnsureSavedMessagesChat(context.Background(), testActor)
	if err != nil {
		t.Fatalf("second call failed: %v", err)
	}
	if repo.ensureCalls != 2 {
		t.Fatalf("expected 2 repo calls, got %d", repo.ensureCalls)
	}
	if first.ID != second.ID {
		t.Fatalf("expected same chat id, got %s and %s", first.ID, second.ID)
	}
	if first.Type != model.ChatTypeSaved {
		t.Fatalf("expected saved chat type, got %s", first.Type)
	}
}

type fakeFolderRepo struct {
	folders []*model.ChatFolder
}

func (f *fakeFolderRepo) ListFolders(_ context.Context, userID string) ([]*model.ChatFolder, error) {
	if f.folders == nil {
		return []*model.ChatFolder{}, nil
	}
	return f.folders, nil
}

func (f *fakeFolderRepo) CreateFolder(_ context.Context, userID, name string, sortOrder int) (*model.ChatFolder, error) {
	folder := &model.ChatFolder{
		ID:        testChat,
		UserID:    userID,
		Name:      name,
		SortOrder: sortOrder,
		ChatIDs:   []string{},
	}
	f.folders = append(f.folders, folder)
	return folder, nil
}

func (f *fakeFolderRepo) UpdateFolder(_ context.Context, userID, folderID string, name *string, sortOrder *int) (*model.ChatFolder, error) {
	return nil, nil
}

func (f *fakeFolderRepo) DeleteFolder(_ context.Context, userID, folderID string) error {
	return nil
}

func (f *fakeFolderRepo) SetFolderChats(_ context.Context, userID, folderID string, chatIDs []string) (*model.ChatFolder, error) {
	return nil, nil
}

func (f *fakeFolderRepo) GetFolder(_ context.Context, userID, folderID string) (*model.ChatFolder, error) {
	return nil, nil
}

func TestFolderService_CreateAndList(t *testing.T) {
	repo := &fakeFolderRepo{}
	svc := NewFolderService(repo)

	created, err := svc.CreateFolder(context.Background(), testActor, &model.CreateChatFolderRequest{
		Name: "Work",
	})
	if err != nil {
		t.Fatalf("create folder failed: %v", err)
	}
	if created.Name != "Work" {
		t.Fatalf("expected folder name Work, got %s", created.Name)
	}

	folders, err := svc.ListFolders(context.Background(), testActor)
	if err != nil {
		t.Fatalf("list folders failed: %v", err)
	}
	if len(folders) != 1 {
		t.Fatalf("expected 1 folder, got %d", len(folders))
	}
}
