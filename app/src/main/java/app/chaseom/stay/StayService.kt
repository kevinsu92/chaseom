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
        try {
            if (android.os.Build.VERSION.SDK_INT >= 34) {
                startForeground(NOTIFICATION_ID, notification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
            } else {
                startForeground(NOTIFICATION_ID, notification()) // minSdk 28; Pleos Connect is API 34
            }
            foreground = true
        } catch (t: Throwable) {
            Log.w(TAG, "startForeground failed: ${t.message}")
            requested = false; pendingStop = false
            stopSelf()
            return START_NOT_STICKY
        }
        // A stop that came before startForeground (P -> D right after P) is done only now.
        if (pendingStop) {
            pendingStop = false; requested = false; foreground = false
            stopSelf()
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        // Only foreground: requested / pendingStop may already belong to a newer start() (D -> P while this
        // instance's destroy is still queued). The system delivers this destroy before the next create/start.
        foreground = false
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

        // Main-thread state. A service started with startForegroundService must reach startForeground before it
        // stops; stopping it earlier (gear D within a moment of P, e.g. right after a cold start) makes the system
        // crash the app with ForegroundServiceDidNotStartInTimeException. So a stop before that point is deferred.
        @Volatile private var requested = false
        @Volatile private var foreground = false
        @Volatile private var pendingStop = false

        /** true when the start was accepted; false (background start not allowed, API 31+) lets the caller retry. */
        fun start(context: Context): Boolean = try {
            context.startForegroundService(Intent(context, StayService::class.java))
            requested = true
            pendingStop = false
            true
        } catch (t: Throwable) {
            // The app process still runs the stay while it lives; StayController tries again on its next update.
            Log.w(TAG, "start refused: ${t.message}")
            false
        }

        fun stop(context: Context) {
            if (!requested) return
            if (foreground) {
                requested = false; foreground = false
                context.stopService(Intent(context, StayService::class.java))
            } else {
                pendingStop = true // onStartCommand stops it right after startForeground
            }
        }
    }
}
