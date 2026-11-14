package app.chaseom

import android.util.Log

/**
 * Timing lines for the measurement script (not in this repository). Off by default, so a
 * normal install logs nothing. The tools switch it on with `adb shell setprop log.tag.PROBE D`.
 *
 * Lines may carry only the wall clock and vehicle signal values. Never log user input (keep
 * distance, place label, settings) or location here.
 */
object Probe {
    const val TAG = "PROBE"

    fun on(): Boolean = Log.isLoggable(TAG, Log.DEBUG)

    fun log(message: String) {
        if (on()) Log.d(TAG, "$message wall=${System.currentTimeMillis()}")
    }
}
