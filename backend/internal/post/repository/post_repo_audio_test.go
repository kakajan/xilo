package repository

import "testing"

func TestNullIfEmpty(t *testing.T) {
	if nullIfEmpty("") != nil {
		t.Fatal("expected nil for empty")
	}
	if nullIfEmpty("  ") != nil {
		t.Fatal("expected nil for whitespace")
	}
	got := nullIfEmpty("https://cdn.example/a.mp3")
	if got == nil || *got != "https://cdn.example/a.mp3" {
		t.Fatalf("unexpected: %v", got)
	}
}

func TestCoalesceOptionalURL(t *testing.T) {
	existing := "https://cdn.example/old.mp3"
	if got := coalesceOptionalURL(nil, &existing); got == nil || *got != existing {
		t.Fatalf("nil update should keep existing, got %v", got)
	}
	empty := ""
	if got := coalesceOptionalURL(&empty, &existing); got != nil {
		t.Fatalf("empty should clear, got %v", got)
	}
	next := "https://cdn.example/new.mp3"
	if got := coalesceOptionalURL(&next, &existing); got == nil || *got != next {
		t.Fatalf("should set new url, got %v", got)
	}
}
