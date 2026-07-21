package ir.xilo.app.core.util

import android.content.Context
import ir.xilo.app.R
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit

enum class CalendarPreference {
    AUTO,
    JALALI,
    GREGORIAN;

    fun apiValue(): String = when (this) {
        AUTO -> "auto"
        JALALI -> "jalali"
        GREGORIAN -> "gregorian"
    }

    companion object {
        fun fromApi(value: String?): CalendarPreference = when (value?.lowercase(Locale.US)) {
            "jalali" -> JALALI
            "gregorian" -> GREGORIAN
            else -> AUTO
        }
    }
}

enum class CalendarSystem {
    JALALI,
    GREGORIAN,
}

object DateFormatter {
    private const val UI_LOCALE = "fa"

    private val jalaliMonths = arrayOf(
        "فروردین", "اردیبهشت", "خرداد", "تیر", "مرداد", "شهریور",
        "مهر", "آبان", "آذر", "دی", "بهمن", "اسفند",
    )

    private val persianDigits = charArrayOf('۰', '۱', '۲', '۳', '۴', '۵', '۶', '۷', '۸', '۹')

    @Volatile
    private var userPreference: CalendarPreference = CalendarPreference.AUTO

    @Volatile
    private var platformDefaults: Map<String, CalendarSystem> = mapOf(
        "fa" to CalendarSystem.JALALI,
        "en" to CalendarSystem.GREGORIAN,
        "ar" to CalendarSystem.GREGORIAN,
        "ru" to CalendarSystem.GREGORIAN,
        "tr" to CalendarSystem.GREGORIAN,
    )

    fun setUserPreference(pref: CalendarPreference) {
        userPreference = pref
    }

    fun setUserPreferenceFromApi(value: String?) {
        userPreference = CalendarPreference.fromApi(value)
    }

    fun setPlatformDefaults(defaults: Map<String, String>) {
        val mapped = defaults.mapNotNull { (lang, cal) ->
            when (cal.lowercase(Locale.US)) {
                "jalali" -> lang to CalendarSystem.JALALI
                "gregorian" -> lang to CalendarSystem.GREGORIAN
                else -> null
            }
        }.toMap()
        if (mapped.isNotEmpty()) {
            platformDefaults = platformDefaults + mapped
        }
    }

    fun resolve(locale: String = UI_LOCALE): CalendarSystem {
        return when (userPreference) {
            CalendarPreference.JALALI -> CalendarSystem.JALALI
            CalendarPreference.GREGORIAN -> CalendarSystem.GREGORIAN
            CalendarPreference.AUTO ->
                platformDefaults[locale] ?: platformDefaults[UI_LOCALE] ?: CalendarSystem.GREGORIAN
        }
    }

    fun formatAbsolute(timestampMs: Long, pattern: String = "d MMMM"): String {
        val date = Date(timestampMs)
        return when (resolve()) {
            CalendarSystem.JALALI -> formatJalali(date, includeYear = pattern.contains("y", ignoreCase = true))
            CalendarSystem.GREGORIAN -> {
                val fmt = if (pattern.contains("y", ignoreCase = true)) "d MMM yyyy" else "d MMM"
                toPersianDigits(SimpleDateFormat(fmt, Locale.forLanguageTag("fa")).format(date))
            }
        }
    }

    fun formatTime(timestampMs: Long): String {
        return toPersianDigits(
            SimpleDateFormat("H:mm", Locale.forLanguageTag("fa")).format(Date(timestampMs))
        )
    }

    /** Absolute date with hour:minute, e.g. «۳۰ تیر ۱۴۰۵، ۱۴:۳۰». */
    fun formatDateTime(timestampMs: Long): String {
        val datePart = formatAbsolute(timestampMs, "d MMMM yyyy")
        val timePart = formatTime(timestampMs)
        return "$datePart، $timePart"
    }

    fun getRelativeTimeSpan(
        context: Context,
        timestampMs: Long,
        nowMs: Long = System.currentTimeMillis(),
    ): String {
        val localized = AppLocale.wrap(context)
        return getRelativeTimeSpan(
            languageCode = AppLocale.languageCode(context),
            timestampMs = timestampMs,
            nowMs = nowMs,
            justNow = localized.getString(R.string.time_just_now),
            minutesAgo = { localized.getString(R.string.time_minutes_ago, it) },
            hoursAgo = { localized.getString(R.string.time_hours_ago, it) },
            daysAgo = { localized.getString(R.string.time_days_ago, it) },
        )
    }

    /** Testable relative-time formatting without Android resource inflation. */
    internal fun getRelativeTimeSpan(
        languageCode: String,
        timestampMs: Long,
        nowMs: Long = System.currentTimeMillis(),
        justNow: String,
        minutesAgo: (String) -> String,
        hoursAgo: (String) -> String,
        daysAgo: (String) -> String,
    ): String {
        val diff = (nowMs - timestampMs).coerceAtLeast(0)
        val seconds = TimeUnit.MILLISECONDS.toSeconds(diff)
        val minutes = TimeUnit.MILLISECONDS.toMinutes(diff)
        val hours = TimeUnit.MILLISECONDS.toHours(diff)
        val days = TimeUnit.MILLISECONDS.toDays(diff)
        // Persian digits are for fa UI only; ar/other locales keep Western digits.
        val usePersianDigits = languageCode.equals("fa", ignoreCase = true)

        fun count(value: Long): String =
            if (usePersianDigits) toPersianDigits(value.toString()) else value.toString()

        return when {
            seconds < 60 -> justNow
            minutes < 60 -> minutesAgo(count(minutes))
            hours < 24 -> hoursAgo(count(hours))
            days < 7 -> daysAgo(count(days))
            else -> formatDateTime(timestampMs)
        }
    }

    private fun formatJalali(date: Date, includeYear: Boolean): String {
        val cal = Calendar.getInstance(TimeZone.getDefault()).apply { time = date }
        val (jy, jm, jd) = gregorianToJalali(
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH) + 1,
            cal.get(Calendar.DAY_OF_MONTH),
        )
        val day = toPersianDigits(jd.toString())
        val month = jalaliMonths[jm - 1]
        return if (includeYear) {
            "$day $month ${toPersianDigits(jy.toString())}"
        } else {
            "$day $month"
        }
    }

    /** Convert Gregorian Y/M/D to Jalali Y/M/D. */
    internal fun gregorianToJalali(gy: Int, gm: Int, gd: Int): Triple<Int, Int, Int> {
        val gdm = intArrayOf(0, 31, 59, 90, 120, 151, 181, 212, 243, 273, 304, 334)
        val gy2 = if (gm > 2) gy + 1 else gy
        var days = 355666 + (365 * gy) + ((gy2 + 3) / 4) - ((gy2 + 99) / 100) +
            ((gy2 + 399) / 400) + gd + gdm[gm - 1]
        var jy = -1595 + (33 * (days / 12053))
        days %= 12053
        jy += 4 * (days / 1461)
        days %= 1461
        if (days > 365) {
            jy += (days - 1) / 365
            days = (days - 1) % 365
        }
        val jm: Int
        val jd: Int
        if (days < 186) {
            jm = 1 + days / 31
            jd = 1 + days % 31
        } else {
            jm = 7 + (days - 186) / 30
            jd = 1 + (days - 186) % 30
        }
        return Triple(jy, jm, jd)
    }

    private fun toPersianDigits(input: String): String {
        val sb = StringBuilder(input.length)
        for (ch in input) {
            sb.append(if (ch in '0'..'9') persianDigits[ch - '0'] else ch)
        }
        return sb.toString()
    }
}
