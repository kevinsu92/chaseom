package app.chaseom.stay

import app.chaseom.cabin.CabinState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Build G (d) review: gaps OutsideTempTest does not cover (engine wiring, toast, boundaries, warm days). */
class OutsideTempGapTest {

    // The demo cabin: SOC 19 %, 77.4 kWh, 22 C, fan 3, keep 30 km.
    private val demo = CabinState(
        connected = true, socPercent = 19, socReadable = true, batteryCapacityKwh = 77.4f, fuelTypes = listOf(10),
        hvacPowerOn = true, acOn = false, fanSpeed = 3, driverSetTempC = 22f, recirculationOn = false, driverSeatHeat = 0,
        driverDoorOpen = false, windows = mapOf("driver" to 0, "passenger" to 0, "rearLeft" to 0, "rearRight" to 0),
    )
    private val settings = StaySettings(keepKm = 30, label = "work")
    private fun StayEngine.at(t: Long, c: CabinState) = update(true, c, settings, t, 12)

    // The engine passes CabinState.outsideTempC into the model (the wiring line in StayEngine).
    @Test fun heroFollowsOutsideTemperature() {
        assertEquals(167, StayEngine().at(0, demo).hero!!.minutes)                          // unknown -> -10 C
        assertEquals(167, StayEngine().at(0, demo.copy(outsideTempC = -10f)).hero!!.minutes)
        // Middle option: a milder reading (0 C, the emulator's 25 C) keeps the -10 C numbers.
        assertEquals(167, StayEngine().at(0, demo.copy(outsideTempC = 0f)).hero!!.minutes)
        assertEquals(167, StayEngine().at(0, demo.copy(outsideTempC = 25f)).hero!!.minutes)
        // Colder than -10 C: fewer minutes.
        assertTrue(StayEngine().at(0, demo.copy(outsideTempC = -20f)).hero!!.minutes!! < 167)
    }

    // An outside-temperature change alone is not a dial action: no toast.
    @Test fun outsideChangeAloneRaisesNoToast() {
        val e = StayEngine()
        e.at(0, demo.copy(outsideTempC = -10f))
        assertNull(e.at(1000, demo.copy(outsideTempC = -5f)).toast)
    }

    // The demo dial (22 C fan 3 -> 26 C fan 6) still reads -21 min at -10 C.
    @Test fun demoToastUnchangedAtMinus10() {
        val e = StayEngine()
        e.at(0, demo.copy(outsideTempC = -10f))
        assertEquals(-21, e.at(1000, demo.copy(outsideTempC = -10f, driverSetTempC = 26f, fanSpeed = 6)).toast!!.deltaMinutes)
    }

    @Test fun validRangeBoundaries() {
        assertEquals(60f, EnergyModel.validOutsideC(60f))
        assertNull(EnergyModel.validOutsideC(60.5f))
        assertNull(EnergyModel.validOutsideC(-40.5f))
        assertNull(EnergyModel.validOutsideC(null))
        assertNull(EnergyModel.validOutsideC(Float.POSITIVE_INFINITY))
    }

    // Cooling stays 2.0 kW whatever the outside reading, including unknown.
    @Test fun coolingIgnoresOutside() {
        for (o in listOf(null, -10f, 0f, 25f, 40f)) {
            val s = ClimateSetting(true, 22f, 0, true, false, 0, outsideC = o)
            assertEquals("outside=$o", EnergyModel.BASE_KW + EnergyModel.AC_KW, EnergyModel.drawKw(s), 0.0001f)
        }
    }

    // 25 C outside (the Pleos Connect emulator reading): the demo stays as recorded, -21 min toast and the +16 card.
    @Test fun emulatorReadingKeepsTheDemo() {
        val e = StayEngine()
        e.at(0, demo.copy(outsideTempC = 25f))
        assertEquals(-21, e.at(1000, demo.copy(outsideTempC = 25f, driverSetTempC = 26f, fanSpeed = 6)).toast!!.deltaMinutes)
        val keep = EnergyModel.keepPercent(30, 77.4f, 5f)
        val cur = ClimateSetting(true, 26f, 6, false, false, 0, outsideC = 25f)
        assertEquals(16, EnergyModel.exchange(19, 77.4f, keep, cur, emptySet())!!.gainMinutes)
    }

    // F5: the demo scenes (SOC 19 %) at each outside temperature, middle option min(outside, -10).
    // Minutes: 22 C fan 3 / 26 C fan 6 / 21 C fan 6 + driver seat heat 2.
    @Test fun demoScenesByOutsideTemperature() {
        val keep = EnergyModel.keepPercent(30, 77.4f, 5f)
        fun m(set: Float, fan: Int, seat: Int, o: Float?) =
            EnergyModel.minutesToKeep(19, keep, 77.4f, EnergyModel.drawKw(ClimateSetting(true, set, fan, false, false, seat, outsideC = o)))
        val expected = mapOf(
            -20f to Triple(132, 118, 128),
            -15f to Triple(147, 131, 143),
            -10f to Triple(167, 146, 162),
            0f to Triple(167, 146, 162),
            25f to Triple(167, 146, 162),
        )
        for ((o, e) in expected) {
            assertEquals("outside=$o", e, Triple(m(22f, 3, 0, o), m(26f, 6, 0, o), m(21f, 6, 2, o)))
        }
        assertEquals(Triple(167, 146, 162), Triple(m(22f, 3, 0, null), m(26f, 6, 0, null), m(21f, 6, 2, null))) // unreadable
    }

    // Director §8.1-4 / eng5: the K1 banner (under 30 min left) and a swap card never show together, so the
    // Small screen cannot overflow with both. Exhaustive over climate settings and outside temperatures <= -10 C.
    @Test fun noSwapCardWhileNearTheKeepShare() {
        val cap = 77.4f
        val keep = EnergyModel.keepPercent(30, cap, 5f)
        var worst = 0
        for (o in listOf(-10f, -15f, -20f, -30f, -40f)) for (soc in 10..30) for (t in 16..32) for (fan in 0..8)
            for (ac in listOf(false, true)) for (rc in listOf(false, true)) for (seat in 0..3) {
                val c = ClimateSetting(true, t.toFloat(), fan, ac, rc, seat, outsideC = o)
                val m = EnergyModel.minutesToKeep(soc, keep, cap, EnergyModel.drawKw(c))
                if (soc <= keep || m >= 30) continue
                val x = EnergyModel.exchange(soc, cap, keep, c, emptySet())
                assertNull("K1 + card at outside=$o soc=$soc $c", x)
                worst = maxOf(worst, m)
            }
        assertTrue(worst in 1..29)
    }
}
