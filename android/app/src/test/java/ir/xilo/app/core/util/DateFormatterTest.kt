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
        assertEquals("الان", DateFormatter.getRelativeTimeSpan(now - 10_000, now))
        assertEquals("۲ دقیقه پیش", DateFormatter.getRelativeTimeSpan(now - 120_000, now))
        assertEquals("۱ ساعت پیش", DateFormatter.getRelativeTimeSpan(now - 3_600_000, now))
        assertEquals("۳ روز پیش", DateFormatter.getRelativeTimeSpan(now - 3L * 24 * 3_600_000, now))
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
