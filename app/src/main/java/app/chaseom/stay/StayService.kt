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
 * Foreground service that keeps the process, and so the vehicle listeners and [StayController], alive while the
 * driver uses another app, so the exit receipt and the 1-hour / backrest notices are decided from live signals.
 * It does no work itself. Type "specialUse" (no other FGS type matches "watch parked-car signals").
 *
 * Round 2 G1 fix (03_engineering/round2_G1_fix.md): started only where Android allows it ([ServiceKeepPolicy]) and kept
 * for the whole ignition cycle, D included, so the moment of P needs no background start. Outside a stay the
 * notification carries no stay content ([ServiceKeepPolicy.Notice.QUIET]).
 *
 * No POST_NOTIFICATIONS runtime request: on API 33+ the ongoing notification is then not shown in the shade (only in
 * the system's running-apps list), but the service still runs (runtime permission count stays 0).
 */
class StayService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val staying = intent?.getBooleanExtra(EXTRA_STAYING, false) ?: false
        if (intent?.action == ACTION_NOTICE) {
            // Notice update on the running service: startForeground again with the same id replaces the text. Not
            // NotificationManager.notify, which needs POST_NOTIFICATIONS (the app requests no runtime permission).
            if (state.foreground) foreground(staying)
            return START_NOT_STICKY
        }
        val ok = foreground(staying)
        when (state.onStartCommand(ok)) {
            StayServiceState.AfterStart.KEEP_RUNNING -> Unit
            StayServiceState.AfterStart.STOP_AFTER_FOREGROUND -> {
                // A stop that came before startForeground is done only now (kept from F2b; the controller no longer
                // stops the service, but the rule stays correct for any caller of stop()).
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
            StayServiceState.AfterStart.STOP_FAILED -> stopSelf()
        }
        return START_NOT_STICKY
    }

    private fun foreground(staying: Boolean): Boolean = try {
        val n = notification(this, ServiceKeepPolicy.noticeFor(staying))
        if (android.os.Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIFICATION_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIFICATION_ID, n) // minSdk 28; Pleos Connect is API 34
        }
        true
    } catch (t: Throwable) {
        Log.w(TAG, "startForeground failed: ${t.message}")
        false
    }

    override fun onDestroy() {
        state.onDestroy()
        onGone?.invoke()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "StayService"
        private const val CHANNEL = "stay"
        private const val NOTIFICATION_ID = 1
        private const val EXTRA_STAYING = "staying"
        private const val ACTION_NOTICE = "app.chaseom.stay.NOTICE"

        // Main-thread state; the rules are in StayServiceState (JVM-tested).
        private val state = StayServiceState()

        /** Set by StayController: the service instance was destroyed (main thread). */
        var onGone: (() -> Unit)? = null

        /**
         * true when the start was accepted; false (background start not allowed, API 31+) lets the caller wait for
         * the next allowed point ([ServiceKeepPolicy]). [staying] picks the first notice.
         */
        fun start(context: Context, staying: Boolean): Boolean = try {
            context.startForegroundService(Intent(context, StayService::class.java).putExtra(EXTRA_STAYING, staying))
            state.onStartAccepted()
            true
        } catch (t: Throwable) {
            Log.w(TAG, "start refused: ${t.message}")
            false
        }

        /**
         * Replaces the ongoing notification's text; a no-op unless the service is in the foreground. A plain startService
         * is allowed from the background here because the app then has a running foreground service.
         */
        fun show(context: Context, notice: ServiceKeepPolicy.Notice) {
            if (!state.foreground) return
            try {
                context.startService(
                    Intent(context, StayService::class.java).setAction(ACTION_NOTICE)
                        .putExtra(EXTRA_STAYING, notice == ServiceKeepPolicy.Notice.STAY),
                )
            } catch (t: Throwable) {
                Log.w(TAG, "notice update failed: ${t.message}")
            }
        }

        fun stop(context: Context) {
            if (state.stop() == StayServiceState.StopAction.STOP_SERVICE_NOW) {
                context.stopService(Intent(context, StayService::class.java))
            }
        }

        private fun notification(context: Context, notice: ServiceKeepPolicy.Notice): Notification {
            val nm = context.getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL, context.getString(R.string.svc_channel), NotificationManager.IMPORTANCE_LOW),
            )
            val open = PendingIntent.getActivity(
                context, 0, Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
                PendingIntent.FLAG_IMMUTABLE,
            )
            val stay = notice == ServiceKeepPolicy.Notice.STAY
            return Notification.Builder(context, CHANNEL)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentTitle(context.getString(if (stay) R.string.svc_title else R.string.svc_title_quiet))
                .setContentText(context.getString(if (stay) R.string.svc_text else R.string.svc_text_quiet))
                .setContentIntent(open)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setCategory(Notification.CATEGORY_SERVICE)
                .build()
        }
    }
}
