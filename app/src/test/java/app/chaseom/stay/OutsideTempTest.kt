package app.chaseom.stay

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Build G (d): heating follows the car's outside temperature; unreadable or implausible -> the old -10 C. */
class OutsideTempTest {

    private fun heat(set: Float, outside: Float?) =
        EnergyModel.drawKw(ClimateSetting(true, set, 0, false, false, 0, outsideC = outside)) - EnergyModel.BASE_KW

    @Test fun unknownOutsideKeepsTheMinus10Model() {
        assertEquals(2.3f, heat(22f, null), 0.0001f)
        assertEquals(heat(22f, -10f), heat(22f, null), 0.0001f)
    }

    @Test fun heatingScalesWithInsideOutsideDifference() {
        assertEquals(2.3f * 22f / 32f, heat(22f, 0f), 0.0001f)
        assertEquals(2.3f * 12f / 32f, heat(22f, 10f), 0.0001f)
        assertEquals(2.3f * 42f / 32f, heat(22f, -20f), 0.0001f) // colder than the reference -> more draw
    }

    @Test fun noHeatingWhenOutsideIsWarmerThanTheSetPoint() = assertEquals(0f, heat(22f, 30f), 0.0001f)

    @Test fun sensorFaultFallsBackToMinus10() {
        assertEquals(2.3f, heat(22f, 99f), 0.0001f)
        assertEquals(2.3f, heat(22f, -60f), 0.0001f)
        assertEquals(2.3f, heat(22f, Float.NaN), 0.0001f)
        assertNull(EnergyModel.validOutsideC(Float.NaN))
        assertEquals(-40f, EnergyModel.validOutsideC(-40f))
    }

    @Test fun coolingAndRecirculationUnchanged() {
        val ac = ClimateSetting(true, 26f, 0, true, false, 0, outsideC = 30f)
        assertEquals(2.0f, EnergyModel.drawKw(ac) - EnergyModel.BASE_KW, 0.0001f)
        val rec = ClimateSetting(true, 22f, 0, false, true, 0, outsideC = 0f)
        assertEquals(2.3f * 22f / 32f * 0.76f, EnergyModel.drawKw(rec) - EnergyModel.BASE_KW, 0.0001f)
    }

    // Milder outside -> lower draw -> more minutes on the same battery.
    @Test fun milderOutsideMeansMoreMinutes() {
        val keep = EnergyModel.keepPercent(30, 77.4f, 5f)
        val cold = EnergyModel.minutesToKeep(20, keep, 77.4f, EnergyModel.drawKw(ClimateSetting(true, 24f, 3, false, false, 0)))
        val mild = EnergyModel.minutesToKeep(20, keep, 77.4f, EnergyModel.drawKw(ClimateSetting(true, 24f, 3, false, false, 0, outsideC = 5f)))
        assertTrue("mild=$mild cold=$cold", mild > cold)
    }

    // The exchange card keeps the outside temperature when it compares settings (copy keeps outsideC).
    @Test fun exchangeComparesAtTheSameOutsideTemperature() {
        val keep = EnergyModel.keepPercent(30, 77.4f, 5f)
        val s = ClimateSetting(true, 26f, 3, false, false, 0, outsideC = 0f)
        fun m(c: ClimateSetting) = EnergyModel.minutesToKeep(15, keep, 77.4f, EnergyModel.drawKw(c))
            .coerceAtMost(EnergyModel.DISPLAY_CAP_MINUTES)
        val gain = m(s.copy(setTempC = 21f, driverSeatHeat = 2)) - m(s)
        assertTrue("fixture must offer a card, gain=$gain", gain >= EnergyModel.MIN_GAIN_MINUTES)
        val x = EnergyModel.exchange(15, 77.4f, keep, s, emptySet())
        assertEquals(gain, x?.gainMinutes)
        // At -10 C (outside unknown) the same swap gains a different amount: the card follows the outside temperature.
        val cold = EnergyModel.exchange(15, 77.4f, keep, s.copy(outsideC = null), emptySet())
        assertTrue(cold?.gainMinutes != x?.gainMinutes)
    }
}
