package service

import (
	"context"
	"errors"
	"strings"
	"unicode/utf8"

	"github.com/xilo-platform/xilo/internal/chat/model"
	"github.com/xilo-platform/xilo/internal/chat/repository"
)

type FolderRepository interface {
	ListFolders(ctx context.Context, userID string) ([]*model.ChatFolder, error)
	CreateFolder(ctx context.Context, userID, name string, sortOrder int) (*model.ChatFolder, error)
	UpdateFolder(ctx context.Context, userID, folderID string, name *string, sortOrder *int) (*model.ChatFolder, error)
	DeleteFolder(ctx context.Context, userID, folderID string) error
	SetFolderChats(ctx context.Context, userID, folderID string, chatIDs []string) (*model.ChatFolder, error)
	GetFolder(ctx context.Context, userID, folderID string) (*model.ChatFolder, error)
}

type FolderService struct {
	repo FolderRepository
}

func NewFolderService(repo FolderRepository) *FolderService {
	return &FolderService{repo: repo}
}

func (s *FolderService) ListFolders(ctx context.Context, userID string) ([]*model.ChatFolder, error) {
	if _, err := canonicalID(userID); err != nil {
		return nil, forbidden("invalid authenticated user", err)
	}
	folders, err := s.repo.ListFolders(ctx, userID)
	if err != nil {
		return nil, translateFolderError(err)
	}
	return folders, nil
}

func (s *FolderService) CreateFolder(
	ctx context.Context,
	userID string,
	req *model.CreateChatFolderRequest,
) (*model.ChatFolder, error) {
	if _, err := canonicalID(userID); err != nil {
		return nil, forbidden("invalid authenticated user", err)
	}
	name, err := normalizeFolderName(req.Name)
	if err != nil {
		return nil, err
	}
	sortOrder := 0
	if req.SortOrder != nil {
		sortOrder = *req.SortOrder
	}
	folder, err := s.repo.CreateFolder(ctx, userID, name, sortOrder)
	if err != nil {
		return nil, translateFolderError(err)
	}
	return folder, nil
}

func (s *FolderService) UpdateFolder(
	ctx context.Context,
	userID string,
	folderID string,
	req *model.UpdateChatFolderRequest,
) (*model.ChatFolder, error) {
	if err := validateIDs(userID, folderID); err != nil {
		return nil, err
	}
	if req.Name == nil && req.SortOrder == nil {
		return nil, validation("at least one updatable field is required")
	}
	var name *string
	if req.Name != nil {
		normalized, err := normalizeFolderName(*req.Name)
		if err != nil {
			return nil, err
		}
		name = &normalized
	}
	folder, err := s.repo.UpdateFolder(ctx, userID, folderID, name, req.SortOrder)
	if err != nil {
		return nil, translateFolderError(err)
	}
	return folder, nil
}

func (s *FolderService) DeleteFolder(ctx context.Context, userID, folderID string) error {
	if err := validateIDs(userID, folderID); err != nil {
		return err
	}
	if err := s.repo.DeleteFolder(ctx, userID, folderID); err != nil {
		return translateFolderError(err)
	}
	return nil
}

func (s *FolderService) SetFolderChats(
	ctx context.Context,
	userID string,
	folderID string,
	req *model.SetFolderChatsRequest,
) (*model.ChatFolder, error) {
	if err := validateIDs(userID, folderID); err != nil {
		return nil, err
	}
	chatIDs, err := normalizeFolderChatIDs(req.ChatIDs)
	if err != nil {
		return nil, err
	}
	folder, err := s.repo.SetFolderChats(ctx, userID, folderID, chatIDs)
	if err != nil {
		return nil, translateFolderError(err)
	}
	return folder, nil
}

func normalizeFolderName(value string) (string, error) {
	name := strings.TrimSpace(value)
	if utf8.RuneCountInString(name) == 0 || utf8.RuneCountInString(name) > 100 {
		return "", validation("folder name must be between 1 and 100 characters")
	}
	return name, nil
}

func normalizeFolderChatIDs(rawIDs []string) ([]string, error) {
	if rawIDs == nil {
		return []string{}, nil
	}
	seen := map[string]struct{}{}
	ids := make([]string, 0, len(rawIDs))
	for _, rawID := range rawIDs {
		id, err := canonicalID(rawID)
		if err != nil {
			return nil, validation("chat_ids must contain valid UUIDs")
		}
		if _, exists := seen[id]; exists {
			continue
		}
		seen[id] = struct{}{}
		ids = append(ids, id)
	}
	return ids, nil
}

func translateFolderError(err error) error {
	switch {
	case repository.IsRetryable(err):
		return &Error{
			Code:    CodeRetryable,
			Message: "service temporarily unavailable; retry shortly",
			Cause:   err,
		}
	case errors.Is(err, repository.ErrFolderNotFound):
		return notFound("folder not found", err)
	case errors.Is(err, repository.ErrForbidden):
		return forbidden("insufficient permissions", err)
	case errors.Is(err, repository.ErrConflict):
		return conflict("folder state conflicts with this operation", err)
	default:
		return internal(err)
	}
}
