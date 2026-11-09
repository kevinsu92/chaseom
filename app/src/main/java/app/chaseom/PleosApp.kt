package app.chaseom

import android.app.Application
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner

class PleosApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        container.stay.start()
        ProcessLifecycleOwner.get().lifecycle.addObserver(SdkLifecycleBinder(container))
    }
}

/**
 * Connects the vehicle signals when the app first comes to the foreground and keeps them for the
 * life of the process (FINDINGS M-01): a stay usually outlasts the time our screen is in front
 * (the driver opens music or navigation), and the receipt / backrest notice must still be decided
 * from live signals. Listeners are cheap ON_CHANGE subscriptions plus two 1 Hz polls; the platform
 * reclaims everything when it kills the process (Suspend after IGN OFF).
 */
class SdkLifecycleBinder(private val container: AppContainer) : DefaultLifecycleObserver {
    override fun onStart(owner: LifecycleOwner) {
        container.carUxRestrictions.start()
        container.vehicleRepository.start()
        container.cabinRepository.start()
    }
}
