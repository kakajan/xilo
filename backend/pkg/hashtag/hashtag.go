// Package hashtag extracts and normalizes Instagram/X-style hashtags from post text.
package hashtag

import (
	"encoding/json"
	"regexp"
	"strings"
	"unicode"
	"unicode/utf8"

	"golang.org/x/text/unicode/norm"
)

const MaxTags = 10
const MaxTagLen = 30

// tagBody matches letters (incl. Arabic/Persian), digits, underscore, and hyphen.
var tagBody = regexp.MustCompile(`^[\p{L}\p{N}_\-]{1,30}$`)

var urlSpan = regexp.MustCompile(`(?i)https?://[^\s<>"']+|www\.[^\s<>"']+`)

// Normalize returns a storage form without leading '#': NFC, Latin lowercased.
// Empty string means the tag is invalid.
func Normalize(raw string) string {
	s := strings.TrimSpace(raw)
	s = strings.TrimPrefix(s, "#")
	s = norm.NFC.String(s)
	if s == "" || utf8.RuneCountInString(s) > MaxTagLen {
		return ""
	}
	if !tagBody.MatchString(s) {
		return ""
	}
	if isDigitsOnly(s) {
		return ""
	}
	return foldLatin(s)
}

func isDigitsOnly(s string) bool {
	for _, r := range s {
		if !unicode.IsDigit(r) {
			return false
		}
	}
	return len(s) > 0
}

func foldLatin(s string) string {
	var b strings.Builder
	b.Grow(len(s))
	for _, r := range s {
		if r <= unicode.MaxASCII && unicode.IsLetter(r) {
			b.WriteRune(unicode.ToLower(r))
		} else {
			b.WriteRune(r)
		}
	}
	return b.String()
}

func dedupeKey(tag string) string {
	return strings.ToLower(tag)
}

// Extract returns unique normalized hashtags in first-seen order (max MaxTags).
func Extract(text string) []string {
	if text == "" {
		return nil
	}
	masked := maskURLs(text)
	var out []string
	seen := make(map[string]struct{})

	runes := []rune(masked)
	for i := 0; i < len(runes); i++ {
		if runes[i] != '#' {
			continue
		}
		if i > 0 && !isBoundary(runes[i-1]) {
			continue
		}
		j := i + 1
		for j < len(runes) && isTagChar(runes[j]) {
			j++
		}
		if j == i+1 {
			continue
		}
		raw := string(runes[i+1 : j])
		normTag := Normalize(raw)
		if normTag == "" {
			continue
		}
		key := dedupeKey(normTag)
		if _, ok := seen[key]; ok {
			continue
		}
		seen[key] = struct{}{}
		out = append(out, normTag)
		if len(out) >= MaxTags {
			break
		}
		i = j - 1
	}
	return out
}

func isBoundary(r rune) bool {
	if unicode.IsSpace(r) {
		return true
	}
	switch r {
	case '(', ')', '[', ']', '{', '}', '"', '\'', '«', '»', '،', ',', '.', '!', '?', ':', ';',
		'…', '—', '–', '/', '\\', '|', '*', '~', '`':
		return true
	}
	return unicode.IsPunct(r) || unicode.IsSymbol(r)
}

func isTagChar(r rune) bool {
	return unicode.IsLetter(r) || unicode.IsDigit(r) || r == '_' || r == '-'
}

func maskURLs(text string) string {
	return urlSpan.ReplaceAllStringFunc(text, func(u string) string {
		return strings.Repeat(" ", len([]rune(u)))
	})
}

// Merge combines extracted hashtags (first) with explicit tags, deduped, capped at MaxTags.
func Merge(extracted []string, explicit []string) []string {
	var out []string
	seen := make(map[string]struct{})
	add := func(raw string) {
		n := Normalize(raw)
		if n == "" {
			return
		}
		key := dedupeKey(n)
		if _, ok := seen[key]; ok {
			return
		}
		seen[key] = struct{}{}
		out = append(out, n)
	}
	for _, t := range extracted {
		if len(out) >= MaxTags {
			return out
		}
		add(t)
	}
	for _, t := range explicit {
		if len(out) >= MaxTags {
			return out
		}
		add(t)
	}
	return out
}

// PlainTextFromContent prefers content_md; falls back to TipTap/plain JSON text extraction.
func PlainTextFromContent(contentMD, content string) string {
	if strings.TrimSpace(contentMD) != "" {
		return contentMD
	}
	return PlainTextFromTipTap(content)
}

// PlainTextFromTipTap walks TipTap JSON and joins text nodes; non-JSON is returned as-is.
func PlainTextFromTipTap(raw string) string {
	raw = strings.TrimSpace(raw)
	if raw == "" || raw == "{}" {
		return ""
	}
	var node tipTapNode
	if err := json.Unmarshal([]byte(raw), &node); err != nil {
		return raw
	}
	var parts []string
	var walk func(n tipTapNode)
	walk = func(n tipTapNode) {
		if n.Text != "" {
			parts = append(parts, n.Text)
		}
		for _, c := range n.Content {
			walk(c)
		}
	}
	walk(node)
	return strings.Join(parts, " ")
}

type tipTapNode struct {
	Text    string       `json:"text"`
	Content []tipTapNode `json:"content"`
}
