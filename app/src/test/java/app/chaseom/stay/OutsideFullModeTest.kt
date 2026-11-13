package app.chaseom.stay

import app.chaseom.cabin.CabinState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Round 2 (d): OutsideMode.FULL (behind BuildConfig.OUTSIDE_FULL, off by default). The build G rule
 * (COLD_ONLY) stays the default and keeps every build G number; see OutsideTempGapTest for that side.
 */
class OutsideFullModeTest {

    private val demo = CabinState(
        connected = true, socPercent = 19, socReadable = true, batteryCapacityKwh = 77.4f, fuelTypes = listOf(10),
        hvacPowerOn = true, acOn = false, fanSpeed = 3, driverSetTempC = 22f, recirculationOn = false, driverSeatHeat = 0,
        driverDoorOpen = false, windows = mapOf("driver" to 0, "passenger" to 0, "rearLeft" to 0, "rearRight" to 0),
    )
    private val settings = StaySettings(keepKm = 30, label = "work")
    private val keep = EnergyModel.keepPercent(30, 77.4f, 5f)
    private fun StayEngine.at(t: Long, c: CabinState) = update(true, c, settings, t, 12)
    private fun full(set: Float, fan: Int, seat: Int, o: Float?, ac: Boolean = false, rc: Boolean = false) =
        ClimateSetting(true, set, fan, ac, rc, seat, outsideC = o, outsideMode = OutsideMode.FULL)
    private fun minutes(s: ClimateSetting) = EnergyModel.minutesToKeep(19, keep, 77.4f, EnergyModel.drawKw(s))

    // The flag is off: ClimateSetting and StayEngine default to the build G rule.
    @Test fun defaultIsTheBuildGRule() {
        assertEquals(OutsideMode.COLD_ONLY, ClimateSetting(true, 22f, 3, false, false, 0).outsideMode)
        assertEquals(167, StayEngine().at(0, demo.copy(outsideTempC = 10f)).hero!!.minutes)
    }

    // Demo scenes (SOC 19 %): 22 C fan 3 / 26 C fan 6 / 21 C fan 6 + seat heat 2, by outside temperature.
    // -10 C and unreadable are the build G numbers; warmer readings lengthen the stay up to the 15 C clamp.
    @Test fun demoScenesByOutsideTemperature() {
        val expected = mapOf<Float?, Triple<Int, Int, Int>>(
            null to Triple(167, 146, 162),
            -20f to Triple(132, 118, 128),
            -10f to Triple(167, 146, 162),
            0f to Triple(228, 191, 219),
            10f to Triple(359, 276, 336),
            15f to Triple(504, 354, 460),
            25f to Triple(504, 354, 460), // clamped to 15 C
            61f to Triple(167, 146, 162), // out of range -> -10 C
        )
        for ((o, e) in expected) {
            assertEquals("outside=$o", e, Triple(minutes(full(22f, 3, 0, o)), minutes(full(26f, 6, 0, o)), minutes(full(21f, 6, 2, o))))
        }
    }

    // The clamp: at or above the set temperature heating does not drop to 0 (that would err long).
    @Test fun mildReadingNeverDropsHeatingToZero() {
        val at15 = EnergyModel.drawKw(full(22f, 3, 0, 15f))
        for (o in listOf(15f, 20f, 22f, 30f, 60f)) assertEquals("outside=$o", at15, EnergyModel.drawKw(full(22f, 3, 0, o)), 0.0001f)
        assertTrue(at15 > EnergyModel.BASE_KW + 3 * EnergyModel.FAN_KW_PER_STEP)
    }

    // M1 (ENERGY_MODEL.md §6, Ioniq 5 N, mild day, 23 C set, about 0.48 kW total): FULL still errs short (>= 2x).
    @Test fun mildDayRecordStillErrsShort() {
        val worst = EnergyModel.drawKw(full(23f, 3, 0, 60f))
        assertTrue("app $worst kW vs M1 0.48 kW", worst / 0.48f >= 2.0f)
    }

    // The engine passes the mode through; an outside change alone is still not a dial action.
    @Test fun engineUsesFullModeAndRaisesNoToast() {
        val e = StayEngine(outsideMode = OutsideMode.FULL)
        assertEquals(228, e.at(0, demo.copy(outsideTempC = 0f)).hero!!.minutes)
        assertNull(e.at(1000, demo.copy(outsideTempC = 5f)).toast)
    }

    // H2 in FULL mode: A/C counts max(heat, cool) under heating demand. FULL heating at 22 C set is 2.3 kW at -10 C
    // and below, and under 2.0 kW from about -5.8 C up, so mild days keep cooling's 2.0 kW.
    @Test fun acWithHeatingDemandTakesTheLargerTerm() {
        assertEquals(EnergyModel.BASE_KW + EnergyModel.HEAT_KW_AT_22C, EnergyModel.drawKw(full(22f, 0, 0, null, ac = true)), 0.0001f)
        assertEquals(EnergyModel.BASE_KW + EnergyModel.HEAT_KW_AT_22C, EnergyModel.drawKw(full(22f, 0, 0, -10f, ac = true)), 0.0001f)
        for (o in listOf(0f, 15f, 40f)) {
            assertEquals("outside=$o", EnergyModel.BASE_KW + EnergyModel.AC_KW, EnergyModel.drawKw(full(22f, 0, 0, o, ac = true)), 0.0001f)
        }
    }

    // The swap card measures both sides with the same mode (copy keeps outsideMode).
    @Test fun exchangeKeepsTheMode() {
        fun capped(s: ClimateSetting) =
            EnergyModel.minutesToKeep(15, keep, 77.4f, EnergyModel.drawKw(s)).coerceAtMost(EnergyModel.DISPLAY_CAP_MINUTES)
        val cur = full(26f, 6, 0, 0f)
        val x1 = capped(cur.copy(setTempC = 21f, driverSeatHeat = 2)) - capped(cur)
        val x2 = capped(cur.copy(fanSpeed = 3)) - capped(cur)
        val ex = EnergyModel.exchange(15, 77.4f, keep, cur, emptySet())!!
        assertEquals(maxOf(x1, x2), ex.gainMinutes)
        // The same dial under the build G rule gives a different card: the mode really reaches the candidates.
        val cold = EnergyModel.exchange(15, 77.4f, keep, cur.copy(outsideMode = OutsideMode.COLD_ONLY), emptySet())
        assertTrue(cold == null || cold.gainMinutes != ex.gainMinutes)
    }

    // Found by the sweep below: without the explicit guard, FULL mode showed X3 (+12 min) at 25 min left.
    @Test fun acOffCardIsNotShownUnderTheBanner() {
        val c = full(23f, 8, 3, 0f, ac = true, rc = true)
        val left = EnergyModel.minutesToKeep(11, keep, 77.4f, EnergyModel.drawKw(c))
        assertTrue(left in 1 until EnergyModel.NEAR_KEEP_MINUTES)
        assertNull(EnergyModel.exchange(11, 77.4f, keep, c, emptySet()))
    }

    // Director §8.1-4 invariant (K1 banner and a swap card never together) holds in FULL mode for every outside
    // temperature up to the clamp.
    @Test fun noSwapCardWhileNearTheKeepShare() {
        val cap = 77.4f
        var checked = 0
        for (o in listOf(-40f, -20f, -10f, 0f, 5f, 10f, 15f)) for (soc in 10..30) for (t in 16..32) for (fan in 0..8)
            for (ac in listOf(false, true)) for (rc in listOf(false, true)) for (seat in 0..3) {
                val c = full(t.toFloat(), fan, seat, o, ac, rc)
                val m = EnergyModel.minutesToKeep(soc, keep, cap, EnergyModel.drawKw(c))
                if (soc <= keep || m >= 30) continue
                assertNull("K1 + card at outside=$o soc=$soc $c", EnergyModel.exchange(soc, cap, keep, c, emptySet()))
                checked++
            }
        assertTrue(checked > 0)
    }
}
