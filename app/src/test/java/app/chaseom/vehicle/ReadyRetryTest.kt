package app.chaseom.vehicle

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Round 2 G5: a failed gear registration is tried once more per car-service connection. */
class ReadyRetryTest {
    @Test fun g5OneRetryPerConnection() {
        val r = ReadyRetry()
        assertTrue(r.onFailure())
        assertFalse(r.onFailure()) // the retry failed too: stay FAILED (gear UNKNOWN, treated as driving)
    }

    @Test fun g5ANewConnectionOrASuccessGivesAFreshChance() {
        val r = ReadyRetry()
        r.onFailure(); r.onFailure()
        r.reset() // onLost then onReady, or a successful registration
        assertTrue(r.onFailure())
    }
}
