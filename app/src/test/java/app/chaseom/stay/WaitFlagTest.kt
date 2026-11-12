package app.chaseom.stay

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Wait forecast rules: 3-hour cap, rounding, outcomes, night and no-hero cases. */
class WaitFlagTest {
    private val min = 60_000L
    // 14:07:30 wall time in UTC millis of an arbitrary day; monotonic clock starts elsewhere.
    private val wall = (14 * 60 + 7) * min + 30_000L
    private val mono = 5_000_000L

    private fun hero(minutes: Int?, below: Boolean = false, off: Boolean = false) =
        Hero(minutes, (minutes ?: 0) >= 180, off, belowKeep = below)

    @Test fun plantRoundsDownToFiveMinutesAndNeverPastThreeHours() {
        val f = WaitFlags.plant(30, mono, wall)
        assertEquals((14 * 60 + 35) * min, f.wallMs) // 14:37:30 -> 14:35
        assertEquals(mono + (f.wallMs - wall), f.atMs)
        WaitFlags.CHOICES.forEach { m ->
            val p = WaitFlags.plant(m, mono, wall)
            assertTrue(p.atMs - mono <= WaitFlags.MAX_MINUTES * min)
            assertTrue(p.atMs > mono)
        }
        // A request beyond 3 hours is cut to 3 hours.
        assertTrue(WaitFlags.plant(600, mono, wall).atMs - mono <= 180 * min)
    }

    @Test fun reachesWhenStayTimeIsAtLeastTheWait() {
        val f = WaitFlags.plant(60, mono, wall)
        val r = WaitFlags.forecast(f, mono, hero(167), null, night = false)!!
        assertEquals(WaitOutcome.REACHES, r.outcome)
        assertEquals(r.remainingMinutes / 180f, r.fraction, 0.0001f)
    }

    @Test fun shortGivesTheGapAndTheSwapGain() {
        val f = WaitFlags.plant(180, mono, wall) // 17:05 -> 177.5 min away
        val r = WaitFlags.forecast(f, mono, hero(146), Exchange(ExchangeRule.X1_SEAT_HEAT, 16), night = false)!!
        assertEquals(WaitOutcome.SHORT, r.outcome)
        assertEquals(178, r.remainingMinutes)
        assertEquals(178 - 146, r.shortMinutes)
        assertEquals(16, r.swapGainMinutes)
    }

    @Test fun twoAndAHalfHourFlagFlipsWithTheSwap() {
        // Demo values (SOC 19 %): 26 C / fan 6 = 146 min, 21 C + seat heat 2 = 162 min.
        val f = WaitFlags.plant(150, mono, (14 * 60 + 5) * min) // on a 5-minute boundary: exactly 150 min
        assertEquals(150, WaitFlags.forecast(f, mono, hero(146), null, false)!!.remainingMinutes)
        assertEquals(WaitOutcome.SHORT, WaitFlags.forecast(f, mono, hero(146), null, false)!!.outcome)
        assertEquals(4, WaitFlags.forecast(f, mono, hero(146), null, false)!!.shortMinutes)
        assertEquals(WaitOutcome.REACHES, WaitFlags.forecast(f, mono, hero(162), null, false)!!.outcome)
    }

    @Test fun climateOffReachesAndBelowKeepDoesNotCompare() {
        val f = WaitFlags.plant(90, mono, wall)
        assertEquals(WaitOutcome.REACHES, WaitFlags.forecast(f, mono, hero(null, off = true), null, false)!!.outcome)
        assertEquals(WaitOutcome.BELOW_KEEP, WaitFlags.forecast(f, mono, hero(0, below = true), null, false)!!.outcome)
    }

    @Test fun noFlagAtNightAfterTheTimeOrWithoutHero() {
        val f = WaitFlags.plant(60, mono, wall)
        assertNull(WaitFlags.forecast(f, mono, hero(100), null, night = true)) // no flag at night
        assertNull(WaitFlags.forecast(f, f.atMs, hero(100), null, night = false))
        assertNull(WaitFlags.forecast(f, mono, null, null, night = false))
        assertNull(WaitFlags.forecast(null, mono, hero(100), null, night = false))
    }

    @Test fun remainingCountsDownWithTheMonotonicClock() {
        val f = WaitFlags.plant(60, mono, wall) // 15:05 -> 57.5 min
        assertEquals(58, WaitFlags.forecast(f, mono, hero(100), null, false)!!.remainingMinutes)
        assertEquals(28, WaitFlags.forecast(f, mono + 30 * min, hero(100), null, false)!!.remainingMinutes)
        assertEquals(1, WaitFlags.forecast(f, f.atMs - 1, hero(100), null, false)!!.remainingMinutes)
    }

    // The forecast is display only and says nothing reassuring when the flag is reached.
    @Test fun choicesStayWithinThreeHours() {
        assertTrue(WaitFlags.CHOICES.all { it in 1..WaitFlags.MAX_MINUTES })
        assertEquals(180, WaitFlags.MAX_MINUTES)
    }

    // The flag uses the same night window as the night notice (22:00-06:00).
    @Test fun nightWindowMatchesTheNightNotice() {
        assertTrue(isNightHour(22) && isNightHour(3) && isNightHour(5))
        assertTrue(!isNightHour(6) && !isNightHour(16) && !isNightHour(21))
    }

    // After an air-source switch, a shortfall line does not newly appear for 10 minutes (the flag time stays).
    @Test fun noNewShortLineForTenMinutesAfterAnAirSwitch() {
        val f = WaitFlags.plant(180, mono, wall)
        val short = WaitFlags.forecast(f, mono, hero(146), null, false)!!
        assertEquals(WaitOutcome.SHORT, short.outcome)
        val switchAt = mono - 60_000L
        val quiet = WaitFlags.quietAfterAirSwitch(short, mono, switchAt, null, shortOnScreen = false)!!
        assertTrue("no new 'short' right after the switch", quiet.airQuiet)
        assertTrue("after 10 minutes the comparison returns",
            !WaitFlags.quietAfterAirSwitch(short, switchAt + WaitFlags.AIR_QUIET_MS, switchAt, null, false)!!.airQuiet)
    }

    // Another dial change after the switch, or a shortfall already on screen, keeps the comparison.
    @Test fun anotherDialChangeOrAShownShortKeepsTheComparison() {
        val f = WaitFlags.plant(180, mono, wall)
        val short = WaitFlags.forecast(f, mono, hero(146), null, false)!!
        val switchAt = mono - 60_000L
        assertTrue(!WaitFlags.quietAfterAirSwitch(short, mono, switchAt, otherChangeMs = switchAt + 1, shortOnScreen = false)!!.airQuiet)
        assertTrue(!WaitFlags.quietAfterAirSwitch(short, mono, switchAt, null, shortOnScreen = true)!!.airQuiet)
        val reaches = WaitFlags.forecast(f, mono, hero(400), null, false)!!
        assertTrue("a reached flag is never marked", !WaitFlags.quietAfterAirSwitch(reaches, mono, switchAt, null, false)!!.airQuiet)
    }

    // The comparison uses the 3-hour-capped time, the same as the hero.
    @Test fun comparesTheCappedTime() {
        val f = WaitFlags.plant(180, mono, wall)
        assertEquals(WaitOutcome.REACHES, WaitFlags.forecast(f, mono, hero(900), null, false)!!.outcome)
        assertEquals(0, WaitFlags.forecast(f, mono, hero(900), null, false)!!.shortMinutes)
    }

    // F-1: the picture follows the line's own comparison, never the rounded buoys.
    @Test fun reachedFlagTouchesTheLastBuoyAndAShortFlagLeavesAGap() {
        val f = WaitFlags.plant(150, mono, (14 * 60 + 5) * min) // exactly 150 min away
        // 162 min reaches 150: buoys = 5, flag right behind the fifth buoy (touching)
        val reach = WaitFlags.forecast(f, mono, hero(162), null, false)!!
        assertEquals(WaitOutcome.REACHES, reach.outcome)
        assertEquals(5.5f, WaitFlags.rowSlot(reach, buoys = 5)!!, 0f)
        // 146 min is 4 min short of 150: buoys = 4, slot ceil(150/30) = 5 would touch, so it moves one gap further
        val short = WaitFlags.forecast(f, mono, hero(146), null, false)!!
        assertEquals(WaitOutcome.SHORT, short.outcome)
        val slot = WaitFlags.rowSlot(short, buoys = 4)!!
        assertTrue("a short flag never touches the last buoy", slot - 4 >= 1.5f)
        // far short: 60 min for a 3-hour flag -> slot 6, plenty of gap
        val far = WaitFlags.forecast(WaitFlags.plant(180, mono, (14 * 60 + 5) * min), mono, hero(60), null, false)!!
        assertEquals(6f, WaitFlags.rowSlot(far, buoys = 2)!!, 0f)
    }

    @Test fun rowSlotStaysOnTheRow() {
        val f = WaitFlags.plant(30, mono, wall) // under 30 min after rounding
        val reach = WaitFlags.forecast(f, mono, hero(400), null, false)!!
        assertEquals(6.5f, WaitFlags.rowSlot(reach, buoys = 6)!!, 0f) // half a gap behind the sixth buoy
        val reachNoBuoy = WaitFlags.forecast(f, mono, hero(29), null, false)!!
        assertEquals(1f, WaitFlags.rowSlot(reachNoBuoy, buoys = 0)!!, 0f) // pinned to the first slot
        val shortTiny = WaitFlags.forecast(f, mono, hero(5), null, false)!!
        assertTrue(WaitFlags.rowSlot(shortTiny, buoys = 0)!! in 1f..WaitFlags.MAX_SLOT)
    }

    // F-2: under tomorrow's share there is no flag picture (the line says it does not compare).
    @Test fun noFlagPictureBelowTomorrowsShare() {
        val f = WaitFlags.plant(60, mono, wall)
        val below = WaitFlags.forecast(f, mono, hero(0, below = true), null, false)!!
        assertEquals(WaitOutcome.BELOW_KEEP, below.outcome)
        assertNull(WaitFlags.rowSlot(below, buoys = 0))
    }
}
