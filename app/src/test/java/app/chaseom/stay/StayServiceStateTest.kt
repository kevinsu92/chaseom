package app.chaseom.stay

import app.chaseom.stay.StayServiceState.AfterStart
import app.chaseom.stay.StayServiceState.StopAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Build G, review S2/S4/S5: every scenario of REVIEW_F2B_SERVICE.md as a JVM test. */
class StayServiceStateTest {

    @Test fun stopWithoutStartDoesNothing() = assertEquals(StopAction.NONE, StayServiceState().stop())

    @Test fun normalStartThenStop() {
        val s = StayServiceState()
        s.onStartAccepted()
        assertEquals(AfterStart.KEEP_RUNNING, s.onStartCommand(true))
        assertEquals(StopAction.STOP_SERVICE_NOW, s.stop())
        assertFalse(s.requested || s.foreground || s.pendingStop)
    }

    // P then D before onStartCommand: stop is deferred until startForeground (the crash fix).
    @Test fun stopBeforeStartForegroundIsDeferred() {
        val s = StayServiceState()
        s.onStartAccepted()
        assertEquals(StopAction.DEFERRED, s.stop())
        assertEquals(AfterStart.STOP_AFTER_FOREGROUND, s.onStartCommand(true))
        assertFalse(s.requested || s.foreground || s.pendingStop)
    }

    // P, D, P before onStartCommand: the second start cancels the pending stop; both commands keep it up.
    @Test fun restartCancelsPendingStop() {
        val s = StayServiceState()
        s.onStartAccepted(); s.stop(); s.onStartAccepted()
        assertEquals(AfterStart.KEEP_RUNNING, s.onStartCommand(true))
        assertEquals(AfterStart.KEEP_RUNNING, s.onStartCommand(true))
        assertTrue(s.foreground)
        assertEquals(StopAction.STOP_SERVICE_NOW, s.stop())
    }

    // P, D, P, D before onStartCommand: stopped right after the first startForeground.
    @Test fun flickerEndsStopped() {
        val s = StayServiceState()
        s.onStartAccepted(); s.stop(); s.onStartAccepted(); s.stop()
        assertEquals(AfterStart.STOP_AFTER_FOREGROUND, s.onStartCommand(true))
        assertFalse(s.requested)
    }

    // S1 regression: the old instance's onDestroy must not wipe a newer request.
    @Test fun oldDestroyKeepsNewRequest() {
        val s = StayServiceState()
        s.onStartAccepted(); s.onStartCommand(true)
        assertEquals(StopAction.STOP_SERVICE_NOW, s.stop()) // gear D
        s.onStartAccepted()                                 // gear P again
        s.onDestroy()                                       // the old instance's destroy arrives late
        assertEquals(AfterStart.KEEP_RUNNING, s.onStartCommand(true))
        assertEquals(StopAction.STOP_SERVICE_NOW, s.stop()) // gear D stops it: not left running while driving
    }

    // S1 regression, deferred path: pendingStop survives the old destroy.
    @Test fun oldDestroyKeepsPendingStop() {
        val s = StayServiceState()
        s.onStartAccepted(); s.stop(); s.onStartCommand(true) // stopSelf
        s.onStartAccepted(); s.stop()                         // new start, D again before its startForeground
        s.onDestroy()
        assertEquals(AfterStart.STOP_AFTER_FOREGROUND, s.onStartCommand(true))
    }

    // S2: startForeground throws -> flags reset so the controller can start again; no stale pending stop.
    @Test fun startForegroundFailureResets() {
        val s = StayServiceState()
        s.onStartAccepted(); s.stop()
        assertEquals(AfterStart.STOP_FAILED, s.onStartCommand(false))
        assertFalse(s.requested || s.foreground || s.pendingStop)
        assertEquals(StopAction.NONE, s.stop())
        s.onStartAccepted()
        assertEquals(AfterStart.KEEP_RUNNING, s.onStartCommand(true))
    }

    // The system stops the service on its own: requested stays true; the next stop only defers, the next start clears it.
    @Test fun systemStopIsHarmless() {
        val s = StayServiceState()
        s.onStartAccepted(); s.onStartCommand(true)
        s.onDestroy()
        assertEquals(StopAction.DEFERRED, s.stop())
        s.onStartAccepted()
        assertFalse(s.pendingStop)
        assertEquals(AfterStart.KEEP_RUNNING, s.onStartCommand(true))
    }

    // S4: the deferred path tells the caller to remove the notification and stop, never to keep running in D.
    @Test fun deferredStopNeverKeepsRunning() {
        val s = StayServiceState()
        s.onStartAccepted(); s.stop()
        assertEquals(AfterStart.STOP_AFTER_FOREGROUND, s.onStartCommand(true))
        assertFalse(s.foreground)
    }
}
