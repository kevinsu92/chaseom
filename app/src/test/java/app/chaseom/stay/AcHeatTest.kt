package app.chaseom.stay

import app.chaseom.cabin.CabinState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * H2 (external review §11 item 7, HQ Addendum 8 item 7): with A/C on and the cabin needing heat, drawKw counts
 * max(heat, cool) instead of cooling's 2.0 kW alone. Before, a winter A/C+heat stay read 13-53 % long.
 */
class AcHeatTest {

    private fun ac(set: Float, o: Float?, fan: Int = 3, rc: Boolean = false, on: Boolean = true) =
        ClimateSetting(true, set, fan, on, rc, 0, outsideC = o)

    // The cited overestimate: stay time with the old rule (A/C = 2.0 kW) over the time with heating counted.
    // 22 C set, fan 3: -10 C was 13 % long, -15 C 28 %, -20 C 43 %, -23 C 52 % (the review's 13-53 % range). Now 0 %.
    @Test fun winterAcNoLongerReadsLong() {
        val oldKw = EnergyModel.BASE_KW + EnergyModel.AC_KW + 3 * EnergyModel.FAN_KW_PER_STEP
        for (o in listOf(-10f, -15f, -20f, -23f)) {
            val heatOnly = EnergyModel.drawKw(ac(22f, o, on = false))
            val now = EnergyModel.drawKw(ac(22f, o))
            assertEquals("outside=$o", heatOnly, now, 0.0001f)
            assertTrue("outside=$o", heatOnly / oldKw - 1f >= 0.12f) // the old rule really was long here
        }
    }

    // Summer reading (outside at/above the set temperature): cooling alone, as before.
    @Test fun summerAcUnchanged() {
        for (o in listOf(22f, 25f, 35f)) {
            assertEquals(EnergyModel.BASE_KW + EnergyModel.AC_KW + 0.09f, EnergyModel.drawKw(ac(22f, o)), 0.0001f)
            assertFalse(EnergyModel.heatingDemand(ac(22f, o)))
        }
    }

    // No reading: the season is unknown, so the larger term counts (errs short, the app's rule).
    @Test fun unknownOutsideTakesTheLargerTerm() {
        assertTrue(EnergyModel.heatingDemand(ac(22f, null)))
        assertTrue(EnergyModel.heatingDemand(ac(22f, Float.NaN)))
        assertTrue(EnergyModel.heatingDemand(ac(22f, 70f))) // out of range = sensor fault = unknown
        assertEquals(EnergyModel.BASE_KW + EnergyModel.HEAT_KW_AT_22C + 0.09f, EnergyModel.drawKw(ac(22f, null)), 0.0001f)
    }

    // Cooling still wins when it is larger: a low set point (18 C at -10 C -> heating 2.01 kW) with recirculation
    // (x 0.76 -> 1.53 kW) keeps A/C's 2.0 kW. Never less than either term.
    @Test fun neverBelowEitherTerm() {
        for (set in listOf(16f, 18f, 22f, 26f, 30f)) for (o in listOf(null, -25f, -10f, 0f, 10f)) for (rc in listOf(false, true)) {
            val s = ac(set, o, rc = rc)
            val cabin = EnergyModel.drawKw(s) - EnergyModel.BASE_KW - 3 * EnergyModel.FAN_KW_PER_STEP
            assertTrue("set=$set o=$o rc=$rc", cabin >= EnergyModel.AC_KW - 0.0001f)
            assertTrue("set=$set o=$o rc=$rc", cabin >= EnergyModel.heatKw(s) - 0.0001f)
        }
        assertEquals(EnergyModel.AC_KW, EnergyModel.drawKw(ac(18f, -10f, fan = 0, rc = true)) - EnergyModel.BASE_KW, 0.0001f)
    }

    // A/C off and HVAC off are untouched.
    @Test fun acOffAndPowerOffUnchanged() {
        val off = ac(22f, -10f, on = false)
        assertEquals(EnergyModel.BASE_KW + EnergyModel.heatKw(off) + 0.09f, EnergyModel.drawKw(off), 0.0001f)
        assertEquals(EnergyModel.BASE_KW, EnergyModel.drawKw(ClimateSetting(false, 22f, 3, true, false, 0, outsideC = -20f)), 0.0001f)
    }

    // Swap card X3 ("A/C 끄기"): in winter turning A/C off no longer gains anything under max(heat, cool), so no
    // card claims minutes the model cannot show.
    @Test fun noWinterAcSwapCard() {
        val keep = EnergyModel.keepPercent(30, 77.4f, 5f)
        for (o in listOf(null, -10f, -20f)) {
            val card = EnergyModel.exchange(19, 77.4f, keep, ac(24f, o), emptySet())
            assertTrue("outside=$o", card == null || card.rule != ExchangeRule.X3_AC)
        }
    }

    // The demo has A/C off: 167 / 146 / 162 min, -21 toast, +16 card stay as recorded, whatever the outside reading.
    @Test fun demoNumbersUnchanged() {
        val demo = CabinState(
            connected = true, socPercent = 19, socReadable = true, batteryCapacityKwh = 77.4f, fuelTypes = listOf(10),
            hvacPowerOn = true, acOn = false, fanSpeed = 3, driverSetTempC = 22f, recirculationOn = false, driverSeatHeat = 0,
            driverDoorOpen = false, windows = mapOf("driver" to 0, "passenger" to 0, "rearLeft" to 0, "rearRight" to 0),
        )
        val settings = StaySettings(keepKm = 30, label = "work")
        for (o in listOf(null, -10f, 25f)) {
            val e = StayEngine()
            assertEquals(167, e.update(true, demo.copy(outsideTempC = o), settings, 0, 12).hero!!.minutes)
            val hot = e.update(true, demo.copy(outsideTempC = o, driverSetTempC = 26f, fanSpeed = 6), settings, 1000, 12)
            assertEquals(146, hot.hero!!.minutes)
            assertEquals(-21, hot.toast!!.deltaMinutes)
            assertEquals(162, StayEngine().update(true, demo.copy(outsideTempC = o, driverSetTempC = 21f, fanSpeed = 6, driverSeatHeat = 2),
                settings, 0, 12).hero!!.minutes)
            val keep = EnergyModel.keepPercent(30, 77.4f, 5f)
            assertEquals(16, EnergyModel.exchange(19, 77.4f, keep, ClimateSetting(true, 26f, 6, false, false, 0, outsideC = o), emptySet())!!.gainMinutes)
        }
    }
}
