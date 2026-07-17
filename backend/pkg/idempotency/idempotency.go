package idempotency

import (
	"crypto/sha256"
	"encoding/hex"
	"encoding/json"
	"errors"
	"fmt"
	"strings"
	"time"

	"github.com/google/uuid"
)

var (
	ErrInvalidKey      = errors.New("idempotency key must be a UUIDv4")
	ErrInvalidReceivedAt = errors.New("idempotency request receipt time is invalid")
	ErrPayloadConflict = errors.New("idempotency key reused with a different payload")
	ErrIncomplete      = errors.New("idempotency record is not completed")
)

type Outcome string

const (
	OutcomeNew    Outcome = "new"
	OutcomeReplay Outcome = "replay"
)

type Request struct {
	PrincipalID string
	Operation   string
	Key         uuid.UUID
	RequestHash string
	ReceivedAt  time.Time
}

type Acquisition struct {
	ID             string
	Outcome        Outcome
	ResourceType   string
	ResourceID     string
	ResponseStatus int
	ResultJSON     json.RawMessage
}

type MutationResult[T any] struct {
	Value          *T
	Outcome        Outcome
	ResponseStatus int
	ReplayJSON     json.RawMessage
}

func (r *MutationResult[T]) IsReplay() bool {
	return r != nil && r.Outcome == OutcomeReplay
}

func ParseKey(value string) (uuid.UUID, error) {
	trimmed := strings.TrimSpace(value)
	key, err := uuid.Parse(trimmed)
	if err != nil ||
		!strings.EqualFold(trimmed, key.String()) ||
		key.Version() != 4 ||
		key.Variant() != uuid.RFC4122 {
		return uuid.Nil, ErrInvalidKey
	}
	return key, nil
}

func HashPayload(payload any) (string, error) {
	canonical, err := json.Marshal(payload)
	if err != nil {
		return "", fmt.Errorf("marshal canonical idempotency payload: %w", err)
	}
	sum := sha256.Sum256(canonical)
	return hex.EncodeToString(sum[:]), nil
}
