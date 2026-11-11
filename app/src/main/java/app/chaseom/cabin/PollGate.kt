package app.chaseom.cabin

/**
 * Decides when the 1 s polling runs: only while a parked stay is open and the vehicle is connected.
 * [start] begins polling; [stop] ends it and clears the polled values. Calls are idempotent.
 */
class PollGate(private val start: () -> Unit, private val stop: () -> Unit) {
    private var wanted = false
    private var ready = false
    var running = false
        private set

    /** A parked stay is open (true) or not (false). */
    fun want(active: Boolean) {
        wanted = active
        apply()
    }

    /** The vehicle connection is up (true) or gone (false). */
    fun ready(up: Boolean) {
        ready = up
        apply()
    }

    private fun apply() {
        val run = wanted && ready
        if (run == running) return
        running = run
        if (run) start() else stop()
    }
}
