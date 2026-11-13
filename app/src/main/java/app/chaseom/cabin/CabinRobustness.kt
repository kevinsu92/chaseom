package app.chaseom.cabin

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/**
 * Round 2 G2: the Pleos SDK has no disconnect callback. During a Pleos service outage every
 * call made by the 1 s poll goes into the SDK's unbounded internal queue (6 per tick, about 21,600 an hour) and is
 * replayed in one burst on reconnect.
 *
 * The watchdog counts poll ticks since the last answer of any kind (value or failure). After [quietTicks] ticks with no
 * answer at all it treats the service as away: the full poll stops and only one probe read goes out every
 * [probeEveryTicks] ticks, so the queue grows by at most one call per [probeEveryTicks] seconds. The first answer,
 * even a late one, restores the full poll.
 *
 * It only throttles the poll and drives `CabinState.connected`. It never clears a value: a slow but healthy service
 * must not hide SOC.
 *
 * Ticks come from the main thread; answers come from SDK binder threads, hence the atomics.
 */
class PollWatchdog(private val quietTicks: Int = 5, private val probeEveryTicks: Int = 10) {
    enum class Poll { FULL, PROBE, SKIP }

    private val ticksSinceAnswer = AtomicLong(0)

    /** True while no answer has come for [quietTicks] ticks. */
    val away: Boolean get() = ticksSinceAnswer.get() >= quietTicks

    /** H2 item 8: poll ticks since the last answer of any kind (the reconnect rule measures silence with it). */
    val silentTicks: Long get() = ticksSinceAnswer.get()

    /** Called once per poll tick, before the reads; says which reads to send. */
    fun onTick(): Poll {
        val missed = ticksSinceAnswer.getAndIncrement()
        if (missed < quietTicks) return Poll.FULL
        return if ((missed - quietTicks) % probeEveryTicks == 0L) Poll.PROBE else Poll.SKIP
    }

    /** Any reply from the service, success or failure. */
    fun onAnswer() = ticksSinceAnswer.set(0)

    fun reset() = ticksSinceAnswer.set(0)
}

/**
 * Round 2 G3: an event that is applied later (the driver-door open waits for one light read, up to 300 ms) must not
 * overwrite a newer event for the same key that was applied at once. Each event takes a token; a deferred apply runs
 * only if its token is still the latest for that key.
 */
class LatestEvent {
    private val seq = ConcurrentHashMap<String, AtomicInteger>()

    fun next(key: String): Int = seq.getOrPut(key) { AtomicInteger(0) }.incrementAndGet()

    fun isLatest(key: String, token: Int): Boolean = seq[key]?.get() == token
}

/**
 * Round 2 G6: what the AAOS car service's loss clears. Values that come from AAOS listeners (outside temperature,
 * charge port) can change while we cannot hear them, so they become unknown; a receipt built in the gap then says
 * "확인 안 됨" rather than the old state. Fuel type and battery capacity are fixed facts of the car and are kept.
 */
fun CabinState.afterCarServiceLost(): CabinState = copy(outsideTempC = null, chargePortOpen = null)
