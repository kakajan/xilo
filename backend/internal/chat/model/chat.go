package model

import "time"

const (
	ChatTypeDirect = "direct"
	ChatTypeGroup  = "group"
	ChatTypeSaved  = "saved"

	SavedMessagesChatName = "Saved Messages"

	MemberRoleAdmin  = "admin"
	MemberRoleMember = "member"

	MessageTypeText   = "text"
	MessageTypeImage  = "image"
	MessageTypeVideo  = "video"
	MessageTypeFile   = "file"
	MessageTypeSystem = "system"
)

type Chat struct {
	ID             string       `json:"id" db:"id"`
	Type           string       `json:"type" db:"type"`
	Name           *string      `json:"name,omitempty" db:"name"`
	AvatarURL      *string      `json:"avatar_url,omitempty" db:"avatar_url"`
	CreatedAt      time.Time    `json:"created_at" db:"created_at"`
	UpdatedAt      time.Time    `json:"updated_at" db:"updated_at"`
	LastMessageAt *time.Time   `json:"last_message_at,omitempty" db:"last_message_at"`
	Members        []ChatMember `json:"members"`
	LastMessage    *Message     `json:"last_message,omitempty"`
	UnreadCount    int64        `json:"unread_count" db:"unread_count"`
	IsMuted        bool         `json:"is_muted" db:"is_muted"`
	IsArchived     bool         `json:"is_archived" db:"is_archived"`
	CurrentRole    string       `json:"current_role" db:"current_role"`
}

type ChatMember struct {
	ChatID      string     `json:"chat_id" db:"chat_id"`
	UserID      string     `json:"user_id" db:"user_id"`
	Role        string     `json:"role" db:"role"`
	Username    string     `json:"username" db:"username"`
	DisplayName string     `json:"display_name" db:"display_name"`
	AvatarURL   *string    `json:"avatar_url,omitempty" db:"avatar_url"`
	JoinedAt    time.Time  `json:"joined_at" db:"joined_at"`
	LastReadAt *time.Time `json:"last_read_at,omitempty" db:"last_read_at"`
	IsMuted     bool       `json:"is_muted" db:"is_muted"`
	IsArchived  bool       `json:"is_archived" db:"is_archived"`
	LeftAt      *time.Time `json:"-" db:"left_at"`
}

type Message struct {
	ID           string     `json:"id" db:"id"`
	ChatID       string     `json:"chat_id" db:"chat_id"`
	SenderID     string     `json:"sender_id" db:"sender_id"`
	SenderName   string     `json:"sender_name,omitempty" db:"sender_name"`
	SenderAvatar *string    `json:"sender_avatar,omitempty" db:"sender_avatar"`
	Type         string     `json:"type" db:"type"`
	Content      *string    `json:"content,omitempty" db:"content"`
	MediaID      *string    `json:"media_id,omitempty" db:"media_id"`
	MediaURL     *string    `json:"media_url,omitempty" db:"media_url"`
	ReplyToID    *string    `json:"reply_to_id,omitempty" db:"reply_to_id"`
	IsEdited     bool       `json:"is_edited" db:"is_edited"`
	IsDeleted    bool       `json:"is_deleted" db:"is_deleted"`
	CreatedAt    time.Time  `json:"created_at" db:"created_at"`
	UpdatedAt    time.Time  `json:"updated_at" db:"updated_at"`
	EditedAt     *time.Time `json:"edited_at,omitempty" db:"edited_at"`
	DeletedAt    *time.Time `json:"deleted_at,omitempty" db:"deleted_at"`
	Reactions    []Reaction `json:"reactions"`
	ReadBy       []Read     `json:"read_by"`
}

type Reaction struct {
	Reaction string `json:"reaction" db:"reaction"`
	Count    int64  `json:"count" db:"count"`
	Reacted  bool   `json:"reacted" db:"reacted"`
}

type Read struct {
	UserID string    `json:"user_id" db:"user_id"`
	ReadAt time.Time `json:"read_at" db:"read_at"`
}

type CreateChatRequest struct {
	Type      string   `json:"type"`
	Name      *string  `json:"name,omitempty"`
	AvatarURL *string  `json:"avatar_url,omitempty"`
	MemberIDs []string `json:"member_ids"`
}

type UpdateChatRequest struct {
	Name       *string `json:"name,omitempty"`
	AvatarURL  *string `json:"avatar_url,omitempty"`
	IsMuted    *bool   `json:"is_muted,omitempty"`
	IsArchived *bool   `json:"is_archived,omitempty"`
}

type AddMembersRequest struct {
	UserIDs []string `json:"user_ids"`
}

type UpdateMemberRoleRequest struct {
	Role string `json:"role"`
}

type PinMessageRequest struct {
	MessageID string `json:"message_id"`
}

type JoinChatRequest struct {
	Token string `json:"token"`
}

type ChatPin struct {
	ChatID    string    `json:"chat_id" db:"chat_id"`
	MessageID string    `json:"message_id" db:"message_id"`
	PinnedBy  string    `json:"pinned_by" db:"pinned_by"`
	PinnedAt  time.Time `json:"pinned_at" db:"pinned_at"`
	Content   *string   `json:"content,omitempty" db:"content"`
	Type      string    `json:"type,omitempty" db:"type"`
}

type ChatInviteLink struct {
	ID        string     `json:"id" db:"id"`
	ChatID    string     `json:"chat_id" db:"chat_id"`
	Token     string     `json:"token" db:"token"`
	CreatedBy string     `json:"created_by" db:"created_by"`
	CreatedAt time.Time  `json:"created_at" db:"created_at"`
	RevokedAt *time.Time `json:"revoked_at,omitempty" db:"revoked_at"`
	UseCount  int        `json:"use_count" db:"use_count"`
}

type CreateMessageRequest struct {
	Type      string  `json:"type"`
	Content   *string `json:"content,omitempty"`
	MediaID   *string `json:"-"`
	MediaURL  *string `json:"media_url,omitempty"`
	ReplyToID *string `json:"reply_to_id,omitempty"`
}

type UpdateMessageRequest struct {
	Content string `json:"content"`
}

type ReactionRequest struct {
	Reaction string `json:"reaction"`
}

type ReactionResult struct {
	Reaction string `json:"reaction"`
	Active   bool   `json:"active"`
	Count    int64  `json:"count"`
}

type ListParams struct {
	Cursor string
	Limit  int
}

type ErrorResponse struct {
	Error string `json:"error"`
	Code  string `json:"code"`
}
