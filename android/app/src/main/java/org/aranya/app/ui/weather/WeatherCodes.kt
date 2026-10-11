package org.aranya.app.ui.weather

import androidx.annotation.StringRes
import org.aranya.app.R

/**
 * WMO 4677 present-weather codes, as returned in Open-Meteo's `weather_code` field.
 *
 * Mapped to localized strings rather than shipped English, because a farmer needs to
 * understand the sky state without reading English. Unknown codes return null rather than a
 * guess: an unrecognised code must not be presented as "clear sky".
 */
object WeatherCodes {

    @StringRes
    fun stringFor(code: Int?): Int? = when (code) {
        0 -> R.string.wmo_0
        1 -> R.string.wmo_1
        2 -> R.string.wmo_2
        3 -> R.string.wmo_3
        45 -> R.string.wmo_45
        48 -> R.string.wmo_48
        51 -> R.string.wmo_51
        53 -> R.string.wmo_53
        55 -> R.string.wmo_55
        56 -> R.string.wmo_56
        57 -> R.string.wmo_57
        61 -> R.string.wmo_61
        63 -> R.string.wmo_63
        65 -> R.string.wmo_65
        66 -> R.string.wmo_66
        67 -> R.string.wmo_67
        71 -> R.string.wmo_71
        73 -> R.string.wmo_73
        75 -> R.string.wmo_75
        77 -> R.string.wmo_77
        80 -> R.string.wmo_80
        81 -> R.string.wmo_81
        82 -> R.string.wmo_82
        85 -> R.string.wmo_85
        86 -> R.string.wmo_86
        95 -> R.string.wmo_95
        96 -> R.string.wmo_96
        99 -> R.string.wmo_99
        else -> null
    }

    /**
     * Semantic grouping, used to pick a colour and to mark which days matter for field
     * work. Deliberately coarse: a farmer deciding whether to spray cares about rain, not
     * the distinction between drizzle and light rain.
     */
    enum class Kind { CLEAR, CLOUD, FOG, DRIZZLE, RAIN, SNOW, STORM, UNKNOWN }

    fun kindFor(code: Int?): Kind = when (code) {
        0, 1 -> Kind.CLEAR
        2, 3 -> Kind.CLOUD
        45, 48 -> Kind.FOG
        51, 53, 55, 56, 57 -> Kind.DRIZZLE
        61, 63, 65, 66, 67, 80, 81, 82 -> Kind.RAIN
        71, 73, 75, 77, 85, 86 -> Kind.SNOW
        95, 96, 99 -> Kind.STORM
        else -> Kind.UNKNOWN
    }

    /** True when a farmer may reasonably be told to avoid field work. */
    fun isDisruptive(code: Int?): Boolean = when (kindFor(code)) {
        Kind.STORM -> true
        Kind.RAIN -> true
        Kind.FOG -> true
        else -> false
    }
}
