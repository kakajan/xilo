package model

import "time"

type ChatFolder struct {
	ID        string    `json:"id" db:"id"`
	UserID    string    `json:"user_id" db:"user_id"`
	Name      string    `json:"name" db:"name"`
	SortOrder int       `json:"sort_order" db:"sort_order"`
	CreatedAt time.Time `json:"created_at" db:"created_at"`
	ChatIDs   []string  `json:"chat_ids,omitempty" db:"-"`
}

type CreateChatFolderRequest struct {
	Name      string `json:"name"`
	SortOrder *int   `json:"sort_order,omitempty"`
}

type UpdateChatFolderRequest struct {
	Name      *string `json:"name,omitempty"`
	SortOrder *int    `json:"sort_order,omitempty"`
}

type SetFolderChatsRequest struct {
	ChatIDs []string `json:"chat_ids"`
}
