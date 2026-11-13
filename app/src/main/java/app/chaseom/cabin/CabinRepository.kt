package app.chaseom.cabin

import app.chaseom.vehicle.DataSource
import kotlinx.coroutines.flow.StateFlow

/**
 * Cabin signals used by the "stay" features (S1/S2/S4/S7). Every field is nullable: null means
 * "not received / not supported", and the domain logic must treat null conservatively
 * (e.g. rear seat null is shown as "not detected - check yourself", never as empty).
 */
data class CabinState(
    val connected: Boolean = false,
    // Energy
    val socPercent: Int? = null,
    val batteryCapacityKwh: Float? = null,
    val socReadable: Boolean? = null,
    /** AAOS INFO_FUEL_TYPE values (android.car.FuelType ints). null = could not read. */
    val fuelTypes: List<Int>? = null,
    val fuelTypeReadFailed: Boolean = false,
    /** km per kWh as reported by the vehicle, if any. */
    val evEfficiencyKmPerKwh: Float? = null,
    val chargePortOpen: Boolean? = null,
    /** AAOS ENV_OUTSIDE_TEMPERATURE in Celsius (0.5 C steps); null = not readable (model assumes -10 C). */
    val outsideTempC: Float? = null,
    // Climate (driver zone)
    val hvacPowerOn: Boolean? = null,
    val acOn: Boolean? = null,
    val fanSpeed: Int? = null,
    val driverSetTempC: Float? = null,
    val recirculationOn: Boolean? = null,
    /** Driver seat heat level: >0 heat, <0 ventilation, 0 off. */
    val driverSeatHeat: Int? = null,
    /** Passenger seat heater level; used only by the weather island. */
    val passengerSeatHeat: Int? = null,
    /** H2 item 10: front passenger seat occupancy (Occupancy.resolve, true = detected or sensor unknown). */
    val passengerOccupied: Boolean? = null,
    /** H2 item 10: HVAC dual mode on = passenger zone unsynced from the driver. */
    val hvacDualOn: Boolean? = null,
    /** H2 item 10: passenger zone set temperature (C). */
    val passengerSetTempC: Float? = null,
    // Seats
    val driverBackrest: Int? = null,
    val passengerBackrest: Int? = null,
    /** Capability bounds for the backrest position (min = fully reclined per AAOS definition). */
    val backrestMin: Int? = null,
    val backrestMax: Int? = null,
    val rearLeftOccupied: Boolean? = null,
    val rearRightOccupied: Boolean? = null,
    // Body
    /** null = not reported or unreadable: never treated as closed. */
    val driverDoorOpen: Boolean? = null,
    /** Only doors with a known position; an unreadable door is left out (unknown, not closed). */
    val doorsOpen: Map<String, Boolean> = emptyMap(),
    /** Window position per window (0 = closed). */
    val windows: Map<String, Int> = emptyMap(),
    val cabinLightOn: Boolean? = null,
    val readingLightOn: Boolean? = null,
    /** Front wiper lever on AUTO (Pleos getCurrentWiperSwitch); null = not readable (then no rain input). */
    val wiperAuto: Boolean? = null,
    /** Front wiper actually wiping (Pleos WiperState.ON). */
    val wiperOn: Boolean? = null,
    val lastError: String? = null,
    /**
     * H2 item 8: monotonic time (SystemClock.elapsedRealtime) the car last delivered an SOC value, by listener or read.
     * null = never stamped (not judged; see [Freshness]).
     */
    val socConfirmedAtMs: Long? = null,
    /** H2 item 8: the same for the climate (power, A/C, fan, set temperature, recirculation, driver seat heat). */
    val climateConfirmedAtMs: Long? = null,
    /**
     * BUG_HVAC_OFF R2: the same for the climate power alone. Fan, temperature or seat events renew
     * [climateConfirmedAtMs] but not this, so a power value the car stopped confirming still goes stale.
     */
    val powerConfirmedAtMs: Long? = null,
) {
    /** Window positions 0..10; [WINDOW_UNKNOWN] = the car did not report this window. */
    val anyWindowOpen: Boolean?
        get() = when {
            windows.values.any { it > 0 } -> true
            windows.isEmpty() || windows.values.any { it == WINDOW_UNKNOWN } -> null // unknown is never "closed"
            else -> false
        }
    val windowsKnown: Boolean get() = windows.isNotEmpty() && windows.values.none { it == WINDOW_UNKNOWN }
    val openWindowCount: Int get() = windows.values.count { it > 0 }

    /** Records one door; [open] null (unreadable) removes it, so it reads as unknown rather than closed. */
    fun withDoor(key: String, open: Boolean?): CabinState {
        val doors = if (open == null) doorsOpen - key else doorsOpen + (key to open)
        return copy(doorsOpen = doors, driverDoorOpen = doors["driver"])
    }
}

/** Door position from the SDK: > 0 open, 0 closed, null unknown. */
fun doorOpenFromPosition(position: Int?): Boolean? = position?.let { it > 0 }

const val WINDOW_UNKNOWN = -1

interface CabinRepository {
    val source: DataSource
    val state: StateFlow<CabinState>
    fun start()
    fun stop()

    /** Polled signals (lights, wiper lever, backrest) are read only while [active] (a parked stay is open). */
    fun setPolling(active: Boolean) = Unit
}
