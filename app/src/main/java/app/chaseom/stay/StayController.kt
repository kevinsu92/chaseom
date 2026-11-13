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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import java.util.Calendar

/** Which parked page is open on top of the stay (driving always shows the glance scene). */
enum class Page { NONE, ONBOARDING, SETTINGS, ABOUT }

/**
 * Glue between Android and [StayEngine]: runs the engine on every signal change and once per
 * second, persists settings and the running stay, keeps [StayService] running for the
 * ignition cycle (round 2 G1), and owns the parked page navigation (first run -> onboarding -> settings).
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
            outsideMode = if (app.chaseom.BuildConfig.OUTSIDE_FULL) OutsideMode.FULL else OutsideMode.COLD_ONLY,
            calibration = Calibration(loadCalibration()),
        )
    }
    private val settings = MutableStateFlow(loadSettings())
    private val _ui = MutableStateFlow(StayUi(settings = settings.value))
    val ui: StateFlow<StayUi> = _ui.asStateFlow()

    private val _page = MutableStateFlow(if (prefs.getBoolean(K_ONBOARDED, false)) Page.NONE else Page.ONBOARDING)
    val page: StateFlow<Page> = _page.asStateFlow()
    val firstRun: Boolean get() = !prefs.getBoolean(K_ONBOARDED, false)


    /** Round 2 G1: the stay service is started only where Android allows it and kept for the ignition cycle. */
    private val service = ServiceKeepPolicy()
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
    /** Wait flag for this stay only. Round 2 C-4: saved with the stay start, restored after a process kill. */
    private var waitFlag: WaitFlag? = null
    private var waitFlagStayStart: Long? = null
    init {
        WaitFlagRecord.decode(prefs.getString(K_FLAG, null))?.takeIf { it.belongsTo(engine.session?.startMs) }?.let {
            waitFlag = it.flag
            waitFlagStayStart = it.stayStartMs
        }
    }
    private var prevRecirc: Boolean? = null
    private var prevOtherClimate: List<Any?>? = null
    private var airSwitchMs: Long? = null
    private var otherChangeMs: Long? = null
    private var flagShortOnScreen = false

    /**
     * Round 2 G1 / platform R-1: the 1 s tick runs only while the car is in P or the gear is not known yet. With the
     * service kept through D the process lives while driving, and the Pleos guide asks for no unnecessary background
     * timers; in D the controller reacts to ON_CHANGE signals only.
     */
    private val ticker = gatedTicker(combine(restriction, vehicle) { r, v -> parkedOf(r, v) != false })

    fun start() {
        StayService.onGone = { service.onServiceGone() } // the next allowed point starts it again
        scope.launch {
            combine(restriction, vehicle, cabin.state, settings, ticker) { r, v, c, s, _ ->
                if (r == DrivingRestriction.PARKED || v.gear != GearPosition.UNKNOWN) gearEverRead = true
                if (r != DrivingRestriction.PARKED && (v.gear == GearPosition.DRIVE || v.gear == GearPosition.REVERSE)) {
                    awayDrivingOrReverse = true
                }
                if (r != DrivingRestriction.PARKED && v.gear != GearPosition.UNKNOWN && v.gear != GearPosition.PARK) {
                    awayGearNotPark = true
                }
                Triple(parkedOf(r, v), c, s)
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
        val cal = Calendar.getInstance()
        val hour = cal.get(Calendar.HOUR_OF_DAY)
        val minuteOfDay = hour * 60 + cal.get(Calendar.MINUTE)
        var ui = engine.update(parked, c, s, now, hour)
        if (engine.session != before) saveSession(engine.session)
        if (engine.takeCalibrationChange()) saveCalibration(engine.calibrationSaved)
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
        // The flag belongs to one stay: leaving P or a new stay removes it.
        if (waitFlag != null && (!ui.parked || engine.session?.startMs != waitFlagStayStart)) setWaitFlag(null)
        val night = isNightHour(hour)
        // Air-source switches and other climate changes, for the quiet time after a switch.
        if (prevRecirc != null && c.recirculationOn != null && c.recirculationOn != prevRecirc) airSwitchMs = now
        if (c.recirculationOn != null) prevRecirc = c.recirculationOn
        val other = listOf(c.hvacPowerOn, c.driverSetTempC, c.fanSpeed, c.acOn, c.driverSeatHeat)
        if (prevOtherClimate != null && other != prevOtherClimate) otherChangeMs = now
        prevOtherClimate = other
        val flagAllowed = ui.parked && !night && ui.hero?.unconfirmed == false && ui.policy.energyBudget &&
            WaitFlags.choicesAt(minuteOfDay).isNotEmpty()
        ui = ui.copy(
            waitForecast = WaitFlags.quietAfterAirSwitch(
                WaitFlags.forecast(waitFlag, now, ui.hero, ui.exchange, night), now, airSwitchMs, otherChangeMs, flagShortOnScreen,
            ).also { f -> flagShortOnScreen = f != null && f.outcome == WaitOutcome.SHORT && !f.airQuiet },
            waitFlagAvailable = flagAllowed,
            connecting = parked == null && !gearEverRead,
            firstStayHint = !hintDone && ui.parked && ui.policy.energyBudget,
            moorAtMs = moorAtMs,
        )
        _ui.value = ui
        if (parked == false && _page.value != Page.ONBOARDING) _page.value = Page.NONE // P released: close pages
        val staying = _ui.value.parked
        cabin.setPolling(staying) // lights, wiper lever and backrest are polled only during a parked stay
        // Round 2 G1 (ServiceKeepPolicy): one attempt per stay; no stop when the car leaves P, only a quiet notice.
        if (service.onUpdate(staying, leftPark = parked == false) == ServiceKeepPolicy.Action.START) startService(staying)
        service.noticeChange(staying)?.let { StayService.show(appContext, it) }
    }

    private fun startService(staying: Boolean) {
        service.onStartResult(StayService.start(appContext, staying), ServiceKeepPolicy.noticeFor(staying))
    }

    /** The app's screen came to the front (MainActivity.onResume): an allowed point to start the service. */
    fun onScreenForeground() {
        if (service.onScreenForeground() == ServiceKeepPolicy.Action.START) startService(_ui.value.parked)
        rerun()
    }

    /** ACTION_BOOT_COMPLETED (BootReceiver): an allowed point, used only after onboarding. */
    fun onBoot() {
        if (service.onBoot(onboarded = !firstRun) == ServiceKeepPolicy.Action.START) startService(_ui.value.parked)
    }

    private fun rerun() = last?.let { run(it) }

    // ---- user actions -------------------------------------------------------------------------

    fun closeBanner() { engine.closeBanner(); rerun() }
    fun acknowledgeNotice() { engine.acknowledgeNotice(clock()); rerun() }
    fun dismissExchange(rule: ExchangeRule) { engine.dismissExchange(rule); rerun() }
    fun dismissAir() { engine.dismissAir(clock()); rerun() }
    fun closeReceipt() { engine.closeReceipt(); rerun() }

    /** Plants the wait flag [offsetMinutes] from now (P only; saved with this stay, see [setWaitFlag]). */
    fun plantWaitFlag(offsetMinutes: Int) {
        if (!_ui.value.waitFlagAvailable) return
        val cal = Calendar.getInstance()
        if (offsetMinutes !in WaitFlags.choicesAt(cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE))) return
        setWaitFlag(WaitFlags.plant(offsetMinutes, clock(), System.currentTimeMillis()))
        rerun()
    }

    fun removeWaitFlag() { setWaitFlag(null); rerun() }

    /** Memory and prefs together, so the saved flag never outlives the one on screen. */
    private fun setWaitFlag(flag: WaitFlag?) {
        val start = engine.session?.startMs
        waitFlag = flag
        waitFlagStayStart = if (flag == null) null else start
        prefs.edit {
            if (flag == null || start == null) remove(K_FLAG) else putString(K_FLAG, WaitFlagRecord(start, flag).encode())
        }
    }

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

    /** H+calib: per-car correction (this head unit = this car). Local only, like everything else. */
    private fun loadCalibration() = Calibration.Saved(
        ratio = prefs.getFloat(K_CAL_RATIO, 1f).takeIf { it.isFinite() && it > 0f } ?: 1f,
        weightPct = prefs.getFloat(K_CAL_WEIGHT, 0f).takeIf { it.isFinite() && it >= 0f } ?: 0f,
        evidenceMs = prefs.getLong(K_CAL_MS, 0L).coerceAtLeast(0L),
    )

    private fun saveCalibration(c: Calibration.Saved) = prefs.edit {
        putFloat(K_CAL_RATIO, c.ratio).putFloat(K_CAL_WEIGHT, c.weightPct).putLong(K_CAL_MS, c.evidenceMs)
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
        const val K_CAL_RATIO = "cal_ratio"
        const val K_CAL_WEIGHT = "cal_weight_pct"
        const val K_CAL_MS = "cal_evidence_ms"
        const val K_FLAG = "session_wait_flag"
        const val SEEN_SAVE_MS = 15_000L
    }
}

/** true = PARKED, false = known not parked (ends the stay), null = not known yet (keep a restored stay). */
internal fun parkedOf(r: DrivingRestriction, v: VehicleSnapshot): Boolean? = when {
    r == DrivingRestriction.PARKED -> true
    v.connection == ConnectionState.CONNECTED && v.gear != GearPosition.UNKNOWN -> false
    else -> null
}

/**
 * One Unit every [periodMs] while [gate] is true; while it is false, a single Unit on the switch and then nothing
 * (combine still gets its first value, and the screen re-runs once when the tick stops).
 */
internal fun gatedTicker(gate: Flow<Boolean>, periodMs: Long = 1_000L): Flow<Unit> = channelFlow {
    gate.distinctUntilChanged().collectLatest { on ->
        send(Unit)
        while (on) {
            delay(periodMs)
            send(Unit)
        }
    }
}

/** Same night window as the stay engine's night notice (22:00-06:00). */
internal fun isNightHour(hour: Int): Boolean = hour >= 22 || hour < 6
