package model

import (
	"errors"
	"fmt"
	"strings"
	"testing"
)

func TestNormalizeInterestIDs(t *testing.T) {
	id1 := "11111111-1111-4111-8111-111111111111"
	id2 := "22222222-2222-4222-8222-222222222222"

	t.Run("dedupes and preserves order", func(t *testing.T) {
		got, err := NormalizeInterestIDs([]string{id1, id2, id1})
		if err != nil {
			t.Fatalf("unexpected error: %v", err)
		}
		if len(got) != 2 || got[0] != id1 || got[1] != id2 {
			t.Fatalf("got %v", got)
		}
	})

	t.Run("rejects more than 20", func(t *testing.T) {
		ids := make([]string, 21)
		for i := range ids {
			ids[i] = fmt.Sprintf("00000000-0000-4000-8000-%012d", i+1)
		}
		_, err := NormalizeInterestIDs(ids)
		if !errors.Is(err, ErrTooMany) {
			t.Fatalf("want ErrTooMany, got %v", err)
		}
	})

	t.Run("rejects invalid uuid", func(t *testing.T) {
		_, err := NormalizeInterestIDs([]string{"not-a-uuid"})
		if !errors.Is(err, ErrInvalidIDs) {
			t.Fatalf("want ErrInvalidIDs, got %v", err)
		}
	})

	t.Run("allows empty set", func(t *testing.T) {
		got, err := NormalizeInterestIDs(nil)
		if err != nil {
			t.Fatalf("unexpected error: %v", err)
		}
		if len(got) != 0 {
			t.Fatalf("want empty, got %v", got)
		}
	})
}

func TestValidateInterestSlug(t *testing.T) {
	tests := []struct {
		slug    string
		wantErr bool
	}{
		{"technology", false},
		{"data-science", false},
		{"Tech", true},
		{"has_underscore", true},
		{"", true},
		{strings.Repeat("a", 65), true},
		{strings.Repeat("a", 64), false},
	}
	for _, tt := range tests {
		err := ValidateInterestSlug(tt.slug)
		if (err != nil) != tt.wantErr {
			t.Errorf("ValidateInterestSlug(%q) err=%v wantErr=%v", tt.slug, err, tt.wantErr)
		}
	}
}

func TestValidateLabels(t *testing.T) {
	if err := ValidateLabels(Labels{"en": "Music", "fa": "موسیقی"}); err != nil {
		t.Fatalf("valid labels: %v", err)
	}
	if err := ValidateLabels(Labels{"en": "Music"}); !errors.Is(err, ErrInvalidInput) {
		t.Fatalf("missing fa: %v", err)
	}
	if err := ValidateLabels(nil); !errors.Is(err, ErrInvalidInput) {
		t.Fatalf("nil labels: %v", err)
	}
}

func TestAssignSortOrders(t *testing.T) {
	id1 := "11111111-1111-4111-8111-111111111111"
	id2 := "22222222-2222-4222-8222-222222222222"

	orders, err := AssignSortOrders([]string{id2, id1})
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	if orders[id2] != 0 || orders[id1] != 1 {
		t.Fatalf("got %v", orders)
	}

	if _, err := AssignSortOrders([]string{}); !errors.Is(err, ErrInvalidInput) {
		t.Fatalf("empty: %v", err)
	}
	if _, err := AssignSortOrders([]string{id1, id1}); !errors.Is(err, ErrInvalidInput) {
		t.Fatalf("dupes: %v", err)
	}
	if _, err := AssignSortOrders([]string{"bad"}); !errors.Is(err, ErrInvalidIDs) {
		t.Fatalf("bad uuid: %v", err)
	}
}
