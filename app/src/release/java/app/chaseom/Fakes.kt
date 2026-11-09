package app.chaseom

import app.chaseom.cabin.CabinRepository
import app.chaseom.vehicle.VehicleRepository
import kotlinx.coroutines.CoroutineScope

/** Release builds contain no fake vehicle data at all (review guideline: no dummy data in release). */
object Fakes {
    @Suppress("UNUSED_PARAMETER")
    fun vehicle(scope: CoroutineScope): VehicleRepository? = null
    fun cabin(): CabinRepository? = null
}
