package pagination

import (
	"encoding/base64"
	"encoding/json"
	"fmt"
	"time"
)

type Cursor struct {
	ID        string    `json:"id"`
	Timestamp time.Time `json:"ts"`
}

type Result[T any] struct {
	Data       []T     `json:"data"`
	NextCursor string  `json:"next_cursor,omitempty"`
	HasMore    bool    `json:"has_more"`
	Total      int64   `json:"total,omitempty"`
}

func EncodeCursor(id string, ts time.Time) string {
	c := Cursor{ID: id, Timestamp: ts}
	data, _ := json.Marshal(c)
	return base64.RawURLEncoding.EncodeToString(data)
}

func DecodeCursor(encoded string) (*Cursor, error) {
	data, err := base64.RawURLEncoding.DecodeString(encoded)
	if err != nil {
		return nil, fmt.Errorf("decode cursor: %w", err)
	}
	var c Cursor
	if err := json.Unmarshal(data, &c); err != nil {
		return nil, fmt.Errorf("unmarshal cursor: %w", err)
	}
	return &c, nil
}

func ValidateLimit(limit int, defaultLimit int, maxLimit int) int {
	if limit <= 0 {
		return defaultLimit
	}
	if limit > maxLimit {
		return maxLimit
	}
	return limit
}
