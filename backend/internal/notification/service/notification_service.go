package service

import (
	"context"
	"encoding/json"
	"errors"
	"fmt"
	"log/slog"
	"strings"
	"time"

	"github.com/google/uuid"
	"github.com/jmoiron/sqlx"
	"github.com/lib/pq"
	"github.com/redis/go-redis/v9"
	"github.com/xilo-platform/xilo/internal/notification/repository"
	"github.com/xilo-platform/xilo/pkg/realtime"
)

const (
	TypeCommentReply       = "comment_reply"
	TypePostComment        = "post_comment"
	TypeCommentMention     = "comment_mention"
	TypeCommentReposted    = "comment_reposted"
	TypeCommentQuoted      = "comment_quoted"
	TypeNewFollower        = "new_follower"
	TypePostPublished      = "post_published"
	TypeNewMessage         = "new_message"
	TypeChatMention        = "chat_mention"
	TypeSystemAnnouncement = "system_announcement"

	EventNotificationNew   = "notification.new"
	EventNotificationCount = "notification.count"

	redisUnreadKeyPrefix = "notif:unread:"
	broadcastBatchSize   = 100
)

var ErrDuplicateNotification = errors.New("duplicate notification")

type PushSender interface {
	Send(ctx context.Context, tokens []string, title, body string, data map[string]string) error
}

type NopPushSender struct{}

func (NopPushSender) Send(context.Context, []string, string, string, map[string]string) error {
	return nil
}

type NotifyRequest struct {
	RecipientID string
	ActorID     string
	Type        string
	Title       string
	Body        string
	Data        map[string]any
	SkipPush    bool
}

type NotificationService struct {
	repo    *repository.NotificationRepo
	db      *sqlx.DB
	rdb     redis.UniversalClient
	pub     realtime.Publisher
	push    PushSender
}

func NewNotificationService(
	repo *repository.NotificationRepo,
	db *sqlx.DB,
	rdb redis.UniversalClient,
	pub realtime.Publisher,
	push PushSender,
) *NotificationService {
	if push == nil {
		push = NopPushSender{}
	}
	if pub == nil {
		pub = realtime.NopPublisher{}
	}
	return &NotificationService{repo: repo, db: db, rdb: rdb, pub: pub, push: push}
}

func (s *NotificationService) Notify(ctx context.Context, req NotifyRequest) (*repository.Notification, error) {
	if req.RecipientID == "" || req.Type == "" || req.Title == "" {
		return nil, fmt.Errorf("recipient, type, and title are required")
	}
	if req.ActorID != "" && req.ActorID == req.RecipientID {
		return nil, nil
	}

	webEnabled, pushEnabled, err := s.prefsEnabled(ctx, req.RecipientID, req.Type)
	if err != nil {
		return nil, err
	}
	if !webEnabled && !pushEnabled {
		return nil, nil
	}

	dataJSON := "{}"
	if req.Data != nil {
		b, err := json.Marshal(req.Data)
		if err != nil {
			return nil, fmt.Errorf("marshal notification data: %w", err)
		}
		dataJSON = string(b)
	}

	var n *repository.Notification
	if webEnabled {
		n, err = s.repo.Create(ctx, req.RecipientID, req.Type, req.Title, req.Body, dataJSON)
		if err != nil {
			if isUniqueViolation(err) {
				return nil, ErrDuplicateNotification
			}
			return nil, err
		}
		unread, _ := s.incrUnread(ctx, req.RecipientID)
		s.emitNew(ctx, req.RecipientID, n, unread)
	}

	if pushEnabled && !req.SkipPush {
		go s.sendPush(context.Background(), req.RecipientID, req.Type, req.Title, req.Body, req.Data)
	}

	return n, nil
}

func (s *NotificationService) NotifyMany(ctx context.Context, actorID, notifType, title, body string, data map[string]any, recipientIDs []string) {
	for _, recipientID := range recipientIDs {
		if _, err := s.Notify(ctx, NotifyRequest{
			RecipientID: recipientID,
			ActorID:     actorID,
			Type:        notifType,
			Title:       title,
			Body:        body,
			Data:        data,
		}); err != nil && !errors.Is(err, ErrDuplicateNotification) {
			slog.Warn("notification fanout failed", "type", notifType, "recipient", recipientID, "error", err)
		}
	}
}

// BroadcastRequest is an admin custom announcement to all users.
type BroadcastRequest struct {
	Title    string
	Body     string
	Link     string
	SendPush bool
	SendInbox bool
}

// BroadcastResult summarizes an admin broadcast.
type BroadcastResult struct {
	UsersTotal     int `json:"users_total"`
	InboxCreated   int `json:"inbox_created"`
	InboxFailed    int `json:"inbox_failed"`
	PushTokens     int `json:"push_tokens"`
	PushSendErrors int `json:"push_send_errors"`
}

// BroadcastToAllUsers creates system announcements for every active user and
// optionally fans out FCM to all registered device tokens. Admin broadcasts
// bypass per-type preference gates so the message always reaches the inbox/push.
func (s *NotificationService) BroadcastToAllUsers(ctx context.Context, req BroadcastRequest) (*BroadcastResult, error) {
	title := strings.TrimSpace(req.Title)
	body := strings.TrimSpace(req.Body)
	if title == "" {
		return nil, fmt.Errorf("title is required")
	}
	if !req.SendInbox && !req.SendPush {
		req.SendInbox = true
		req.SendPush = true
	}

	data := map[string]any{"source": "admin_broadcast"}
	if link := strings.TrimSpace(req.Link); link != "" {
		data["link"] = link
	}
	dataJSONBytes, err := json.Marshal(data)
	if err != nil {
		return nil, err
	}
	dataJSON := string(dataJSONBytes)

	var userIDs []string
	if err := s.db.SelectContext(ctx, &userIDs, `
		SELECT id FROM users WHERE deleted_at IS NULL ORDER BY created_at
	`); err != nil {
		return nil, fmt.Errorf("list users: %w", err)
	}

	result := &BroadcastResult{UsersTotal: len(userIDs)}
	pushPayload := map[string]string{"type": TypeSystemAnnouncement, "source": "admin_broadcast"}
	if link, ok := data["link"].(string); ok && link != "" {
		pushPayload["link"] = link
	}

	for i := 0; i < len(userIDs); i += broadcastBatchSize {
		end := i + broadcastBatchSize
		if end > len(userIDs) {
			end = len(userIDs)
		}
		batch := userIDs[i:end]

		if req.SendInbox {
			for _, userID := range batch {
				n, err := s.repo.Create(ctx, userID, TypeSystemAnnouncement, title, body, dataJSON)
				if err != nil {
					result.InboxFailed++
					slog.Warn("broadcast inbox create failed", "user", userID, "error", err)
					continue
				}
				result.InboxCreated++
				unread, _ := s.incrUnread(ctx, userID)
				s.emitNew(ctx, userID, n, unread)
			}
		}

		if req.SendPush {
			var tokens []string
			if err := s.db.SelectContext(ctx, &tokens, `
				SELECT token FROM push_tokens WHERE user_id = ANY($1)
			`, pq.Array(batch)); err != nil {
				slog.Warn("broadcast list tokens failed", "error", err)
				continue
			}
			if len(tokens) == 0 {
				continue
			}
			result.PushTokens += len(tokens)
			// Send in smaller FCM chunks to avoid oversized requests.
			const tokenChunk = 50
			for t := 0; t < len(tokens); t += tokenChunk {
				tend := t + tokenChunk
				if tend > len(tokens) {
					tend = len(tokens)
				}
				if err := s.push.Send(ctx, tokens[t:tend], title, body, pushPayload); err != nil {
					result.PushSendErrors++
					slog.Warn("broadcast push chunk failed", "error", err)
				}
			}
		}
	}

	return result, nil
}

func (s *NotificationService) UnreadCount(ctx context.Context, userID string) (int, error) {
	if s.rdb != nil {
		val, err := s.rdb.Get(ctx, redisUnreadKeyPrefix+userID).Int()
		if err == nil {
			return val, nil
		}
		if err != redis.Nil {
			slog.Warn("redis unread get failed, falling back to SQL", "error", err)
		}
	}
	count, err := s.repo.UnreadCount(ctx, userID)
	if err != nil {
		return 0, err
	}
	if s.rdb != nil {
		_ = s.rdb.Set(ctx, redisUnreadKeyPrefix+userID, count, 24*time.Hour).Err()
	}
	return count, nil
}

func (s *NotificationService) MarkRead(ctx context.Context, id, userID string) error {
	wasUnread, err := s.repo.MarkReadReturningWasUnread(ctx, id, userID)
	if err != nil {
		return err
	}
	if wasUnread {
		unread, _ := s.decrUnread(ctx, userID)
		s.emitCount(ctx, userID, unread)
	}
	return nil
}

func (s *NotificationService) MarkAllRead(ctx context.Context, userID string) error {
	if err := s.repo.MarkAllRead(ctx, userID); err != nil {
		return err
	}
	if s.rdb != nil {
		_ = s.rdb.Set(ctx, redisUnreadKeyPrefix+userID, 0, 24*time.Hour).Err()
	}
	s.emitCount(ctx, userID, 0)
	return nil
}

func (s *NotificationService) List(ctx context.Context, userID string, limit int) ([]*repository.Notification, error) {
	return s.repo.List(ctx, userID, limit)
}

func (s *NotificationService) ListFollowerIDs(ctx context.Context, authorID string) ([]string, error) {
	var ids []string
	err := s.db.SelectContext(ctx, &ids, `
		SELECT follower_id FROM follows WHERE following_id = $1
	`, authorID)
	return ids, err
}

func (s *NotificationService) UpsertPushToken(ctx context.Context, userID, token, platform string) error {
	token = strings.TrimSpace(token)
	if token == "" {
		return fmt.Errorf("token is required")
	}
	if platform == "" {
		platform = "android"
	}
	_, err := s.db.ExecContext(ctx, `
		INSERT INTO push_tokens (user_id, token, platform, updated_at)
		VALUES ($1, $2, $3, NOW())
		ON CONFLICT (token) DO UPDATE
		SET user_id = EXCLUDED.user_id,
		    platform = EXCLUDED.platform,
		    updated_at = NOW()
	`, userID, token, platform)
	return err
}

func (s *NotificationService) DeletePushToken(ctx context.Context, userID, token string) error {
	_, err := s.db.ExecContext(ctx, `
		DELETE FROM push_tokens WHERE user_id = $1 AND token = $2
	`, userID, token)
	return err
}

func (s *NotificationService) DeleteAllPushTokens(ctx context.Context, userID string) error {
	_, err := s.db.ExecContext(ctx, `DELETE FROM push_tokens WHERE user_id = $1`, userID)
	return err
}

func (s *NotificationService) prefsEnabled(ctx context.Context, userID, notifType string) (web, push bool, err error) {
	webCol, pushCol := prefColumns(notifType)
	if webCol == "" {
		return true, false, nil
	}

	query := fmt.Sprintf(`
		SELECT COALESCE(%s, TRUE) AS web_enabled, COALESCE(%s, TRUE) AS push_enabled
		FROM notification_preferences WHERE user_id = $1
	`, webCol, pushCol)

	var row struct {
		Web  bool `db:"web_enabled"`
		Push bool `db:"push_enabled"`
	}
	err = s.db.GetContext(ctx, &row, query, userID)
	if err != nil {
		// No prefs row → defaults (enabled)
		_, _ = s.db.ExecContext(ctx, `
			INSERT INTO notification_preferences (user_id) VALUES ($1) ON CONFLICT DO NOTHING
		`, userID)
		return true, true, nil
	}
	return row.Web, row.Push, nil
}

func prefColumns(notifType string) (webCol, pushCol string) {
	switch notifType {
	case TypeCommentReply, TypePostComment, TypeCommentMention, TypeCommentReposted, TypeCommentQuoted:
		// Comment-thread events share the comment_reply preference toggles.
		return "comment_reply_web", "comment_reply_push"
	case TypeNewFollower:
		return "new_follower_web", "new_follower_push"
	case TypePostPublished:
		return "post_published_web", "post_published_push"
	case TypeNewMessage, TypeChatMention:
		return "new_message_web", "new_message_push"
	default:
		return "", ""
	}
}

func (s *NotificationService) incrUnread(ctx context.Context, userID string) (int, error) {
	if s.rdb == nil {
		return s.repo.UnreadCount(ctx, userID)
	}
	n, err := s.rdb.Incr(ctx, redisUnreadKeyPrefix+userID).Result()
	if err != nil {
		return s.repo.UnreadCount(ctx, userID)
	}
	_ = s.rdb.Expire(ctx, redisUnreadKeyPrefix+userID, 24*time.Hour).Err()
	return int(n), nil
}

func (s *NotificationService) decrUnread(ctx context.Context, userID string) (int, error) {
	if s.rdb == nil {
		return s.repo.UnreadCount(ctx, userID)
	}
	n, err := s.rdb.Decr(ctx, redisUnreadKeyPrefix+userID).Result()
	if err != nil {
		return s.repo.UnreadCount(ctx, userID)
	}
	if n < 0 {
		_ = s.rdb.Set(ctx, redisUnreadKeyPrefix+userID, 0, 24*time.Hour).Err()
		return 0, nil
	}
	return int(n), nil
}

func (s *NotificationService) emitNew(ctx context.Context, userID string, n *repository.Notification, unread int) {
	if n == nil {
		return
	}
	data := json.RawMessage(n.Data)
	if len(data) == 0 {
		data = json.RawMessage(`{}`)
	}
	payload := map[string]any{
		"id":         n.ID,
		"type":       n.Type,
		"title":      n.Title,
		"body":       n.Body,
		"data":       data,
		"is_read":    n.IsRead,
		"created_at": n.CreatedAt.UTC().Format(time.RFC3339Nano),
	}
	env, err := realtime.NewEnvelope(EventNotificationNew, payload)
	if err != nil {
		slog.Error("notification envelope", "error", err)
		return
	}
	if err := s.pub.Publish(ctx, realtime.Delivery{Channel: "user:" + userID, Envelope: env}); err != nil {
		slog.Warn("publish notification.new", "error", err)
	}
	s.emitCount(ctx, userID, unread)
}

func (s *NotificationService) emitCount(ctx context.Context, userID string, unread int) {
	env, err := realtime.NewEnvelope(EventNotificationCount, map[string]any{"unread": unread})
	if err != nil {
		return
	}
	if err := s.pub.Publish(ctx, realtime.Delivery{Channel: "user:" + userID, Envelope: env}); err != nil {
		slog.Warn("publish notification.count", "error", err)
	}
}

func (s *NotificationService) sendPush(ctx context.Context, userID, notifType, title, body string, data map[string]any) {
	var tokens []string
	if err := s.db.SelectContext(ctx, &tokens, `
		SELECT token FROM push_tokens WHERE user_id = $1
	`, userID); err != nil || len(tokens) == 0 {
		return
	}
	payload := map[string]string{"type": notifType}
	for k, v := range data {
		payload[k] = fmt.Sprint(v)
	}
	if err := s.push.Send(ctx, tokens, title, body, payload); err != nil {
		slog.Warn("push send failed", "user", userID, "error", err)
	}
}

func isUniqueViolation(err error) bool {
	var pqErr *pq.Error
	if errors.As(err, &pqErr) {
		return pqErr.Code == "23505"
	}
	return strings.Contains(err.Error(), "duplicate key") || strings.Contains(err.Error(), "unique constraint")
}

// EnsureUUID validates a UUID string (helpers for producers).
func EnsureUUID(id string) bool {
	_, err := uuid.Parse(id)
	return err == nil
}
