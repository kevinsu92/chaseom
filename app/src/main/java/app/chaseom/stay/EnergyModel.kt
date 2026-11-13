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
    /** Outside temperature from the car (AAOS ENV_OUTSIDE_TEMPERATURE); how it is used depends on [outsideMode]. */
    val outsideC: Float? = null,
    /** Round 2 (d): which outside-temperature rule the heating term uses. Default is the build G rule. */
    val outsideMode: OutsideMode = OutsideMode.COLD_ONLY,
    /**
     * H2 item 10: the passenger zone and the passenger seat heater count only when [passengerCounted] (a front
     * passenger is detected, or the passenger zone is unsynced from the driver). Otherwise both are ignored, as before.
     */
    val passengerCounted: Boolean = false,
    val passengerSeatHeat: Int? = null,
    /** The passenger zone is unsynced (HVAC dual on). Only then can its set temperature differ from the driver's. */
    val passengerZoneUnsynced: Boolean = false,
    /** Passenger zone set temperature; used only when [passengerZoneUnsynced] and warmer than the driver's. */
    val passengerSetTempC: Float? = null,
)

/**
 * How the heating term uses the car's outside temperature.
 *  - [COLD_ONLY] (build G, default): only a reading colder than -10 C; otherwise -10 C.
 *  - [FULL] (round 2 (d), behind BuildConfig.OUTSIDE_FULL, off by default): any valid reading, clamped to
 *    [EnergyModel.FULL_OUTSIDE_MAX_C] so a mild or hot reading never drops heating below the mild-day evidence.
 *    Unreadable or out of range is -10 C in both modes.
 */
enum class OutsideMode { COLD_ONLY, FULL }

/**
 * Draw model for a parked, powered EV. Every number on screen built from it is shown as "(추정)".
 *
 * Neither the Pleos SDK nor AAOS exposes per-item climate power and the emulator does not report EV
 * efficiency, so the app multiplies the car's own climate state by a published coefficient table.
 * Sources and grades: docs/ENERGY_MODEL.md (same table as the proposal's "숫자 근거표").
 * Heating scales with the inside-outside difference. Build G reads the car's outside temperature
 * (ENV_OUTSIDE_TEMPERATURE) and uses it only when it is colder than -10 C; otherwise -10 C.
 */
object EnergyModel {
    /** Base load while parked with the car on: INL accessory-off auxiliary load proxy + margin. */
    const val BASE_KW = 0.30f
    /** Cabin heating at a 22 C set point. ADAC 2022 (-10 C outside) holds 20 C with 1.5-2.0 kW; the app takes
     *  more (2.3 kW) so the stay time errs short. (2.3 kWh was an ID.3 warm-up energy, not a holding power.) */
    const val HEAT_KW_AT_22C = 2.3f
    /** Heating scales with (set - outside) / 32: steady-state loss ~ inside-outside difference.
     *  32 = 22 C set - (-10 C) outside, the condition the 2.3 kW coefficient was taken at. */
    const val HEAT_OUTSIDE_C = -10f
    /** Outside readings outside this range are treated as a sensor fault (fall back to [HEAT_OUTSIDE_C]). */
    const val OUTSIDE_MIN_C = -40f
    const val OUTSIDE_MAX_C = 60f

    fun validOutsideC(c: Float?): Float? = c?.takeIf { !it.isNaN() && it in OUTSIDE_MIN_C..OUTSIDE_MAX_C }

    /**
     * The outside temperature the heating term uses: the car's reading only when it is colder than -10 C
     * (round 1 "middle option"). A milder reading is not used to lengthen the stay time: the coefficient
     * was taken at -10 C, the linear model is unverified on mild days, and the estimate must err short.
     * Unreadable or out of range -> -10 C.
     */
    fun heatOutsideC(s: ClimateSetting): Float {
        val reading = validOutsideC(s.outsideC) ?: return HEAT_OUTSIDE_C
        return when (s.outsideMode) {
            OutsideMode.COLD_ONLY -> minOf(reading, HEAT_OUTSIDE_C)
            OutsideMode.FULL -> minOf(reading, FULL_OUTSIDE_MAX_C)
        }
    }

    /**
     * [OutsideMode.FULL] upper clamp. Above it the heating term stays at its 15 C value (22 C set: 0.50 kW).
     * Why 15 C: the only mild-day record (ENERGY_MODEL.md M1, Ioniq 5 N, 23 C set, about 0.48 kW total) must
     * still come out larger in the app. At 15 C a 23 C set gives 0.30 + 0.575 + 0.09 = 0.97 kW (2.0x M1).
     * Without the clamp a reading at or above the set temperature gives heating 0 (0.39 kW, 0.81x M1: errs long).
     */
    const val FULL_OUTSIDE_MAX_C = 15f
    const val HEAT_REFERENCE_DELTA = 32f
    /** Cooling with A/C on (owner reports 1-2 kW, upper value). With heating demand the larger of the two counts. */
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

    /** Display cap: above this the hero says "3시간 이상". */
    const val DISPLAY_CAP_MINUTES = 180

    fun efficiency(vehicleKmPerKwh: Float?): Float =
        vehicleKmPerKwh?.takeIf { it in 1f..15f }?.let { minOf(it, DEFAULT_KM_PER_KWH) } ?: DEFAULT_KM_PER_KWH

    fun seatKw(level: Int?): Float = SEAT_HEAT_KW[(level ?: 0).coerceIn(0, 3)]

    fun drawKw(s: ClimateSetting): Float {
        val seat = seatKw(s.driverSeatHeat) + if (s.passengerCounted) seatKw(s.passengerSeatHeat) else 0f
        if (!s.powerOn) return BASE_KW + seat
        val fan = (s.fanSpeed ?: 0).coerceIn(0, 8) * FAN_KW_PER_STEP
        val heat = heatKw(s)
        // H2 (external review §11 item 7): with A/C on, heating is still counted when the cabin needs heat. Before,
        // A/C replaced heating with 2.0 kW, so a winter A/C+heat stay read 13-53 % long (2.3-3.4 kW heating vs 2.0).
        // max(heat, cool), not heat + a compressor share: it closes exactly that gap with the two sourced terms and
        // adds no new coefficient (no source gives the compressor share of a dehumidifying winter run).
        val cabin = when {
            s.acOn != true -> heat
            heatingDemand(s) -> maxOf(heat, AC_KW)
            else -> AC_KW
        }
        return BASE_KW + cabin + fan + seat
    }

    /** Heating term at the set temperature (recirculation applied); the outside rule is [heatOutsideC]. */
    fun heatKw(s: ClimateSetting): Float {
        val t = heatSetC(s)
        val heat = (HEAT_KW_AT_22C * (t - heatOutsideC(s)) / HEAT_REFERENCE_DELTA).coerceAtLeast(0f)
        return if (s.recircOn == true) heat * RECIRC_HEAT_FACTOR else heat
    }

    /**
     * The cabin needs heat: the car's outside reading is below the set temperature. With no valid reading the
     * season is unknown, so it counts as true and A/C takes the larger term (the estimate errs short).
     * A summer reading (outside at or above the set temperature) keeps cooling alone, as before.
     */
    fun heatingDemand(s: ClimateSetting): Boolean {
        val outside = validOutsideC(s.outsideC) ?: return true
        return outside < heatSetC(s)
    }

    /**
     * Set temperature the heating term uses. The 2.3 kW coefficient holds the whole cabin at one set point (zones
     * synced; a synced passenger zone follows the driver, so its own reading is ignored). An unsynced passenger zone
     * set warmer than the driver's raises it to the two zones' mean.
     * A colder passenger zone is not used to lower it: the estimate keeps erring short.
     */
    fun heatSetC(s: ClimateSetting): Float {
        val t = s.setTempC ?: 22f
        val p = s.passengerSetTempC
        return if (s.passengerZoneUnsynced && p != null && p > t) (t + p) / 2f else t
    }

    fun keepPercent(keepKm: Int, capacityKwh: Float, kmPerKwh: Float): Float {
        if (capacityKwh <= 0f) return 100f
        return (keepKm / kmPerKwh * KEEP_FACTOR / capacityKwh * 100f).coerceIn(0f, 100f)
    }

    /**
     * Change of the stay time, in whole percent, when the draw goes from [beforeKw] to [afterKw] on the same
     * battery: before / after - 1. Independent of the battery level, so it is used above the 3-hour display cap.
     * null when the change is under 1 % (or a draw is not positive).
     */
    fun stayRatePercent(beforeKw: Float, afterKw: Float): Int? {
        if (beforeKw <= 0f || afterKw <= 0f) return null
        val pct = ((beforeKw / afterKw - 1f) * 100f).roundToInt()
        return pct.takeIf { kotlin.math.abs(it) >= 1 }
    }

    /** Largest stay-rate change written as a number; beyond it the toast says "±50% 이상". */
    const val STAY_RATE_SHOWN_MAX = 50

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

    /** H·X: X1..X3, biggest gain >= [MIN_GAIN_MINUTES], skipping [dismissed]. */
    fun exchange(
        soc: Int, capacityKwh: Float, keepPercent: Float, current: ClimateSetting, dismissed: Set<ExchangeRule>,
        /** H+calib: the per-car factor (Calibration.factor); 1 = model only. */
        factor: Float = 1f,
    ): Exchange? {
        // Gains are measured on the 3-hour-capped values. Already at/above the cap -> gain 0 -> no card.
        fun minutes(s: ClimateSetting) = minutesToKeep(soc, keepPercent, capacityKwh, drawKw(s) * factor).coerceAtMost(DISPLAY_CAP_MINUTES)
        val base = minutes(current)
        // Round 2 (d): the K1 banner (under NEAR_KEEP_MINUTES) and a swap card never show together (Small screen).
        // Under the build G rule no card qualifies there anyway (OutsideTempGapTest); in OutsideMode.FULL, A/C off on
        // a mild day can (0 C, 23 C, A/C on, SOC 11 %: X3 +12 min at 25 min left), so the rule is now explicit.
        if (base < NEAR_KEEP_MINUTES) return null
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
    /** Below this many minutes left the K1 "near tomorrow's share" banner shows (StayEngine). */
    const val NEAR_KEEP_MINUTES = 30
}
