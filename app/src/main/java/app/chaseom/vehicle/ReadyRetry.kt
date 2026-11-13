package app.chaseom.vehicle

/**
 * Round 2 G5: if the gear registration fails when the car service comes up (no
 * CarPropertyManager, or the register throws), the gear stayed FAILED for the rest of the process. Now it is tried
 * again, at most [maxRetries] time(s) per car-service connection, [DELAY_MS] later. Each new connection
 * (onLost, then onReady) gets a fresh chance; a success resets the count.
 *
 * Until then the state stays FAILED and the gear UNKNOWN, which DrivingStateGate treats as driving (fails safe).
 */
class ReadyRetry(private val maxRetries: Int = 1) {
    private var used = 0

    /** A registration failed: true if one more attempt should be scheduled. */
    @Synchronized
    fun onFailure(): Boolean = (used < maxRetries).also { if (it) used++ }

    /** Registered, or the connection was lost (the next connection starts over). */
    @Synchronized
    fun reset() { used = 0 }

    companion object {
        const val DELAY_MS = 2_000L
    }
}
