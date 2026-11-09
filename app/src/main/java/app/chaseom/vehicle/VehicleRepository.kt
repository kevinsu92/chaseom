package app.chaseom.vehicle

import kotlinx.coroutines.flow.StateFlow

/** App-level gear abstraction, decoupled from the Pleos SDK enum. */
enum class GearPosition { PARK, REVERSE, NEUTRAL, DRIVE, UNKNOWN }

/** Where the data comes from. Shown in debug UI so a demo never silently uses fake data. */
enum class DataSource { PLEOS_SDK, FAKE }

enum class ConnectionState { DISCONNECTED, CONNECTING, CONNECTED, FAILED }

/**
 * Immutable snapshot of the vehicle state the app cares about.
 * Every value is nullable: a property may be unsupported on the current vehicle/emulator.
 */
data class VehicleSnapshot(
    val connection: ConnectionState = ConnectionState.DISCONNECTED,
    val gear: GearPosition = GearPosition.UNKNOWN,
    /** EV battery state of charge, percent 0..100. */
    val evSocPercent: Int? = null,
    /** Fuel level, percent 0..100 (ICE / hybrid). */
    val fuelPercent: Int? = null,
    /** Remaining range as reported by the vehicle (unit as delivered by the SDK, expected meters). */
    val rangeRemaining: Float? = null,
    val chargePortOpen: Boolean? = null,
    val isCharging: Boolean? = null,
    val lastError: String? = null,
)

/**
 * Single source of vehicle state for the UI.
 *
 * Lifecycle contract:
 *  - [start] connects (Pleos: `Vehicle(context).initialize()`) and registers listeners. Idempotent.
 *  - [stop] unregisters listeners and releases the SDK (`Vehicle.release()`). Idempotent.
 * [VehicleLifecycleBinder] calls these from the process lifecycle so config changes do not churn.
 */
interface VehicleRepository {
    val source: DataSource
    val state: StateFlow<VehicleSnapshot>
    fun start()
    fun stop()
}
