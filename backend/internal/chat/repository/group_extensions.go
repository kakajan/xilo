package repository

import (
	"context"
	"crypto/rand"
	"database/sql"
	"encoding/hex"
	"errors"
	"fmt"
	"strings"

	"github.com/lib/pq"
	"github.com/xilo-platform/xilo/internal/chat/model"
)

func (r *ChatRepo) UpdateMemberRole(
	ctx context.Context,
	chatID string,
	actorID string,
	targetID string,
	role string,
) error {
	tx, err := r.db.BeginTxx(ctx, &sql.TxOptions{Isolation: sql.LevelSerializable})
	if err != nil {
		return fmt.Errorf("begin update member role: %w", err)
	}
	defer func() { _ = tx.Rollback() }()

	if err := requireGroupAdmin(ctx, tx, chatID, actorID); err != nil {
		return err
	}

	var targetRole string
	err = tx.GetContext(ctx, &targetRole, `
		SELECT role
		FROM chat_members
		WHERE chat_id = $1 AND user_id = $2 AND left_at IS NULL
		FOR UPDATE
	`, chatID, targetID)
	if errors.Is(err, sql.ErrNoRows) {
		return ErrChatNotFound
	}
	if err != nil {
		return fmt.Errorf("lock target membership: %w", err)
	}

	if role == model.MemberRoleMember && targetRole == model.MemberRoleAdmin {
		var otherAdminCount int
		if err := tx.GetContext(ctx, &otherAdminCount, `
			SELECT COUNT(*)
			FROM chat_members
			WHERE chat_id = $1
			  AND user_id <> $2
			  AND role = 'admin'
			  AND left_at IS NULL
		`, chatID, targetID); err != nil {
			return fmt.Errorf("count group admins: %w", err)
		}
		if otherAdminCount == 0 {
			return ErrForbidden
		}
	}

	if _, err := tx.ExecContext(ctx, `
		UPDATE chat_members
		SET role = $3, updated_at = NOW()
		WHERE chat_id = $1 AND user_id = $2 AND left_at IS NULL
	`, chatID, targetID, role); err != nil {
		return mapWriteError("update member role", err)
	}
	if err := tx.Commit(); err != nil {
		return mapWriteError("commit update member role", err)
	}
	return nil
}

func (r *ChatRepo) InsertSystemMessage(
	ctx context.Context,
	chatID string,
	senderID string,
	content string,
) (*model.Message, error) {
	content = strings.TrimSpace(content)
	if content == "" {
		return nil, fmt.Errorf("system message content required")
	}
	var message model.Message
	err := r.db.GetContext(ctx, &message, `
		INSERT INTO messages (chat_id, sender_id, type, content)
		VALUES ($1, $2, $3, $4)
		RETURNING id, chat_id, sender_id, type, content, media_id, media_url, reply_to_id,
		          is_edited, is_deleted, created_at, updated_at, edited_at, deleted_at
	`, chatID, senderID, model.MessageTypeSystem, content)
	if err != nil {
		return nil, mapWriteError("insert system message", err)
	}
	if _, err := r.db.ExecContext(ctx, `
		UPDATE chats
		SET last_message_at = GREATEST(COALESCE(last_message_at, $2), $2),
		    updated_at = NOW()
		WHERE id = $1
	`, chatID, message.CreatedAt); err != nil {
		return nil, fmt.Errorf("update chat last message: %w", err)
	}
	message.Reactions = []model.Reaction{}
	message.ReadBy = []model.Read{}
	return &message, nil
}

func (r *ChatRepo) LookupUserIDsByUsernames(
	ctx context.Context,
	usernames []string,
) (map[string]string, error) {
	out := make(map[string]string, len(usernames))
	if len(usernames) == 0 {
		return out, nil
	}
	normalized := make([]string, 0, len(usernames))
	seen := map[string]struct{}{}
	for _, raw := range usernames {
		u := strings.ToLower(strings.TrimSpace(strings.TrimPrefix(raw, "@")))
		if u == "" {
			continue
		}
		if _, ok := seen[u]; ok {
			continue
		}
		seen[u] = struct{}{}
		normalized = append(normalized, u)
	}
	if len(normalized) == 0 {
		return out, nil
	}
	type row struct {
		ID       string `db:"id"`
		Username string `db:"username"`
	}
	var rows []row
	if err := r.db.SelectContext(ctx, &rows, `
		SELECT id, LOWER(username) AS username
		FROM users
		WHERE LOWER(username) = ANY($1) AND deleted_at IS NULL
	`, pq.Array(normalized)); err != nil {
		return nil, fmt.Errorf("lookup usernames: %w", err)
	}
	for _, row := range rows {
		out[row.Username] = row.ID
	}
	return out, nil
}

func (r *ChatRepo) PinMessage(
	ctx context.Context,
	chatID string,
	actorID string,
	messageID string,
) error {
	tx, err := r.db.BeginTxx(ctx, &sql.TxOptions{Isolation: sql.LevelReadCommitted})
	if err != nil {
		return fmt.Errorf("begin pin message: %w", err)
	}
	defer func() { _ = tx.Rollback() }()

	if err := requireGroupAdmin(ctx, tx, chatID, actorID); err != nil {
		return err
	}
	var exists bool
	err = tx.GetContext(ctx, &exists, `
		SELECT TRUE
		FROM messages
		WHERE chat_id = $1 AND id = $2 AND NOT is_deleted
	`, chatID, messageID)
	if errors.Is(err, sql.ErrNoRows) {
		return ErrMessageNotFound
	}
	if err != nil {
		return fmt.Errorf("validate pin message: %w", err)
	}
	if _, err := tx.ExecContext(ctx, `
		INSERT INTO chat_pins (chat_id, message_id, pinned_by)
		VALUES ($1, $2, $3)
		ON CONFLICT (chat_id, message_id) DO UPDATE
		SET pinned_by = EXCLUDED.pinned_by, pinned_at = NOW()
	`, chatID, messageID, actorID); err != nil {
		return mapWriteError("pin message", err)
	}
	if err := tx.Commit(); err != nil {
		return mapWriteError("commit pin message", err)
	}
	return nil
}

func (r *ChatRepo) UnpinMessage(
	ctx context.Context,
	chatID string,
	actorID string,
	messageID string,
) error {
	tx, err := r.db.BeginTxx(ctx, &sql.TxOptions{Isolation: sql.LevelReadCommitted})
	if err != nil {
		return fmt.Errorf("begin unpin message: %w", err)
	}
	defer func() { _ = tx.Rollback() }()

	if err := requireGroupAdmin(ctx, tx, chatID, actorID); err != nil {
		return err
	}
	result, err := tx.ExecContext(ctx, `
		DELETE FROM chat_pins WHERE chat_id = $1 AND message_id = $2
	`, chatID, messageID)
	if err != nil {
		return fmt.Errorf("unpin message: %w", err)
	}
	if affected, _ := result.RowsAffected(); affected == 0 {
		return ErrMessageNotFound
	}
	if err := tx.Commit(); err != nil {
		return mapWriteError("commit unpin message", err)
	}
	return nil
}

func (r *ChatRepo) ListPins(
	ctx context.Context,
	chatID string,
	userID string,
) ([]*model.ChatPin, error) {
	if _, err := r.GetMembership(ctx, chatID, userID); err != nil {
		return nil, err
	}
	var pins []*model.ChatPin
	if err := r.db.SelectContext(ctx, &pins, `
		SELECT p.chat_id, p.message_id, p.pinned_by, p.pinned_at,
		       CASE WHEN m.is_deleted THEN NULL ELSE m.content END AS content,
		       m.type
		FROM chat_pins p
		JOIN messages m ON m.chat_id = p.chat_id AND m.id = p.message_id
		WHERE p.chat_id = $1
		ORDER BY p.pinned_at DESC, p.message_id DESC
	`, chatID); err != nil {
		return nil, fmt.Errorf("list chat pins: %w", err)
	}
	if pins == nil {
		pins = []*model.ChatPin{}
	}
	return pins, nil
}

func (r *ChatRepo) CreateInviteLink(
	ctx context.Context,
	chatID string,
	actorID string,
) (*model.ChatInviteLink, error) {
	tx, err := r.db.BeginTxx(ctx, &sql.TxOptions{Isolation: sql.LevelReadCommitted})
	if err != nil {
		return nil, fmt.Errorf("begin create invite: %w", err)
	}
	defer func() { _ = tx.Rollback() }()

	if err := requireGroupAdmin(ctx, tx, chatID, actorID); err != nil {
		return nil, err
	}
	token, err := randomInviteToken()
	if err != nil {
		return nil, err
	}
	var link model.ChatInviteLink
	err = tx.GetContext(ctx, &link, `
		INSERT INTO chat_invite_links (chat_id, token, created_by)
		VALUES ($1, $2, $3)
		RETURNING id, chat_id, token, created_by, created_at, revoked_at, use_count
	`, chatID, token, actorID)
	if err != nil {
		return nil, mapWriteError("insert invite link", err)
	}
	if err := tx.Commit(); err != nil {
		return nil, mapWriteError("commit invite link", err)
	}
	return &link, nil
}

func (r *ChatRepo) RevokeInviteLink(
	ctx context.Context,
	chatID string,
	actorID string,
	token string,
) error {
	tx, err := r.db.BeginTxx(ctx, &sql.TxOptions{Isolation: sql.LevelReadCommitted})
	if err != nil {
		return fmt.Errorf("begin revoke invite: %w", err)
	}
	defer func() { _ = tx.Rollback() }()

	if err := requireGroupAdmin(ctx, tx, chatID, actorID); err != nil {
		return err
	}
	result, err := tx.ExecContext(ctx, `
		UPDATE chat_invite_links
		SET revoked_at = NOW()
		WHERE chat_id = $1 AND token = $2 AND revoked_at IS NULL
	`, chatID, token)
	if err != nil {
		return fmt.Errorf("revoke invite link: %w", err)
	}
	if affected, _ := result.RowsAffected(); affected == 0 {
		return ErrChatNotFound
	}
	if err := tx.Commit(); err != nil {
		return mapWriteError("commit revoke invite", err)
	}
	return nil
}

func (r *ChatRepo) JoinByInviteToken(
	ctx context.Context,
	userID string,
	token string,
) (string, error) {
	tx, err := r.db.BeginTxx(ctx, &sql.TxOptions{Isolation: sql.LevelSerializable})
	if err != nil {
		return "", fmt.Errorf("begin join invite: %w", err)
	}
	defer func() { _ = tx.Rollback() }()

	var link model.ChatInviteLink
	err = tx.GetContext(ctx, &link, `
		SELECT id, chat_id, token, created_by, created_at, revoked_at, use_count
		FROM chat_invite_links
		WHERE token = $1 AND revoked_at IS NULL
		FOR UPDATE
	`, token)
	if errors.Is(err, sql.ErrNoRows) {
		return "", ErrChatNotFound
	}
	if err != nil {
		return "", fmt.Errorf("load invite link: %w", err)
	}

	var chatType string
	err = tx.GetContext(ctx, &chatType, `
		SELECT type FROM chats WHERE id = $1 FOR KEY SHARE
	`, link.ChatID)
	if errors.Is(err, sql.ErrNoRows) {
		return "", ErrChatNotFound
	}
	if err != nil {
		return "", fmt.Errorf("load invite chat: %w", err)
	}
	if chatType != model.ChatTypeGroup {
		return "", ErrForbidden
	}

	if _, err := tx.ExecContext(ctx, `
		INSERT INTO chat_members (chat_id, user_id, role)
		VALUES ($1, $2, 'member')
		ON CONFLICT (chat_id, user_id) DO UPDATE
		SET left_at = NULL,
		    is_archived = FALSE,
		    updated_at = NOW()
		WHERE chat_members.left_at IS NOT NULL
	`, link.ChatID, userID); err != nil {
		return "", mapWriteError("join via invite", err)
	}
	if _, err := tx.ExecContext(ctx, `
		UPDATE chat_invite_links
		SET use_count = use_count + 1
		WHERE id = $1
	`, link.ID); err != nil {
		return "", fmt.Errorf("increment invite use: %w", err)
	}
	if err := tx.Commit(); err != nil {
		return "", mapWriteError("commit join invite", err)
	}
	return link.ChatID, nil
}

func randomInviteToken() (string, error) {
	buf := make([]byte, 24)
	if _, err := rand.Read(buf); err != nil {
		return "", fmt.Errorf("generate invite token: %w", err)
	}
	return hex.EncodeToString(buf), nil
}
