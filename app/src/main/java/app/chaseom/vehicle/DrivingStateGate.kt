package app.chaseom.vehicle

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn

/**
 * UI restriction level derived from vehicle state.
 *  - PARKED: full UI allowed (text input, long lists, video, settings).
 *  - DRIVING: distraction-optimized UI only (glanceable, large targets, no keyboard / video / long lists).
 */
enum class DrivingRestriction { PARKED, DRIVING }

/**
 * Fail-safe gate. PARKED requires ALL of:
 *  - vehicle connected and gear explicitly PARK (Pleos Vehicle SDK), and
 *  - AAOS CarUxRestrictions does not require distraction optimization (null = unavailable -> ignored).
 * UNKNOWN gear / disconnected is treated as DRIVING, so a missing signal can never expose
 * parked-only content while the car moves.
 */
object DrivingStateGate {
    fun restrictionFor(snapshot: VehicleSnapshot, uxRequiresDO: Boolean? = null): DrivingRestriction {
        val gearParked = snapshot.connection == ConnectionState.CONNECTED && snapshot.gear == GearPosition.PARK
        val systemAllows = uxRequiresDO != true
        return if (gearParked && systemAllows) DrivingRestriction.PARKED else DrivingRestriction.DRIVING
    }

    fun observe(
        repo: VehicleRepository,
        scope: CoroutineScope,
        uxRequiresDO: Flow<Boolean?> = flowOf(null),
    ): StateFlow<DrivingRestriction> =
        combine(repo.state, uxRequiresDO, ::restrictionFor)
            .distinctUntilChanged()
            .stateIn(scope, SharingStarted.Eagerly, restrictionFor(repo.state.value))
}
