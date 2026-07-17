package theme

import (
	"encoding/json"
	"fmt"
	"regexp"
	"strings"
)

const SettingsKey = "theme"

var hexColorRE = regexp.MustCompile(`^#[0-9A-Fa-f]{6}$`)

// Palette is one light or dark color set for the whole platform.
type Palette struct {
	Primary             string `json:"primary"`
	PrimaryHover        string `json:"primary_hover"`
	PrimaryPressed      string `json:"primary_pressed"`
	PrimarySurface      string `json:"primary_surface"`
	Background          string `json:"background"`
	BackgroundSecondary string `json:"background_secondary"`
	BackgroundTertiary  string `json:"background_tertiary"`
	TextPrimary         string `json:"text_primary"`
	TextSecondary       string `json:"text_secondary"`
	TextTertiary        string `json:"text_tertiary"`
	Border              string `json:"border"`
	BorderStrong        string `json:"border_strong"`
	Error               string `json:"error"`
	Success             string `json:"success"`
	Warning             string `json:"warning"`
	BubbleOwn           string `json:"bubble_own"`
	BubbleOthers        string `json:"bubble_others"`
}

// Settings is the platform-wide theme stored in platform_settings.
type Settings struct {
	Light Palette `json:"light"`
	Dark  Palette `json:"dark"`
}

// Default returns the Xilo design-system palette (ui-ux-spec §2).
func Default() Settings {
	return Settings{
		Light: Palette{
			Primary:             "#1D9BF0",
			PrimaryHover:        "#1A8CD8",
			PrimaryPressed:      "#1A7BC5",
			PrimarySurface:      "#E8F5FE",
			Background:          "#FFFFFF",
			BackgroundSecondary: "#F7F9FA",
			BackgroundTertiary:  "#EFF3F4",
			TextPrimary:         "#0F1419",
			TextSecondary:       "#536471",
			TextTertiary:        "#8295A3",
			Border:              "#EFF3F4",
			BorderStrong:        "#CFD9DE",
			Error:               "#F4212E",
			Success:             "#00BA7C",
			Warning:             "#FFAD1F",
			BubbleOwn:           "#E8F5FE",
			BubbleOthers:        "#F7F9FA",
		},
		Dark: Palette{
			Primary:             "#1D9BF0",
			PrimaryHover:        "#4DB8F5",
			PrimaryPressed:      "#6BC9F7",
			PrimarySurface:      "#1A2A3A",
			Background:          "#15202B",
			BackgroundSecondary: "#192734",
			BackgroundTertiary:  "#22303C",
			TextPrimary:         "#E7E9EA",
			TextSecondary:       "#71767B",
			TextTertiary:        "#536471",
			Border:              "#38444D",
			BorderStrong:        "#4A5A66",
			Error:               "#F4212E",
			Success:             "#00BA7C",
			Warning:             "#FFAD1F",
			BubbleOwn:           "#1E3A5F",
			BubbleOthers:        "#2C2C2E",
		},
	}
}

// Validate ensures every color field is a #RRGGBB hex value.
func Validate(s Settings) error {
	if err := validatePalette("light", s.Light); err != nil {
		return err
	}
	return validatePalette("dark", s.Dark)
}

func validatePalette(mode string, p Palette) error {
	fields := map[string]string{
		"primary":              p.Primary,
		"primary_hover":        p.PrimaryHover,
		"primary_pressed":      p.PrimaryPressed,
		"primary_surface":      p.PrimarySurface,
		"background":           p.Background,
		"background_secondary": p.BackgroundSecondary,
		"background_tertiary":  p.BackgroundTertiary,
		"text_primary":         p.TextPrimary,
		"text_secondary":       p.TextSecondary,
		"text_tertiary":        p.TextTertiary,
		"border":               p.Border,
		"border_strong":        p.BorderStrong,
		"error":                p.Error,
		"success":              p.Success,
		"warning":              p.Warning,
		"bubble_own":           p.BubbleOwn,
		"bubble_others":        p.BubbleOthers,
	}
	for name, value := range fields {
		if !IsHexColor(value) {
			return fmt.Errorf("invalid %s.%s color: %q (expected #RRGGBB)", mode, name, value)
		}
	}
	return nil
}

// IsHexColor reports whether s is a 6-digit hex color with leading #.
func IsHexColor(s string) bool {
	return hexColorRE.MatchString(strings.TrimSpace(s))
}

// Normalize uppercases hex digits for stable storage.
func Normalize(s Settings) Settings {
	return Settings{
		Light: normalizePalette(s.Light),
		Dark:  normalizePalette(s.Dark),
	}
}

func normalizePalette(p Palette) Palette {
	return Palette{
		Primary:             strings.ToUpper(strings.TrimSpace(p.Primary)),
		PrimaryHover:        strings.ToUpper(strings.TrimSpace(p.PrimaryHover)),
		PrimaryPressed:      strings.ToUpper(strings.TrimSpace(p.PrimaryPressed)),
		PrimarySurface:      strings.ToUpper(strings.TrimSpace(p.PrimarySurface)),
		Background:          strings.ToUpper(strings.TrimSpace(p.Background)),
		BackgroundSecondary: strings.ToUpper(strings.TrimSpace(p.BackgroundSecondary)),
		BackgroundTertiary:  strings.ToUpper(strings.TrimSpace(p.BackgroundTertiary)),
		TextPrimary:         strings.ToUpper(strings.TrimSpace(p.TextPrimary)),
		TextSecondary:       strings.ToUpper(strings.TrimSpace(p.TextSecondary)),
		TextTertiary:        strings.ToUpper(strings.TrimSpace(p.TextTertiary)),
		Border:              strings.ToUpper(strings.TrimSpace(p.Border)),
		BorderStrong:        strings.ToUpper(strings.TrimSpace(p.BorderStrong)),
		Error:               strings.ToUpper(strings.TrimSpace(p.Error)),
		Success:             strings.ToUpper(strings.TrimSpace(p.Success)),
		Warning:             strings.ToUpper(strings.TrimSpace(p.Warning)),
		BubbleOwn:           strings.ToUpper(strings.TrimSpace(p.BubbleOwn)),
		BubbleOthers:        strings.ToUpper(strings.TrimSpace(p.BubbleOthers)),
	}
}

// Merge fills empty fields on patch from base (partial admin updates).
func Merge(base, patch Settings) Settings {
	out := base
	out.Light = mergePalette(base.Light, patch.Light)
	out.Dark = mergePalette(base.Dark, patch.Dark)
	return out
}

func mergePalette(base, patch Palette) Palette {
	out := base
	set := func(dst *string, src string) {
		if strings.TrimSpace(src) != "" {
			*dst = src
		}
	}
	set(&out.Primary, patch.Primary)
	set(&out.PrimaryHover, patch.PrimaryHover)
	set(&out.PrimaryPressed, patch.PrimaryPressed)
	set(&out.PrimarySurface, patch.PrimarySurface)
	set(&out.Background, patch.Background)
	set(&out.BackgroundSecondary, patch.BackgroundSecondary)
	set(&out.BackgroundTertiary, patch.BackgroundTertiary)
	set(&out.TextPrimary, patch.TextPrimary)
	set(&out.TextSecondary, patch.TextSecondary)
	set(&out.TextTertiary, patch.TextTertiary)
	set(&out.Border, patch.Border)
	set(&out.BorderStrong, patch.BorderStrong)
	set(&out.Error, patch.Error)
	set(&out.Success, patch.Success)
	set(&out.Warning, patch.Warning)
	set(&out.BubbleOwn, patch.BubbleOwn)
	set(&out.BubbleOthers, patch.BubbleOthers)
	return out
}

// Parse unmarshals theme JSON or returns defaults on empty/invalid structure.
func Parse(raw json.RawMessage) (Settings, error) {
	if len(raw) == 0 {
		return Default(), nil
	}
	var s Settings
	if err := json.Unmarshal(raw, &s); err != nil {
		return Settings{}, err
	}
	merged := Merge(Default(), s)
	if err := Validate(merged); err != nil {
		return Settings{}, err
	}
	return Normalize(merged), nil
}
