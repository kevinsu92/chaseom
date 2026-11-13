package app.chaseom.ui.theme

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import java.util.Calendar

/**
 * Night = the vehicle's dark theme OR the local clock between [FROM_HOUR]:00 and [TO_HOUR]:00 (round 2,
 * internal note #3). The whole app switches, not only the island, so the page and the picture
 * always share one theme and the contrast rules of S1_SPEC §4 hold (a dark island on a light page broke them).
 * It is a theme switch, not a motion: nothing on the island moves because the clock changed (MOTION_PRINCIPLES §1.1).
 */
object NightClock {
    /** Dark from 18:00 (November sunset in Korea is about 17:20; dark by 18:00 all year except June-July evenings). */
    const val FROM_HOUR = 18
    /** Light from 06:00. */
    const val TO_HOUR = 6

    fun isNightHour(hour: Int): Boolean = hour >= FROM_HOUR || hour < TO_HOUR

    fun isNight(systemDark: Boolean, hour: Int): Boolean = systemDark || isNightHour(hour)

    /** Milliseconds until the next whole minute (the clock is checked once a minute, and at once when it is set). */
    fun msToNextMinute(nowMs: Long): Long = 60_000L - Math.floorMod(nowMs, 60_000L)
}

/** Null outside [PleosAppTheme] (previews, tests): then [isNight] falls back to the system theme alone. */
internal val LocalNight = compositionLocalOf<Boolean?> { null }

/** The one night test for every screen: use this, never isSystemInDarkTheme() directly. */
@Composable
fun isNight(): Boolean = LocalNight.current ?: isSystemInDarkTheme()

/** Local hour of day, re-read every minute and at once when the time or the time zone is set. */
@Composable
internal fun rememberLocalHour(): Int {
    val ctx = LocalContext.current
    var hour by remember { mutableIntStateOf(Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) }
    var setAt by remember { mutableLongStateOf(0L) }
    DisposableEffect(ctx) {
        val r = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, i: Intent?) { setAt = System.nanoTime() }
        }
        val f = IntentFilter().apply {
            addAction(Intent.ACTION_TIME_CHANGED)
            addAction(Intent.ACTION_TIMEZONE_CHANGED)
        }
        // System broadcasts only; not exported to other apps.
        androidx.core.content.ContextCompat.registerReceiver(ctx, r, f, androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED)
        onDispose { runCatching { ctx.unregisterReceiver(r) } }
    }
    LaunchedEffect(setAt) {
        while (true) {
            hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
            kotlinx.coroutines.delay(NightClock.msToNextMinute(System.currentTimeMillis()))
        }
    }
    return hour
}
