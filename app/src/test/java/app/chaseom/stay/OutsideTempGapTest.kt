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
}
