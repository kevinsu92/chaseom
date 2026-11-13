package app.chaseom.cabin

/**
 * H2 item 8 (external review §11): stale-value check for the two inputs of the stay estimate, SOC and climate.
 *
 * The Pleos SDK gives no disconnect callback (05_qa/STATIC_REVIEW_G.md G2). If its service stops answering, SOC and
 * climate keep their last values and the hero would keep showing minutes computed from a frozen battery.
 *
 * The repository stamps every SOC or climate value the car actually delivered, through a listener or a read. During a
 * stay it also re-reads SOC and the climate power state every [REFRESH_TICKS] poll ticks, so a healthy but unchanged
 * value is re-confirmed every 10 s. A value counts as stale when it has not been confirmed for [STALE_MS]. The stay
 * screen then says "확인 안 됨" and hides the estimate instead of showing stale minutes.
 *
 * Why 60 s:
 * - It tolerates 5 missed 10 s refreshes. One slow binder reply never makes the hero flicker, and neither does the
 *   PollWatchdog's 5 s "away" threshold (which only throttles the poll).
 * - Within 60 s the true SOC cannot move by one display step. The highest parked climate load in the model is about
 *   3.5 kW (StayEngine.MAX_PLAUSIBLE_KW note). 60 s at 3.5 kW is about 0.06 kWh, about 0.1 % of a 58 kWh pack, and
 *   SOC arrives in 1 % steps. So a value up to 60 s old is as good as a fresh one for the estimate.
 * - Past 60 s with no answer at all, the service is not delivering. Then the next SOC step could already have been
 *   missed, and the minutes stop being an honest estimate.
 *
 * A value that was never stamped (null) is not judged. Only the Pleos repository stamps; the debug fake and the unit
 * tests' hand-built states have no stamps and keep their old behaviour, so no demo number changes.
 */
object Freshness {
    const val STALE_MS = 60_000L

    /** During a stay, SOC and climate power are re-read every this many 1 s poll ticks (10 s). */
    const val REFRESH_TICKS = 10

    /**
     * True when the value was confirmed once ([confirmedAtMs] not null) and nothing confirmed it for more than [limitMs].
     * [sinceMs] is when the stay screen began watching: a value that was simply unchanged before the stay (for example
     * the climate during a drive) gets a full [limitMs] from that moment to be re-read, so the stay start never flickers.
     */
    fun stale(confirmedAtMs: Long?, sinceMs: Long, nowMs: Long, limitMs: Long = STALE_MS): Boolean {
        if (confirmedAtMs == null) return false
        return nowMs - maxOf(confirmedAtMs, sinceMs) > limitMs
    }
}

/**
 * H2 item 8: when the SDK reconnects itself. The SDK re-binds on its own after its service restarts (STATIC_REVIEW_G),
 * but nothing recovers if the bind itself is lost for good or `Vehicle.initialize()` failed at start. This policy
 * decides when the repository releases the [ai.pleos.playground.vehicle.Vehicle] and creates a new one.
 *
 * - Only after [silentMs] with no answer of any kind from the service. That is the same 60 s as the stale-value rule,
 *   so the hero already says "확인 안 됨" before any reconnect happens. A slow but answering service is never torn down.
 * - Then at most once per backoff step (60 s, 120 s, then every 300 s), so a missing service is not hammered.
 * - Any answer resets it.
 *
 * Pure (no Android types), so every rule is unit-tested.
 */
class ReconnectPolicy(
    private val silentMs: Long = Freshness.STALE_MS,
    private val backoff: Backoff = Backoff(listOf(60_000L, 120_000L, 300_000L)),
) {
    private var nextAtMs: Long? = null

    /** True when a reconnect should start now. The caller then starts exactly one. */
    fun shouldAttempt(silentForMs: Long, nowMs: Long): Boolean {
        if (silentForMs < silentMs) return false
        val next = nextAtMs
        if (next != null && nowMs < next) return false
        nextAtMs = nowMs + backoff.next()
        return true
    }

    /** Any reply from the service, value or failure. */
    fun onAnswer() {
        nextAtMs = null
        backoff.reset()
    }
}

/** Delays that grow per call and stay at the last one; [reset] starts over. */
class Backoff(private val delaysMs: List<Long>) {
    init {
        require(delaysMs.isNotEmpty())
    }

    private var step = 0

    fun next(): Long = delaysMs[step.coerceAtMost(delaysMs.lastIndex)].also { step++ }

    fun reset() {
        step = 0
    }
}
