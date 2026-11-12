package app.chaseom.stay

/**
 * Wait forecast: the driver may plant a flag at "how long I will wait", and the screen shows whether
 * today's stay time reaches it (estimate).
 *
 * Rules:
 * - Input only while parked, by choosing one of a few clock times (no keyboard).
 * - The flag can never be later than now + 3 hours (the same 3-hour cap as the hero).
 * - At night (N2 hours) there is no flag: the night notice speaks instead.
 * - Display only: the app never changes the climate to reach the flag.
 * - The choice is kept in memory for this stay only; it is never saved.
 * - No "you are fine until then" wording: a reached flag says only that the band passes it (estimate).
 */
data class WaitFlag(
    /** Monotonic time the flag stands at (same clock as the stay engine). */
    val atMs: Long,
    /** Wall-clock time shown on the flag (hh:mm), taken when the flag was planted. */
    val wallMs: Long,
)

/** What the band and the line under it show for a planted flag. */
data class WaitForecast(
    val flag: WaitFlag,
    /** Minutes from now to the flag, 1..[WaitFlags.MAX_MINUTES]. */
    val remainingMinutes: Int,
    /** Flag position on a 3-hour scale (0..1). */
    val fraction: Float,
    val outcome: WaitOutcome,
    /** Outcome SHORT: how many minutes the stay time ends before the flag. */
    val shortMinutes: Int = 0,
    /** Outcome SHORT with an exchange card on screen: its gain (the card is the action, not this line). */
    val swapGainMinutes: Int? = null,
    /**
     * Right after an air-source switch the line keeps the flag time only (no new "짧아요"), so switching to
     * outside air is never followed by a shortfall message. The hero still shows the new time at once.
     */
    val airQuiet: Boolean = false,
)

enum class WaitOutcome {
    /** Today's stay time passes the flag (estimate). */
    REACHES,
    /** Today's stay time ends before the flag (estimate). */
    SHORT,
    /** The battery is already at or under tomorrow's share: no stay time to compare. */
    BELOW_KEEP,
}

object WaitFlags {
    /** The flag cannot stand later than now + 3 hours. */
    const val MAX_MINUTES = EnergyModel.DISPLAY_CAP_MINUTES

    /** Offered choices, minutes from now (shown as clock times). */
    val CHOICES = listOf(30, 60, 90, 120, 150, 180)

    /** Flag clock times are rounded down to this step, so the flag is never later than the choice. */
    const val ROUND_MINUTES = 5

    /**
     * The flag for "[offsetMinutes] from now", rounded down to [ROUND_MINUTES] of wall time and kept
     * within (now, now + [MAX_MINUTES]].
     */
    fun plant(offsetMinutes: Int, nowMs: Long, nowWallMs: Long): WaitFlag {
        val offset = offsetMinutes.coerceIn(1, MAX_MINUTES).toLong() * 60_000L
        val step = ROUND_MINUTES * 60_000L
        val wallTarget = nowWallMs + offset
        var wall = wallTarget - Math.floorMod(wallTarget, step)
        if (wall <= nowWallMs) wall = wallTarget // a 5-minute floor must not put the flag in the past
        return WaitFlag(atMs = nowMs + (wall - nowWallMs), wallMs = wall)
    }

    /**
     * Where the flag stands on the buoy row, in buoy slots (slot 1 = the first buoy = 30 min; QA ruling 11).
     * Its own place is the chosen time: minutes to the flag / 30 (F-1: under 30 min it is pinned to slot 1).
     * Whether it is reached comes from the line's own comparison (capped stay time >= time to the flag):
     * - reached: min(own place, last buoy), so a reached flag always stands inside the buoy row (at the cap,
     *   inside the six buoys); when the chosen time falls just past the last buoy in the same 30-min slot,
     *   the flag is pulled onto that buoy and drawn in front of it;
     * - short: its own place, which is always past the last buoy (the gap shows how short; the line gives the number);
     * - below tomorrow's share: no flag picture (null, F-2).
     */
    fun rowSlot(f: WaitForecast, buoys: Int): Float? {
        val own = (f.remainingMinutes / 30f).coerceIn(1f, MAX_SLOT)
        return when (f.outcome) {
            WaitOutcome.BELOW_KEEP -> null
            WaitOutcome.REACHES -> minOf(own, buoys.coerceIn(1, 6).toFloat())
            WaitOutcome.SHORT -> own
        }
    }

    /** The latest wall time a flag may carry: the night notice starts at 22:00 (QA F-8). */
    const val LAST_FLAG_MINUTE_OF_DAY = 22 * 60

    /**
     * The choices offered at local time [nowMinuteOfDay] (0..1439): only flags at or before 22:00, and none
     * in the night window (22:00-06:00), so a wait is never planned into the sleep-risk hours.
     */
    fun choicesAt(nowMinuteOfDay: Int): List<Int> {
        if (isNightHour(nowMinuteOfDay / 60)) return emptyList()
        return CHOICES.filter { nowMinuteOfDay + it <= LAST_FLAG_MINUTE_OF_DAY }
    }

    /** Last slot of the row (the sixth buoy). */
    const val MAX_SLOT = 6f

    /** How long a shortfall line stays away after an air-source switch. */
    const val AIR_QUIET_MS = 10 * 60_000L

    /**
     * After a recirculation / outside-air switch, a SHORT line does not newly appear for [AIR_QUIET_MS]; the line
     * keeps the flag time. Another dial change since the switch, or a SHORT already on screen, lifts this.
     */
    fun quietAfterAirSwitch(
        f: WaitForecast?, nowMs: Long, airSwitchMs: Long?, otherChangeMs: Long?, shortOnScreen: Boolean,
    ): WaitForecast? {
        if (f == null || f.outcome != WaitOutcome.SHORT || shortOnScreen || airSwitchMs == null) return f
        if (nowMs - airSwitchMs >= AIR_QUIET_MS) return f
        if (otherChangeMs != null && otherChangeMs > airSwitchMs) return f
        return f.copy(airQuiet = true)
    }

    /** Null when there is nothing to show: no flag, the flag time has passed, night, or no hero. */
    fun forecast(flag: WaitFlag?, nowMs: Long, hero: Hero?, exchange: Exchange?, night: Boolean): WaitForecast? {
        if (flag == null || night || hero == null) return null
        val remainingMs = flag.atMs - nowMs
        if (remainingMs <= 0) return null
        val remaining = ((remainingMs + 59_999L) / 60_000L).toInt().coerceIn(1, MAX_MINUTES)
        val fraction = remaining / MAX_MINUTES.toFloat()
        if (hero.belowKeep) return WaitForecast(flag, remaining, fraction, WaitOutcome.BELOW_KEEP)
        // Compared on the 3-hour-capped time, the same as the hero shows.
        val minutes = hero.minutes?.coerceAtMost(EnergyModel.DISPLAY_CAP_MINUTES)
        // Climate off: base load only, and the hero shows no number either.
        if (hero.hvacOff || (minutes != null && minutes >= remaining)) {
            return WaitForecast(flag, remaining, fraction, WaitOutcome.REACHES)
        }
        val have = minutes ?: 0
        return WaitForecast(
            flag, remaining, fraction, WaitOutcome.SHORT,
            shortMinutes = remaining - have,
            swapGainMinutes = exchange?.gainMinutes,
        )
    }
}
