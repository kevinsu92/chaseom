package app.chaseom.stay

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import android.util.Log
import app.chaseom.MainActivity
import app.chaseom.R

/**
 * Foreground service held only while a stay is running (gear P). It keeps the process, and so the
 * vehicle listeners and [StayController], alive while the driver uses another app, so the exit
 * receipt and the 1-hour / backrest notices are still decided from live signals.
 * It does no work itself. Type "specialUse" (no other FGS type matches "watch parked-car signals").
 *
 * No POST_NOTIFICATIONS runtime request: on API 33+ the ongoing notification may then be hidden,
 * but the service still runs (runtime permission count stays 0).
 */
class StayService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val ok = try {
            if (android.os.Build.VERSION.SDK_INT >= 34) {
                startForeground(NOTIFICATION_ID, notification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
            } else {
                startForeground(NOTIFICATION_ID, notification()) // minSdk 28; Pleos Connect is API 34
            }
            true
        } catch (t: Throwable) {
            Log.w(TAG, "startForeground failed: ${t.message}")
            false
        }
        when (state.onStartCommand(ok)) {
            StayServiceState.AfterStart.KEEP_RUNNING -> Unit
            StayServiceState.AfterStart.STOP_AFTER_FOREGROUND -> {
                // A stop that came before startForeground (P -> D right after P) is done only now. Remove the
                // notification first so it does not stay up in gear D (review S4).
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
            StayServiceState.AfterStart.STOP_FAILED -> stopSelf()
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        state.onDestroy()
        super.onDestroy()
    }

    private fun notification(): Notification {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, getString(R.string.svc_channel), NotificationManager.IMPORTANCE_LOW),
        )
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE,
        )
        return Notification.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(getString(R.string.svc_title))
            .setContentText(getString(R.string.svc_text))
            .setContentIntent(open)
            .setOngoing(true)
            .build()
    }

    companion object {
        private const val TAG = "StayService"
        private const val CHANNEL = "stay"
        private const val NOTIFICATION_ID = 1

        // Main-thread state; the rules are in StayServiceState (JVM-tested).
        private val state = StayServiceState()

        /** true when the start was accepted; false (background start not allowed, API 31+) lets the caller retry. */
        fun start(context: Context): Boolean = try {
            context.startForegroundService(Intent(context, StayService::class.java))
            state.onStartAccepted()
            true
        } catch (t: Throwable) {
            // The app process still runs the stay while it lives; StayController tries again on its next update.
            Log.w(TAG, "start refused: ${t.message}")
            false
        }

        fun stop(context: Context) {
            if (state.stop() == StayServiceState.StopAction.STOP_SERVICE_NOW) {
                context.stopService(Intent(context, StayService::class.java))
            }
        }
    }
}
