package contacthash

import (
	"os"
	"testing"
)

func TestNormalizePhone(t *testing.T) {
	cases := []struct {
		in, want string
	}{
		{"09121234567", "989121234567"},
		{"+98 912 123 4567", "989121234567"},
		{"00989121234567", "989121234567"},
		{"989121234567", "989121234567"},
		{"  0912-123-4567  ", "989121234567"},
		{"", ""},
		{"abc", ""},
		{"9121234567", "9121234567"}, // no leading 09 → unchanged
	}
	for _, tc := range cases {
		if got := NormalizePhone(tc.in); got != tc.want {
			t.Errorf("NormalizePhone(%q) = %q, want %q", tc.in, got, tc.want)
		}
	}
}

func TestNormalizeEmail(t *testing.T) {
	cases := []struct {
		in, want string
	}{
		{"  User@Example.COM ", "user@example.com"},
		{"a@b.co", "a@b.co"},
		{"", ""},
	}
	for _, tc := range cases {
		if got := NormalizeEmail(tc.in); got != tc.want {
			t.Errorf("NormalizeEmail(%q) = %q, want %q", tc.in, got, tc.want)
		}
	}
}

func TestHashPhoneDeterministicHMAC(t *testing.T) {
	pepper := "test-pepper"
	a := HashPhone(pepper, "09121234567")
	b := HashPhone(pepper, "+98 912 123 4567")
	if a == "" || a != b {
		t.Fatalf("expected same hash for equivalent phones, got %q vs %q", a, b)
	}
	if len(a) != 64 {
		t.Fatalf("expected 64-char hex, got len=%d", len(a))
	}
	// Pipeline: normalize → client sha256 hex → HMAC
	n := NormalizePhone("09121234567")
	client := ClientSHA256Hex(n)
	want := ServerHMACHex(pepper, client)
	if a != want {
		t.Fatalf("HashPhone != ServerHMACHex(ClientSHA256Hex): %q vs %q", a, want)
	}
	other := HashPhone("other-pepper", "09121234567")
	if other == a {
		t.Fatal("different peppers must produce different hashes")
	}
}

func TestHashEmailDeterministicHMAC(t *testing.T) {
	pepper := "test-pepper"
	a := HashEmail(pepper, "  User@Example.COM ")
	b := HashEmail(pepper, "user@example.com")
	if a == "" || a != b {
		t.Fatalf("expected same hash for equivalent emails, got %q vs %q", a, b)
	}
	if len(a) != 64 {
		t.Fatalf("expected 64-char hex, got len=%d", len(a))
	}
}

func TestResolvePepper_DevDefault(t *testing.T) {
	t.Setenv("CONTACT_MATCH_PEPPER", "")
	t.Setenv("APP_ENV", "")
	t.Setenv("GO_ENV", "")
	t.Setenv("ENV", "")
	if got := ResolvePepper(); got != DevPepper {
		t.Fatalf("ResolvePepper() = %q, want %q", got, DevPepper)
	}
}

func TestResolvePepper_FromEnv(t *testing.T) {
	t.Setenv("CONTACT_MATCH_PEPPER", "secret-pepper")
	if got := ResolvePepper(); got != "secret-pepper" {
		t.Fatalf("ResolvePepper() = %q, want secret-pepper", got)
	}
}

func TestResolvePepper_FailClosedInProduction(t *testing.T) {
	t.Setenv("CONTACT_MATCH_PEPPER", "")
	t.Setenv("APP_ENV", "production")
	if got := ResolvePepper(); got != "" {
		t.Fatalf("ResolvePepper() in prod = %q, want empty", got)
	}
	_ = os.Unsetenv("APP_ENV")
}
