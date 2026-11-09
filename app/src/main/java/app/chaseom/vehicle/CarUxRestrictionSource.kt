package app.chaseom.vehicle

import android.car.Car
import android.car.drivingstate.CarUxRestrictions
import android.car.drivingstate.CarUxRestrictionsManager
import android.content.Context
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * AAOS system signal: does the platform currently require distraction-optimized UI?
 * Complements the Pleos gear signal (Pleos SDK has no speed API). Value is null when the car
 * service is unavailable (e.g. JVM tests, non-automotive device), in which case the gate falls
 * back to gear only.
 */
class CarUxRestrictionSource(context: Context) {

    private val appContext = context.applicationContext
    private val _requiresDO = MutableStateFlow<Boolean?>(null)
    val requiresDistractionOptimization: StateFlow<Boolean?> = _requiresDO.asStateFlow()

    private var car: Car? = null
    private var manager: CarUxRestrictionsManager? = null

    private val listener = CarUxRestrictionsManager.OnUxRestrictionsChangedListener { r: CarUxRestrictions ->
        _requiresDO.value = r.isRequiresDistractionOptimization
    }

    @Synchronized
    fun start() {
        if (car != null) return
        try {
            val c = Car.createCar(appContext) ?: return
            val m = c.getCarManager(Car.CAR_UX_RESTRICTION_SERVICE) as? CarUxRestrictionsManager ?: return
            car = c
            manager = m
            _requiresDO.value = m.currentCarUxRestrictions?.isRequiresDistractionOptimization
            m.registerListener(listener)
        } catch (t: Throwable) {
            Log.w(TAG, "CarUxRestrictions unavailable: ${t.message}")
            _requiresDO.value = null
        }
    }

    @Synchronized
    fun stop() {
        try {
            manager?.unregisterListener()
            car?.disconnect()
        } catch (t: Throwable) {
            Log.w(TAG, "CarUxRestrictions stop failed: ${t.message}")
        }
        manager = null
        car = null
    }

    private companion object {
        const val TAG = "CarUxRestrictions"
    }
}
