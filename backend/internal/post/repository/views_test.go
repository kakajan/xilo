package repository

import (
	"testing"

	"github.com/xilo-platform/xilo/internal/post/model"
)

func TestViewerKey_Authenticated(t *testing.T) {
	key, err := viewerKey("user-123", "")
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	if key != "u:user-123" {
		t.Fatalf("got %q", key)
	}
}

func TestViewerKey_AnonymousRequiresSession(t *testing.T) {
	_, err := viewerKey("", "short")
	if err == nil {
		t.Fatal("expected error for short session")
	}
	if err != model.ErrInvalidViewSession {
		t.Fatalf("expected ErrInvalidViewSession, got %v", err)
	}
}

func TestViewerKey_Anonymous(t *testing.T) {
	key, err := viewerKey("", "0123456789abcdef")
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	if key != "s:0123456789abcdef" {
		t.Fatalf("got %q", key)
	}
}
