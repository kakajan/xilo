package repository

import (
	"context"
	"database/sql"
	"errors"
	"fmt"

	"github.com/jmoiron/sqlx"
	"github.com/lib/pq"
	"github.com/xilo-platform/xilo/internal/chat/model"
)

var (
	ErrFolderNotFound = errors.New("folder not found")
)

type FolderRepo struct {
	db *sqlx.DB
}

func NewFolderRepo(db *sqlx.DB) *FolderRepo {
	return &FolderRepo{db: db}
}

func (r *FolderRepo) ListFolders(ctx context.Context, userID string) ([]*model.ChatFolder, error) {
	var folders []*model.ChatFolder
	if err := r.db.SelectContext(ctx, &folders, `
		SELECT id, user_id, name, sort_order, created_at
		FROM chat_folders
		WHERE user_id = $1
		ORDER BY sort_order, created_at, id
	`, userID); err != nil {
		return nil, fmt.Errorf("list chat folders: %w", err)
	}
	if folders == nil {
		folders = []*model.ChatFolder{}
	}
	if err := r.attachChatIDs(ctx, folders); err != nil {
		return nil, err
	}
	return folders, nil
}

func (r *FolderRepo) CreateFolder(
	ctx context.Context,
	userID string,
	name string,
	sortOrder int,
) (*model.ChatFolder, error) {
	var folder model.ChatFolder
	err := r.db.GetContext(ctx, &folder, `
		INSERT INTO chat_folders (user_id, name, sort_order)
		VALUES ($1, $2, $3)
		RETURNING id, user_id, name, sort_order, created_at
	`, userID, name, sortOrder)
	if err != nil {
		return nil, mapWriteError("insert chat folder", err)
	}
	folder.ChatIDs = []string{}
	return &folder, nil
}

func (r *FolderRepo) UpdateFolder(
	ctx context.Context,
	userID string,
	folderID string,
	name *string,
	sortOrder *int,
) (*model.ChatFolder, error) {
	var folder model.ChatFolder
	err := r.db.GetContext(ctx, &folder, `
		UPDATE chat_folders
		SET name = COALESCE($3, name),
		    sort_order = COALESCE($4, sort_order)
		WHERE id = $1 AND user_id = $2
		RETURNING id, user_id, name, sort_order, created_at
	`, folderID, userID, name, sortOrder)
	if errors.Is(err, sql.ErrNoRows) {
		return nil, ErrFolderNotFound
	}
	if err != nil {
		return nil, fmt.Errorf("update chat folder: %w", err)
	}
	if err := r.attachChatIDs(ctx, []*model.ChatFolder{&folder}); err != nil {
		return nil, err
	}
	return &folder, nil
}

func (r *FolderRepo) DeleteFolder(ctx context.Context, userID, folderID string) error {
	result, err := r.db.ExecContext(ctx, `
		DELETE FROM chat_folders
		WHERE id = $1 AND user_id = $2
	`, folderID, userID)
	if err != nil {
		return fmt.Errorf("delete chat folder: %w", err)
	}
	affected, _ := result.RowsAffected()
	if affected == 0 {
		return ErrFolderNotFound
	}
	return nil
}

func (r *FolderRepo) SetFolderChats(
	ctx context.Context,
	userID string,
	folderID string,
	chatIDs []string,
) (*model.ChatFolder, error) {
	tx, err := r.db.BeginTxx(ctx, &sql.TxOptions{Isolation: sql.LevelReadCommitted})
	if err != nil {
		return nil, fmt.Errorf("begin set folder chats: %w", err)
	}
	defer func() { _ = tx.Rollback() }()

	var ownerID string
	err = tx.GetContext(ctx, &ownerID, `
		SELECT user_id FROM chat_folders WHERE id = $1 FOR UPDATE
	`, folderID)
	if errors.Is(err, sql.ErrNoRows) {
		return nil, ErrFolderNotFound
	}
	if err != nil {
		return nil, fmt.Errorf("lock chat folder: %w", err)
	}
	if ownerID != userID {
		return nil, ErrForbidden
	}

	if len(chatIDs) > 0 {
		var memberCount int
		if err := tx.GetContext(ctx, &memberCount, `
			SELECT COUNT(*)
			FROM chat_members cm
			WHERE cm.user_id = $1
			  AND cm.chat_id = ANY($2)
			  AND cm.left_at IS NULL
		`, userID, pq.Array(chatIDs)); err != nil {
			return nil, fmt.Errorf("validate folder chat memberships: %w", err)
		}
		if memberCount != len(chatIDs) {
			return nil, ErrForbidden
		}
	}

	if _, err := tx.ExecContext(ctx, `
		DELETE FROM chat_folder_items WHERE folder_id = $1
	`, folderID); err != nil {
		return nil, fmt.Errorf("clear folder chats: %w", err)
	}

	for index, chatID := range chatIDs {
		if _, err := tx.ExecContext(ctx, `
			INSERT INTO chat_folder_items (folder_id, chat_id, sort_order)
			VALUES ($1, $2, $3)
		`, folderID, chatID, index); err != nil {
			return nil, mapWriteError("insert folder chat item", err)
		}
	}

	var folder model.ChatFolder
	if err := tx.GetContext(ctx, &folder, `
		SELECT id, user_id, name, sort_order, created_at
		FROM chat_folders
		WHERE id = $1
	`, folderID); err != nil {
		return nil, fmt.Errorf("reload chat folder: %w", err)
	}
	if err := tx.Commit(); err != nil {
		return nil, mapWriteError("commit set folder chats", err)
	}
	folder.ChatIDs = append([]string(nil), chatIDs...)
	return &folder, nil
}

func (r *FolderRepo) attachChatIDs(ctx context.Context, folders []*model.ChatFolder) error {
	if len(folders) == 0 {
		return nil
	}
	ids := make([]string, 0, len(folders))
	byID := make(map[string]*model.ChatFolder, len(folders))
	for _, folder := range folders {
		folder.ChatIDs = []string{}
		ids = append(ids, folder.ID)
		byID[folder.ID] = folder
	}

	type folderChat struct {
		FolderID string `db:"folder_id"`
		ChatID   string `db:"chat_id"`
	}
	var rows []folderChat
	if err := r.db.SelectContext(ctx, &rows, `
		SELECT folder_id, chat_id
		FROM chat_folder_items
		WHERE folder_id = ANY($1)
		ORDER BY folder_id, sort_order, chat_id
	`, pq.Array(ids)); err != nil {
		return fmt.Errorf("load folder chat ids: %w", err)
	}
	for _, row := range rows {
		if folder := byID[row.FolderID]; folder != nil {
			folder.ChatIDs = append(folder.ChatIDs, row.ChatID)
		}
	}
	return nil
}

func (r *FolderRepo) GetFolder(ctx context.Context, userID, folderID string) (*model.ChatFolder, error) {
	var folder model.ChatFolder
	err := r.db.GetContext(ctx, &folder, `
		SELECT id, user_id, name, sort_order, created_at
		FROM chat_folders
		WHERE id = $1 AND user_id = $2
	`, folderID, userID)
	if errors.Is(err, sql.ErrNoRows) {
		return nil, ErrFolderNotFound
	}
	if err != nil {
		return nil, fmt.Errorf("get chat folder: %w", err)
	}
	if err := r.attachChatIDs(ctx, []*model.ChatFolder{&folder}); err != nil {
		return nil, err
	}
	return &folder, nil
}
