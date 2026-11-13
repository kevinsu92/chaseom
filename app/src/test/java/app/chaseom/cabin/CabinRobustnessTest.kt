package app.chaseom.cabin

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Round 2 G2, G3, G6 (05_qa/STATIC_REVIEW_G.md). */
class CabinRobustnessTest {

    // ---- G2: the poll stops feeding the SDK queue while the service gives no answer ------------------------------

    @Test fun g2HealthyServiceKeepsTheFullPoll() {
        val w = PollWatchdog()
        repeat(100) {
            assertEquals(PollWatchdog.Poll.FULL, w.onTick())
            w.onAnswer()
        }
        assertFalse(w.away)
    }

    @Test fun g2AfterFiveSilentTicksOnlyOneProbeEveryTenSeconds() {
        val w = PollWatchdog(quietTicks = 5, probeEveryTicks = 10)
        val polls = (1..3605).map { w.onTick() }
        assertEquals(List(5) { PollWatchdog.Poll.FULL }, polls.take(5))
        assertTrue(w.away)
        // Calls queued in an hour of outage: 5 full ticks x 6 reads, then 1 probe per 10 s (was about 21,600).
        val queued = polls.sumOf { when (it) { PollWatchdog.Poll.FULL -> 6; PollWatchdog.Poll.PROBE -> 1; else -> 0 }.toInt() }
        assertEquals(30 + 360, queued)
    }

    @Test fun g2AnyAnswerEvenLateRestoresTheFullPoll() {
        val w = PollWatchdog()
        repeat(40) { w.onTick() }
        assertTrue(w.away)
        w.onAnswer()
        assertFalse(w.away)
        assertEquals(PollWatchdog.Poll.FULL, w.onTick())
    }

    @Test fun g2ASlowServiceUnderFiveSecondsIsNeverAway() {
        val w = PollWatchdog()
        repeat(50) {
            repeat(4) { assertEquals(PollWatchdog.Poll.FULL, w.onTick()) }
            assertFalse(w.away)
            w.onAnswer()
        }
    }

    // ---- G3: a late driver-door open cannot overwrite the close that came after it --------------------------------

    @Test fun g3DeferredOpenIsDroppedWhenACloseArrivedMeanwhile() {
        val e = LatestEvent()
        val open = e.next("driver") // open: waits for the light read
        val close = e.next("driver") // close: applied at once
        assertFalse(e.isLatest("driver", open)) // the deferred open is skipped
        assertTrue(e.isLatest("driver", close))
    }

    @Test fun g3DeferredOpenStillAppliesWhenNothingCameAfter() {
        val e = LatestEvent()
        e.next("passenger")
        val open = e.next("driver")
        e.next("rearLeft") // other doors do not count
        assertTrue(e.isLatest("driver", open))
    }

    // ---- G6: losing the car service makes the charge port unknown, not its old value -----------------------------

    @Test fun g6CarServiceLossClearsPortAndOutsideButKeepsFixedFacts() {
        val before = CabinState(socPercent = 19, chargePortOpen = true, outsideTempC = -5f, batteryCapacityKwh = 77.4f, fuelTypes = listOf(10))
        val after = before.afterCarServiceLost()
        assertNull(after.chargePortOpen)
        assertNull(after.outsideTempC)
        assertEquals(77.4f, after.batteryCapacityKwh)
        assertEquals(listOf(10), after.fuelTypes)
        assertEquals(19, after.socPercent) // Pleos SDK values are not from the car service
    }
}
