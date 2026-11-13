package app.chaseom.stay

/**
 * When StayController starts [StayService], and what its notification says. A pure class (no Android types), so every
 * rule is a JVM test (ServiceKeepPolicyTest). Replaces round 2's ServiceStartGate.
 *
 * Problem: the stay usually begins while another app is in front
 * (navigation, music). On API 31+ a foreground service cannot be started from the background, so build G and round 2
 * ran the stay with no service: a cached process, which the system may freeze or kill.
 *
 * Rule now: the service is started only at points where Android allows it, and once it runs it is **kept for the
 * whole ignition cycle**, through D, instead of being stopped when the car leaves P. Then the moment of P needs no
 * start at all, wherever the driver's screen is. Allowed start points:
 *  - [onScreenForeground]: our Activity is resumed (an app with a visible activity may start one);
 *  - [onBoot]: ACTION_BOOT_COMPLETED (an exempted broadcast), only after onboarding, i.e. the driver chose to use the app;
 *  - [onUpdate] while a stay is open: one attempt per stay, as before. It succeeds only when the app is in front or
 *    already exempt; a refusal waits for the next allowed point instead of retrying every second (P-4).
 * There is no STOP action: leaving P only changes the notification to [Notice.QUIET] (no stay content while driving).
 * The process (and so the service) ends with the ignition cycle (power off) or when the system kills it.
 *
 * All calls happen on the main thread (StayController on Dispatchers.Main.immediate, Activity, receiver and service
 * callbacks on main).
 */
class ServiceKeepPolicy {

    enum class Action { NONE, START }

    /** What the ongoing notification shows: the stay text in P, a neutral line with no stay content otherwise. */
    enum class Notice { STAY, QUIET }

    /** The last start was accepted and the service has not gone since. */
    var running = false
        private set

    /** The last start was refused; [onUpdate] does not try again until the screen is in front or the stay ends. */
    var refused = false
        private set

    /** Notice currently shown by the running service; null when the service is not running. */
    var shown: Notice? = null
        private set

    /**
     * Called on every controller update (each signal change and once per second).
     * @param staying a parked stay is on screen (StayUi.parked).
     * @param leftPark the car is known to be out of P. Unknown gear (null) is neither.
     */
    fun onUpdate(staying: Boolean, leftPark: Boolean): Action {
        if (leftPark) refused = false // the next stay may try once more
        return if (staying && !running && !refused) Action.START else Action.NONE
    }

    /** Our Activity is resumed: always an allowed point. Starts the service if it is not running. */
    fun onScreenForeground(): Action {
        refused = false
        return if (running) Action.NONE else Action.START
    }

    /** ACTION_BOOT_COMPLETED: an allowed point, used only once the driver has finished onboarding. */
    fun onBoot(onboarded: Boolean): Action = if (onboarded && !running) Action.START else Action.NONE

    /** Result of a START just sent, with the notice the start carried. */
    fun onStartResult(accepted: Boolean, notice: Notice) {
        running = accepted
        refused = !accepted
        shown = if (accepted) notice else null
    }

    /** The service was destroyed (system stop, or a failed startForeground): the next allowed point starts it again. */
    fun onServiceGone() {
        running = false
        shown = null
    }

    /** The notice to post now, or null when nothing changes (no repeated notify every second). */
    fun noticeChange(staying: Boolean): Notice? {
        if (!running) return null
        val want = noticeFor(staying)
        if (want == shown) return null
        shown = want
        return want
    }

    companion object {
        fun noticeFor(staying: Boolean): Notice = if (staying) Notice.STAY else Notice.QUIET
    }
}
