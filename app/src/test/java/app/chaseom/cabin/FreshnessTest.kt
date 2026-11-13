package app.chaseom.cabin

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** H2 item 8: stale-value rule and SDK reconnect policy (cabin.Freshness). */
class FreshnessTest {

    private val s = 1_000L

    @Test fun thresholdIsSixtySecondsAndRefreshIsTenTicks() {
        assertEquals(60_000L, Freshness.STALE_MS)
        assertEquals(10, Freshness.REFRESH_TICKS)
        // The 60 s window holds 5 missed refreshes, so one slow reply never trips it.
        assertTrue(Freshness.STALE_MS / (Freshness.REFRESH_TICKS * 1_000L) >= 5)
    }

    @Test fun neverStampedIsNotJudged() {
        assertFalse(Freshness.stale(confirmedAtMs = null, sinceMs = 0, nowMs = 10 * 60 * s))
    }

    @Test fun freshUpToSixtySecondsStaleAfter() {
        assertFalse(Freshness.stale(confirmedAtMs = 0, sinceMs = 0, nowMs = 60 * s))
        assertTrue(Freshness.stale(confirmedAtMs = 0, sinceMs = 0, nowMs = 60 * s + 1))
    }

    @Test fun aNewConfirmationRestartsTheWindow() {
        assertTrue(Freshness.stale(confirmedAtMs = 0, sinceMs = 0, nowMs = 90 * s))
        assertFalse(Freshness.stale(confirmedAtMs = 85 * s, sinceMs = 0, nowMs = 90 * s))
    }

    // A value unchanged since long before the stay (climate during a drive) gets the full window from the stay start.
    @Test fun graceStartsWhenTheStayScreenBeginsWatching() {
        val stayStart = 30 * 60 * s
        assertFalse(Freshness.stale(confirmedAtMs = 0, sinceMs = stayStart, nowMs = stayStart))
        assertFalse(Freshness.stale(confirmedAtMs = 0, sinceMs = stayStart, nowMs = stayStart + 60 * s))
        assertTrue(Freshness.stale(confirmedAtMs = 0, sinceMs = stayStart, nowMs = stayStart + 61 * s))
    }

    @Test fun reconnectNotBeforeSixtySecondsOfSilence() {
        val p = ReconnectPolicy()
        assertFalse(p.shouldAttempt(silentForMs = 59 * s, nowMs = 100 * s))
        assertTrue(p.shouldAttempt(silentForMs = 60 * s, nowMs = 100 * s))
    }

    @Test fun reconnectBacksOffSixtyThenOneTwentyThenEveryFiveMinutes() {
        val p = ReconnectPolicy()
        var t = 0L
        assertTrue(p.shouldAttempt(60 * s, t))
        assertFalse(p.shouldAttempt(120 * s, t + 59 * s))
        t += 60 * s
        assertTrue(p.shouldAttempt(120 * s, t))
        assertFalse(p.shouldAttempt(240 * s, t + 119 * s))
        t += 120 * s
        assertTrue(p.shouldAttempt(240 * s, t))
        t += 300 * s
        assertTrue(p.shouldAttempt(540 * s, t))
        assertFalse(p.shouldAttempt(600 * s, t + 299 * s)) // stays at 5 min
        assertTrue(p.shouldAttempt(840 * s, t + 300 * s))
    }

    @Test fun anAnswerResetsTheBackoff() {
        val p = ReconnectPolicy()
        assertTrue(p.shouldAttempt(60 * s, 0))
        assertTrue(p.shouldAttempt(120 * s, 60 * s))
        p.onAnswer()
        // Silence must build up again, and the first delay is 60 s again.
        assertFalse(p.shouldAttempt(10 * s, 61 * s))
        assertTrue(p.shouldAttempt(60 * s, 61 * s))
        assertFalse(p.shouldAttempt(119 * s, 120 * s))
        assertTrue(p.shouldAttempt(120 * s, 121 * s))
    }

    @Test fun initRetryBackoffGrowsAndHoldsAtTheLastStep() {
        val b = Backoff(listOf(5_000L, 15_000L, 60_000L))
        assertEquals(listOf(5_000L, 15_000L, 60_000L, 60_000L), List(4) { b.next() })
        b.reset()
        assertEquals(5_000L, b.next())
    }

    @Test fun watchdogReportsSilentTicks() {
        val w = PollWatchdog()
        repeat(7) { w.onTick() }
        assertEquals(7L, w.silentTicks)
        w.onAnswer()
        assertEquals(0L, w.silentTicks)
    }
}
