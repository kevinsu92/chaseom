package app.chaseom.vehicle

import org.junit.Assert.assertEquals
import org.junit.Test

class DrivingStateGateTest {

    private fun snap(gear: GearPosition, conn: ConnectionState = ConnectionState.CONNECTED) =
        VehicleSnapshot(connection = conn, gear = gear)

    @Test fun parkGearUnlocksParkedUi() =
        assertEquals(DrivingRestriction.PARKED, DrivingStateGate.restrictionFor(snap(GearPosition.PARK)))

    @Test fun driveReverseNeutralAreDriving() {
        listOf(GearPosition.DRIVE, GearPosition.REVERSE, GearPosition.NEUTRAL).forEach {
            assertEquals(DrivingRestriction.DRIVING, DrivingStateGate.restrictionFor(snap(it)))
        }
    }

    @Test fun unknownGearFailsSafeToDriving() =
        assertEquals(DrivingRestriction.DRIVING, DrivingStateGate.restrictionFor(snap(GearPosition.UNKNOWN)))

    @Test fun systemUxRestrictionOverridesParkGear() =
        assertEquals(DrivingRestriction.DRIVING, DrivingStateGate.restrictionFor(snap(GearPosition.PARK), uxRequiresDO = true))

    @Test fun unavailableUxSignalFallsBackToGear() =
        assertEquals(DrivingRestriction.PARKED, DrivingStateGate.restrictionFor(snap(GearPosition.PARK), uxRequiresDO = null))

    @Test fun disconnectedFailsSafeToDrivingEvenIfLastGearWasPark() =
        assertEquals(
            DrivingRestriction.DRIVING,
            DrivingStateGate.restrictionFor(snap(GearPosition.PARK, ConnectionState.DISCONNECTED)),
        )

    // AAOS GEAR_SELECTION values replace the undocumented Pleos getGear().
    @Test fun aaosGearValuesMapToPositions() {
        assertEquals(GearPosition.PARK, aaosGearToPosition(4))
        assertEquals(GearPosition.DRIVE, aaosGearToPosition(8))
        assertEquals(GearPosition.DRIVE, aaosGearToPosition(16))
        assertEquals(GearPosition.REVERSE, aaosGearToPosition(2))
        assertEquals(GearPosition.NEUTRAL, aaosGearToPosition(1))
        assertEquals(GearPosition.UNKNOWN, aaosGearToPosition(0))
        assertEquals(GearPosition.UNKNOWN, aaosGearToPosition(null))
    }

    // Gear read errors: a value whose status is not AVAILABLE is UNKNOWN, and UNKNOWN is driving.
    @Test fun gearWithUnavailableOrErrorStatusIsUnknownAndCountsAsDriving() {
        assertEquals(GearPosition.PARK, gearFromProperty(CAR_STATUS_AVAILABLE, 4))
        listOf(1, 2).forEach { status -> // STATUS_UNAVAILABLE, STATUS_ERROR
            val g = gearFromProperty(status, 4)
            assertEquals(GearPosition.UNKNOWN, g)
            assertEquals(DrivingRestriction.DRIVING, DrivingStateGate.restrictionFor(snap(g)))
        }
        assertEquals(GearPosition.UNKNOWN, gearFromProperty(CAR_STATUS_AVAILABLE, null))
    }
}
