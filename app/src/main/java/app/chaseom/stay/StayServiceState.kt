package app.chaseom.stay

/**
 * The stay service's start / stop rules as a pure class (no Android types), so every case in the F2b review
 * (03_engineering/REVIEW_F2B_SERVICE.md) is a JVM test (StayServiceStateTest) instead of a reading.
 *
 * Why the rules exist: a service started with startForegroundService must reach startForeground before it stops.
 * Stopping it earlier (gear D a moment after P, e.g. right after a cold start) makes the system crash the app with
 * ForegroundServiceDidNotStartInTimeException. So a stop that comes before that point is deferred.
 *
 * All calls happen on the main thread (StayController on Dispatchers.Main.immediate; service callbacks on main).
 */
class StayServiceState {
    var requested = false
        private set
    var foreground = false
        private set
    var pendingStop = false
        private set

    /** What [stop] asks the caller to do. */
    enum class StopAction { NONE, STOP_SERVICE_NOW, DEFERRED }

    /** What the service does right after its startForeground attempt. */
    enum class AfterStart { KEEP_RUNNING, STOP_AFTER_FOREGROUND, STOP_FAILED }

    /** startForegroundService was accepted. A newer start cancels a stop still waiting for startForeground. */
    fun onStartAccepted() {
        requested = true
        pendingStop = false
    }

    fun stop(): StopAction = when {
        !requested -> StopAction.NONE
        foreground -> {
            requested = false
            foreground = false
            StopAction.STOP_SERVICE_NOW
        }
        else -> {
            pendingStop = true // onStartCommand stops it right after startForeground
            StopAction.DEFERRED
        }
    }

    /**
     * Called from onStartCommand after the startForeground attempt.
     *  - success, no pending stop: the service stays in the foreground.
     *  - success with a pending stop: stop now; the caller removes the notification first (review S4: no ongoing
     *    notification left in gear D), then stopSelf.
     *  - failure (review S2): flags reset so the controller can start again on its next update. The caller still has
     *    to stopSelf; on API 34 the only realistic throw is a manifest-type SecurityException, which the manifest covers.
     */
    fun onStartCommand(startForegroundOk: Boolean): AfterStart {
        if (!startForegroundOk) {
            requested = false
            pendingStop = false
            foreground = false
            return AfterStart.STOP_FAILED
        }
        foreground = true
        if (pendingStop) {
            pendingStop = false
            requested = false
            foreground = false
            return AfterStart.STOP_AFTER_FOREGROUND
        }
        return AfterStart.KEEP_RUNNING
    }

    /**
     * Only [foreground]: requested / pendingStop may already belong to a newer start (D -> P while this instance's
     * destroy is still queued; review S1). The system delivers this destroy before the next create/start.
     */
    fun onDestroy() {
        foreground = false
    }
}
