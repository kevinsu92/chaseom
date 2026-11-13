package app.chaseom.cabin

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Build G (a): no release-wide inversion; AAOS SEAT_OCCUPANCY wins when readable; Pleos inverted on the emulator only. */
class OccupancyTest {

    @Test fun aaosStatesMapByPlatformMeaning() {
        assertEquals(true, Occupancy.fromAaos(Occupancy.AAOS_OCCUPIED))
        assertEquals(false, Occupancy.fromAaos(Occupancy.AAOS_VACANT))
        assertNull(Occupancy.fromAaos(Occupancy.AAOS_UNKNOWN))
        assertNull(Occupancy.fromAaos(null))
        assertNull(Occupancy.fromAaos(7))
    }

    @Test fun aaosWinsOverPleosInEitherBuild() {
        for (inv in listOf(true, false)) for (p in listOf(true, false, null)) {
            assertEquals(true, Occupancy.resolve(Occupancy.AAOS_OCCUPIED, p, inv))
            assertEquals(false, Occupancy.resolve(Occupancy.AAOS_VACANT, p, inv))
        }
    }

    @Test fun realCarUsesPleosValueAsIs() {
        assertEquals(true, Occupancy.resolve(null, true, invertPleos = false))
        assertEquals(false, Occupancy.resolve(null, false, invertPleos = false))
        assertEquals(true, Occupancy.resolve(Occupancy.AAOS_UNKNOWN, true, invertPleos = false))
    }

    @Test fun emulatorInvertsPleosValue() {
        assertEquals(true, Occupancy.resolve(null, false, invertPleos = true))
        assertEquals(false, Occupancy.resolve(null, true, invertPleos = true))
    }

    // A failed read never becomes "empty": the receipt shows "확인 안 됨".
    @Test fun unknownStaysUnknown() {
        assertNull(Occupancy.resolve(null, null, true))
        assertNull(Occupancy.resolve(Occupancy.AAOS_UNKNOWN, null, false))
    }

    @Test fun emulatorDetection() {
        assertTrue(Occupancy.isEmulatorBuild("ranchu", "", ""))
        assertTrue(Occupancy.isEmulatorBuild("goldfish", null, null))
        assertTrue(Occupancy.isEmulatorBuild("x", "generic/sdk_car_x86_64/emulator_car_x86_64:14/UAA1/1:userdebug/test-keys", "x"))
        assertTrue(Occupancy.isEmulatorBuild("x", "Android/sdk_gcar_x86_64/emulator64:14/x", "sdk_gcar_x86_64"))
        assertFalse(Occupancy.isEmulatorBuild("qcom", "hyundai/ccnc/ccnc:14/UP1A/123:user/release-keys", "ccnc"))
        assertFalse(Occupancy.isEmulatorBuild(null, null, null))
    }
}
