package ir.xilo.app.core.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class DateFormatterTest {

    @Before
    fun setUp() {
        DateFormatter.setUserPreference(CalendarPreference.AUTO)
        DateFormatter.setPlatformDefaults(
            mapOf(
                "fa" to "jalali",
                "en" to "gregorian",
            )
        )
    }

    @Test
    fun resolve_autoUsesPlatformDefaultForFa() {
        assertEquals(CalendarSystem.JALALI, DateFormatter.resolve("fa"))
    }

    @Test
    fun resolve_userGregorianOverridesFaDefault() {
        DateFormatter.setUserPreference(CalendarPreference.GREGORIAN)
        assertEquals(CalendarSystem.GREGORIAN, DateFormatter.resolve("fa"))
    }

    @Test
    fun resolve_userJalaliOverridesEnDefault() {
        DateFormatter.setUserPreference(CalendarPreference.JALALI)
        assertEquals(CalendarSystem.JALALI, DateFormatter.resolve("en"))
    }

    @Test
    fun relative_recentIsPersianRelative() {
        val now = System.currentTimeMillis()
        fun fa(ts: Long) = DateFormatter.getRelativeTimeSpan(
            languageCode = "fa",
            timestampMs = ts,
            nowMs = now,
            justNow = "الان",
            minutesAgo = { "$it دقیقه پیش" },
            hoursAgo = { "$it ساعت پیش" },
            daysAgo = { "$it روز پیش" },
        )
        assertEquals("الان", fa(now - 10_000))
        assertEquals("۲ دقیقه پیش", fa(now - 120_000))
        assertEquals("۱ ساعت پیش", fa(now - 3_600_000))
        assertEquals("۳ روز پیش", fa(now - 3L * 24 * 3_600_000))
    }

    @Test
    fun relative_englishUsesLatinDigits() {
        val now = System.currentTimeMillis()
        val label = DateFormatter.getRelativeTimeSpan(
            languageCode = "en",
            timestampMs = now - 120_000,
            nowMs = now,
            justNow = "now",
            minutesAgo = { "$it minutes ago" },
            hoursAgo = { "$it hours ago" },
            daysAgo = { "$it days ago" },
        )
        assertEquals("2 minutes ago", label)
    }

    @Test
    fun absolute_jalaliDoesNotUseGregorianMayLabel() {
        DateFormatter.setUserPreference(CalendarPreference.JALALI)
        val cal = Calendar.getInstance(TimeZone.getDefault()).apply {
            set(2026, Calendar.MAY, 18, 12, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val formatted = DateFormatter.formatAbsolute(cal.timeInMillis, "d MMMM")
        assertFalse("unexpected Gregorian Persian May label: $formatted", formatted.contains("مه"))
        assertTrue("expected Jalali month in: $formatted", formatted.contains("اردیبهشت"))
    }

    @Test
    fun gregorianToJalali_knownDate() {
        val (jy, jm, jd) = DateFormatter.gregorianToJalali(2026, 5, 18)
        assertEquals(1405, jy)
        assertEquals(2, jm) // Ordibehesht
        assertEquals(28, jd)
    }
}
