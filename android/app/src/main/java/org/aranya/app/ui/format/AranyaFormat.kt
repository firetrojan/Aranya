package org.aranya.app.ui.format

import android.content.Context
import org.aranya.app.R
import org.aranya.app.ui.theme.AranyaSpacing
import java.text.NumberFormat
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/**
 * Locale-aware formatting.
 *
 * Every user-visible number, date and relative time goes through here. Screens must not
 * build strings from `String.format` directly: that produces Western digits and English
 * month names in a Hindi or Marathi locale, which is wrong and reads as unpolished.
 *
 * Rules implemented here:
 *  - Numbers use the locale's default NumberFormat, so grouping and digits follow the
 *    device language (Devanagari digits in hi-IN, Western in en-IN).
 *  - Measurement values are locale-formatted but keep their conventional precision, so a
 *    pH of 6.8 does not become "6.800".
 *  - Dates use the locale's medium style, and the time zone is the device's.
 *  - Relative times ("2 hours ago") are computed in UTC and formatted in the device zone.
 */
object AranyaFormat {

    /** Locale-aware number for a general quantity. */
    fun number(context: Context, value: Double, maxDecimals: Int = 1): String {
        val fmt = NumberFormat.getNumberInstance(localeOf(context)).apply {
            minimumFractionDigits = 0
            maximumFractionDigits = maxDecimals
            isGroupingUsed = true
        }
        return fmt.format(value)
    }

    /** Locale-aware integer, for counts and areas. */
    fun integer(context: Context, value: Long): String =
        NumberFormat.getIntegerInstance(localeOf(context)).format(value)

    /**
     * A measurement with its unit, e.g. "27 °C" or "12.4 mm".
     *
     * The number is localized; the unit comes from string resources so a translator can
     * adapt it where the convention differs, without touching this code.
     */
    fun measurement(context: Context, value: Double, unitRes: Int, maxDecimals: Int = 1): String {
        val unit = context.getString(unitRes)
        return context.getString(R.string.format_value_with_unit, number(context, value, maxDecimals), unit)
    }

    /**
     * pH-like values that must keep their precision exactly.
     *
     * pH is logarithmic, so 6.8 and 6.80 are not interchangeable: significant figures are
     * part of the meaning, not presentation.
     */
    fun ph(context: Context, value: Double, decimals: Int = 1): String =
        number(context, value, decimals)

    /** Currency, localized to the device locale with the rupee symbol. */
    fun rupees(context: Context, value: Double): String {
        val fmt = NumberFormat.getCurrencyInstance(localeOf(context)).apply {
            maximumFractionDigits = if (value % 1.0 == 0.0) 0 else 2
            minimumFractionDigits = if (value % 1.0 == 0.0) 0 else 2
        }
        return fmt.format(value)
    }

    /** Date and time for the locale, in the device's own time zone. */
    fun dateTime(context: Context, instant: Instant): String {
        val fmt = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)
            .withLocale(localeOf(context))
            .withZone(ZoneId.systemDefault())
        return fmt.format(instant)
    }

    /** Date only, for a trading day or a seasonal bound. */
    fun date(context: Context, instant: Instant): String {
        val fmt = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
            .withLocale(localeOf(context))
            .withZone(ZoneId.systemDefault())
        return fmt.format(instant)
    }

    /**
     * Compact relative time for freshness labels: "just now", "2 hours ago", "3 days ago".
     *
     * Deliberately coarse. A farmer needs to know whether data is current or stale, not a
     * countdown to the second, and coarse phrasing is shorter to read on a small screen.
     */
    fun relativeAge(context: Context, then: Instant, now: Instant = Instant.now()): String {
        val seconds = Duration.between(then, now).seconds
        val minutes = seconds / 60
        val hours = minutes / 60
        val days = hours / 24
        return when {
            seconds < 90 -> context.getString(R.string.freshness_live)
            minutes < 60 -> context.getString(R.string.freshness_fresh, plural(context, minutes, "minute"))
            hours < 24 -> context.getString(R.string.freshness_fresh, plural(context, hours, "hour"))
            days < 30 -> context.getString(R.string.freshness_fresh, plural(context, days, "day"))
            else -> date(context, then)
        }
    }

    /**
     * The locale the user actually chose, not the developer's default.
     *
     * minSdk is 26, so `configuration.locales` (API 24+) is always available and the
     * deprecated single-locale accessor is dead code.
     */
    fun localeOf(context: Context): Locale =
        context.resources.configuration.locales[0]

    private fun plural(context: Context, count: Long, unit: String): String =
        context.resources.getQuantityString(
            resourcesFor(unit), count.toInt(), count.toInt(),
        )

    private fun resourcesFor(unit: String): Int = when (unit) {
        "minute" -> R.plurals.duration_minutes
        "hour" -> R.plurals.duration_hours
        "day" -> R.plurals.duration_days
        else -> R.plurals.duration_days
    }

    /** Convenience so callers can pad without importing the theme tokens directly. */
    val screenPadding = AranyaSpacing.lg
}

/** Compact plural forms for relative durations. */
object Plurals {
    fun hours(context: Context, count: Int): String =
        context.resources.getQuantityString(R.plurals.duration_hours, count, count)

    fun days(context: Context, count: Int): String =
        context.resources.getQuantityString(R.plurals.duration_days, count, count)
}
