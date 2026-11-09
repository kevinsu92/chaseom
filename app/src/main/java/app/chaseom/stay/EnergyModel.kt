package app.chaseom.stay

import kotlin.math.floor
import kotlin.math.roundToInt

/** Climate settings that drive the estimated draw. */
data class ClimateSetting(
    val powerOn: Boolean,
    val setTempC: Float?,
    val fanSpeed: Int?,
    val acOn: Boolean?,
    val recircOn: Boolean? = null,
    val driverSeatHeat: Int?,
)

/**
 * Draw model for a parked, powered EV. Every number on screen built from it is shown as "(추정)".
 *
 * Neither the Pleos SDK nor AAOS exposes per-item climate power and the emulator does not report EV
 * efficiency, so the app multiplies the car's own climate state by a published coefficient table.
 * Sources and grades: docs/ENERGY_MODEL.md (same table as the proposal's "숫자 근거표",
 * 01_strategy PRODUCT_SPEC 4.2, QA COEFF judgement). Heating assumes -10 C outside (no outside
 * temperature signal), so the app labels the estimate "영하 10°C 기준".
 */
object EnergyModel {
    /** Base load while parked with the car on: INL accessory-off auxiliary load proxy + margin. */
    const val BASE_KW = 0.30f
    /** Cabin heating at a 22 C set point (ADAC 2022, -10 C outside, upper value of 1.5-2.3 kWh/h). */
    const val HEAT_KW_AT_22C = 2.3f
    /** Heating scales with (set + 10) / 32: steady-state loss ~ inside-outside difference, -10 C outside. */
    const val HEAT_OUTSIDE_C = -10f
    const val HEAT_REFERENCE_DELTA = 32f
    /** Cooling with A/C on, replaces heating (owner reports 1-2 kW, upper value). */
    const val AC_KW = 2.0f
    /** Recirculation while heating: steady heating load 4.32 -> 3.29 kW (-24 %). */
    const val RECIRC_HEAT_FACTOR = 0.76f
    /** Blower per fan step (0..8): small add-on, measured totals already include the blower. */
    const val FAN_KW_PER_STEP = 0.03f
    /** One seat heater, levels 1/2/3 (bench 48.6 W / 66 W, level 3 ~100 W). */
    val SEAT_HEAT_KW = floatArrayOf(0f, 0.049f, 0.066f, 0.100f)

    /** Conservative driving efficiency (km/kWh); a lower vehicle-reported value wins. */
    const val DEFAULT_KM_PER_KWH = 5.0f
    /** Keep share = tomorrow's need x 1.2 (S1-b, "여유분 20%"). */
    const val KEEP_FACTOR = 1.2f

    /** Display cap (QA L-A2): above this the hero says "3시간 이상" (QA M-1). */
    const val DISPLAY_CAP_MINUTES = 180

    fun efficiency(vehicleKmPerKwh: Float?): Float =
        vehicleKmPerKwh?.takeIf { it in 1f..15f }?.let { minOf(it, DEFAULT_KM_PER_KWH) } ?: DEFAULT_KM_PER_KWH

    fun seatKw(level: Int?): Float = SEAT_HEAT_KW[(level ?: 0).coerceIn(0, 3)]

    fun drawKw(s: ClimateSetting): Float {
        val seat = seatKw(s.driverSeatHeat)
        if (!s.powerOn) return BASE_KW + seat
        val fan = (s.fanSpeed ?: 0).coerceIn(0, 8) * FAN_KW_PER_STEP
        val cabin = if (s.acOn == true) {
            AC_KW
        } else {
            val t = s.setTempC ?: 22f
            val heat = (HEAT_KW_AT_22C * (t - HEAT_OUTSIDE_C) / HEAT_REFERENCE_DELTA).coerceAtLeast(0f)
            if (s.recircOn == true) heat * RECIRC_HEAT_FACTOR else heat
        }
        return BASE_KW + cabin + fan + seat
    }

    fun keepPercent(keepKm: Int, capacityKwh: Float, kmPerKwh: Float): Float {
        if (capacityKwh <= 0f) return 100f
        return (keepKm / kmPerKwh * KEEP_FACTOR / capacityKwh * 100f).coerceIn(0f, 100f)
    }

    /** Minutes until SOC falls to the keep share at [drawKw]; 0 when already at/below it. */
    fun minutesToKeep(socPercent: Int, keepPercent: Float, capacityKwh: Float, drawKw: Float): Int {
        val availableKwh = (socPercent - keepPercent) / 100f * capacityKwh
        if (availableKwh <= 0f || drawKw <= 0f) return 0
        return floor(availableKwh / drawKw * 60f).toInt()
    }

    /** kWh used between two SOC readings, one decimal for display. */
    fun usedKwh(socStart: Int, socNow: Int, capacityKwh: Float): String {
        val kwh = ((socStart - socNow).coerceAtLeast(0) / 100f * capacityKwh * 10f).roundToInt() / 10f
        return kwh.toString()
    }

    /** Distance the current charge still covers with the same keep factor (receipt "30km 중 약 24km"). */
    fun coveredKm(socPercent: Int, capacityKwh: Float, kmPerKwh: Float): Int =
        floor(socPercent / 100f * capacityKwh * kmPerKwh / KEEP_FACTOR).toInt().coerceAtLeast(0)

    /** H·X: X1..X3 (04_design/FLOWS.md 4), biggest gain >= [MIN_GAIN_MINUTES], skipping [dismissed]. */
    fun exchange(
        soc: Int, capacityKwh: Float, keepPercent: Float, current: ClimateSetting, dismissed: Set<ExchangeRule>,
    ): Exchange? {
        // QA M-11: gains are measured on the 3-hour-capped values. Already at/above the cap -> gain 0 -> no card.
        fun minutes(s: ClimateSetting) = minutesToKeep(soc, keepPercent, capacityKwh, drawKw(s)).coerceAtMost(DISPLAY_CAP_MINUTES)
        val base = minutes(current)
        val temp = current.setTempC
        val candidates = buildList {
            if (current.powerOn && temp != null && temp >= 23f && (current.driverSeatHeat ?: 0) < 2) {
                add(Exchange(ExchangeRule.X1_SEAT_HEAT, minutes(current.copy(setTempC = 21f, driverSeatHeat = 2)) - base))
            }
            if ((current.fanSpeed ?: 0) >= 5) {
                add(Exchange(ExchangeRule.X2_FAN, minutes(current.copy(fanSpeed = 3)) - base))
            }
            if (current.powerOn && current.acOn == true && temp != null && temp >= 23f) {
                add(Exchange(ExchangeRule.X3_AC, minutes(current.copy(acOn = false)) - base))
            }
        }
        return candidates.filter { it.rule !in dismissed && it.gainMinutes >= MIN_GAIN_MINUTES }
            .maxByOrNull { it.gainMinutes }
    }

    const val MIN_GAIN_MINUTES = 10
}
