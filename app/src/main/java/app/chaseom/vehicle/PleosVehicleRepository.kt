package app.chaseom.vehicle

import android.car.Car
import android.car.VehiclePropertyIds
import android.car.hardware.CarPropertyValue
import android.car.hardware.property.CarPropertyManager
import android.util.Log
import app.chaseom.Probe
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Gear from the documented AAOS property `GEAR_SELECTION` (CAR_POWERTRAIN, normal permission). The Pleos
 * `Vehicle.getGear()` domain is not in the public Pleos documentation, so it is not used.
 * All other signals, SOC included, live in PleosCabinRepository (build G: the Pleos `Vehicle` is created
 * once, there; before, this class made a second `Vehicle` only to read SOC again).
 *
 * Connection state follows the shared [CarConnection]: CONNECTED only after the car service is up AND the
 * first gear read answered; when the car service goes away the gear becomes UNKNOWN (treated as driving)
 * and the state returns to CONNECTING until the service is back and the callback is registered again.
 */
class PleosVehicleRepository(private val carConnection: CarConnection) : VehicleRepository, CarUser {

    private val _state = MutableStateFlow(VehicleSnapshot())
    override val state: StateFlow<VehicleSnapshot> = _state.asStateFlow()
    override val source = DataSource.PLEOS_SDK

    private var subscribed = false
    @Volatile private var propertyManager: CarPropertyManager? = null
    private val gearCallback = object : CarPropertyManager.CarPropertyEventCallback {
        override fun onChangeEvent(value: CarPropertyValue<*>) {
            // A late event from a manager dropped by onLost() must not bring back PARK / CONNECTED.
            if (propertyManager == null) return
            val g = value.value as? Int
            Probe.log("gear=$g status=${value.status}")
            _state.update { it.copy(gear = gearFromProperty(value.status, g), connection = ConnectionState.CONNECTED) }
        }

        // A gear we cannot read is treated as driving (UNKNOWN fails safe in DrivingStateGate).
        override fun onErrorEvent(propertyId: Int, areaId: Int) {
            _state.update { it.copy(gear = GearPosition.UNKNOWN) }
            recordError("gear", null)
        }
    }

    @Synchronized
    override fun start() {
        if (subscribed) return
        subscribed = true
        _state.update { it.copy(connection = ConnectionState.CONNECTING, lastError = null) }
        carConnection.subscribe(this)
    }

    @Synchronized
    override fun onReady(handle: Car) {
        try {
            val cpm = handle.getCarManager(Car.PROPERTY_SERVICE) as? CarPropertyManager ?: error("no CarPropertyManager")
            propertyManager = cpm
            // Subscribe first: if the one-off read below fails, the next gear event still connects us.
            cpm.registerCallback(gearCallback, VehiclePropertyIds.GEAR_SELECTION, CarPropertyManager.SENSOR_RATE_ONCHANGE)
            val p = try {
                cpm.getProperty<Int>(VehiclePropertyIds.GEAR_SELECTION, 0)
            } catch (t: Throwable) {
                recordError("gearRead", t)
                null
            }
            if (p != null) {
                _state.update { it.copy(gear = gearFromProperty(p.status, p.value), connection = ConnectionState.CONNECTED, lastError = null) }
            }
        } catch (t: Throwable) {
            _state.update { it.copy(gear = GearPosition.UNKNOWN, connection = ConnectionState.FAILED) }
            recordError("gear", t)
        }
    }

    /** Car service crashed or is restarting: old managers are invalid, gear is unknown until it is back. */
    @Synchronized
    override fun onLost() {
        propertyManager = null
        _state.update { it.copy(gear = GearPosition.UNKNOWN, connection = ConnectionState.CONNECTING) }
    }

    @Synchronized
    override fun stop() {
        try {
            propertyManager?.unregisterCallback(gearCallback)
        } catch (t: Throwable) {
            recordError("unregister", t)
        }
        propertyManager = null
        carConnection.unsubscribe(this)
        subscribed = false
        _state.update { it.copy(connection = ConnectionState.DISCONNECTED, gear = GearPosition.UNKNOWN) }
    }

    private fun recordError(what: String, t: Throwable?) {
        Log.w(TAG, "$what failed: ${t?.message}")
        _state.update { it.copy(lastError = "$what: ${t?.message}") }
    }

    private companion object {
        const val TAG = "PleosVehicleRepo"
    }
}

/** `CarPropertyValue.STATUS_AVAILABLE`; kept as a constant so the mapping is testable without the car library. */
internal const val CAR_STATUS_AVAILABLE = 0

/** A gear value counts only when the property status is AVAILABLE; otherwise it is UNKNOWN (treated as driving). */
internal fun gearFromProperty(status: Int, gear: Int?): GearPosition =
    if (status != CAR_STATUS_AVAILABLE) GearPosition.UNKNOWN else aaosGearToPosition(gear)

/** AAOS `VehicleGear` values (android.car.VehicleGear): 1 N, 2 R, 4 P, 8 D, 16.. manual gears. */
internal fun aaosGearToPosition(gear: Int?): GearPosition = when (gear) {
    4 -> GearPosition.PARK
    2 -> GearPosition.REVERSE
    1 -> GearPosition.NEUTRAL
    null, 0 -> GearPosition.UNKNOWN
    else -> if (gear == 8 || gear >= 16) GearPosition.DRIVE else GearPosition.UNKNOWN
}
