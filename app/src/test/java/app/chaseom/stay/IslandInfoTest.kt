package app.chaseom.stay

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** QA redo/qa.md 2-A rules for the information island (C-3, C-4, W-1..W-5, H-1..H-4). */
class IslandInfoTest {
    private val climate = ClimateSetting(powerOn = true, setTempC = 22f, fanSpeed = 3, acOn = false, recircOn = false, driverSeatHeat = 0)

    private fun ui(minutes: Int?, soc: Int = 19, keep: Float = 9.3f, below: Boolean = false, off: Boolean = false, banner: KeepBanner? = null) =
        StayUi(
            parked = true, policy = ModePolicy.Full,
            hero = Hero(minutes, (minutes ?: 0) >= 180, off, belowKeep = below),
            bar = BarModel(soc, keep, 30, "work", below),
            keepBanner = banner, climate = climate,
        )

    @Test fun sameGateAsTheIsland() {
        assertNull(IslandInfo.of(ui(100).copy(parked = false)))
        assertNull(IslandInfo.of(ui(100).copy(policy = ModePolicy.Reduced)))
        assertNull(IslandInfo.of(ui(100).copy(hero = null)))
    }

    @Test fun sunIsStayTimeInStepsAndStopsAtThreeHours() {
        val steps = listOf(0, 29, 30, 59, 60, 119, 120, 179, 180, 600).map { IslandInfo.of(ui(it))!!.sunStep }
        assertEquals(listOf(0, 0, 1, 1, 2, 2, 3, 3, 4, 4), steps)
        assertEquals(IslandInfo.of(ui(180))!!.sunStep, IslandInfo.of(ui(9_999))!!.sunStep) // H-2
        assertTrue(steps.all { it in 0 until IslandInfo.SUN_STEPS })
    }

    @Test fun belowKeepNeverLooksBetter() {
        // C-4: at K2 the sun is the smallest and the crates are outlined, whatever the minutes say.
        val k2 = IslandInfo.of(ui(0, soc = 8, below = true, banner = KeepBanner.BELOW))!!
        assertEquals(0, k2.sunStep)
        assertTrue(k2.crateOutline)
        assertTrue(k2.waterStep <= IslandInfo.of(ui(100))!!.waterStep)
    }

    @Test fun waterIsBatteryInAtMostFiveSteps() {
        val steps = listOf(0, 19, 20, 39, 40, 59, 60, 79, 80, 100).map { IslandInfo.waterStep(it.toFloat()) }
        assertEquals(listOf(0, 0, 1, 1, 2, 2, 3, 3, 4, 4), steps)
        assertTrue(IslandInfo.WATER_STEPS <= 5) // W-3
        assertEquals(0, IslandInfo.of(ui(100, keep = 9.3f))!!.keepStep) // keep line drawn at its own step
    }

    @Test fun climateOffIsTheFullSunAndWeatherFollowsClimate() {
        assertEquals(IslandInfo.SUN_STEPS - 1, IslandInfo.of(ui(null, off = true))!!.sunStep)
        val i = IslandInfo.of(ui(100).copy(climate = climate.copy(setTempC = 26f, fanSpeed = 7, acOn = true, recircOn = true, driverSeatHeat = 2)))!!
        assertEquals(2, i.wind)
        assertTrue(i.cool && i.cloud)
        assertEquals(2, i.heatDrv)
        assertEquals(2, i.tempLevel) // H-4: temperature has its own field, not the sun
    }

    @Test fun oneMeaningPerElement() {
        // C-3: changing only the set temperature moves tempLevel, never the sun step (stay time held equal).
        val a = IslandInfo.of(ui(100).copy(climate = climate.copy(setTempC = 18f)))!!
        val b = IslandInfo.of(ui(100).copy(climate = climate.copy(setTempC = 27f)))!!
        assertEquals(a.sunStep, b.sunStep)
        assertTrue(a.tempLevel != b.tempLevel)
    }
}
