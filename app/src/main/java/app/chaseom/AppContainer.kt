package app.chaseom

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import app.chaseom.cabin.CabinRepository
import app.chaseom.cabin.PleosCabinRepository
import app.chaseom.stay.StayController
import app.chaseom.vehicle.CarConnection
import app.chaseom.vehicle.CarUxRestrictionSource
import app.chaseom.vehicle.DrivingRestriction
import app.chaseom.vehicle.DrivingStateGate
import app.chaseom.vehicle.PleosVehicleRepository
import app.chaseom.vehicle.VehicleRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.StateFlow

/**
 * Manual DI container (no Hilt: fewer moving parts).
 *
 * Data source selection:
 *  - release: always the real Pleos SDK (review guideline forbids dummy data in release).
 *  - debug:   real SDK when the Pleos vehicle service is installed (Pleos Connect emulator / car),
 *             otherwise fakes, so the UI can be developed on a plain AAOS image.
 *
 * The fake implementations live in the debug source set only (src/debug), so the release APK
 * does not contain them at all.
 */
class AppContainer(context: Context) {

    private val appContext = context.applicationContext
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    /** The single AAOS car service connection, shared by gear, cabin properties and UX restrictions. */
    val carConnection = CarConnection(appContext)

    private val useFakes = BuildConfig.FAKE_DATA_ALLOWED && !isServicePresent(VEHICLE_ACTION)

    val vehicleRepository: VehicleRepository =
        (if (useFakes) Fakes.vehicle(appScope) else null) ?: PleosVehicleRepository(carConnection)

    val cabinRepository: CabinRepository =
        (if (useFakes) Fakes.cabin() else null) ?: PleosCabinRepository(appContext, appScope, carConnection)

    val carUxRestrictions = CarUxRestrictionSource(carConnection)

    // With fake vehicle data the scripted gear drives the gate on its own (demo/test mode);
    // with the real SDK, the AAOS CarUxRestrictions signal is combined in as well.
    val drivingRestriction: StateFlow<DrivingRestriction> =
        if (vehicleRepository.source == app.chaseom.vehicle.DataSource.FAKE) {
            DrivingStateGate.observe(vehicleRepository, appScope)
        } else {
            DrivingStateGate.observe(vehicleRepository, appScope, carUxRestrictions.requiresDistractionOptimization)
        }

    val stay = StayController(appContext, appScope, cabinRepository, drivingRestriction, vehicleRepository.state)

    /**
     * Connects every vehicle signal; idempotent. Called when the app first comes to the front (SdkLifecycleBinder)
     * and, round 2 G1, from BootReceiver: a process started at boot has no screen, but the kept stay service needs
     * live gear and cabin signals.
     */
    fun connectSignals() {
        // Users subscribe first; the one car connection then serves them all (and again after a car service restart).
        carUxRestrictions.start()
        vehicleRepository.start()
        cabinRepository.start()
        carConnection.start()
    }

    private fun isServicePresent(action: String): Boolean {
        val pm = appContext.packageManager
        val intent = Intent(action)
        return pm.queryIntentServices(intent, PackageManager.MATCH_ALL).isNotEmpty() ||
            pm.queryIntentActivities(intent, PackageManager.MATCH_ALL).isNotEmpty()
    }

    private companion object {
        // Action declared in the <queries> of the Pleos Vehicle SDK AAR manifest.
        const val VEHICLE_ACTION = "ai.pleos.playground.vehicle.VEHICLE_START"
    }
}
