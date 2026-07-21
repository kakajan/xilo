package repository

import (
	"context"
	"database/sql"
	"errors"
	"fmt"
	"net/http"
	"strings"
	"time"

	"github.com/lib/pq"
	"github.com/xilo-platform/xilo/internal/chat/model"
	pkgidempotency "github.com/xilo-platform/xilo/pkg/idempotency"
	"github.com/xilo-platform/xilo/pkg/pagination"
)

func (r *ChatRepo) ListMessages(
	ctx context.Context,
	chatID string,
	userID string,
	params model.ListParams,
) ([]*model.Message, string, error) {
	if _, err := r.GetMembership(ctx, chatID, userID); err != nil {
		return nil, "", err
	}
	return r.queryMessages(ctx, chatID, userID, "", params)
}

func (r *ChatRepo) SearchMessages(
	ctx context.Context,
	chatID string,
	userID string,
	query string,
	params model.ListParams,
) ([]*model.Message, string, error) {
	if _, err := r.GetMembership(ctx, chatID, userID); err != nil {
		return nil, "", err
	}
	return r.queryMessages(ctx, chatID, userID, query, params)
}

func (r *ChatRepo) queryMessages(
	ctx context.Context,
	chatID string,
	userID string,
	search string,
	params model.ListParams,
) ([]*model.Message, string, error) {
	limit := pagination.ValidateLimit(params.Limit, 50, 100)
	var cursor *pagination.Cursor
	var err error
	if params.Cursor != "" {
		cursor, err = pagination.DecodeCursor(params.Cursor)
		if err != nil {
			return nil, "", ErrInvalidCursor
		}
		if !validCursor(cursor) {
			return nil, "", ErrInvalidCursor
		}
	}

	query := `
		SELECT m.id, m.chat_id, m.sender_id, m.type,
		       COALESCE(NULLIF(TRIM(sender.display_name), ''), sender.username) AS sender_name,
		       NULLIF(TRIM(sender.avatar_url), '') AS sender_avatar,
		       CASE WHEN m.is_deleted THEN NULL ELSE m.content END AS content,
		       CASE WHEN m.is_deleted THEN NULL ELSE m.media_id END AS media_id,
		       CASE WHEN m.is_deleted THEN NULL ELSE m.media_url END AS media_url,
		       m.reply_to_id, m.is_edited, m.is_deleted,
		       m.created_at, m.updated_at, m.edited_at, m.deleted_at
		FROM messages m
		JOIN users sender ON sender.id = m.sender_id
		WHERE m.chat_id = $1
		  AND EXISTS (
		      SELECT 1
		      FROM chat_members viewer
		      WHERE viewer.chat_id = m.chat_id
		        AND viewer.user_id = $2
		        AND viewer.left_at IS NULL
		  )
	`
	args := []any{chatID, userID}
	if search != "" {
		query += `
		  AND NOT m.is_deleted
		  AND (
		      m.content ILIKE $3 ESCAPE '\'
		      OR sender.username ILIKE $3 ESCAPE '\'
		      OR COALESCE(sender.display_name, '') ILIKE $3 ESCAPE '\'
		  )
		`
		args = append(args, "%"+escapeLikePattern(search)+"%")
	}
	if cursor != nil {
		query += fmt.Sprintf(`
		  AND (
		      m.created_at < $%d
		      OR (m.created_at = $%d AND m.id < $%d)
		  )
		`, len(args)+1, len(args)+1, len(args)+2)
		args = append(args, cursor.Timestamp, cursor.ID)
	}
	query += fmt.Sprintf(`
		ORDER BY m.created_at DESC, m.id DESC
		LIMIT $%d
	`, len(args)+1)
	args = append(args, limit+1)

	var messages []*model.Message
	if err := r.db.SelectContext(ctx, &messages, query, args...); err != nil {
		return nil, "", fmt.Errorf("query chat messages: %w", err)
	}
	var nextCursor string
	if len(messages) > limit {
		messages = messages[:limit]
		nextCursor = messageCursor(messages[len(messages)-1])
	}
	if messages == nil {
		messages = []*model.Message{}
	}
	if err := r.attachMessageMetadata(ctx, messages, userID); err != nil {
		return nil, "", err
	}
	return messages, nextCursor, nil
}

func (r *ChatRepo) CreateMessage(
	ctx context.Context,
	chatID string,
	senderID string,
	idempotencyRequest pkgidempotency.Request,
	req *model.CreateMessageRequest,
) (*pkgidempotency.MutationResult[model.Message], error) {
	tx, err := r.db.BeginTxx(ctx, &sql.TxOptions{Isolation: sql.LevelReadCommitted})
	if err != nil {
		return nil, fmt.Errorf("begin create message: %w", err)
	}
	defer func() { _ = tx.Rollback() }()

	acquisition, err := pkgidempotency.Acquire(ctx, tx, idempotencyRequest)
	if err != nil {
		return nil, fmt.Errorf("acquire create message idempotency: %w", err)
	}
	if acquisition.Outcome == pkgidempotency.OutcomeReplay {
		if acquisition.ResourceType != "message" {
			return nil, fmt.Errorf("invalid replay resource type")
		}
		if err := tx.Commit(); err != nil {
			return nil, mapWriteError("commit replayed message", err)
		}
		return &pkgidempotency.MutationResult[model.Message]{
			Outcome:        acquisition.Outcome,
			ResponseStatus: acquisition.ResponseStatus,
			ReplayJSON:     append([]byte(nil), acquisition.ResultJSON...),
		}, nil
	}

	messageRequest := *req

	var lockedChatID string
	err = tx.GetContext(ctx, &lockedChatID, `
		SELECT id
		FROM chats
		WHERE id = $1
		FOR KEY SHARE
	`, chatID)
	if errors.Is(err, sql.ErrNoRows) {
		return nil, ErrChatNotFound
	}
	if err != nil {
		return nil, fmt.Errorf("lock message chat: %w", err)
	}

	var active bool
	err = tx.GetContext(ctx, &active, `
		SELECT TRUE
		FROM chat_members
		WHERE chat_id = $1 AND user_id = $2 AND left_at IS NULL
		FOR SHARE
	`, chatID, senderID)
	if errors.Is(err, sql.ErrNoRows) {
		return nil, ErrForbidden
	}
	if err != nil {
		return nil, fmt.Errorf("authorize message sender: %w", err)
	}

	if messageRequest.MediaURL != nil {
		if r.mediaValidator == nil {
			return nil, errors.New("message media validation is unavailable")
		}
		mediaID, err := r.mediaValidator.ValidateMediaTx(
			ctx,
			tx,
			senderID,
			*messageRequest.MediaURL,
			messageRequest.Type,
		)
		if err != nil {
			return nil, err
		}
		messageRequest.MediaID = &mediaID
	}

	if messageRequest.ReplyToID != nil {
		var replyExists bool
		err := tx.GetContext(ctx, &replyExists, `
			SELECT TRUE
			FROM messages
			WHERE chat_id = $1 AND id = $2
			FOR SHARE
		`, chatID, *messageRequest.ReplyToID)
		if errors.Is(err, sql.ErrNoRows) {
			return nil, ErrMessageNotFound
		}
		if err != nil {
			return nil, fmt.Errorf("validate reply message: %w", err)
		}
	}

	var message model.Message
	err = tx.GetContext(ctx, &message, `
		INSERT INTO messages (chat_id, sender_id, type, content, media_id, media_url, reply_to_id)
		VALUES ($1, $2, $3, $4, $5, $6, $7)
		RETURNING id, chat_id, sender_id, type, content, media_id, media_url, reply_to_id,
		          is_edited, is_deleted, created_at, updated_at, edited_at, deleted_at
	`, chatID, senderID, messageRequest.Type, messageRequest.Content, messageRequest.MediaID,
		messageRequest.MediaURL, messageRequest.ReplyToID)
	if err != nil {
		return nil, mapWriteError("insert message", err)
	}
	if err := fillMessageSenderProfile(ctx, tx, &message); err != nil {
		return nil, err
	}
	if _, err := tx.ExecContext(ctx, `
		UPDATE chats
		SET last_message_at = GREATEST(COALESCE(last_message_at, $2), $2),
		    updated_at = NOW()
		WHERE id = $1
	`, chatID, message.CreatedAt); err != nil {
		return nil, fmt.Errorf("update chat last message: %w", err)
	}
	message.Reactions = []model.Reaction{}
	message.ReadBy = []model.Read{}
	if err := pkgidempotency.Complete(
		ctx,
		tx,
		acquisition.ID,
		"message",
		message.ID,
		http.StatusCreated,
		&message,
	); err != nil {
		return nil, fmt.Errorf("complete create message idempotency: %w", err)
	}
	if err := tx.Commit(); err != nil {
		return nil, mapWriteError("commit message", err)
	}
	return &pkgidempotency.MutationResult[model.Message]{
		Value:          &message,
		Outcome:        acquisition.Outcome,
		ResponseStatus: http.StatusCreated,
	}, nil
}

func (r *ChatRepo) GetMessage(ctx context.Context, messageID string) (*model.Message, error) {
	var message model.Message
	err := r.db.GetContext(ctx, &message, `
		SELECT m.id, m.chat_id, m.sender_id, m.type,
		       COALESCE(NULLIF(TRIM(u.display_name), ''), u.username) AS sender_name,
		       NULLIF(TRIM(u.avatar_url), '') AS sender_avatar,
		       CASE WHEN m.is_deleted THEN NULL ELSE m.content END AS content,
		       CASE WHEN m.is_deleted THEN NULL ELSE m.media_id END AS media_id,
		       CASE WHEN m.is_deleted THEN NULL ELSE m.media_url END AS media_url,
		       m.reply_to_id, m.is_edited, m.is_deleted,
		       m.created_at, m.updated_at, m.edited_at, m.deleted_at
		FROM messages m
		JOIN users u ON u.id = m.sender_id
		WHERE m.id = $1
	`, messageID)
	if errors.Is(err, sql.ErrNoRows) {
		return nil, ErrMessageNotFound
	}
	if err != nil {
		return nil, fmt.Errorf("get message: %w", err)
	}
	message.Reactions = []model.Reaction{}
	message.ReadBy = []model.Read{}
	return &message, nil
}

func (r *ChatRepo) UpdateMessage(
	ctx context.Context,
	messageID string,
	senderID string,
	content string,
) (*model.Message, error) {
	var message model.Message
	err := r.db.GetContext(ctx, &message, `
		UPDATE messages
		SET content = $3,
		    is_edited = TRUE,
		    edited_at = NOW(),
		    updated_at = NOW()
		WHERE id = $1
		  AND sender_id = $2
		  AND NOT is_deleted
		  AND created_at >= NOW() - INTERVAL '48 hours'
		RETURNING id, chat_id, sender_id, type, content, media_id, media_url, reply_to_id,
		          is_edited, is_deleted, created_at, updated_at, edited_at, deleted_at
	`, messageID, senderID, content)
	if errors.Is(err, sql.ErrNoRows) {
		return nil, ErrConflict
	}
	if err != nil {
		return nil, mapWriteError("update message", err)
	}
	if err := fillMessageSenderProfile(ctx, r.db, &message); err != nil {
		return nil, err
	}
	message.Reactions = []model.Reaction{}
	message.ReadBy = []model.Read{}
	return &message, nil
}

func (r *ChatRepo) DeleteMessage(ctx context.Context, messageID string, senderID string) error {
	result, err := r.db.ExecContext(ctx, `
		UPDATE messages
		SET content = NULL,
		    media_id = NULL,
		    media_url = NULL,
		    is_deleted = TRUE,
		    deleted_at = NOW(),
		    updated_at = NOW()
		WHERE id = $1 AND sender_id = $2 AND NOT is_deleted
	`, messageID, senderID)
	if err != nil {
		return mapWriteError("delete message", err)
	}
	if affected, _ := result.RowsAffected(); affected == 0 {
		return ErrConflict
	}
	return nil
}

func (r *ChatRepo) MarkRead(
	ctx context.Context,
	messageID string,
	userID string,
) (*model.Read, error) {
	tx, err := r.db.BeginTxx(ctx, &sql.TxOptions{Isolation: sql.LevelReadCommitted})
	if err != nil {
		return nil, fmt.Errorf("begin mark message read: %w", err)
	}
	defer func() { _ = tx.Rollback() }()

	var target struct {
		ChatID    string    `db:"chat_id"`
		CreatedAt time.Time `db:"created_at"`
	}
	err = tx.GetContext(ctx, &target, `
		SELECT m.chat_id, m.created_at
		FROM messages m
		JOIN chat_members cm
		  ON cm.chat_id = m.chat_id
		 AND cm.user_id = $2
		 AND cm.left_at IS NULL
		WHERE m.id = $1
		FOR SHARE OF m, cm
	`, messageID, userID)
	if errors.Is(err, sql.ErrNoRows) {
		return nil, ErrForbidden
	}
	if err != nil {
		return nil, fmt.Errorf("authorize message read: %w", err)
	}

	read := &model.Read{UserID: userID}
	if err := tx.GetContext(ctx, &read.ReadAt, `
		INSERT INTO message_reads (message_id, chat_id, user_id, read_at)
		VALUES ($1, $2, $3, NOW())
		ON CONFLICT (message_id, user_id) DO UPDATE
		SET read_at = GREATEST(message_reads.read_at, EXCLUDED.read_at)
		RETURNING read_at
	`, messageID, target.ChatID, userID); err != nil {
		return nil, mapWriteError("upsert message read", err)
	}
	if _, err := tx.ExecContext(ctx, `
		UPDATE chat_members
		SET last_read_at = GREATEST(COALESCE(last_read_at, joined_at), $3),
		    updated_at = NOW()
		WHERE chat_id = $1 AND user_id = $2 AND left_at IS NULL
	`, target.ChatID, userID, target.CreatedAt); err != nil {
		return nil, fmt.Errorf("update member read position: %w", err)
	}
	if err := tx.Commit(); err != nil {
		return nil, mapWriteError("commit message read", err)
	}
	return read, nil
}

func (r *ChatRepo) ToggleReaction(
	ctx context.Context,
	messageID string,
	userID string,
	reaction string,
) (*model.ReactionResult, error) {
	tx, err := r.db.BeginTxx(ctx, &sql.TxOptions{Isolation: sql.LevelReadCommitted})
	if err != nil {
		return nil, fmt.Errorf("begin toggle reaction: %w", err)
	}
	defer func() { _ = tx.Rollback() }()

	var chatID string
	err = tx.GetContext(ctx, &chatID, `
		SELECT m.chat_id
		FROM messages m
		JOIN chat_members cm
		  ON cm.chat_id = m.chat_id
		 AND cm.user_id = $2
		 AND cm.left_at IS NULL
		WHERE m.id = $1 AND NOT m.is_deleted
		FOR SHARE OF m, cm
	`, messageID, userID)
	if errors.Is(err, sql.ErrNoRows) {
		return nil, ErrForbidden
	}
	if err != nil {
		return nil, fmt.Errorf("authorize message reaction: %w", err)
	}

	result := &model.ReactionResult{Reaction: reaction}
	deleteResult, err := tx.ExecContext(ctx, `
		DELETE FROM message_reactions
		WHERE message_id = $1 AND user_id = $2 AND reaction = $3
	`, messageID, userID, reaction)
	if err != nil {
		return nil, fmt.Errorf("delete existing message reaction: %w", err)
	}
	deleted, _ := deleteResult.RowsAffected()
	if deleted == 0 {
		if _, err := tx.ExecContext(ctx, `
			INSERT INTO message_reactions (message_id, chat_id, user_id, reaction)
			VALUES ($1, $2, $3, $4)
		`, messageID, chatID, userID, reaction); err != nil {
			return nil, mapWriteError("insert message reaction", err)
		}
		result.Active = true
	}
	if err := tx.GetContext(ctx, &result.Count, `
		SELECT COUNT(*)
		FROM message_reactions
		WHERE message_id = $1 AND reaction = $2
	`, messageID, reaction); err != nil {
		return nil, fmt.Errorf("count message reactions: %w", err)
	}
	if err := tx.Commit(); err != nil {
		return nil, mapWriteError("commit message reaction", err)
	}
	return result, nil
}

func (r *ChatRepo) attachMessageMetadata(
	ctx context.Context,
	messages []*model.Message,
	viewerID string,
) error {
	if len(messages) == 0 {
		return nil
	}
	ids := make([]string, 0, len(messages))
	byID := make(map[string]*model.Message, len(messages))
	for _, message := range messages {
		message.Reactions = []model.Reaction{}
		message.ReadBy = []model.Read{}
		ids = append(ids, message.ID)
		byID[message.ID] = message
	}

	var reactions []struct {
		MessageID string `db:"message_id"`
		model.Reaction
	}
	err := r.db.SelectContext(ctx, &reactions, `
		SELECT message_id, reaction, COUNT(*) AS count,
		       BOOL_OR(user_id = $2) AS reacted
		FROM message_reactions
		WHERE message_id = ANY($1)
		GROUP BY message_id, reaction
		ORDER BY message_id, reaction
	`, pq.Array(ids), viewerID)
	if err != nil {
		return fmt.Errorf("load message reactions: %w", err)
	}
	for _, reaction := range reactions {
		if message := byID[reaction.MessageID]; message != nil {
			message.Reactions = append(message.Reactions, reaction.Reaction)
		}
	}

	var reads []struct {
		MessageID string `db:"message_id"`
		model.Read
	}
	err = r.db.SelectContext(ctx, &reads, `
		SELECT message_id, user_id, read_at
		FROM message_reads
		WHERE message_id = ANY($1)
		ORDER BY message_id, read_at
	`, pq.Array(ids))
	if err != nil {
		return fmt.Errorf("load message reads: %w", err)
	}
	for _, read := range reads {
		if message := byID[read.MessageID]; message != nil {
			message.ReadBy = append(message.ReadBy, read.Read)
		}
	}
	return nil
}

type messageSenderQuerier interface {
	GetContext(ctx context.Context, dest any, query string, args ...any) error
}

func fillMessageSenderProfile(ctx context.Context, q messageSenderQuerier, msg *model.Message) error {
	if msg == nil || msg.SenderID == "" {
		return nil
	}
	var row struct {
		SenderName   string  `db:"sender_name"`
		SenderAvatar *string `db:"sender_avatar"`
	}
	if err := q.GetContext(ctx, &row, `
		SELECT COALESCE(NULLIF(TRIM(display_name), ''), username) AS sender_name,
		       NULLIF(TRIM(avatar_url), '') AS sender_avatar
		FROM users
		WHERE id = $1
	`, msg.SenderID); err != nil {
		return fmt.Errorf("load message sender profile: %w", err)
	}
	msg.SenderName = row.SenderName
	msg.SenderAvatar = row.SenderAvatar
	return nil
}

func escapeLikePattern(value string) string {
	return strings.NewReplacer(
		`\`, `\\`,
		`%`, `\%`,
		`_`, `\_`,
	).Replace(value)
}
