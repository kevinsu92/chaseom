package app.chaseom.stay

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** ISLAND_REDESIGN v2.1 + QA REVIEW_ISLAND_REDESIGN_v2 (H-1 buoys, L-2 hysteresis, one meaning per element). */
class IslandInfoTest {
    private val climate = ClimateSetting(powerOn = true, setTempC = 22f, fanSpeed = 3, acOn = false, recircOn = false, driverSeatHeat = 0)

    private fun ui(minutes: Int?, soc: Int = 19, below: Boolean = false, off: Boolean = false, banner: KeepBanner? = null, c: ClimateSetting = climate) =
        StayUi(
            parked = true, policy = ModePolicy.Full,
            hero = Hero(minutes, (minutes ?: 0) >= 180, off, belowKeep = below),
            bar = BarModel(soc, 9.3f, 30, "work", below),
            keepBanner = banner, climate = c,
        )

    @Test fun sameGateAsTheIsland() {
        assertNull(IslandInfo.of(ui(100).copy(parked = false)))
        assertNull(IslandInfo.of(ui(100).copy(policy = ModePolicy.Reduced)))
        assertNull(IslandInfo.of(ui(100).copy(hero = null)))
        assertEquals(0, IslandInfo.buoys(null))
    }

    @Test fun buoysAreThirtyMinutesRoundedDownAndCappedAtSix() {
        val minutes = listOf(29, 30, 59, 60, 88, 146, 179, 180, 9_999)
        val buoys = minutes.map { IslandInfo.of(ui(it))!!.buoys }
        assertEquals(listOf(0, 1, 1, 2, 2, 4, 5, 6, 6), buoys)
        // H-1: the picture never says more than the hero number.
        minutes.zip(buoys).forEach { (m, b) -> assertTrue(b * IslandInfo.MINUTES_PER_BUOY <= m) }
    }

    @Test fun belowKeepHasNoBuoysAndOutlinedCrates() {
        val k2 = IslandInfo.of(ui(140, soc = 8, below = true, banner = KeepBanner.BELOW))!!
        assertEquals(0, k2.buoys)
        assertTrue(k2.crateOutline)
        assertTrue(IslandInfo.of(ui(100, banner = KeepBanner.NEAR))!!.crateOutline)
        assertTrue(!IslandInfo.of(ui(100))!!.crateOutline)
    }

    @Test fun waterFiveStepsFromAFreshReading() {
        val socs = listOf(1, 20, 21, 40, 41, 60, 61, 80, 81, 100)
        assertEquals(listOf(0, 0, 1, 1, 2, 2, 3, 3, 4, 4), socs.map { IslandInfo.waterStepFresh(it) })
    }

    @Test fun waterHysteresisOnePercentPoint() {
        val w = WaterStepper()
        assertEquals(0, w.next(19))
        assertEquals(0, w.next(20)) // not up yet
        assertEquals(1, w.next(21)) // up at 21
        assertEquals(1, w.next(20)) // stays: down only at 19
        assertEquals(0, w.next(19)) // down at 19
        assertEquals(0, w.next(20))
        assertEquals(1, w.next(21))
        assertEquals(3, w.next(70)) // a jump crosses several steps at once
        assertEquals(2, w.next(59))
        w.reset()
        assertEquals(1, w.next(40)) // fresh again after the stay ends
    }

    @Test fun temperatureMovesOnlyTheSunAndTimeMovesOnlyTheBuoys() {
        val cold = IslandInfo.of(ui(100, c = climate.copy(setTempC = 18f)))!!
        val warm = IslandInfo.of(ui(100, c = climate.copy(setTempC = 26f)))!!
        assertEquals(0, cold.sunLevel)
        assertEquals(2, warm.sunLevel)
        assertEquals(cold.copy(sunLevel = warm.sunLevel), warm) // only the sun differs

        val long = IslandInfo.of(ui(170))!!
        val short = IslandInfo.of(ui(40))!!
        assertEquals(long.copy(buoys = short.buoys), short) // only the buoys differ
    }

    @Test fun weatherFollowsClimateAndOffIsTheSmallSun() {
        val i = IslandInfo.of(ui(100, c = climate.copy(fanSpeed = 7, acOn = true, recircOn = true, driverSeatHeat = 2)))!!
        assertEquals(2, i.wind)
        assertTrue(i.cool && i.cloud)
        assertEquals(2, i.heatDrv)
        val off = IslandInfo.of(ui(null, off = true, c = climate.copy(powerOn = false)))!!
        assertEquals(0, off.sunLevel)
        assertEquals(IslandInfo.MAX_BUOYS, off.buoys)
    }
}
