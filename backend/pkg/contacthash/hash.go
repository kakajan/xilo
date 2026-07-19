// Package contacthash provides privacy-preserving phone/email hashing for contact match.
//
// Pipeline:
//  1. Normalize raw value (client and server must agree)
//  2. ClientSHA256Hex(normalized) — clients send this hex; never send raw contacts
//  3. ServerHMACHex(pepper, clientSHA256Hex) — stored/compared server-side
package contacthash

import (
	"crypto/hmac"
	"crypto/sha256"
	"encoding/hex"
	"strings"
	"unicode"
)

// NormalizePhone strips non-digits, removes leading 00 country escape, and maps
// Iranian local mobiles 09xxxxxxxxx (11 digits) to 989xxxxxxxxx.
func NormalizePhone(raw string) string {
	var b strings.Builder
	b.Grow(len(raw))
	for _, r := range raw {
		if unicode.IsDigit(r) {
			b.WriteRune(r)
		}
	}
	digits := b.String()
	for strings.HasPrefix(digits, "00") {
		digits = digits[2:]
	}
	if len(digits) == 11 && strings.HasPrefix(digits, "09") {
		digits = "98" + digits[1:]
	}
	return digits
}

// NormalizeEmail trims whitespace and lowercases.
func NormalizeEmail(raw string) string {
	return strings.ToLower(strings.TrimSpace(raw))
}

// ClientSHA256Hex returns the hex-encoded SHA-256 of the normalized value.
// Clients submit this; the server never stores it directly.
func ClientSHA256Hex(normalized string) string {
	sum := sha256.Sum256([]byte(normalized))
	return hex.EncodeToString(sum[:])
}

// ServerHMACHex returns hex-encoded HMAC-SHA256(pepper, clientSHA256Hex).
func ServerHMACHex(pepper, clientSHA256Hex string) string {
	mac := hmac.New(sha256.New, []byte(pepper))
	_, _ = mac.Write([]byte(clientSHA256Hex))
	return hex.EncodeToString(mac.Sum(nil))
}

// HashPhone normalizes a raw phone, then apply client SHA-256 + server HMAC.
// Returns "" if the normalized phone is empty.
func HashPhone(pepper, rawPhone string) string {
	n := NormalizePhone(rawPhone)
	if n == "" {
		return ""
	}
	return ServerHMACHex(pepper, ClientSHA256Hex(n))
}

// HashEmail normalizes a raw email, then apply client SHA-256 + server HMAC.
// Returns "" if the normalized email is empty.
func HashEmail(pepper, rawEmail string) string {
	n := NormalizeEmail(rawEmail)
	if n == "" {
		return ""
	}
	return ServerHMACHex(pepper, ClientSHA256Hex(n))
}

// HMACClientHashes applies ServerHMACHex to each non-empty client hash.
func HMACClientHashes(pepper string, clientHashes []string) []string {
	out := make([]string, 0, len(clientHashes))
	for _, h := range clientHashes {
		h = strings.TrimSpace(h)
		if h == "" {
			continue
		}
		out = append(out, ServerHMACHex(pepper, h))
	}
	return out
}
