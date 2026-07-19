package model

import (
	"regexp"
	"strings"

	"github.com/google/uuid"
)

var interestSlugRegex = regexp.MustCompile(`^[a-z0-9]+(?:-[a-z0-9]+)*$`)

// NormalizeInterestIDs validates UUID format, enforces max count, and deduplicates
// while preserving first-seen order.
func NormalizeInterestIDs(ids []string) ([]string, error) {
	if ids == nil {
		ids = []string{}
	}
	if len(ids) > MaxUserInterests {
		return nil, ErrTooMany
	}
	seen := make(map[string]struct{}, len(ids))
	out := make([]string, 0, len(ids))
	for _, raw := range ids {
		id := strings.TrimSpace(raw)
		if id == "" {
			return nil, ErrInvalidIDs
		}
		if _, err := uuid.Parse(id); err != nil {
			return nil, ErrInvalidIDs
		}
		if _, ok := seen[id]; ok {
			continue
		}
		seen[id] = struct{}{}
		out = append(out, id)
	}
	if len(out) > MaxUserInterests {
		return nil, ErrTooMany
	}
	return out, nil
}

// ValidateInterestSlug checks catalog slug rules (lowercase kebab-case, max 64).
func ValidateInterestSlug(slug string) error {
	slug = strings.TrimSpace(slug)
	if slug == "" {
		return ErrInvalidInput
	}
	if len(slug) > 64 {
		return ErrInvalidInput
	}
	if !interestSlugRegex.MatchString(slug) {
		return ErrInvalidInput
	}
	return nil
}

// ValidateLabels requires non-empty en and fa entries.
func ValidateLabels(labels Labels) error {
	if labels == nil {
		return ErrInvalidInput
	}
	en := strings.TrimSpace(labels["en"])
	fa := strings.TrimSpace(labels["fa"])
	if en == "" || fa == "" {
		return ErrInvalidInput
	}
	return nil
}

// AssignSortOrders maps each id in orderedIDs to its zero-based index.
// Duplicate IDs are rejected.
func AssignSortOrders(orderedIDs []string) (map[string]int, error) {
	if len(orderedIDs) == 0 {
		return nil, ErrInvalidInput
	}
	out := make(map[string]int, len(orderedIDs))
	for i, raw := range orderedIDs {
		id := strings.TrimSpace(raw)
		if id == "" {
			return nil, ErrInvalidIDs
		}
		if _, err := uuid.Parse(id); err != nil {
			return nil, ErrInvalidIDs
		}
		if _, exists := out[id]; exists {
			return nil, ErrInvalidInput
		}
		out[id] = i
	}
	return out, nil
}
