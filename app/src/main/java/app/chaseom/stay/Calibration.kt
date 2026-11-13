package app.chaseom.stay

import kotlin.math.abs

/**
 * Per-car calibration of the draw model from the car's own SOC drop during a parked stay.
 * Pure (no Android types), so every rule is a JVM test (CalibrationTest).
 *
 * Measurement: the SOC is an integer percent, so a drop measured from an arbitrary start is off by up to 1 %p. A
 * window therefore runs **from one SOC step-down to a later one** (edge to edge): both ends are moments the integer
 * changed, so the drop between them is exact up to the car's own rounding. A window counts only while:
 *  - the car is parked in a stay and capacity and SOC are known;
 *  - the climate setting (and so the model's kW) stays the same. Any change, a SOC rise (charging) or the end of
 *    the stay starts over;
 *  - the SOC moves down one step at a time (a jump of more than 1 %p is a set value, e.g. a Virtual Console script,
 *    and starts over), so scripted emulator scenes can never teach a factor;
 *  - the evidence is enough: at least [MIN_DROP_PCT] %p and [MIN_WINDOW_MS] between the two edges.
 * A window gives the ratio measured kW / model kW. Ratios outside [RAW_MIN]..[RAW_MAX] are thrown away as not climate
 * (another consumer, a SOC recalibration jump) rather than clamped.
 *
 * Applying: ratios are averaged, weighted by their %p drop. The total weight is capped at [MAX_WEIGHT_PCT] so a
 * later change in the car still moves the factor. The factor is clamped to [MIN_FACTOR]..[MAX_FACTOR]. A factor
 * below 1 makes the stay time **longer** than the model, so it is used only with strong evidence
 * ([STRONG_DROP_PCT] %p over [STRONG_MS] in total); before that the factor is held at 1 (the estimate keeps erring
 * short). Factors within [SHOW_EPSILON] of 1 are not applied or shown.
 *
 * The emulator's SOC never moves, so no window ever completes there and the demo numbers (167/146/162) do not change.
 */
class Calibration(saved: Saved = Saved()) {

    /** What is stored per car (one install = one car's head unit; no VIN is read). */
    data class Saved(
        /** Weighted mean of accepted ratios (measured / model). 1 = no evidence. */
        val ratio: Float = 1f,
        /** Total accepted drop, %p, capped at [MAX_WEIGHT_PCT]. */
        val weightPct: Float = 0f,
        /** Total accepted window time, ms (for the strong-evidence rule). */
        val evidenceMs: Long = 0L,
    )

    var saved: Saved = saved
        private set

    private var lastSoc: Int? = null
    private var lastKey: Any? = null
    private var edgeSoc: Int? = null
    private var edgeMs: Long? = null

    /** Factor the draw model is multiplied by. */
    val factor: Float
        get() {
            val s = saved
            if (s.weightPct <= 0f) return 1f
            var f = s.ratio.coerceIn(MIN_FACTOR, MAX_FACTOR)
            if (f < 1f && !strong(s)) f = 1f // longer only with strong evidence
            return if (abs(f - 1f) < SHOW_EPSILON) 1f else f
        }

    /** The factor differs from 1: the hero says "이 차 기준으로 보정됨 (추정)". */
    val applied: Boolean get() = factor != 1f

    /**
     * Feeds one parked update. [settingKey] is anything that equals itself only while the climate setting and the
     * model's inputs are the same (StayEngine passes the ClimateSetting). [modelKw] is the uncalibrated model draw.
     * @return true when [saved] changed (the caller persists it).
     */
    fun observe(soc: Int, capacityKwh: Float, settingKey: Any, modelKw: Float, nowMs: Long): Boolean {
        val prev = lastSoc
        val sameSetting = settingKey == lastKey
        lastSoc = soc
        lastKey = settingKey
        if (prev == null || !sameSetting || soc > prev || capacityKwh <= 0f || modelKw <= 0f) {
            edgeSoc = null; edgeMs = null // start over: first sample, new setting, charging, unusable inputs
            return false
        }
        if (soc == prev) return false
        if (prev - soc > 1) {
            // A real climate drain moves the integer SOC one step at a time (1 %p is ~0.7 kWh). A larger jump is a
            // set value (Virtual Console script, SOC recalibration): never evidence, start over at this value.
            edgeSoc = null; edgeMs = null
            return false
        }
        // A step down: the first one opens the window, a later one may close it.
        val startSoc = edgeSoc
        val startMs = edgeMs
        if (startSoc == null || startMs == null) {
            edgeSoc = soc; edgeMs = nowMs
            return false
        }
        val drop = startSoc - soc
        val dt = nowMs - startMs
        if (drop < MIN_DROP_PCT || dt < MIN_WINDOW_MS) return false
        // Window complete: the next one starts at this edge (windows never overlap).
        edgeSoc = soc; edgeMs = nowMs
        val measuredKw = drop / 100f * capacityKwh / (dt / 3_600_000f)
        val raw = measuredKw / modelKw
        if (raw !in RAW_MIN..RAW_MAX) return false
        saved = merge(saved, raw, drop.toFloat(), dt)
        return true
    }

    /** The stay ended: an open window is dropped (the saved factor stays). */
    fun endStay() {
        lastSoc = null; lastKey = null; edgeSoc = null; edgeMs = null
    }

    companion object {
        const val MIN_DROP_PCT = 2
        const val MIN_WINDOW_MS = 20 * 60_000L
        const val STRONG_DROP_PCT = 6f
        const val STRONG_MS = 60 * 60_000L
        const val MAX_WEIGHT_PCT = 20f
        const val MIN_FACTOR = 0.8f
        const val MAX_FACTOR = 1.5f
        const val RAW_MIN = 0.5f
        const val RAW_MAX = 2.5f
        const val SHOW_EPSILON = 0.02f

        fun strong(s: Saved): Boolean = s.weightPct >= STRONG_DROP_PCT && s.evidenceMs >= STRONG_MS

        /** Weighted mean; the old weight is scaled down so the total never exceeds [MAX_WEIGHT_PCT]. */
        fun merge(s: Saved, ratio: Float, weightPct: Float, ms: Long): Saved {
            val oldW = s.weightPct.coerceAtMost(MAX_WEIGHT_PCT - weightPct.coerceAtMost(MAX_WEIGHT_PCT)).coerceAtLeast(0f)
            val w = weightPct.coerceAtMost(MAX_WEIGHT_PCT)
            val mean = if (oldW + w <= 0f) 1f else (s.ratio * oldW + ratio * w) / (oldW + w)
            return Saved(mean, oldW + w, s.evidenceMs + ms)
        }
    }
}
