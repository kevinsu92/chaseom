package app.chaseom.cabin

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Build G (a): occupancy follows the verified Pleos SDK mapping (true = AAOS VACANT), not an emulator guess. */
class OccupancyTest {

    @Test fun verifiedSdkTrueMeansVacant() {
        assertEquals(false, Occupancy.resolve(true, "2.3.2"))
    }

    // false = OCCUPIED or UNKNOWN in SDK 2.3.2: shown as "탑승 감지됨", which sends the driver to look.
    @Test fun verifiedSdkFalseMeansOccupiedOrUnknown() {
        assertEquals(true, Occupancy.resolve(false, "2.3.2"))
    }

    @Test fun unverifiedSdkIsUnknown() {
        assertNull(Occupancy.resolve(true, "2.4.0"))
        assertNull(Occupancy.resolve(false, "2.4.0"))
        assertNull(Occupancy.resolve(true, ""))
    }

    // A failed read never becomes "empty": the receipt shows "확인 안 됨".
    @Test fun nullStaysNull() = assertNull(Occupancy.resolve(null, "2.3.2"))

    // An SDK upgrade must re-check the mapping first: the pinned version has to be in the verified set.
    @Test fun pinnedSdkVersionIsVerified() {
        val toml = listOf("gradle/libs.versions.toml", "../gradle/libs.versions.toml").map(::File).first { it.exists() }
        val v = Regex("""(?m)^pleosVehicle\s*=\s*"([^"]+)"""").find(toml.readText())!!.groupValues[1]
        assertTrue("Pleos SDK $v: check SeatOccupancyListener mapping, then add it to VERIFIED_SDK_VERSIONS", v in Occupancy.VERIFIED_SDK_VERSIONS)
    }
}
