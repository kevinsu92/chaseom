package app.chaseom.vehicle

import ai.pleos.playground.vehicle.Vehicle
import ai.pleos.playground.vehicle.listener.EvBatteryStateOfChargeListener
import android.car.Car
import android.car.VehiclePropertyIds
import android.car.hardware.CarPropertyValue
import android.car.hardware.property.CarPropertyManager
import android.content.Context
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Gear from the documented AAOS property `GEAR_SELECTION` (CAR_POWERTRAIN, normal permission), EV SOC
 * from the Pleos Vehicle SDK. The Pleos `Vehicle.getGear()` domain is not in the public Pleos
 * documentation, so it is not used (02_platform API_MAP: the official alternative is GEAR_SELECTION).
 * Other cabin signals live in PleosCabinRepository.
 *
 * Signatures verified against Vehicle-2.3.2.aar (class files) on 2026-11-08.
 * Each domain read is async: getXxx(onComplete, onFailed). Each listener extends VehicleListener
 * (onFailed). A property that is not supported simply stays null and records lastError.
 */
class PleosVehicleRepository(context: Context) : VehicleRepository {

    private val appContext = context.applicationContext
    private val _state = MutableStateFlow(VehicleSnapshot())
    override val state: StateFlow<VehicleSnapshot> = _state.asStateFlow()
    override val source = DataSource.PLEOS_SDK

    private var vehicle: Vehicle? = null

    private var car: Car? = null
    private var propertyManager: CarPropertyManager? = null
    private val gearCallback = object : CarPropertyManager.CarPropertyEventCallback {
        override fun onChangeEvent(value: CarPropertyValue<*>) {
            val g = value.value as? Int
            Log.i("PROBE", "gear=$g wall=${System.currentTimeMillis()}")
            _state.update { it.copy(gear = aaosGearToPosition(g)) }
        }
        override fun onErrorEvent(propertyId: Int, areaId: Int) = recordError("gear", null)
    }
    private val socListener = object : EvBatteryStateOfChargeListener {
        override fun onEvBatteryStateOfChargeUpdated(value: Int?) = _state.update { it.copy(evSocPercent = value) }
        override fun onFailed(e: Exception) = recordError("evSoc", e)
    }

    @Synchronized
    override fun start() {
        if (vehicle != null) return
        _state.update { it.copy(connection = ConnectionState.CONNECTING, lastError = null) }
        val v = try {
            Vehicle(appContext).also { it.initialize() }
        } catch (t: Throwable) {
            Log.e(TAG, "Vehicle.initialize failed", t)
            _state.update { it.copy(connection = ConnectionState.FAILED, lastError = t.message) }
            return
        }
        vehicle = v
        _state.update { it.copy(connection = ConnectionState.CONNECTED) }

        // Initial reads (async), then subscribe for changes. Each call is guarded separately so one
        // unsupported domain does not prevent the others from working.
        guard("gear") {
            val c = Car.createCar(appContext) ?: error("Car service not available")
            val cpm = c.getCarManager(Car.PROPERTY_SERVICE) as? CarPropertyManager ?: error("no CarPropertyManager")
            car = c
            propertyManager = cpm
            val g = cpm.getProperty<Int>(VehiclePropertyIds.GEAR_SELECTION, 0)?.value
            _state.update { it.copy(gear = aaosGearToPosition(g)) }
            cpm.registerCallback(gearCallback, VehiclePropertyIds.GEAR_SELECTION, CarPropertyManager.SENSOR_RATE_ONCHANGE)
        }
        guard("evSoc") {
            v.getEvBattery().getEvBatteryStateOfCharge(
                { s -> _state.update { it.copy(evSocPercent = s) } },
                { e -> recordError("evSoc", e) },
            )
            v.getEvBattery().registerEvBatteryStateOfCharge(socListener)
        }
    }

    @Synchronized
    override fun stop() {
        val v = vehicle ?: return
        guard("unregister") {
            propertyManager?.unregisterCallback(gearCallback)
            car?.disconnect()
            v.getEvBattery().unregisterEvBatteryStateOfCharge(socListener)
        }
        propertyManager = null
        car = null
        guard("release") { v.release() }
        vehicle = null
        _state.update { it.copy(connection = ConnectionState.DISCONNECTED) }
    }

    private inline fun guard(what: String, block: () -> Unit) {
        try {
            block()
        } catch (t: Throwable) {
            recordError(what, t)
        }
    }

    private fun recordError(what: String, t: Throwable?) {
        Log.w(TAG, "$what failed: ${t?.message}")
        _state.update { it.copy(lastError = "$what: ${t?.message}") }
    }

    private companion object {
        const val TAG = "PleosVehicleRepo"
    }
}

/** AAOS `VehicleGear` values (android.car.VehicleGear): 1 N, 2 R, 4 P, 8 D, 16.. manual gears. */
internal fun aaosGearToPosition(gear: Int?): GearPosition = when (gear) {
    4 -> GearPosition.PARK
    2 -> GearPosition.REVERSE
    1 -> GearPosition.NEUTRAL
    null, 0 -> GearPosition.UNKNOWN
    else -> if (gear == 8 || gear >= 16) GearPosition.DRIVE else GearPosition.UNKNOWN
}
