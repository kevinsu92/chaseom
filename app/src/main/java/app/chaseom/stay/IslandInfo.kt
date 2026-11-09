package app.chaseom.stay

/**
 * Round 1b "information island" (CEO 2026-11-10, ROUND1_REDO; QA redo/qa.md 2-A): what each picture element
 * means, computed from the same StayUi as the numbers and kept apart from drawing, so the confirmed 04_design
 * layers can be swapped in without touching the rules. One element = one meaning (C-3):
 *
 *  - sun size   = today's stay time (H-1: size only, never lower toward the horizon; H-2: >= 3 h is one step)
 *  - water step = battery SOC (W-1..W-5: max 5 steps, changes only when a step is crossed; the boat always floats)
 *  - keep line  = tomorrow's share as a water step (W-4: amber dashed line, never red)
 *  - crates     = tomorrow's share is on board; fixed count (MOTION_PRINCIPLES v3 3.1), outline at K1 / K2
 *  - weather    = climate setting (wind = fan, cool = A/C, cloud = recirculation, warm rocks = seat heat)
 *  - tempLevel  = set temperature, freed from the sun by H-4; 04_design assigns it to a new element
 *
 * Steps, not continuous values (W-3 / H-3): a change plays one transition only when the step changes.
 * Thresholds are named so the design can tune them; tests pin the rules, not the numbers' look.
 */
data class IslandInfo(
    /** 0..[SUN_STEPS]-1; the last step = 3 hours or more, or climate off (base load only). */
    val sunStep: Int,
    /** 0..[WATER_STEPS]-1 from SOC. */
    val waterStep: Int,
    /** Water step of tomorrow's share (the amber line); null when unknown. */
    val keepStep: Int?,
    /** K1 near / K2 below tomorrow's share: crate outline (amber). The island never looks better at K2 (C-4). */
    val crateOutline: Boolean,
    val wind: Int,
    val cool: Boolean,
    val cloud: Boolean,
    val heatDrv: Int,
    val heatPas: Int,
    /** 0 low (< 20 C or off), 1 middle, 2 high (>= 24.5 C); same thresholds as the old sun. */
    val tempLevel: Int,
) {
    companion object {
        const val SUN_STEPS = 5
        /** Upper bounds (minutes, exclusive) of sun steps 0..3; >= the last bound is step 4 (= 3 hours or more). */
        val SUN_BOUNDS_MIN = intArrayOf(30, 60, 120, EnergyModel.DISPLAY_CAP_MINUTES)
        const val WATER_STEPS = 5
        /** Upper bounds (SOC %, exclusive) of water steps 0..3. */
        val WATER_BOUNDS_SOC = intArrayOf(20, 40, 60, 80)

        fun sunStep(hero: Hero): Int = when {
            hero.belowKeep -> 0 // K2: smallest sun, never a "good" picture (C-4)
            hero.hvacOff -> SUN_STEPS - 1
            else -> stepOf(hero.minutes ?: 0, SUN_BOUNDS_MIN)
        }

        fun waterStep(socPercent: Float): Int = stepOf(socPercent.toInt(), WATER_BOUNDS_SOC)

        private fun stepOf(v: Int, bounds: IntArray): Int = bounds.indexOfFirst { v < it }.let { if (it < 0) bounds.size else it }

        /** Null when there is no island (not parked, reduced mode, no hero) - same gate as [Island.state]. */
        fun of(ui: StayUi): IslandInfo? {
            if (!ui.parked || !ui.policy.energyBudget) return null
            val hero = ui.hero ?: return null
            val bar = ui.bar
            val c = ui.climate
            val power = c?.powerOn == true
            return IslandInfo(
                sunStep = sunStep(hero),
                waterStep = bar?.let { waterStep(it.socPercent.toFloat()) } ?: 0,
                keepStep = bar?.let { waterStep(it.keepPercent) },
                crateOutline = ui.keepBanner != null,
                wind = Island.wind(power, c?.fanSpeed),
                cool = power && c?.acOn == true,
                cloud = power && c?.recircOn == true,
                heatDrv = (c?.driverSeatHeat ?: 0).coerceIn(0, 3),
                heatPas = (ui.passengerSeatHeat ?: 0).coerceIn(0, 3),
                tempLevel = Island.sun(power, c?.setTempC).ordinal,
            )
        }
    }
}
