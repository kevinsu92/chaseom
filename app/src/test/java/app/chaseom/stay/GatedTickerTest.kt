package app.chaseom.stay

import app.chaseom.vehicle.ConnectionState
import app.chaseom.vehicle.DrivingRestriction
import app.chaseom.vehicle.GearPosition
import app.chaseom.vehicle.VehicleSnapshot
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Round 2 G1 / platform R-1: with the service kept through D the process lives while
 * driving, so the controller's 1 s tick must stop in D ("불필요한 타이머·폴링은 최소화").
 */
@OptIn(ExperimentalCoroutinesApi::class)
class GatedTickerTest {

    @Test fun ticksOnlyWhileTheGateIsOpen() = runTest {
        val gate = MutableStateFlow(true)
        var ticks = 0
        backgroundScope.launch { gatedTicker(gate).collect { ticks++ } }
        runCurrent()
        assertEquals(1, ticks) // immediate first tick
        advanceTimeBy(10_000); runCurrent()
        assertEquals(11, ticks) // one per second in P

        val atDrive = ticks
        gate.value = false // D
        runCurrent()
        advanceTimeBy(600_000); runCurrent() // 10 minutes of driving
        assertEquals(atDrive + 1, ticks) // only the single switch tick, no timer in D

        gate.value = true // P again
        runCurrent()
        advanceTimeBy(3_000); runCurrent()
        assertEquals(atDrive + 1 + 1 + 3, ticks)
    }

    @Test fun startingInDriveGivesOneTickThenNothing() = runTest {
        val gate = MutableStateFlow(false)
        var ticks = 0
        backgroundScope.launch { gatedTicker(gate).collect { ticks++ } }
        advanceTimeBy(60_000); runCurrent()
        assertEquals(1, ticks) // combine still gets its first value
    }

    @Test fun repeatedSameGateValueDoesNotRestartTheTick() = runTest {
        val gate = MutableStateFlow(true)
        var ticks = 0
        backgroundScope.launch { gatedTicker(gate).collect { ticks++ } }
        runCurrent()
        repeat(5) { gate.value = true; runCurrent() }
        assertEquals(1, ticks)
    }

    private fun snap(conn: ConnectionState, gear: GearPosition) = VehicleSnapshot(connection = conn, gear = gear)

    // The gate is open in P and while the gear is unknown (restored stay, car service restarting); closed in D/R/N.
    @Test fun parkedOfDecidesTheGate() {
        assertEquals(true, parkedOf(DrivingRestriction.PARKED, snap(ConnectionState.CONNECTED, GearPosition.PARK)))
        assertEquals(false, parkedOf(DrivingRestriction.DRIVING, snap(ConnectionState.CONNECTED, GearPosition.DRIVE)))
        assertEquals(false, parkedOf(DrivingRestriction.DRIVING, snap(ConnectionState.CONNECTED, GearPosition.REVERSE)))
        assertEquals(null, parkedOf(DrivingRestriction.DRIVING, snap(ConnectionState.CONNECTING, GearPosition.UNKNOWN)))
        assertEquals(null, parkedOf(DrivingRestriction.DRIVING, snap(ConnectionState.CONNECTED, GearPosition.UNKNOWN)))
    }
}
