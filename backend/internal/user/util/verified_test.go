package util

import "testing"

func TestIsVerifiedWriter(t *testing.T) {
	tests := []struct {
		role string
		want bool
	}{
		{"writer", true},
		{"admin", true},
		{"editor", true},
		{"author", true},
		{"reader", false},
		{"", false},
	}
	for _, tt := range tests {
		if got := IsVerifiedWriter(tt.role); got != tt.want {
			t.Errorf("IsVerifiedWriter(%q) = %v, want %v", tt.role, got, tt.want)
		}
	}
}
