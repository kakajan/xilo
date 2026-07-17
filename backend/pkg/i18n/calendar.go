package i18n

const (
	CalendarAuto      = "auto"
	CalendarJalali    = "jalali"
	CalendarGregorian = "gregorian"
)

// DefaultCalendarDefaults is the seeded platform map (fa → jalali).
var DefaultCalendarDefaults = map[string]string{
	"fa": CalendarJalali,
	"en": CalendarGregorian,
	"ar": CalendarGregorian,
	"ru": CalendarGregorian,
	"tr": CalendarGregorian,
}

func IsValidCalendarPreference(value string) bool {
	switch value {
	case CalendarAuto, CalendarJalali, CalendarGregorian:
		return true
	default:
		return false
	}
}

func IsValidCalendarSystem(value string) bool {
	switch value {
	case CalendarJalali, CalendarGregorian:
		return true
	default:
		return false
	}
}

// ResolveCalendar returns jalali or gregorian from user preference + locale defaults.
func ResolveCalendar(userPref string, locale string, defaults map[string]string) string {
	if userPref == CalendarJalali || userPref == CalendarGregorian {
		return userPref
	}
	if defaults != nil {
		if c, ok := defaults[locale]; ok && IsValidCalendarSystem(c) {
			return c
		}
	}
	if c, ok := DefaultCalendarDefaults[locale]; ok {
		return c
	}
	return CalendarGregorian
}
