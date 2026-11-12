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

    private val at1405 = (14 * 60 + 5) * min
    private fun fc(flagMin: Int, stay: Int) = WaitFlags.forecast(WaitFlags.plant(flagMin, mono, at1405), mono, hero(stay), null, false)!!
    private fun buoysFor(stay: Int) = (minOf(stay, 180) / 30)

    // QA ruling 11 (a): a reached flag stands at or before the last buoy, for every flag and stay time.
    @Test fun ruling11aReachedFlagIsInsideTheRow() {
        for (flag in WaitFlags.CHOICES) for (stay in flag..400) {
            val f = fc(flag, stay)
            assertEquals(WaitOutcome.REACHES, f.outcome)
            assertTrue("flag $flag stay $stay", WaitFlags.rowSlot(f, buoysFor(stay))!! <= buoysFor(stay).toFloat())
        }
    }

    // QA ruling 11 (b): a short flag stands past the last buoy (outside the row), for every flag and stay time.
    @Test fun ruling11bShortFlagIsPastTheLastBuoy() {
        for (flag in WaitFlags.CHOICES) for (stay in 0 until flag) {
            val f = fc(flag, stay)
            assertEquals(WaitOutcome.SHORT, f.outcome)
            assertTrue("flag $flag stay $stay", WaitFlags.rowSlot(f, buoysFor(stay))!! > buoysFor(stay).toFloat())
        }
        // the gap grows with the shortfall: 146 min for a 150-min flag stands right past buoy 4, 60 min far past buoy 2
        assertEquals(5f, WaitFlags.rowSlot(fc(150, 146), 4)!!, 0f)
        assertEquals(6f, WaitFlags.rowSlot(fc(180, 60), 2)!!, 0f)
    }

    // QA ruling 11 (c): reached in the same 30-min slot (stay 140 = 4 buoys, flag 130 min = slot 4.33) is pulled onto buoy 4.
    @Test fun ruling11cSameSlotReachIsPulledToTheLastBuoy() {
        val f = WaitFlags.forecast(WaitFlags.plant(130, mono, at1405), mono, hero(140), null, false)!!
        assertEquals(WaitOutcome.REACHES, f.outcome)
        assertEquals(130, f.remainingMinutes)
        assertEquals(4f, WaitFlags.rowSlot(f, buoys = 4)!!, 0f)
        // a reach well inside the row keeps its own place (the buoys pass it): 60-min flag, 162 min = 5 buoys -> slot 2
        assertEquals(2f, WaitFlags.rowSlot(fc(60, 162), 5)!!, 0f)
    }

    // QA ruling 11 (d): at the cap (six buoys) the flag is never past the sixth buoy.
    @Test fun ruling11dAtTheCapTheFlagIsWithinTheSixthBuoy() {
        for (flag in WaitFlags.CHOICES) {
            val slot = WaitFlags.rowSlot(fc(flag, 900), buoys = 6)!!
            assertTrue("flag $flag", slot <= 6f)
            assertEquals(flag / 30f, slot, 0f)
        }
    }

    // F-1: under 30 minutes to the flag (time passed, or a 5-minute round-down) the flag is pinned to the first slot.
    @Test fun f1UnderThirtyMinutesIsPinnedToTheFirstSlot() {
        val f = WaitFlags.plant(30, mono, wall) // 14:07:30 + 30 min -> 14:35, 27.5 min away
        val reach = WaitFlags.forecast(f, mono, hero(400), null, false)!!
        assertTrue(reach.remainingMinutes < 30)
        assertEquals(1f, WaitFlags.rowSlot(reach, buoys = 6)!!, 0f)
        val shortTiny = WaitFlags.forecast(f, mono, hero(5), null, false)!!
        assertEquals(WaitOutcome.SHORT, shortTiny.outcome)
        assertEquals(1f, WaitFlags.rowSlot(shortTiny, buoys = 0)!!, 0f)
        val reachNoBuoy = WaitFlags.forecast(f, mono, hero(29), null, false)!!
        assertEquals(1f, WaitFlags.rowSlot(reachNoBuoy, buoys = 0)!!, 0f)
    }

    // F-8 (a): before 22:00 only flags at or before 22:00 are offered; at 21:00 the latest choice is 22:00.
    @Test fun f8ChoicesEndAtTwentyTwo() {
        assertEquals(listOf(30, 60), WaitFlags.choicesAt(21 * 60))
        assertTrue(WaitFlags.choicesAt(21 * 60).all { 21 * 60 + it <= 22 * 60 })
        assertEquals(WaitFlags.CHOICES, WaitFlags.choicesAt(16 * 60 + 30))
        assertEquals(listOf(30, 60, 90), WaitFlags.choicesAt(20 * 60 + 5))
        assertTrue(WaitFlags.choicesAt(21 * 60 + 31).isEmpty())
        // no choices in the night window, nor before 06:00
        assertTrue(WaitFlags.choicesAt(22 * 60).isEmpty())
        assertTrue(WaitFlags.choicesAt(5 * 60 + 59).isEmpty())
        assertEquals(WaitFlags.CHOICES, WaitFlags.choicesAt(6 * 60))
    }

    // F-8 (b): a flag planted before 22:00 hides at the 22:00 boundary (the night notice takes over).
    @Test fun f8FlagHidesAtTwentyTwo() {
        val f = WaitFlags.plant(60, mono, wall)
        assertTrue(WaitFlags.forecast(f, mono, hero(400), null, night = isNightHour(21)) != null)
        assertNull(WaitFlags.forecast(f, mono, hero(400), null, night = isNightHour(22)))
    }

    // F-2: under tomorrow's share there is no flag picture (the line says it does not compare).
    @Test fun noFlagPictureBelowTomorrowsShare() {
        val f = WaitFlags.plant(60, mono, wall)
        val below = WaitFlags.forecast(f, mono, hero(0, below = true), null, false)!!
        assertEquals(WaitOutcome.BELOW_KEEP, below.outcome)
        assertNull(WaitFlags.rowSlot(below, buoys = 0))
    }
}
