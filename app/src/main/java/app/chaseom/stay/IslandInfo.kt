package app.chaseom.stay

/**
 * Round 1b "information island" v2.1 (04_design island_v2/ISLAND_REDESIGN.md v2.1, QA REVIEW_ISLAND_REDESIGN_v2
 * PASS): what each picture element means, computed from the same StayUi as the numbers and kept apart from
 * drawing, so the design layers can be swapped without touching the rules. One element = one meaning:
 *
 *  - sun size  = set temperature (small < 20 C or climate off, middle, large >= 24.5 C; height fixed)
 *  - buoys     = today's stay time: floor(minutes / 30), at most 6 (3 hours or more stays 6). The picture never
 *                says more than the number (2:26 -> 4, 1:28 -> 2, under 30 min -> 0). K2 or no hero -> 0.
 *  - water     = battery SOC in 5 steps (1-20 / 21-40 / 41-60 / 61-80 / 81-100 %) with a 1 %p hysteresis that
 *                only ever shows less: down at once at 20 / 40 / 60 / 80, up one point late at 22 / 42 / 62 / 82
 *                (QA ROUND1B_GATE check 2). The picture step is never above the step the SOC belongs to.
 *                The boat always floats.
 *  - crates    = tomorrow's share: always 3, amber outline at K1 / K2
 *  - weather   = climate (wind = fan, cool = A/C, cloud = recirculation, warm rocks = seat heat)
 */
data class IslandInfo(
    /** 0 small, 1 middle, 2 large. */
    val sunLevel: Int,
    /** 0..[MAX_BUOYS]. */
    val buoys: Int,
    /** 0..[WATER_STEPS]-1. */
    val waterStep: Int,
    val crateOutline: Boolean,
    val wind: Int,
    val cool: Boolean,
    val cloud: Boolean,
    val heatDrv: Int,
    val heatPas: Int,
) {
    companion object {
        const val MAX_BUOYS = 6
        const val MINUTES_PER_BUOY = 30
        const val WATER_STEPS = 5
        /** SOC at which the water goes up into step i+1 (from step i): one point after the step starts. */
        val WATER_UP_AT = intArrayOf(22, 42, 62, 82)
        /** SOC at or below which the water goes down into step i (from step i+1): the step's own top, at once. */
        val WATER_DOWN_AT = intArrayOf(20, 40, 60, 80)

        /** QA H-1: rounded down, so the buoys never promise more than the hero number. */
        fun buoys(hero: Hero?): Int = when {
            hero == null || hero.belowKeep -> 0
            hero.minutes != null -> (hero.minutes / MINUTES_PER_BUOY).coerceIn(0, MAX_BUOYS)
            hero.hvacOff -> MAX_BUOYS // climate off: base load only, the hero says "공조 꺼짐"
            else -> 0
        }

        /** First reading (no previous step): plain 20 %-wide steps. */
        fun waterStepFresh(soc: Int): Int = when {
            soc <= 20 -> 0
            soc <= 40 -> 1
            soc <= 60 -> 2
            soc <= 80 -> 3
            else -> 4
        }

        /** Next water step from [previous] with the 1 %p hysteresis (QA L-2, never above [waterStepFresh]). */
        fun waterStep(soc: Int, previous: Int?): Int {
            var s = previous?.coerceIn(0, WATER_STEPS - 1) ?: return waterStepFresh(soc)
            while (s < WATER_STEPS - 1 && soc >= WATER_UP_AT[s]) s++
            while (s > 0 && soc <= WATER_DOWN_AT[s - 1]) s--
            return s
        }

        /** Null when there is no island (not parked, reduced mode, no hero) - same gate as [Island.state]. */
        fun of(ui: StayUi, previousWaterStep: Int? = null): IslandInfo? {
            if (!ui.parked || !ui.policy.energyBudget) return null
            val hero = ui.hero ?: return null
            val c = ui.climate
            val power = c?.powerOn == true
            return IslandInfo(
                sunLevel = Island.sun(power, c?.setTempC).ordinal,
                buoys = buoys(hero),
                waterStep = ui.bar?.let { waterStep(it.socPercent, previousWaterStep) } ?: 0,
                crateOutline = ui.keepBanner != null,
                wind = Island.wind(power, c?.fanSpeed),
                cool = power && c?.acOn == true,
                cloud = power && c?.recircOn == true,
                heatDrv = (c?.driverSeatHeat ?: 0).coerceIn(0, 3),
                heatPas = (ui.passengerSeatHeat ?: 0).coerceIn(0, 3),
            )
        }
    }
}

/** Holds the last water step so the hysteresis works across updates; reset when the stay ends. */
class WaterStepper {
    private var last: Int? = null
    fun next(soc: Int): Int = IslandInfo.waterStep(soc, last).also { last = it }
    fun reset() { last = null }
}
