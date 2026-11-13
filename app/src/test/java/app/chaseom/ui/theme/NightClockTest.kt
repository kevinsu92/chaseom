package app.chaseom.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Round 2: the night picture follows the clock as well as the vehicle theme (REVIEW_BUILD_G_RELEASE #3). */
class NightClockTest {
    @Test fun eveningAndNightCapturesAreNight() {
        // H10 21:00 and H11 22:00 were day pictures on a light-theme emulator.
        assertTrue(NightClock.isNight(systemDark = false, hour = 21))
        assertTrue(NightClock.isNight(systemDark = false, hour = 22))
        assertTrue(NightClock.isNight(systemDark = false, hour = 18))
        assertTrue(NightClock.isNight(systemDark = false, hour = 0))
        assertTrue(NightClock.isNight(systemDark = false, hour = 5))
    }

    @Test fun dayHoursFollowTheVehicleTheme() {
        for (h in 6..17) {
            assertFalse(NightClock.isNight(systemDark = false, hour = h))
            assertTrue(NightClock.isNight(systemDark = true, hour = h)) // the car's dark theme always wins
        }
    }

    @Test fun clockIsReadOnTheMinute() {
        assertEquals(60_000L, NightClock.msToNextMinute(120_000L))
        assertEquals(1L, NightClock.msToNextMinute(179_999L))
        assertEquals(30_000L, NightClock.msToNextMinute(30_000L))
    }
}
