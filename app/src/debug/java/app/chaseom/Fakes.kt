package app.chaseom

import app.chaseom.cabin.CabinRepository
import app.chaseom.cabin.FakeCabinRepository
import app.chaseom.vehicle.FakeVehicleRepository
import app.chaseom.vehicle.VehicleRepository
import kotlinx.coroutines.CoroutineScope

/** Debug builds only: scripted vehicle data for a plain AAOS image without the Pleos service. */
object Fakes {
    fun vehicle(scope: CoroutineScope): VehicleRepository? = FakeVehicleRepository(scope)
    fun cabin(): CabinRepository? = FakeCabinRepository()
}
