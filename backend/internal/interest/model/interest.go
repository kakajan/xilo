package model

import (
	"database/sql/driver"
	"encoding/json"
	"errors"
	"fmt"
	"time"
)

const MaxUserInterests = 20

var (
	ErrNotFound      = errors.New("interest not found")
	ErrConflict      = errors.New("interest conflict")
	ErrInvalidInput  = errors.New("invalid interest input")
	ErrTooMany       = errors.New("too many interests")
	ErrInvalidIDs    = errors.New("invalid interest ids")
)

// Labels stores multilingual display names (at least en + fa).
type Labels map[string]string

func (l Labels) Value() (driver.Value, error) {
	if l == nil {
		return []byte("{}"), nil
	}
	return json.Marshal(l)
}

func (l *Labels) Scan(value interface{}) error {
	if value == nil {
		*l = Labels{}
		return nil
	}
	var b []byte
	switch v := value.(type) {
	case []byte:
		b = v
	case string:
		b = []byte(v)
	default:
		return fmt.Errorf("cannot scan %T into Labels", value)
	}
	var out Labels
	if err := json.Unmarshal(b, &out); err != nil {
		return err
	}
	*l = out
	return nil
}

// Interest is an admin-managed catalog entry.
type Interest struct {
	ID        string    `json:"id" db:"id"`
	Slug      string    `json:"slug" db:"slug"`
	Labels    Labels    `json:"labels" db:"labels"`
	Icon      *string   `json:"icon,omitempty" db:"icon"`
	SortOrder int       `json:"sort_order" db:"sort_order"`
	IsActive  bool      `json:"is_active" db:"is_active"`
	CreatedAt time.Time `json:"created_at,omitempty" db:"created_at"`
	UpdatedAt time.Time `json:"updated_at,omitempty" db:"updated_at"`
}

// PublicInterest is the client-facing catalog shape (active only).
type PublicInterest struct {
	ID        string  `json:"id"`
	Slug      string  `json:"slug"`
	Labels    Labels  `json:"labels"`
	Icon      *string `json:"icon,omitempty"`
	SortOrder int     `json:"sort_order"`
}

func ToPublic(i Interest) PublicInterest {
	return PublicInterest{
		ID:        i.ID,
		Slug:      i.Slug,
		Labels:    i.Labels,
		Icon:      i.Icon,
		SortOrder: i.SortOrder,
	}
}

func ToPublicList(items []Interest) []PublicInterest {
	out := make([]PublicInterest, 0, len(items))
	for _, item := range items {
		out = append(out, ToPublic(item))
	}
	return out
}

type CreateInterestRequest struct {
	Slug      string  `json:"slug"`
	Labels    Labels  `json:"labels"`
	Icon      *string `json:"icon"`
	SortOrder *int    `json:"sort_order"`
	IsActive  *bool   `json:"is_active"`
}

type PatchInterestRequest struct {
	Slug      *string `json:"slug"`
	Labels    Labels  `json:"labels"`
	Icon      *string `json:"icon"`
	SortOrder *int    `json:"sort_order"`
	IsActive  *bool   `json:"is_active"`
}

type PutUserInterestsRequest struct {
	InterestIDs []string `json:"interest_ids"`
}

type ReorderRequest struct {
	OrderedIDs []string `json:"ordered_ids"`
}
