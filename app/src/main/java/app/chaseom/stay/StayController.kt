package app.chaseom.stay

import android.content.Context
import android.os.SystemClock
import androidx.core.content.edit
import app.chaseom.cabin.CabinRepository
import app.chaseom.cabin.CabinState
import app.chaseom.vehicle.ConnectionState
import app.chaseom.vehicle.DrivingRestriction
import app.chaseom.vehicle.GearPosition
import app.chaseom.vehicle.VehicleSnapshot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import java.util.Calendar

/** Which parked page is open on top of the stay (driving always shows the glance scene). */
enum class Page { NONE, ONBOARDING, SETTINGS, ABOUT }

/**
 * Glue between Android and [StayEngine]: runs the engine on every signal change and once per
 * second, persists settings and the running stay, keeps [StayService] running during a
 * stay, and owns the parked page navigation (first run -> onboarding -> settings).
 */
class StayController(
    context: Context,
    private val scope: CoroutineScope,
    private val cabin: CabinRepository,
    private val restriction: StateFlow<DrivingRestriction>,
    private val vehicle: StateFlow<VehicleSnapshot>,
    /** Monotonic: stay timers never move when the wall clock is changed. */
    private val clock: () -> Long = SystemClock::elapsedRealtime,
) {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences("stay", Context.MODE_PRIVATE)
    private val engine = loadSession().let { s ->
        StayEngine(
            restored = s,
            // Older saves have no "seen" time; their start time then fails the monotonic check.
            restoredSeenAtMs = s?.let { prefs.getLong(K_SEEN, it.startMs) },
            restoredSocAtSave = prefs.getInt(K_SEEN_SOC, -1).takeIf { it >= 0 },
        )
    }
    private val settings = MutableStateFlow(loadSettings())
    private val _ui = MutableStateFlow(StayUi(settings = settings.value))
    val ui: StateFlow<StayUi> = _ui.asStateFlow()

    private val _page = MutableStateFlow(if (prefs.getBoolean(K_ONBOARDED, false)) Page.NONE else Page.ONBOARDING)
    val page: StateFlow<Page> = _page.asStateFlow()
    val firstRun: Boolean get() = !prefs.getBoolean(K_ONBOARDED, false)


    private var serviceRunning = false
    private var last: Triple<Boolean?, CabinState, StaySettings>? = null
    /** A gear value has been read at least once since start ("connecting" vs driving). */
    private var gearEverRead = false
    private var lastSeenSaveMs = 0L
    private var hintDone = prefs.getBoolean(K_HINT_DONE, false)
    private var stayedOnce = false
    // Mooring: when the car left P and whether it was in D/R meanwhile.
    private var awaySinceMs: Long? = null
    private var awayDrivingOrReverse = false
    /** A gear other than P was actually read while away (startup restriction lag alone is not "away"). */
    private var awayGearNotPark = false
    private var moorAtMs: Long? = null

    private val ticker = flow {
        while (true) {
            emit(Unit)
            delay(1_000)
        }
    }

    fun start() {
        scope.launch {
            combine(restriction, vehicle, cabin.state, settings, ticker) { r, v, c, s, _ ->
                if (r == DrivingRestriction.PARKED || v.gear != GearPosition.UNKNOWN) gearEverRead = true
                if (r != DrivingRestriction.PARKED && (v.gear == GearPosition.DRIVE || v.gear == GearPosition.REVERSE)) {
                    awayDrivingOrReverse = true
                }
                if (r != DrivingRestriction.PARKED && v.gear != GearPosition.UNKNOWN && v.gear != GearPosition.PARK) {
                    awayGearNotPark = true
                }
                val parked = when {
                    r == DrivingRestriction.PARKED -> true
                    v.connection == ConnectionState.CONNECTED && v.gear != GearPosition.UNKNOWN -> false
                    else -> null // not known yet: keep a restored stay
                }
                Triple(parked, c, s)
            }.collect { run(it) }
        }
    }

    private fun run(input: Triple<Boolean?, CabinState, StaySettings>) {
        last = input
        val (parked, c, s) = input
        val before = engine.session
        val now = clock()
        when (parked) {
            false -> if (awaySinceMs == null) awaySinceMs = now
            true -> {
                awaySinceMs?.let { since ->
                    // Only a real trip (D/R) or a non-P gear held >= 3 s. A restart while in P
                    // (restriction catching up, gear always P) draws the end picture.
                    if (awayGearNotPark && Island.shouldMoor(now - since, awayDrivingOrReverse)) moorAtMs = now
                }
                awaySinceMs = null
                awayDrivingOrReverse = false
                awayGearNotPark = false
            }
            null -> Unit
        }
        if (parked == false) moorAtMs = null
        var ui = engine.update(parked, c, s, now, Calendar.getInstance().get(Calendar.HOUR_OF_DAY))
        if (engine.session != before) saveSession(engine.session)
        if (engine.session != null && now - lastSeenSaveMs >= SEEN_SAVE_MS) {
            prefs.edit { putLong(K_SEEN, now).putInt(K_SEEN_SOC, c.socPercent ?: -1) }
            lastSeenSaveMs = now
        }
        // First-stay hint: until the first climate change, or the end of the first stay.
        if (ui.parked) stayedOnce = true
        if (!hintDone && (ui.lastClimateChangeMs != null || (parked == false && stayedOnce))) {
            hintDone = true
            prefs.edit { putBoolean(K_HINT_DONE, true) }
        }
        ui = ui.copy(
            connecting = parked == null && !gearEverRead,
            firstStayHint = !hintDone && ui.parked && ui.policy.energyBudget,
            moorAtMs = moorAtMs,
        )
        _ui.value = ui
        if (parked == false && _page.value != Page.ONBOARDING) _page.value = Page.NONE // P released: close pages
        val staying = _ui.value.parked
        cabin.setPolling(staying) // lights, wiper lever and backrest are polled only during a parked stay
        if (staying && !serviceRunning) {
            StayService.start(appContext)
            serviceRunning = true
        } else if (parked == false && serviceRunning) {
            StayService.stop(appContext)
            serviceRunning = false
        }
    }

    private fun rerun() = last?.let { run(it) }

    // ---- user actions -------------------------------------------------------------------------

    fun closeBanner() { engine.closeBanner(); rerun() }
    fun acknowledgeNotice() { engine.acknowledgeNotice(clock()); rerun() }
    fun dismissExchange(rule: ExchangeRule) { engine.dismissExchange(rule); rerun() }
    fun dismissAir() { engine.dismissAir(clock()); rerun() }
    fun closeReceipt() { engine.closeReceipt(); rerun() }

    fun finishOnboarding(goToSettings: Boolean) {
        prefs.edit { putBoolean(K_ONBOARDED, true) }
        _page.value = if (goToSettings) Page.SETTINGS else Page.NONE
    }

    fun open(page: Page) { _page.value = page }

    /** Start with 30 km · 회사 without the user choosing: nothing is stored, the keep line says "기본값". */
    fun useDefaults() { _page.value = Page.NONE }

    fun saveSettings(km: Int, label: String) {
        val s = StaySettings(km.coerceIn(StaySettings.MIN_KM, StaySettings.MAX_KM), label)
        prefs.edit { putInt(K_KM, s.keepKm).putString(K_LABEL, s.label) }
        settings.value = s
        _page.value = Page.NONE
    }

    // ---- persistence (local only: nothing leaves the car) ------------------------------

    private fun loadSettings() = StaySettings(
        prefs.getInt(K_KM, StaySettings.DEFAULT_KM),
        prefs.getString(K_LABEL, null)?.takeIf { it in StaySettings.LABELS } ?: StaySettings.DEFAULT_LABEL,
        saved = prefs.contains(K_KM),
    )

    private fun loadSession(): StaySession? {
        val start = prefs.getLong(K_START, -1L).takeIf { it > 0 } ?: return null
        return StaySession(
            startMs = start,
            socAtStart = prefs.getInt(K_SOC, -1).takeIf { it >= 0 },
            nextHourMs = prefs.getLong(K_HOUR, start + 60 * 60_000L),
            nextNightMs = prefs.getLong(K_NIGHT, -1L).takeIf { it > 0 },
            lastBackrestMs = prefs.getLong(K_BACK, -1L).takeIf { it > 0 },
        )
    }

    private fun saveSession(s: StaySession?) = prefs.edit {
        if (s == null) {
            listOf(K_START, K_SOC, K_HOUR, K_NIGHT, K_BACK, K_SEEN, K_SEEN_SOC).forEach { remove(it) }
        } else {
            putLong(K_START, s.startMs).putInt(K_SOC, s.socAtStart ?: -1).putLong(K_HOUR, s.nextHourMs)
                .putLong(K_NIGHT, s.nextNightMs ?: -1L).putLong(K_BACK, s.lastBackrestMs ?: -1L)
        }
    }

    private companion object {
        const val K_ONBOARDED = "onboarded"
        const val K_KM = "keep_km"
        const val K_LABEL = "keep_label"
        const val K_START = "session_start"
        const val K_SOC = "session_soc"
        const val K_HOUR = "session_next_hour"
        const val K_NIGHT = "session_next_night"
        const val K_BACK = "session_backrest"
        const val K_SEEN = "session_seen"
        const val K_SEEN_SOC = "session_seen_soc"
        const val K_HINT_DONE = "first_hint_done"
        const val SEEN_SAVE_MS = 15_000L
    }
}
