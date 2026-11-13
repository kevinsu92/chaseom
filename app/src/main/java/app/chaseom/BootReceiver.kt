package app.chaseom

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Round 2 G1 (03_engineering/round2_G1_fix.md): at boot, an exempted point for a background foreground-service start,
 * connect the vehicle signals and start the stay service (only once the driver has finished onboarding), so the first
 * P of the ignition cycle is counted even if the app is never opened. Runs on the main thread.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val container = (context.applicationContext as PleosApp).container
        container.connectSignals()
        container.stay.onBoot()
    }
}
