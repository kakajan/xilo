package idempotency

import (
	"errors"
	"testing"
)

func TestParseKeyRequiresUUIDv4(t *testing.T) {
	valid := "123e4567-e89b-42d3-a456-426614174000"
	key, err := ParseKey(valid)
	if err != nil {
		t.Fatalf("ParseKey(valid) returned error: %v", err)
	}
	if key.String() != valid {
		t.Fatalf("parsed key = %q, want %q", key, valid)
	}

	for _, invalid := range []string{
		"",
		"not-a-uuid",
		"{123e4567-e89b-42d3-a456-426614174000}",
		"123e4567-e89b-12d3-a456-426614174000",
		"123e4567-e89b-42d3-7456-426614174000",
	} {
		t.Run(invalid, func(t *testing.T) {
			if _, err := ParseKey(invalid); !errors.Is(err, ErrInvalidKey) {
				t.Fatalf("ParseKey(%q) error = %v, want ErrInvalidKey", invalid, err)
			}
		})
	}
}

func TestHashPayloadIsCanonicalAndSensitiveToSemantics(t *testing.T) {
	first := map[string]any{
		"type":       "group",
		"name":       "team",
		"member_ids": []string{"a", "b"},
	}
	sameDifferentInsertionOrder := map[string]any{
		"member_ids": []string{"a", "b"},
		"name":       "team",
		"type":       "group",
	}
	different := map[string]any{
		"type":       "group",
		"name":       "team-renamed",
		"member_ids": []string{"a", "b"},
	}

	firstHash, err := HashPayload(first)
	if err != nil {
		t.Fatalf("hash first payload: %v", err)
	}
	sameHash, err := HashPayload(sameDifferentInsertionOrder)
	if err != nil {
		t.Fatalf("hash equivalent payload: %v", err)
	}
	differentHash, err := HashPayload(different)
	if err != nil {
		t.Fatalf("hash different payload: %v", err)
	}

	if firstHash != sameHash {
		t.Fatalf("equivalent payload hashes differ: %q != %q", firstHash, sameHash)
	}
	if firstHash == differentHash {
		t.Fatalf("different semantic payloads produced the same hash: %q", firstHash)
	}
	if len(firstHash) != 64 {
		t.Fatalf("SHA-256 hex length = %d, want 64", len(firstHash))
	}
}
