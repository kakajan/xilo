package brand

import (
	"encoding/json"
	"fmt"
	"strings"
	"unicode/utf8"
)

const SettingsKey = "brand"

const (
	maxNameLen    = 64
	maxDisplayLen = 128
)

// Settings is the deploy-facing brand stored in platform_settings.
type Settings struct {
	NameFA  string `json:"name_fa"`
	NameEN  string `json:"name_en"`
	Display string `json:"display"`
}

// Default returns the Aile deploy brand (user-facing).
func Default() Settings {
	return Settings{
		NameFA:  "آیله",
		NameEN:  "aile",
		Display: "آیله | aile",
	}
}

// Validate ensures brand fields are non-empty and within length limits.
func Validate(s Settings) error {
	if strings.TrimSpace(s.NameFA) == "" {
		return fmt.Errorf("name_fa is required")
	}
	if strings.TrimSpace(s.NameEN) == "" {
		return fmt.Errorf("name_en is required")
	}
	if strings.TrimSpace(s.Display) == "" {
		return fmt.Errorf("display is required")
	}
	if utf8.RuneCountInString(s.NameFA) > maxNameLen {
		return fmt.Errorf("name_fa exceeds %d characters", maxNameLen)
	}
	if utf8.RuneCountInString(s.NameEN) > maxNameLen {
		return fmt.Errorf("name_en exceeds %d characters", maxNameLen)
	}
	if utf8.RuneCountInString(s.Display) > maxDisplayLen {
		return fmt.Errorf("display exceeds %d characters", maxDisplayLen)
	}
	return nil
}

// Normalize trims whitespace.
func Normalize(s Settings) Settings {
	return Settings{
		NameFA:  strings.TrimSpace(s.NameFA),
		NameEN:  strings.TrimSpace(s.NameEN),
		Display: strings.TrimSpace(s.Display),
	}
}

// Merge fills empty fields on patch from base.
func Merge(base, patch Settings) Settings {
	out := base
	if strings.TrimSpace(patch.NameFA) != "" {
		out.NameFA = patch.NameFA
	}
	if strings.TrimSpace(patch.NameEN) != "" {
		out.NameEN = patch.NameEN
	}
	if strings.TrimSpace(patch.Display) != "" {
		out.Display = patch.Display
	}
	return out
}

// Parse unmarshals brand JSON or returns defaults on empty.
func Parse(raw json.RawMessage) (Settings, error) {
	if len(raw) == 0 {
		return Default(), nil
	}
	var s Settings
	if err := json.Unmarshal(raw, &s); err != nil {
		return Settings{}, err
	}
	merged := Normalize(Merge(Default(), s))
	if err := Validate(merged); err != nil {
		return Settings{}, err
	}
	return merged, nil
}
