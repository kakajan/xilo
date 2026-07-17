package repository

import (
	"context"
	"database/sql"
	"errors"
	"fmt"
	"net/http"

	"github.com/google/uuid"
	"github.com/jmoiron/sqlx"
	"github.com/lib/pq"
	"github.com/xilo-platform/xilo/internal/chat/model"
	pkgidempotency "github.com/xilo-platform/xilo/pkg/idempotency"
	"github.com/xilo-platform/xilo/pkg/pagination"
	"github.com/xilo-platform/xilo/pkg/storage"
)

var (
	ErrChatNotFound    = errors.New("chat not found")
	ErrMessageNotFound = errors.New("message not found")
	ErrForbidden       = errors.New("forbidden")
	ErrConflict        = errors.New("conflict")
	ErrInvalidCursor   = errors.New("invalid cursor")
	ErrMediaNotFound   = errors.New("media not found")
	ErrMediaURL        = errors.New("media URL mismatch")
	ErrMediaType       = errors.New("media type mismatch")
	ErrMediaTooLarge   = errors.New("media too large")
	ErrRetryable       = errors.New("retryable repository error")
)

type ChatRepo struct {
	db             *sqlx.DB
	mediaValidator *MediaValidator
}

func NewChatRepo(db *sqlx.DB) *ChatRepo {
	return &ChatRepo{db: db}
}

func NewChatRepoWithStorage(db *sqlx.DB, storageDriver storage.Driver) *ChatRepo {
	return &ChatRepo{
		db:             db,
		mediaValidator: NewMediaValidator(db, storageDriver),
	}
}

func (r *ChatRepo) CreateChat(
	ctx context.Context,
	actorID string,
	idempotencyRequest pkgidempotency.Request,
	req *model.CreateChatRequest,
	memberIDs []string,
	directLow string,
	directHigh string,
) (*pkgidempotency.MutationResult[model.Chat], error) {
	tx, err := r.db.BeginTxx(ctx, &sql.TxOptions{Isolation: sql.LevelReadCommitted})
	if err != nil {
		return nil, fmt.Errorf("begin create chat: %w", err)
	}
	defer func() { _ = tx.Rollback() }()

	acquisition, err := pkgidempotency.Acquire(ctx, tx, idempotencyRequest)
	if err != nil {
		return nil, fmt.Errorf("acquire create chat idempotency: %w", err)
	}
	if acquisition.Outcome == pkgidempotency.OutcomeReplay {
		if acquisition.ResourceType != "chat" {
			return nil, fmt.Errorf("invalid replay resource type")
		}
		if err := tx.Commit(); err != nil {
			return nil, mapWriteError("commit replayed chat", err)
		}
		return &pkgidempotency.MutationResult[model.Chat]{
			Outcome:        acquisition.Outcome,
			ResponseStatus: acquisition.ResponseStatus,
			ReplayJSON:     append([]byte(nil), acquisition.ResultJSON...),
		}, nil
	}

	var activeUserCount int
	if err := tx.GetContext(ctx, &activeUserCount, `
		SELECT COUNT(*)
		FROM users
		WHERE id = ANY($1) AND deleted_at IS NULL
	`, pq.Array(memberIDs)); err != nil {
		return nil, fmt.Errorf("validate chat members: %w", err)
	}
	if activeUserCount != len(memberIDs) {
		return nil, ErrChatNotFound
	}

	var chatID string
	if req.Type == model.ChatTypeDirect {
		err = tx.GetContext(ctx, &chatID, `
			INSERT INTO chats (type, name, avatar_url, direct_user_low, direct_user_high)
			VALUES ($1, NULL, NULL, $2, $3)
			ON CONFLICT (direct_user_low, direct_user_high) WHERE type = 'direct'
			DO UPDATE SET updated_at = chats.updated_at
			RETURNING id
		`, req.Type, directLow, directHigh)
	} else {
		err = tx.GetContext(ctx, &chatID, `
			INSERT INTO chats (type, name, avatar_url, direct_user_low, direct_user_high)
			VALUES ($1, $2, $3, NULL, NULL)
			RETURNING id
		`, req.Type, req.Name, req.AvatarURL)
	}
	if err != nil {
		return nil, mapWriteError("insert chat", err)
	}

	for _, userID := range memberIDs {
		role := model.MemberRoleMember
		if req.Type == model.ChatTypeGroup && userID == actorID {
			role = model.MemberRoleAdmin
		}
		if _, err := tx.ExecContext(ctx, `
			INSERT INTO chat_members (chat_id, user_id, role)
			VALUES ($1, $2, $3)
			ON CONFLICT (chat_id, user_id) DO NOTHING
		`, chatID, userID, role); err != nil {
			return nil, mapWriteError("insert chat member", err)
		}
	}

	if req.Type == model.ChatTypeDirect {
		if _, err := tx.ExecContext(ctx, `
			UPDATE chat_members
			SET is_archived = FALSE, updated_at = NOW()
			WHERE chat_id = $1 AND user_id = $2 AND left_at IS NULL
		`, chatID, actorID); err != nil {
			return nil, fmt.Errorf("unarchive direct chat: %w", err)
		}
	}

	chat, err := getChatTx(ctx, tx, actorID, chatID)
	if err != nil {
		return nil, err
	}
	if err := pkgidempotency.Complete(
		ctx,
		tx,
		acquisition.ID,
		"chat",
		chat.ID,
		http.StatusCreated,
		chat,
	); err != nil {
		return nil, fmt.Errorf("complete create chat idempotency: %w", err)
	}
	if err := tx.Commit(); err != nil {
		return nil, mapWriteError("commit create chat", err)
	}
	return &pkgidempotency.MutationResult[model.Chat]{
		Value:          chat,
		Outcome:        acquisition.Outcome,
		ResponseStatus: http.StatusCreated,
	}, nil
}

func (r *ChatRepo) ListChats(
	ctx context.Context,
	userID string,
	params model.ListParams,
) ([]*model.Chat, string, error) {
	limit := pagination.ValidateLimit(params.Limit, 20, 50)
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
		SELECT c.id, c.type, c.name, c.avatar_url, c.created_at, c.updated_at,
		       c.last_message_at, cm.role AS current_role, cm.is_muted, cm.is_archived,
		       (
		           SELECT COUNT(*)
		           FROM messages unread
		           WHERE unread.chat_id = c.id
		             AND unread.sender_id <> $1
		             AND NOT unread.is_deleted
		             AND unread.created_at > COALESCE(cm.last_read_at, cm.joined_at)
		       ) AS unread_count
		FROM chats c
		JOIN chat_members cm
		  ON cm.chat_id = c.id
		 AND cm.user_id = $1
		 AND cm.left_at IS NULL
	`
	args := []any{userID}
	if cursor != nil {
		query += `
			WHERE COALESCE(c.last_message_at, c.created_at) < $2
			   OR (
			       COALESCE(c.last_message_at, c.created_at) = $2
			       AND c.id < $3
			   )
		`
		args = append(args, cursor.Timestamp, cursor.ID)
	}
	query += `
		ORDER BY COALESCE(c.last_message_at, c.created_at) DESC, c.id DESC
		LIMIT $` + fmt.Sprint(len(args)+1)
	args = append(args, limit+1)

	var chats []*model.Chat
	if err := r.db.SelectContext(ctx, &chats, query, args...); err != nil {
		return nil, "", fmt.Errorf("list chats: %w", err)
	}

	var nextCursor string
	if len(chats) > limit {
		chats = chats[:limit]
		last := chats[len(chats)-1]
		sortTime := last.CreatedAt
		if last.LastMessageAt != nil {
			sortTime = *last.LastMessageAt
		}
		nextCursor = pagination.EncodeCursor(last.ID, sortTime)
	}
	if chats == nil {
		chats = []*model.Chat{}
	}
	if err := r.attachMembers(ctx, chats); err != nil {
		return nil, "", err
	}
	if err := r.attachLastMessages(ctx, chats); err != nil {
		return nil, "", err
	}
	return chats, nextCursor, nil
}

func (r *ChatRepo) GetChat(ctx context.Context, userID string, chatID string) (*model.Chat, error) {
	var chat model.Chat
	err := r.db.GetContext(ctx, &chat, `
		SELECT c.id, c.type, c.name, c.avatar_url, c.created_at, c.updated_at,
		       c.last_message_at, cm.role AS current_role, cm.is_muted, cm.is_archived,
		       (
		           SELECT COUNT(*)
		           FROM messages unread
		           WHERE unread.chat_id = c.id
		             AND unread.sender_id <> $1
		             AND NOT unread.is_deleted
		             AND unread.created_at > COALESCE(cm.last_read_at, cm.joined_at)
		       ) AS unread_count
		FROM chats c
		JOIN chat_members cm
		  ON cm.chat_id = c.id
		 AND cm.user_id = $1
		 AND cm.left_at IS NULL
		WHERE c.id = $2
	`, userID, chatID)
	if errors.Is(err, sql.ErrNoRows) {
		return nil, ErrChatNotFound
	}
	if err != nil {
		return nil, fmt.Errorf("get chat: %w", err)
	}
	chats := []*model.Chat{&chat}
	if err := r.attachMembers(ctx, chats); err != nil {
		return nil, err
	}
	if err := r.attachLastMessages(ctx, chats); err != nil {
		return nil, err
	}
	return &chat, nil
}

func (r *ChatRepo) GetMembership(
	ctx context.Context,
	chatID string,
	userID string,
) (*model.ChatMember, error) {
	var member model.ChatMember
	err := r.db.GetContext(ctx, &member, `
		SELECT cm.chat_id, cm.user_id, cm.role, cm.joined_at, cm.last_read_at,
		       cm.is_muted, cm.is_archived, cm.left_at,
		       u.username, COALESCE(u.display_name, '') AS display_name, u.avatar_url
		FROM chat_members cm
		JOIN users u ON u.id = cm.user_id AND u.deleted_at IS NULL
		WHERE cm.chat_id = $1 AND cm.user_id = $2 AND cm.left_at IS NULL
	`, chatID, userID)
	if errors.Is(err, sql.ErrNoRows) {
		return nil, ErrForbidden
	}
	if err != nil {
		return nil, fmt.Errorf("get chat membership: %w", err)
	}
	return &member, nil
}

func (r *ChatRepo) UpdateChat(
	ctx context.Context,
	chatID string,
	userID string,
	name *string,
	avatarURL *string,
	isMuted *bool,
	isArchived *bool,
) error {
	tx, err := r.db.BeginTxx(ctx, &sql.TxOptions{Isolation: sql.LevelReadCommitted})
	if err != nil {
		return fmt.Errorf("begin update chat: %w", err)
	}
	defer func() { _ = tx.Rollback() }()

	var membership struct {
		ChatType string `db:"chat_type"`
		Role     string `db:"role"`
	}
	err = tx.GetContext(ctx, &membership, `
		SELECT c.type AS chat_type, cm.role
		FROM chats c
		JOIN chat_members cm
		  ON cm.chat_id = c.id
		 AND cm.user_id = $2
		 AND cm.left_at IS NULL
		WHERE c.id = $1
		FOR UPDATE OF c, cm
	`, chatID, userID)
	if errors.Is(err, sql.ErrNoRows) {
		return ErrForbidden
	}
	if err != nil {
		return fmt.Errorf("authorize chat update: %w", err)
	}

	metadataRequested := name != nil || avatarURL != nil
	if metadataRequested {
		if membership.ChatType != model.ChatTypeGroup || membership.Role != model.MemberRoleAdmin {
			return ErrForbidden
		}
		if _, err := tx.ExecContext(ctx, `
			UPDATE chats
			SET name = COALESCE($2, name),
			    avatar_url = CASE WHEN $3::boolean THEN $4 ELSE avatar_url END,
			    updated_at = NOW()
			WHERE id = $1
		`, chatID, name, avatarURL != nil, avatarURL); err != nil {
			return mapWriteError("update chat metadata", err)
		}
	}
	if _, err := tx.ExecContext(ctx, `
		UPDATE chat_members
		SET is_muted = CASE WHEN $3::boolean THEN $4 ELSE is_muted END,
		    is_archived = CASE WHEN $5::boolean THEN $6 ELSE is_archived END,
		    updated_at = NOW()
		WHERE chat_id = $1 AND user_id = $2 AND left_at IS NULL
	`, chatID, userID, isMuted != nil, isMuted, isArchived != nil, isArchived); err != nil {
		return fmt.Errorf("update chat member state: %w", err)
	}
	if err := tx.Commit(); err != nil {
		return mapWriteError("commit chat update", err)
	}
	return nil
}

func (r *ChatRepo) AddMembers(
	ctx context.Context,
	chatID string,
	actorID string,
	userIDs []string,
) error {
	tx, err := r.db.BeginTxx(ctx, &sql.TxOptions{Isolation: sql.LevelSerializable})
	if err != nil {
		return fmt.Errorf("begin add members: %w", err)
	}
	defer func() { _ = tx.Rollback() }()

	if err := requireGroupAdmin(ctx, tx, chatID, actorID); err != nil {
		return err
	}
	var activeUserCount int
	if err := tx.GetContext(ctx, &activeUserCount, `
		SELECT COUNT(*)
		FROM users
		WHERE id = ANY($1) AND deleted_at IS NULL
	`, pq.Array(userIDs)); err != nil {
		return fmt.Errorf("validate new chat members: %w", err)
	}
	if activeUserCount != len(userIDs) {
		return ErrChatNotFound
	}
	for _, userID := range userIDs {
		_, err := tx.ExecContext(ctx, `
			INSERT INTO chat_members (chat_id, user_id, role)
			VALUES ($1, $2, 'member')
			ON CONFLICT (chat_id, user_id) DO UPDATE
			SET role = 'member',
			    joined_at = NOW(),
			    last_read_at = NULL,
			    is_muted = FALSE,
			    is_archived = FALSE,
			    left_at = NULL,
			    updated_at = NOW()
			WHERE chat_members.left_at IS NOT NULL
		`, chatID, userID)
		if err != nil {
			return mapWriteError("add chat member", err)
		}
	}
	if err := tx.Commit(); err != nil {
		return mapWriteError("commit add members", err)
	}
	return nil
}

func (r *ChatRepo) RemoveMember(
	ctx context.Context,
	chatID string,
	actorID string,
	targetID string,
) error {
	tx, err := r.db.BeginTxx(ctx, &sql.TxOptions{Isolation: sql.LevelSerializable})
	if err != nil {
		return fmt.Errorf("begin remove member: %w", err)
	}
	defer func() { _ = tx.Rollback() }()

	var actorRole string
	err = tx.GetContext(ctx, &actorRole, `
		SELECT cm.role
		FROM chats c
		JOIN chat_members cm ON cm.chat_id = c.id
		WHERE c.id = $1
		  AND c.type = 'group'
		  AND cm.user_id = $2
		  AND cm.left_at IS NULL
		FOR UPDATE OF c, cm
	`, chatID, actorID)
	if errors.Is(err, sql.ErrNoRows) {
		return ErrForbidden
	}
	if err != nil {
		return fmt.Errorf("lock actor membership: %w", err)
	}
	if actorID != targetID && actorRole != model.MemberRoleAdmin {
		return ErrForbidden
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

	if targetRole == model.MemberRoleAdmin {
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
			var promotedID string
			err := tx.GetContext(ctx, &promotedID, `
				SELECT user_id
				FROM chat_members
				WHERE chat_id = $1 AND user_id <> $2 AND left_at IS NULL
				ORDER BY joined_at, user_id
				LIMIT 1
				FOR UPDATE
			`, chatID, targetID)
			if err != nil && !errors.Is(err, sql.ErrNoRows) {
				return fmt.Errorf("select replacement admin: %w", err)
			}
			if promotedID != "" {
				if _, err := tx.ExecContext(ctx, `
					UPDATE chat_members
					SET role = 'admin', updated_at = NOW()
					WHERE chat_id = $1 AND user_id = $2
				`, chatID, promotedID); err != nil {
					return fmt.Errorf("promote replacement admin: %w", err)
				}
			}
		}
	}

	result, err := tx.ExecContext(ctx, `
		UPDATE chat_members
		SET left_at = NOW(), is_archived = TRUE, updated_at = NOW()
		WHERE chat_id = $1 AND user_id = $2 AND left_at IS NULL
	`, chatID, targetID)
	if err != nil {
		return fmt.Errorf("remove chat member: %w", err)
	}
	if affected, _ := result.RowsAffected(); affected == 0 {
		return ErrChatNotFound
	}
	if err := tx.Commit(); err != nil {
		return mapWriteError("commit remove member", err)
	}
	return nil
}

func (r *ChatRepo) LeaveChat(ctx context.Context, chatID string, userID string) error {
	var chatType string
	err := r.db.GetContext(ctx, &chatType, `
		SELECT c.type
		FROM chats c
		JOIN chat_members cm
		  ON cm.chat_id = c.id
		 AND cm.user_id = $2
		 AND cm.left_at IS NULL
		WHERE c.id = $1
	`, chatID, userID)
	if errors.Is(err, sql.ErrNoRows) {
		return ErrChatNotFound
	}
	if err != nil {
		return fmt.Errorf("get chat before leave: %w", err)
	}
	if chatType == model.ChatTypeDirect {
		return r.UpdateChat(ctx, chatID, userID, nil, nil, nil, boolPointer(true))
	}
	return r.RemoveMember(ctx, chatID, userID, userID)
}

func getChatTx(
	ctx context.Context,
	tx *sqlx.Tx,
	userID string,
	chatID string,
) (*model.Chat, error) {
	var chat model.Chat
	err := tx.GetContext(ctx, &chat, `
		SELECT c.id, c.type, c.name, c.avatar_url, c.created_at, c.updated_at,
		       c.last_message_at, cm.role AS current_role, cm.is_muted, cm.is_archived,
		       (
		           SELECT COUNT(*)
		           FROM messages unread
		           WHERE unread.chat_id = c.id
		             AND unread.sender_id <> $1
		             AND NOT unread.is_deleted
		             AND unread.created_at > COALESCE(cm.last_read_at, cm.joined_at)
		       ) AS unread_count
		FROM chats c
		JOIN chat_members cm
		  ON cm.chat_id = c.id
		 AND cm.user_id = $1
		 AND cm.left_at IS NULL
		WHERE c.id = $2
	`, userID, chatID)
	if errors.Is(err, sql.ErrNoRows) {
		return nil, ErrChatNotFound
	}
	if err != nil {
		return nil, fmt.Errorf("get created chat: %w", err)
	}

	chat.Members = []model.ChatMember{}
	if err := tx.SelectContext(ctx, &chat.Members, `
		SELECT cm.chat_id, cm.user_id, cm.role, cm.joined_at, cm.last_read_at,
		       cm.is_muted, cm.is_archived, cm.left_at,
		       u.username, COALESCE(u.display_name, '') AS display_name, u.avatar_url
		FROM chat_members cm
		JOIN users u ON u.id = cm.user_id AND u.deleted_at IS NULL
		WHERE cm.chat_id = $1 AND cm.left_at IS NULL
		ORDER BY cm.joined_at, cm.user_id
	`, chatID); err != nil {
		return nil, fmt.Errorf("load created chat members: %w", err)
	}

	var lastMessage model.Message
	err = tx.GetContext(ctx, &lastMessage, `
		SELECT m.id, m.chat_id, m.sender_id, m.type,
		       CASE WHEN m.is_deleted THEN NULL ELSE m.content END AS content,
		       CASE WHEN m.is_deleted THEN NULL ELSE m.media_id END AS media_id,
		       CASE WHEN m.is_deleted THEN NULL ELSE m.media_url END AS media_url,
		       m.reply_to_id, m.is_edited, m.is_deleted,
		       m.created_at, m.updated_at, m.edited_at, m.deleted_at
		FROM messages m
		WHERE m.chat_id = $1
		ORDER BY m.created_at DESC, m.id DESC
		LIMIT 1
	`, chatID)
	if err != nil && !errors.Is(err, sql.ErrNoRows) {
		return nil, fmt.Errorf("load created chat last message: %w", err)
	}
	if err == nil {
		lastMessage.Reactions = []model.Reaction{}
		lastMessage.ReadBy = []model.Read{}
		chat.LastMessage = &lastMessage
	}
	return &chat, nil
}

func (r *ChatRepo) attachMembers(ctx context.Context, chats []*model.Chat) error {
	if len(chats) == 0 {
		return nil
	}
	ids := make([]string, 0, len(chats))
	byID := make(map[string]*model.Chat, len(chats))
	for _, chat := range chats {
		chat.Members = []model.ChatMember{}
		ids = append(ids, chat.ID)
		byID[chat.ID] = chat
	}

	var members []model.ChatMember
	err := r.db.SelectContext(ctx, &members, `
		SELECT cm.chat_id, cm.user_id, cm.role, cm.joined_at, cm.last_read_at,
		       cm.is_muted, cm.is_archived, cm.left_at,
		       u.username, COALESCE(u.display_name, '') AS display_name, u.avatar_url
		FROM chat_members cm
		JOIN users u ON u.id = cm.user_id AND u.deleted_at IS NULL
		WHERE cm.chat_id = ANY($1) AND cm.left_at IS NULL
		ORDER BY cm.joined_at, cm.user_id
	`, pq.Array(ids))
	if err != nil {
		return fmt.Errorf("load chat members: %w", err)
	}
	for _, member := range members {
		if chat := byID[member.ChatID]; chat != nil {
			chat.Members = append(chat.Members, member)
		}
	}
	return nil
}

func (r *ChatRepo) attachLastMessages(ctx context.Context, chats []*model.Chat) error {
	if len(chats) == 0 {
		return nil
	}
	ids := make([]string, 0, len(chats))
	byID := make(map[string]*model.Chat, len(chats))
	for _, chat := range chats {
		ids = append(ids, chat.ID)
		byID[chat.ID] = chat
	}

	var messages []*model.Message
	err := r.db.SelectContext(ctx, &messages, `
		SELECT DISTINCT ON (m.chat_id)
		       m.id, m.chat_id, m.sender_id, m.type,
		       CASE WHEN m.is_deleted THEN NULL ELSE m.content END AS content,
		       CASE WHEN m.is_deleted THEN NULL ELSE m.media_id END AS media_id,
		       CASE WHEN m.is_deleted THEN NULL ELSE m.media_url END AS media_url,
		       m.reply_to_id, m.is_edited, m.is_deleted,
		       m.created_at, m.updated_at, m.edited_at, m.deleted_at
		FROM messages m
		WHERE m.chat_id = ANY($1)
		ORDER BY m.chat_id, m.created_at DESC, m.id DESC
	`, pq.Array(ids))
	if err != nil {
		return fmt.Errorf("load last chat messages: %w", err)
	}
	for _, message := range messages {
		message.Reactions = []model.Reaction{}
		message.ReadBy = []model.Read{}
		if chat := byID[message.ChatID]; chat != nil {
			chat.LastMessage = message
		}
	}
	return nil
}

func requireGroupAdmin(ctx context.Context, tx *sqlx.Tx, chatID string, userID string) error {
	var allowed bool
	err := tx.GetContext(ctx, &allowed, `
		SELECT TRUE
		FROM chats c
		JOIN chat_members cm ON cm.chat_id = c.id
		WHERE c.id = $1
		  AND c.type = 'group'
		  AND cm.user_id = $2
		  AND cm.role = 'admin'
		  AND cm.left_at IS NULL
		FOR UPDATE OF c, cm
	`, chatID, userID)
	if errors.Is(err, sql.ErrNoRows) {
		return ErrForbidden
	}
	if err != nil {
		return fmt.Errorf("authorize group admin: %w", err)
	}
	return nil
}

func mapWriteError(operation string, err error) error {
	var pqErr *pq.Error
	if errors.As(err, &pqErr) {
		switch pqErr.Code {
		case "40001", "40P01":
			return fmt.Errorf("%s: %w", operation, ErrRetryable)
		case "23503":
			return fmt.Errorf("%s: %w", operation, ErrChatNotFound)
		case "23505", "23514":
			return fmt.Errorf("%s: %w", operation, ErrConflict)
		}
	}
	return fmt.Errorf("%s: %w", operation, err)
}

func IsRetryable(err error) bool {
	if errors.Is(err, ErrRetryable) {
		return true
	}
	var pqErr *pq.Error
	if !errors.As(err, &pqErr) {
		return false
	}
	return pqErr.Code == "40001" || pqErr.Code == "40P01"
}

func boolPointer(value bool) *bool {
	return &value
}

func messageCursor(message *model.Message) string {
	return pagination.EncodeCursor(message.ID, message.CreatedAt)
}

func validCursor(cursor *pagination.Cursor) bool {
	return cursor != nil && !cursor.Timestamp.IsZero() && uuid.Validate(cursor.ID) == nil
}

func (r *ChatRepo) EnsureSavedMessagesChat(ctx context.Context, userID string) (*model.Chat, error) {
	var chatID string
	err := r.db.GetContext(ctx, &chatID, `
		SELECT c.id
		FROM chats c
		JOIN chat_members cm
		  ON cm.chat_id = c.id
		 AND cm.user_id = $1
		 AND cm.is_saved_chat = TRUE
		 AND cm.left_at IS NULL
		WHERE c.type = $2
		LIMIT 1
	`, userID, model.ChatTypeSaved)
	if err == nil {
		return r.GetChat(ctx, userID, chatID)
	}
	if !errors.Is(err, sql.ErrNoRows) {
		return nil, fmt.Errorf("find saved messages chat: %w", err)
	}

	tx, err := r.db.BeginTxx(ctx, &sql.TxOptions{Isolation: sql.LevelReadCommitted})
	if err != nil {
		return nil, fmt.Errorf("begin saved messages chat: %w", err)
	}
	defer func() { _ = tx.Rollback() }()

	err = tx.GetContext(ctx, &chatID, `
		SELECT c.id
		FROM chats c
		JOIN chat_members cm
		  ON cm.chat_id = c.id
		 AND cm.user_id = $1
		 AND cm.is_saved_chat = TRUE
		 AND cm.left_at IS NULL
		WHERE c.type = $2
		LIMIT 1
	`, userID, model.ChatTypeSaved)
	if err == nil {
		chat, getErr := getChatTx(ctx, tx, userID, chatID)
		if getErr != nil {
			return nil, getErr
		}
		if commitErr := tx.Commit(); commitErr != nil {
			return nil, mapWriteError("commit saved messages lookup", commitErr)
		}
		return chat, nil
	}
	if !errors.Is(err, sql.ErrNoRows) {
		return nil, fmt.Errorf("re-check saved messages chat: %w", err)
	}

	savedName := model.SavedMessagesChatName
	err = tx.GetContext(ctx, &chatID, `
		INSERT INTO chats (type, name, avatar_url, direct_user_low, direct_user_high)
		VALUES ($1, $2, NULL, NULL, NULL)
		RETURNING id
	`, model.ChatTypeSaved, savedName)
	if err != nil {
		return nil, mapWriteError("insert saved messages chat", err)
	}

	if _, err := tx.ExecContext(ctx, `
		INSERT INTO chat_members (chat_id, user_id, role, is_saved_chat)
		VALUES ($1, $2, $3, TRUE)
	`, chatID, userID, model.MemberRoleAdmin); err != nil {
		return nil, mapWriteError("insert saved messages member", err)
	}

	chat, err := getChatTx(ctx, tx, userID, chatID)
	if err != nil {
		return nil, err
	}
	if err := tx.Commit(); err != nil {
		return nil, mapWriteError("commit saved messages chat", err)
	}
	return chat, nil
}
