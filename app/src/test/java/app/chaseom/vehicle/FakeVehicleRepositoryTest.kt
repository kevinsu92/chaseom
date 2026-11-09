package app.chaseom.vehicle

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class FakeVehicleRepositoryTest {

    private fun TestScope.repo(s: FakeScenario) =
        FakeVehicleRepository(backgroundScope, s, tickMillis = 1_000L).also { it.start(); runCurrent() }

    @Test fun parkedScenarioIsParked() = runTest {
        val r = repo(FakeScenario.PARKED)
        assertEquals(GearPosition.PARK, r.state.value.gear)
        assertEquals(DrivingRestriction.PARKED, DrivingStateGate.restrictionFor(r.state.value))
    }

    @Test fun drivingScenarioDrainsBattery() = runTest {
        val r = repo(FakeScenario.DRIVING)
        val before = r.state.value.evSocPercent!!
        advanceTimeBy(10_001)
        assertTrue(r.state.value.evSocPercent!! < before)
        assertEquals(DrivingRestriction.DRIVING, DrivingStateGate.restrictionFor(r.state.value))
    }

    @Test fun chargingScenarioIncreasesSoc() = runTest {
        val r = repo(FakeScenario.CHARGING)
        val before = r.state.value.evSocPercent!!
        advanceTimeBy(4_001)
        assertTrue(r.state.value.evSocPercent!! > before)
        assertEquals(true, r.state.value.isCharging)
    }

    @Test fun lowBatteryNeverBelowFloor() = runTest {
        val r = repo(FakeScenario.LOW_BATTERY)
        advanceTimeBy(600_000)
        assertTrue(r.state.value.evSocPercent!! >= 3)
    }

    @Test fun demoLoopTogglesGate() = runTest {
        val r = repo(FakeScenario.DEMO_LOOP)
        assertEquals(GearPosition.PARK, r.state.value.gear)
        advanceTimeBy(10_001)
        assertEquals(GearPosition.DRIVE, r.state.value.gear)
    }

    @Test fun stopIsIdempotentAndDisconnects() = runTest {
        val r = repo(FakeScenario.PARKED)
        r.stop(); r.stop()
        assertEquals(ConnectionState.DISCONNECTED, r.state.value.connection)
        assertEquals(DrivingRestriction.DRIVING, DrivingStateGate.restrictionFor(r.state.value))
    }
}
