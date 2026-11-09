package app.chaseom.vehicle

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** Scripted vehicle situations for UI development and demo videos without the emulator. */
enum class FakeScenario(val label: String) {
    PARKED("정차 (P단)"),
    DRIVING("주행 중 (D단)"),
    CHARGING("충전 중"),
    LOW_BATTERY("배터리 부족"),
    /** Parked -> driving -> parked loop; handy for recording the driving-gate transition. */
    DEMO_LOOP("데모 루프"),
}

/**
 * Debug-only fake. Never used in release builds (BuildConfig.FAKE_DATA_ALLOWED = false).
 * [scope] is injected so tests can drive virtual time.
 */
class FakeVehicleRepository(
    private val scope: CoroutineScope,
    initialScenario: FakeScenario = FakeScenario.PARKED,
    private val tickMillis: Long = 1_000L,
) : VehicleRepository {

    private val _state = MutableStateFlow(VehicleSnapshot())
    override val state: StateFlow<VehicleSnapshot> = _state.asStateFlow()
    override val source = DataSource.FAKE

    private val _scenario = MutableStateFlow(initialScenario)
    val scenario: StateFlow<FakeScenario> = _scenario.asStateFlow()

    private var job: Job? = null

    @Synchronized
    override fun start() {
        if (job != null) return
        job = scope.launch { run(_scenario.value) }
    }

    @Synchronized
    override fun stop() {
        job?.cancel()
        job = null
        _state.update { it.copy(connection = ConnectionState.DISCONNECTED) }
    }

    /** Switch scenario at runtime (debug panel). Restarts the script if running. */
    @Synchronized
    fun setScenario(s: FakeScenario) {
        _scenario.value = s
        if (job != null) {
            job?.cancel()
            job = scope.launch { run(s) }
        }
    }

    private suspend fun run(s: FakeScenario) {
        _state.value = baseline(s)
        when (s) {
            FakeScenario.PARKED -> Unit
            FakeScenario.DRIVING -> drainWhileDriving()
            FakeScenario.CHARGING -> charge()
            FakeScenario.LOW_BATTERY -> drainWhileDriving(floor = 3)
            FakeScenario.DEMO_LOOP -> demoLoop()
        }
    }

    private suspend fun drainWhileDriving(floor: Int = 10) {
        while (scope.isActive) {
            delay(tickMillis * 5)
            _state.update {
                val soc = ((it.evSocPercent ?: 80) - 1).coerceAtLeast(floor)
                it.copy(evSocPercent = soc, rangeRemaining = soc * RANGE_M_PER_PERCENT)
            }
        }
    }

    private suspend fun charge() {
        while (scope.isActive) {
            delay(tickMillis * 2)
            _state.update {
                val soc = ((it.evSocPercent ?: 20) + 1).coerceAtMost(100)
                it.copy(evSocPercent = soc, rangeRemaining = soc * RANGE_M_PER_PERCENT, isCharging = soc < 100)
            }
        }
    }

    private suspend fun demoLoop() {
        val steps = listOf(GearPosition.PARK, GearPosition.DRIVE, GearPosition.PARK)
        var i = 0
        while (scope.isActive) {
            _state.update { it.copy(gear = steps[i % steps.size]) }
            i++
            delay(tickMillis * 10)
        }
    }

    companion object {
        /** ~4.5 km per percent: plausible for a 77 kWh EV with ~450 km range. */
        const val RANGE_M_PER_PERCENT = 4_500f

        fun baseline(s: FakeScenario): VehicleSnapshot {
            val base = VehicleSnapshot(connection = ConnectionState.CONNECTED, chargePortOpen = false, isCharging = false)
            return when (s) {
                FakeScenario.PARKED -> base.copy(gear = GearPosition.PARK, evSocPercent = 72)
                FakeScenario.DRIVING -> base.copy(gear = GearPosition.DRIVE, evSocPercent = 64)
                FakeScenario.CHARGING -> base.copy(gear = GearPosition.PARK, evSocPercent = 35, chargePortOpen = true, isCharging = true)
                FakeScenario.LOW_BATTERY -> base.copy(gear = GearPosition.DRIVE, evSocPercent = 8)
                FakeScenario.DEMO_LOOP -> base.copy(gear = GearPosition.PARK, evSocPercent = 58)
            }.let { it.copy(rangeRemaining = (it.evSocPercent ?: 0) * RANGE_M_PER_PERCENT) }
        }
    }
}
