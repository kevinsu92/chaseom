package app.chaseom.stay

import app.chaseom.cabin.CabinState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * H2 item 10 (HQ addendum 8): the passenger zone and the passenger seat heater count only when a front passenger is
 * detected or the passenger zone is unsynced (HVAC dual on). The demo numbers 167/146/162, -21 and +16 do not change.
 */
class PassengerZoneTest {

    private val demo = CabinState(
        connected = true, socPercent = 19, socReadable = true, batteryCapacityKwh = 77.4f, fuelTypes = listOf(10),
        hvacPowerOn = true, acOn = false, fanSpeed = 3, driverSetTempC = 22f, recirculationOn = false, driverSeatHeat = 0,
        driverDoorOpen = false, windows = mapOf("driver" to 0, "passenger" to 0, "rearLeft" to 0, "rearRight" to 0),
    )
    private val settings = StaySettings(keepKm = 30, label = "work")
    private val keep = EnergyModel.keepPercent(30, 77.4f, 5f)
    private fun StayEngine.at(t: Long, c: CabinState) = update(true, c, settings, t, 12)
    private fun scenes(c: CabinState) = listOf(
        c, c.copy(driverSetTempC = 26f, fanSpeed = 6), c.copy(driverSetTempC = 21f, fanSpeed = 6, driverSeatHeat = 2),
    )

    /** Hero triple, the dial toast and the swap-card gain for one passenger state, all through the engine's wiring. */
    private fun demoNumbers(p: CabinState): List<Int?> {
        val heroes = scenes(p).map { StayEngine().at(0, it).hero!!.minutes }
        val e = StayEngine()
        e.at(0, p)
        val toast = e.at(1000, p.copy(driverSetTempC = 26f, fanSpeed = 6)).toast!!.deltaMinutes
        val cur = ClimateSetting(
            true, 26f, 6, false, false, 0, outsideC = p.outsideTempC,
            passengerCounted = p.passengerOccupied == true || p.hvacDualOn == true,
            passengerSeatHeat = p.passengerSeatHeat, passengerZoneUnsynced = p.hvacDualOn == true,
            passengerSetTempC = p.passengerSetTempC,
        )
        val gain = EnergyModel.exchange(19, 77.4f, keep, cur, emptySet())!!.gainMinutes
        return heroes + toast + gain
    }

    // HQ gate: every passenger state the emulator demo can be in keeps 167/146/162, -21, +16. The VC demo sets the
    // passenger seat heater to 0 and never turns dual on; SDK 2.3.2 reports an unknown seat as detected, so the
    // detected cases are included. A synced zone's own temperature reading is ignored.
    @Test fun demoNumbersUnchanged() {
        val expected = listOf<Int?>(167, 146, 162, -21, 16)
        val states = mutableListOf<CabinState>()
        for (occ in listOf(null, false, true)) for (dual in listOf(null, false)) for (pt in listOf(null, 18f, 22f, 30f)) {
            for (o in listOf(null, 25f)) {
                states += demo.copy(passengerOccupied = occ, hvacDualOn = dual, passengerSeatHeat = 0, passengerSetTempC = pt, outsideTempC = o)
            }
        }
        // Not detected and synced: even a passenger seat heater on is ignored, as before.
        states += demo.copy(passengerOccupied = false, hvacDualOn = false, passengerSeatHeat = 3)
        for (s in states) assertEquals("$s", expected, demoNumbers(s))
    }

    // A detected passenger's seat heater is counted (level 2 = 0.066 kW).
    @Test fun detectedPassengerSeatHeaterCounts() {
        val base = EnergyModel.drawKw(ClimateSetting(true, 22f, 3, false, false, 0))
        val withPas = EnergyModel.drawKw(ClimateSetting(true, 22f, 3, false, false, 0, passengerCounted = true, passengerSeatHeat = 2))
        assertEquals(base + 0.066f, withPas, 0.0001f)
        val hero = StayEngine().at(0, demo.copy(passengerOccupied = true, passengerSeatHeat = 2)).hero!!
        assertTrue(hero.minutes!! < 167)
    }

    // An unsynced zone alone (no passenger detected) also counts the seat heater.
    @Test fun unsyncedZoneCountsTheSeatHeater() {
        val hero = StayEngine().at(0, demo.copy(passengerOccupied = false, hvacDualOn = true, passengerSeatHeat = 3)).hero!!
        assertTrue(hero.minutes!! < 167)
    }

    // The seat heater draws with the climate off too, like the driver's.
    @Test fun countedSeatHeaterWithClimateOff() {
        val off = ClimateSetting(false, 22f, 3, false, false, 0, passengerCounted = true, passengerSeatHeat = 1)
        assertEquals(EnergyModel.BASE_KW + 0.049f, EnergyModel.drawKw(off), 0.0001f)
    }

    // Unsynced and warmer: heating uses the two zones' mean. Colder: unchanged (the estimate keeps erring short).
    @Test fun unsyncedWarmerZoneRaisesHeatingColderDoesNot() {
        fun kw(pas: Float, dual: Boolean) = EnergyModel.drawKw(
            ClimateSetting(true, 22f, 3, false, false, 0, passengerCounted = dual, passengerZoneUnsynced = dual, passengerSetTempC = pas),
        )
        val plain = EnergyModel.drawKw(ClimateSetting(true, 22f, 3, false, false, 0))
        assertEquals(plain + EnergyModel.HEAT_KW_AT_22C * 2f / 32f, kw(26f, dual = true), 0.0001f) // 24 C mean
        assertEquals(plain, kw(18f, dual = true), 0.0001f)
        assertEquals(plain, kw(26f, dual = false), 0.0001f) // synced: reading ignored
        assertEquals(22f, EnergyModel.heatSetC(ClimateSetting(true, 22f, 3, false, false, 0, passengerZoneUnsynced = true)))
    }

    // H2 merge with h2-ac: with A/C on, cooling alone applies only when the cabin needs no heat (outside at or above
    // the set point); the warmer unsynced passenger zone is then ignored, as before.
    @Test fun acOnInSummerIgnoresTheZoneTemperature() {
        val ac = ClimateSetting(true, 22f, 3, true, false, 0, outsideC = 30f, passengerCounted = true, passengerZoneUnsynced = true, passengerSetTempC = 28f)
        assertEquals(EnergyModel.drawKw(ClimateSetting(true, 22f, 3, true, false, 0, outsideC = 30f)), EnergyModel.drawKw(ac), 0.0001f)
    }

    // H2 merge with h2-ac: when the cabin needs heat, A/C on counts max(heating, cooling); the heating term uses the
    // two-zone set point, so the warmer unsynced zone raises it exactly as with A/C off.
    @Test fun acOnInWinterCountsTheZoneHeating() {
        val ac = ClimateSetting(true, 22f, 3, true, false, 0, passengerCounted = true, passengerZoneUnsynced = true, passengerSetTempC = 28f)
        val noAc = ac.copy(acOn = false)
        assertEquals(EnergyModel.drawKw(noAc), EnergyModel.drawKw(ac), 0.0001f)
    }
}
