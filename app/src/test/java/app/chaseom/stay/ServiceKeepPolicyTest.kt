package app.chaseom.stay

import app.chaseom.stay.ServiceKeepPolicy.Action
import app.chaseom.stay.ServiceKeepPolicy.Notice
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Round 2 G1: the stay service is started only at points
 * Android allows and is kept for the ignition cycle, so a stay that begins in the background is still protected.
 */
class ServiceKeepPolicyTest {

    /** A fake system: [allowed] says whether a startForegroundService would be accepted right now. */
    private class Sim(var allowed: Boolean) {
        val p = ServiceKeepPolicy()
        var starts = 0
        var notices = mutableListOf<Notice>()

        private fun start(staying: Boolean) {
            starts++
            p.onStartResult(allowed, ServiceKeepPolicy.noticeFor(staying))
        }

        fun tick(staying: Boolean, leftPark: Boolean) {
            if (p.onUpdate(staying, leftPark) == Action.START) start(staying)
            p.noticeChange(staying)?.let { notices += it }
        }

        fun screen(staying: Boolean) {
            allowed = true // a resumed activity may start a foreground service
            if (p.onScreenForeground() == Action.START) start(staying)
        }

        fun boot(onboarded: Boolean) {
            allowed = true // BOOT_COMPLETED is an exempted point
            if (p.onBoot(onboarded) == Action.START) start(false)
            allowed = false // afterwards the app is in the background
        }

        fun drive(seconds: Int) = repeat(seconds) { tick(staying = false, leftPark = true) }
        fun park(seconds: Int) = repeat(seconds) { tick(staying = true, leftPark = false) }
    }

    // G1 itself: the app was opened once, then sent to the back; later P happens in the background. The service is
    // already running, so no background start is needed and the stay is protected from its first second.
    @Test fun stayInBackgroundIsProtectedWhenTheAppWasOpenedEarlierInTheCycle() {
        val s = Sim(allowed = false)
        s.screen(staying = false) // driver opened the app while parked before the trip or at a red light screen
        s.allowed = false // then navigation in front
        s.drive(60)
        s.park(60)
        assertTrue(s.p.running)
        assertEquals(1, s.starts)
    }

    // Boot is the second allowed point: the app is never opened this cycle, the stay is still protected.
    @Test fun bootStartCoversACycleWithoutOpeningTheApp() {
        val s = Sim(allowed = false)
        s.boot(onboarded = true)
        s.drive(30)
        s.park(30)
        assertTrue(s.p.running)
        assertEquals(1, s.starts)
    }

    @Test fun bootDoesNothingBeforeOnboarding() {
        val s = Sim(allowed = false)
        s.boot(onboarded = false)
        assertFalse(s.p.running)
        assertEquals(0, s.starts)
    }

    // The service is never stopped by gear: P -> D -> P -> D sends exactly one start and no stop action exists.
    @Test fun leavingParkDoesNotStopTheService() {
        val s = Sim(allowed = true)
        repeat(5) { s.park(10); s.drive(10) }
        assertTrue(s.p.running)
        assertEquals(1, s.starts)
    }

    // While driving the notice carries no stay content; it changes only on the transitions, not every second.
    @Test fun noticeIsQuietWhileDrivingAndChangesOnlyOnTransitions() {
        val s = Sim(allowed = true)
        s.park(10)
        assertEquals(Notice.STAY, s.p.shown)
        s.drive(10)
        assertEquals(Notice.QUIET, s.p.shown)
        s.park(10)
        assertEquals(listOf(Notice.QUIET, Notice.STAY), s.notices) // the first STAY came with the start itself
    }

    @Test fun noNoticeUpdatesWhileTheServiceIsNotRunning() {
        val s = Sim(allowed = false)
        s.park(10)
        s.drive(10)
        assertTrue(s.notices.isEmpty())
        assertNull(s.p.shown)
    }

    // P-4 kept: a refused background start is tried once per stay, not every second.
    @Test fun refusedStartIsTriedOncePerStay() {
        val s = Sim(allowed = false)
        s.park(60)
        assertEquals(1, s.starts)
        assertTrue(s.p.refused)
        s.drive(5)
        s.park(60)
        assertEquals(2, s.starts)
    }

    // Unknown gear (neither staying nor left P) does not clear a refusal.
    @Test fun unknownGearDoesNotRetry() {
        val s = Sim(allowed = false)
        s.park(1)
        repeat(30) { s.tick(staying = false, leftPark = false) }
        s.park(1)
        assertEquals(1, s.starts)
    }

    // The screen coming to the front starts a refused (or never started) service once.
    @Test fun screenForegroundStartsTheServiceOnce() {
        val s = Sim(allowed = false)
        s.park(10)
        s.screen(staying = true)
        s.screen(staying = true)
        assertTrue(s.p.running)
        assertEquals(2, s.starts) // the refused one and the accepted one
        assertEquals(Notice.STAY, s.p.shown)
    }

    // The system stopped the service (or startForeground failed): the next allowed point starts it again.
    @Test fun serviceGoneIsRestartedAtTheNextAllowedPoint() {
        val s = Sim(allowed = true)
        s.park(5)
        s.p.onServiceGone()
        assertFalse(s.p.running)
        assertNull(s.p.shown)
        s.allowed = false
        s.park(5) // a background attempt in the same stay; refused
        assertEquals(2, s.starts)
        s.screen(staying = true)
        assertTrue(s.p.running)
        assertEquals(3, s.starts)
    }

    @Test fun noticeForMapsStayingOnly() {
        assertEquals(Notice.STAY, ServiceKeepPolicy.noticeFor(true))
        assertEquals(Notice.QUIET, ServiceKeepPolicy.noticeFor(false))
    }
}
