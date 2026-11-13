package app.chaseom.stay

import app.chaseom.cabin.CabinState

/** Timing rules. Tests may shorten them; release never does. */
data class StayTiming(
    val bannerExpandedMs: Long = 10_000L,
    val hourMs: Long = 60 * 60_000L,
    val backrestIntervalMs: Long = 30 * 60_000L,
    /** After acknowledging a notice, lower-priority notices due within this window are skipped. */
    val overlapMs: Long = 5 * 60_000L,
    val airAfterMs: Long = 20 * 60_000L,
    val airSnoozeMs: Long = 20 * 60_000L,
    val seatHighMs: Long = 30 * 60_000L,
    val toastBatchMs: Long = 1_200L,
    val toastShowMs: Long = 3_000L,
    val receiptMinStayMs: Long = 60_000L,
    /** A saved stay is continued only if the app comes back within this time. */
    val resumeWindowMs: Long = 10 * 60_000L,
    /** Night = local hour in [nightStartHour, 24) or [0, nightEndHour). */
    val nightStartHour: Int = 22,
    val nightEndHour: Int = 6,
    /** N3: reclined when the backrest moved this fraction of its capability range below baseline. */
    val backrestDropFraction: Float = 0.15f,
)

/**
 * Persisted part of a stay (restored after the process is recreated). All times are the
 * controller's monotonic clock (elapsed since boot), so changing the wall clock moves nothing.
 */
data class StaySession(
    val startMs: Long,
    val socAtStart: Int?,
    val nextHourMs: Long,
    val nextNightMs: Long? = null,
    val lastBackrestMs: Long? = null,
)

/**
 * Pure state machine for the stay screens (no Android types, so every rule is unit-tested in
 * StayEngineTest). Call [update] on every signal change and once per second.
 */
class StayEngine(
    private val timing: StayTiming = StayTiming(),
    restored: StaySession? = null,
    /** When the saved stay was last seen running, and the SOC then. */
    restoredSeenAtMs: Long? = null,
    restoredSocAtSave: Int? = null,
) {
    var session: StaySession? = restored
        private set

    private var pendingTimeCheck: Long? = if (restored != null) restoredSeenAtMs else null
    private var pendingSocCheck: Int? = if (restored != null) restoredSocAtSave else null
    /** A saved stay was dropped as stale: the title says "새로 시작했어요" for the first minute. */
    private var restartedFromStale = false

    private var bannerClosed = false
    private var notice: NoticeKind? = null
    private var lastAck: Pair<Long, NoticeKind>? = null
    private var driverBaseline: Int? = null
    private var passengerBaseline: Int? = null
    private var wasReclined = false
    private var prevClimate: ClimateSetting? = null
    private var prevMinutes: Int? = null
    private var batchStartMs: Long? = null
    private var batchBaseMinutes: Int? = null
    private var batchBaseDrawKw: Float? = null
    private var batchItems = mutableListOf<ClimateChange>()
    private var toast: Toast? = null
    private var lastClimateChangeMs: Long? = null
    private var prevPassengerHeat: Int? = null
    private val dismissedX = mutableSetOf<ExchangeRule>()
    private var airSinceMs: Long? = null
    private var airSnoozeUntil = 0L
    private var seatHighSinceMs: Long? = null
    private var prevDriverDoorOpen: Boolean? = null
    private var receipt: Receipt? = null

    /**
     * @param parked true = PARKED (gear P and the system allows), false = definitely not parked (ends
     *   the stay), null = unknown (signals not connected yet): hidden, but a restored stay is kept.
     */
    fun update(parked: Boolean?, cabin: CabinState, settings: StaySettings, nowMs: Long, hourOfDay: Int): StayUi {
        val policy = ModePolicy.of(ModeDetector.decide(cabin))
        if (parked == null) return StayUi(parked = false, policy = policy, settings = settings)
        if (!parked) {
            endStay()
            return StayUi(parked = false, policy = policy, settings = settings)
        }
        dropStaleRestore(cabin, nowMs)
        val night = hourOfDay >= timing.nightStartHour || hourOfDay < timing.nightEndHour
        val s = session ?: StaySession(
            startMs = nowMs,
            socAtStart = cabin.socPercent,
            nextHourMs = nowMs + timing.hourMs,
            nextNightMs = if (night) nowMs else null,
        ).also { session = it }
        if (s.socAtStart == null && cabin.socPercent != null) session = s.copy(socAtStart = cabin.socPercent)
        if (night && session!!.nextNightMs == null) session = session!!.copy(nextNightMs = nowMs) // night began
        if (!night && session!!.nextNightMs != null) session = session!!.copy(nextNightMs = null)

        val climate = ClimateSetting(
            powerOn = cabin.hvacPowerOn == true,
            setTempC = cabin.driverSetTempC,
            fanSpeed = cabin.fanSpeed,
            acOn = cabin.acOn,
            recircOn = cabin.recirculationOn,
            driverSeatHeat = cabin.driverSeatHeat,
            outsideC = cabin.outsideTempC,
        )

        val backrestSupported = cabin.backrestMin != null && cabin.backrestMax != null && cabin.backrestMax > cabin.backrestMin
        scheduleNotices(cabin, nowMs, backrestSupported)

        var hero: Hero? = null
        var bar: BarModel? = null
        var keepBanner: KeepBanner? = null
        var exchange: Exchange? = null
        val soc = cabin.socPercent
        val cap = cabin.batteryCapacityKwh
        if (policy.energyBudget && soc != null && cap != null) {
            val eff = EnergyModel.efficiency(cabin.evEfficiencyKmPerKwh)
            val keepPct = EnergyModel.keepPercent(settings.keepKm, cap, eff)
            val minutes = EnergyModel.minutesToKeep(soc, keepPct, cap, EnergyModel.drawKw(climate))
            val below = soc <= keepPct
            hero = Hero(
                minutes, minutes >= EnergyModel.DISPLAY_CAP_MINUTES, !climate.powerOn, EnergyModel.drawKw(climate),
                belowKeep = below,
                coveredKm = if (below) EnergyModel.coveredKm(soc, cap, eff).coerceAtMost(settings.keepKm) else null,
            )
            bar = BarModel(soc, keepPct, settings.keepKm, settings.label, below)
            keepBanner = when {
                below -> KeepBanner.BELOW
                climate.powerOn && minutes < 30 -> KeepBanner.NEAR
                else -> null
            }
            if (policy.dialToast) updateToast(climate, minutes, nowMs)
            if (policy.exchangeCard) exchange = EnergyModel.exchange(soc, cap, keepPct, climate, dismissedX)
        }
        if (toast != null && nowMs > toast!!.untilMs) toast = null

        val seatLong = seatHeatLong(cabin, nowMs)
        val air = if (policy.airCard) airCard(cabin, nowMs) else null
        val recirc = recircMinutes(cabin, nowMs)

        updateReceipt(cabin, settings, policy, nowMs)

        // Passenger seat heat drives only the island (no toast, no energy); it still opens the
        // short transition window like any climate change.
        val pas = cabin.passengerSeatHeat
        if (prevPassengerHeat != null && pas != null && pas != prevPassengerHeat) lastClimateChangeMs = nowMs
        if (pas != null) prevPassengerHeat = pas

        val st = session!!
        return StayUi(
            parked = true,
            policy = policy,
            elapsedMinutes = ((nowMs - st.startMs) / 60_000L).toInt(),
            banner = Banner(expanded = !bannerClosed && nowMs - st.startMs < timing.bannerExpandedMs),
            notice = notice,
            hero = hero,
            bar = bar,
            keepBanner = keepBanner,
            climate = climate,
            toast = toast,
            exchange = if (seatLong) null else exchange,
            seatHeatLong = seatLong && policy.exchangeCard,
            airMinutes = air,
            recircMinutes = recirc,
            rain = cabin.wiperAuto == true && cabin.wiperOn == true,
            trunkOpen = cabin.doorsOpen["trunk"] == true,
            chargePortOpen = cabin.chargePortOpen == true,
            receipt = receipt,
            settings = settings,
            backrestSupported = backrestSupported,
            lastClimateChangeMs = lastClimateChangeMs,
            passengerSeatHeat = pas,
            restartedFromStale = restartedFromStale,
        )
    }

    fun closeBanner() {
        bannerClosed = true
    }

    fun acknowledgeNotice(nowMs: Long) {
        notice?.let { lastAck = nowMs to it }
        notice = null
    }

    fun dismissExchange(rule: ExchangeRule) {
        dismissedX += rule
    }

    fun dismissAir(nowMs: Long) {
        airSnoozeUntil = nowMs + timing.airSnoozeMs
    }

    fun closeReceipt() {
        receipt = null
    }

    /**
     * A stay saved before the process died is continued only when the app is back within
     * [StayTiming.resumeWindowMs] (and not after a reboot: the monotonic clock restarted) and the
     * battery did not rise meanwhile (charged = a different stay). Otherwise a new stay starts.
     */
    private fun dropStaleRestore(cabin: CabinState, nowMs: Long) {
        pendingTimeCheck?.let { seen ->
            pendingTimeCheck = null
            if (nowMs < seen || nowMs - seen > timing.resumeWindowMs) {
                if (session != null) restartedFromStale = true
                session = null
                pendingSocCheck = null
            }
        }
        val socAtSave = pendingSocCheck ?: return
        val soc = cabin.socPercent ?: return
        pendingSocCheck = null
        // Charged, or a different battery state than when last seen: a different stay.
        if (soc > socAtSave || socAtSave - soc > RESUME_SOC_TOLERANCE) {
            if (session != null) restartedFromStale = true
            session = null
        }
    }

    private fun endStay() {
        session = null
        restartedFromStale = false
        pendingTimeCheck = null // a stay that ended while the app watched is not "restored"
        pendingSocCheck = null
        bannerClosed = false
        notice = null
        lastAck = null
        driverBaseline = null
        passengerBaseline = null
        wasReclined = false
        prevClimate = null
        prevMinutes = null
        batchStartMs = null
        batchBaseMinutes = null
        batchBaseDrawKw = null
        batchItems.clear()
        toast = null
        lastClimateChangeMs = null
        prevPassengerHeat = null
        dismissedX.clear()
        airSinceMs = null
        airSnoozeUntil = 0L
        seatHighSinceMs = null
        prevDriverDoorOpen = null
        receipt = null
    }

    // ---- N1 / N2 / N3 -----------------------------------------------------------------------

    private fun scheduleNotices(c: CabinState, nowMs: Long, backrestSupported: Boolean) {
        val due = mutableSetOf<NoticeKind>()
        var s = session!!
        if (nowMs >= s.nextHourMs) {
            due += NoticeKind.HOUR
            s = s.copy(nextHourMs = s.nextHourMs + timing.hourMs)
        }
        s.nextNightMs?.let { n ->
            if (nowMs >= n) {
                due += NoticeKind.NIGHT
                s = s.copy(nextNightMs = n + timing.hourMs)
            }
        }
        if (backrestSupported) {
            if (driverBaseline == null) driverBaseline = c.driverBackrest
            if (passengerBaseline == null) passengerBaseline = c.passengerBackrest
            val threshold = (c.backrestMax!! - c.backrestMin!!) * timing.backrestDropFraction
            fun reclined(base: Int?, now: Int?) = base != null && now != null && base - now >= threshold
            val reclinedNow = reclined(driverBaseline, c.driverBackrest) || reclined(passengerBaseline, c.passengerBackrest)
            val crossed = reclinedNow && !wasReclined
            wasReclined = reclinedNow
            val last = s.lastBackrestMs
            if (crossed && c.hvacPowerOn == true && (last == null || nowMs - last >= timing.backrestIntervalMs)) {
                due += NoticeKind.BACKREST
                s = s.copy(lastBackrestMs = nowMs)
            }
        }
        session = s
        if (due.isEmpty()) return
        val ack = lastAck
        val candidates = due.filterNot { k ->
            ack != null && nowMs - ack.first < timing.overlapMs && k.priority < ack.second.priority
        }
        val best = (candidates + listOfNotNull(notice)).maxByOrNull { it.priority } ?: return
        notice = best // one card at a time; overlapping lower ones are consumed
    }

    // ---- H·D dial toast ----------------------------------------------------------------------

    private fun updateToast(setting: ClimateSetting, minutes: Int, nowMs: Long) {
        val prev = prevClimate
        val prevMin = prevMinutes
        prevClimate = setting
        prevMinutes = minutes
        if (prev == null || prevMin == null || prev == setting) return
        val changes = buildList {
            if (prev.powerOn != setting.powerOn) add(ClimateChange.Power(setting.powerOn))
            if (prev.setTempC != setting.setTempC && setting.setTempC != null) add(ClimateChange.Temp(setting.setTempC))
            if (prev.fanSpeed != setting.fanSpeed && setting.fanSpeed != null) add(ClimateChange.Fan(setting.fanSpeed))
            if (prev.acOn != setting.acOn && setting.acOn != null) add(ClimateChange.Ac(setting.acOn))
            if (prev.recircOn != setting.recircOn && setting.recircOn != null) add(ClimateChange.Recirc(setting.recircOn))
            if (prev.driverSeatHeat != setting.driverSeatHeat && setting.driverSeatHeat != null) {
                add(ClimateChange.SeatHeat(setting.driverSeatHeat))
            }
        }
        if (changes.isEmpty()) return
        lastClimateChangeMs = nowMs
        val start = batchStartMs
        if (start == null || nowMs - start > timing.toastBatchMs) {
            batchStartMs = nowMs
            batchBaseMinutes = prevMin
            batchBaseDrawKw = EnergyModel.drawKw(prev)
            batchItems = mutableListOf()
        }
        changes.forEach { ch -> batchItems.removeAll { it::class == ch::class }; batchItems += ch }
        // Drawn immediately on the callback, then refined while the 1.2 s batch is open.
        // Both values are cut at the 3-hour display cap before the difference is taken,
        // so a toast can never show an hours-long delta.
        val cap = EnergyModel.DISPLAY_CAP_MINUTES
        val before = batchBaseMinutes!!.coerceAtMost(cap)
        val after = minutes.coerceAtMost(cap)
        // An air-source switch must not be rewarded or punished with minutes (either direction), and minutes
        // bundled with it would carry the same message, so the whole toast drops them and keeps the switch named.
        val recirc = batchItems.lastOrNull { it is ClimateChange.Recirc }
        val shown = if (recirc == null) batchItems.takeLast(2) else batchItems.filter { it !is ClimateChange.Recirc }.takeLast(1) + recirc
        val held = before >= cap && after >= cap
        toast = Toast(
            shown, after - before, nowMs + timing.toastShowMs,
            heldAboveCap = held,
            showMinutes = recirc == null,
            // No percentage when the batch switched the climate on or off: off drops to the base load and
            // would read as a large reward for switching off. The air-source rule (no number) wins anyway.
            capRatePercent = if (held && batchItems.none { it is ClimateChange.Power }) {
                EnergyModel.stayRatePercent(batchBaseDrawKw!!, EnergyModel.drawKw(setting))
            } else {
                null
            },
        )
    }

    // ---- H·X seat heat 30 min, H·A air card -------------------------------------------------

    private fun seatHeatLong(c: CabinState, nowMs: Long): Boolean {
        if ((c.driverSeatHeat ?: 0) >= 2) {
            val since = seatHighSinceMs ?: nowMs.also { seatHighSinceMs = it }
            return nowMs - since >= timing.seatHighMs
        }
        seatHighSinceMs = null
        return false
    }

    /**
     * 'Circling cloud': minutes the same condition as the air
     * card has held (HVAC on, recirculation on, every window known closed), once it has held [StayTiming.airAfterMs].
     * Unlike the card it is not snoozed by closing the card: it is a fact on the picture, not a prompt. It never
     * grows or darkens with time (one fixed layer) and leaves without any reward when the condition ends. Manual
     * recirculation only: the app subscribes to the HVAC recirculation state; auto recirculation is not read.
     */
    private fun recircMinutes(c: CabinState, nowMs: Long): Int? {
        val since = airSinceMs ?: return null
        if (!(c.hvacPowerOn == true && c.recirculationOn == true && c.anyWindowOpen == false)) return null
        val elapsed = nowMs - since
        return if (elapsed >= timing.airAfterMs) (elapsed / 60_000L).toInt() else null
    }

    private fun airCard(c: CabinState, nowMs: Long): Int? {
        val allClosed = c.anyWindowOpen == false
        if (c.hvacPowerOn == true && c.recirculationOn == true && allClosed) {
            val since = airSinceMs ?: nowMs.also { airSinceMs = it }
            val elapsed = nowMs - since
            return if (elapsed >= timing.airAfterMs && nowMs >= airSnoozeUntil) (elapsed / 60_000L).toInt() else null
        }
        airSinceMs = null // disappears quietly: there is no "air is fine" state
        return null
    }

    // ---- E receipt -------------------------------------------------------------------------

    private fun updateReceipt(c: CabinState, settings: StaySettings, policy: ModePolicy, nowMs: Long) {
        val open = c.driverDoorOpen
        val opened = open == true && prevDriverDoorOpen == false
        if (open != null) prevDriverDoorOpen = open
        if (open == false) receipt = null // closes when the door closes
        val s = session ?: return
        if (opened && nowMs - s.startMs >= timing.receiptMinStayMs) {
            receipt = buildReceipt(c, settings, policy, nowMs)
        } else {
            // An open receipt follows the light switch (switched while the receipt is up, or a late read).
            receipt = receipt?.let { r -> r.copy(rows = r.rows.map { if (it is ReceiptRow.Light) ReceiptRow.Light(c.cabinLightOn) else it }) }
        }
    }

    fun buildReceipt(c: CabinState, settings: StaySettings, policy: ModePolicy, nowMs: Long): Receipt {
        val s = session
        val rows = mutableListOf<ReceiptRow>()
        rows += ReceiptRow.Stayed(s?.let { ((nowMs - it.startMs) / 60_000L).toInt() } ?: 0)
        val cap = c.batteryCapacityKwh
        val soc = c.socPercent
        if (policy.receiptEnergyRows && cap != null && soc != null) {
            s?.socAtStart?.let { start ->
                val kwh = EnergyModel.usedKwh(start, soc, cap)
                val stayedH = ((nowMs - (s.startMs)).coerceAtLeast(60_000L)) / 3_600_000f
                // In this order: charged, <= 1 %p (integer SOC resolution),
                // >= 2 %p faster than any parked climate load, otherwise the number.
                val drop = start - soc
                val kind = when {
                    soc > start -> EnergyKind.CHARGED
                    drop <= 1 -> EnergyKind.SMALL
                    kwh.toFloat() / stayedH > MAX_PLAUSIBLE_KW -> EnergyKind.IMPLAUSIBLE
                    else -> EnergyKind.VALUE
                }
                rows += ReceiptRow.Energy(kwh, kind)
            }
            val eff = EnergyModel.efficiency(c.evEfficiencyKmPerKwh)
            val keepPct = EnergyModel.keepPercent(settings.keepKm, cap, eff)
            rows += if (soc > keepPct) {
                ReceiptRow.Keep(settings.keepKm, null)
            } else {
                ReceiptRow.Keep(settings.keepKm, EnergyModel.coveredKm(soc, cap, eff).coerceAtMost(settings.keepKm))
            }
        }
        rows += ReceiptRow.Windows(c.windows.filterValues { it > 0 }.keys.sortedBy { WINDOW_ORDER.indexOf(it) }, c.windowsKnown)
        rows += ReceiptRow.Light(c.cabinLightOn)
        if (policy.receiptEnergyRows) rows += ReceiptRow.ChargePort(c.chargePortOpen)
        rows += ReceiptRow.Rear(rearDetected(c.rearLeftOccupied, c.rearRightOccupied))
        return Receipt(rows, nowMs)
    }

    companion object {
        /** Percentage points the SOC may have fallen while the app was away and the stay still continue. */
        const val RESUME_SOC_TOLERANCE = 5
        /** Average draw above this while parked is not climate use (the sourced maximum is ~3.5 kW). */
        const val MAX_PLAUSIBLE_KW = 10f
        val WINDOW_ORDER = listOf("driver", "passenger", "rearLeft", "rearRight")
    }
}

/** Rear row value: someone on either seat = true; otherwise a seat that could not be read = null; both read empty = false. */
internal fun rearDetected(left: Boolean?, right: Boolean?): Boolean? = when {
    left == true || right == true -> true
    left == null || right == null -> null
    else -> false
}
