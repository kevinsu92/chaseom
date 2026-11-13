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
        val mild = StayEngine().at(0, demo.copy(outsideTempC = 0f)).hero!!
        assertEquals(228, mild.minutes)
        assertTrue(mild.overCap)
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

    // 25 C outside (the AOSP reference VHAL default): the warmth exchange (X1, the demo "+16") never reaches 10 min.
    // The fan card (X2) may still appear at very low SOC; that is correct and not checked here.
    @Test fun noExchangeCardOnAWarmDay() {
        val cur = ClimateSetting(true, 26f, 6, false, false, 0, outsideC = 25f)
        for (soc in 5..60) {
            val keep = EnergyModel.keepPercent(30, 77.4f, 5f)
            assertNull("soc=$soc", EnergyModel.exchange(soc, 77.4f, keep, cur, setOf(ExchangeRule.X2_FAN)))
        }
        assertEquals(EnergyModel.BASE_KW, EnergyModel.drawKw(cur.copy(setTempC = 22f, fanSpeed = 0)), 0.0001f) // heating 0
    }
}
