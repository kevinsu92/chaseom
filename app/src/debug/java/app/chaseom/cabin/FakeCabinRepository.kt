package app.chaseom.cabin

import app.chaseom.vehicle.DataSource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Debug-only cabin data for running the UI without the Pleos service (never used in release:
 * AppContainer only picks it when BuildConfig.FAKE_DATA_ALLOWED). Values mirror the emulator's
 * defaults measured in the D1 probe (EV, 77.4 kWh).
 */
class FakeCabinRepository : CabinRepository {
    override val source = DataSource.FAKE
    private val _state = MutableStateFlow(
        CabinState(
            connected = true, socPercent = 62, socReadable = true, batteryCapacityKwh = 77.4f, fuelTypes = listOf(10),
            hvacPowerOn = true, acOn = false, fanSpeed = 3, driverSetTempC = 22f, recirculationOn = false,
            driverSeatHeat = 0, passengerSeatHeat = 0, driverBackrest = 60, passengerBackrest = 60, backrestMin = 0, backrestMax = 100,
            rearLeftOccupied = false, rearRightOccupied = false, driverDoorOpen = false,
            windows = mapOf("driver" to 0, "passenger" to 0, "rearLeft" to 0, "rearRight" to 0),
            cabinLightOn = false, chargePortOpen = false,
        ),
    )
    override val state: StateFlow<CabinState> = _state.asStateFlow()

    fun edit(transform: (CabinState) -> CabinState) = _state.update(transform)

    override fun start() = Unit
    override fun stop() = Unit
}
