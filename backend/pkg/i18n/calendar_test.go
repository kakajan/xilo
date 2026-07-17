package i18n

import "testing"

func TestIsValidCalendarPreference(t *testing.T) {
	for _, v := range []string{CalendarAuto, CalendarJalali, CalendarGregorian} {
		if !IsValidCalendarPreference(v) {
			t.Fatalf("expected %q valid", v)
		}
	}
	if IsValidCalendarPreference("hijri") {
		t.Fatal("hijri must be invalid")
	}
}

func TestResolveCalendar(t *testing.T) {
	defaults := map[string]string{"fa": CalendarJalali, "en": CalendarGregorian}

	if got := ResolveCalendar(CalendarGregorian, "fa", defaults); got != CalendarGregorian {
		t.Fatalf("user override: got %s", got)
	}
	if got := ResolveCalendar(CalendarAuto, "fa", defaults); got != CalendarJalali {
		t.Fatalf("auto fa: got %s", got)
	}
	if got := ResolveCalendar("", "en", defaults); got != CalendarGregorian {
		t.Fatalf("empty en: got %s", got)
	}
	if got := ResolveCalendar(CalendarAuto, "fa", nil); got != CalendarJalali {
		t.Fatalf("nil defaults fa: got %s", got)
	}
}
