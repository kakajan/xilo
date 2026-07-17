package handler

import (
	"encoding/json"
	"errors"

	"github.com/gofiber/fiber/v2"
	"github.com/jmoiron/sqlx"
	"github.com/xilo-platform/xilo/pkg/i18n"
	"github.com/xilo-platform/xilo/pkg/theme"
)

const calendarDefaultsKey = "calendar_defaults"

type SettingsHandler struct {
	db *sqlx.DB
}

func NewSettingsHandler(db *sqlx.DB) *SettingsHandler {
	return &SettingsHandler{db: db}
}

type platformSettingRow struct {
	Key   string          `db:"key"`
	Value json.RawMessage `db:"value"`
}

// GetSettings returns public platform settings (calendar defaults + theme).
func (h *SettingsHandler) GetSettings(c *fiber.Ctx) error {
	defaults, err := h.loadCalendarDefaults(c)
	if err != nil {
		return c.Status(fiber.StatusInternalServerError).JSON(fiber.Map{
			"error": "failed to load platform settings",
			"code":  "internal_error",
		})
	}
	themeSettings, err := h.loadTheme(c)
	if err != nil {
		return c.Status(fiber.StatusInternalServerError).JSON(fiber.Map{
			"error": "failed to load theme settings",
			"code":  "internal_error",
		})
	}
	return c.JSON(fiber.Map{
		"calendar_defaults": defaults,
		"theme":             themeSettings,
	})
}

type updateSettingsRequest struct {
	CalendarDefaults map[string]string `json:"calendar_defaults"`
	Theme            *theme.Settings   `json:"theme"`
}

// UpdateSettings updates platform settings (admin only).
func (h *SettingsHandler) UpdateSettings(c *fiber.Ctx) error {
	var req updateSettingsRequest
	if err := c.BodyParser(&req); err != nil {
		return c.Status(fiber.StatusBadRequest).JSON(fiber.Map{
			"error": "invalid request body",
			"code":  "bad_request",
		})
	}
	if len(req.CalendarDefaults) == 0 && req.Theme == nil {
		return c.Status(fiber.StatusBadRequest).JSON(fiber.Map{
			"error": "calendar_defaults or theme is required",
			"code":  "bad_request",
		})
	}

	var calendarOut map[string]string
	if len(req.CalendarDefaults) > 0 {
		normalized := make(map[string]string, len(req.CalendarDefaults))
		for lang, cal := range req.CalendarDefaults {
			if !i18n.IsValidLanguage(lang) {
				return c.Status(fiber.StatusBadRequest).JSON(fiber.Map{
					"error": "invalid language code: " + lang,
					"code":  "bad_request",
				})
			}
			if !i18n.IsValidCalendarSystem(cal) {
				return c.Status(fiber.StatusBadRequest).JSON(fiber.Map{
					"error": "invalid calendar for " + lang + ": " + cal,
					"code":  "bad_request",
				})
			}
			normalized[lang] = cal
		}

		// Merge with existing defaults so partial updates keep other locales.
		current, _ := h.loadCalendarDefaults(c)
		for k, v := range normalized {
			current[k] = v
		}

		raw, err := json.Marshal(current)
		if err != nil {
			return c.Status(fiber.StatusInternalServerError).JSON(fiber.Map{
				"error": "failed to encode settings",
				"code":  "internal_error",
			})
		}

		if err := h.upsertSetting(c, calendarDefaultsKey, raw); err != nil {
			return c.Status(fiber.StatusInternalServerError).JSON(fiber.Map{
				"error": "failed to save platform settings",
				"code":  "internal_error",
			})
		}
		calendarOut = current
	} else {
		calendarOut, _ = h.loadCalendarDefaults(c)
	}

	var themeOut theme.Settings
	if req.Theme != nil {
		current, err := h.loadTheme(c)
		if err != nil {
			return c.Status(fiber.StatusInternalServerError).JSON(fiber.Map{
				"error": "failed to load theme settings",
				"code":  "internal_error",
			})
		}
		merged := theme.Normalize(theme.Merge(current, *req.Theme))
		if err := theme.Validate(merged); err != nil {
			return c.Status(fiber.StatusBadRequest).JSON(fiber.Map{
				"error": err.Error(),
				"code":  "bad_request",
			})
		}
		raw, err := json.Marshal(merged)
		if err != nil {
			return c.Status(fiber.StatusInternalServerError).JSON(fiber.Map{
				"error": "failed to encode theme",
				"code":  "internal_error",
			})
		}
		if err := h.upsertSetting(c, theme.SettingsKey, raw); err != nil {
			return c.Status(fiber.StatusInternalServerError).JSON(fiber.Map{
				"error": "failed to save theme settings",
				"code":  "internal_error",
			})
		}
		themeOut = merged
	} else {
		themeOut, _ = h.loadTheme(c)
	}

	return c.JSON(fiber.Map{
		"calendar_defaults": calendarOut,
		"theme":             themeOut,
	})
}

// LoadCalendarDefaults is used by the languages endpoint.
func (h *SettingsHandler) LoadCalendarDefaults(c *fiber.Ctx) (map[string]string, error) {
	return h.loadCalendarDefaults(c)
}

func (h *SettingsHandler) upsertSetting(c *fiber.Ctx, key string, raw []byte) error {
	_, err := h.db.ExecContext(c.UserContext(), `
		INSERT INTO platform_settings (key, value, updated_at)
		VALUES ($1, $2::jsonb, NOW())
		ON CONFLICT (key) DO UPDATE
		SET value = EXCLUDED.value, updated_at = NOW()
	`, key, string(raw))
	return err
}

func (h *SettingsHandler) loadCalendarDefaults(c *fiber.Ctx) (map[string]string, error) {
	var row platformSettingRow
	err := h.db.GetContext(c.UserContext(), &row, `
		SELECT key, value FROM platform_settings WHERE key = $1
	`, calendarDefaultsKey)
	if err != nil {
		// Table missing or empty — return seeded defaults.
		out := copyDefaults(i18n.DefaultCalendarDefaults)
		return out, nil
	}

	var parsed map[string]string
	if err := json.Unmarshal(row.Value, &parsed); err != nil {
		return nil, err
	}
	if parsed == nil {
		return nil, errors.New("empty calendar_defaults")
	}
	return parsed, nil
}

func (h *SettingsHandler) loadTheme(c *fiber.Ctx) (theme.Settings, error) {
	var row platformSettingRow
	err := h.db.GetContext(c.UserContext(), &row, `
		SELECT key, value FROM platform_settings WHERE key = $1
	`, theme.SettingsKey)
	if err != nil {
		return theme.Default(), nil
	}
	return theme.Parse(row.Value)
}

func copyDefaults(src map[string]string) map[string]string {
	out := make(map[string]string, len(src))
	for k, v := range src {
		out[k] = v
	}
	return out
}
