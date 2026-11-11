package app.chaseom.stay

/**
 * "작은 날씨 섬" face of the stay screen. Pure model, rendered by
 * ui/scenes/IslandView behind BuildConfig.KICK_ISLAND. [layers] is a 1:1 port of
 * the design prototype's island_state.js `islandLayers`, minus the inputs this version leaves out.
 *
 * Rules (tested in IslandTest):
 *  - P only: no island when not parked or in reduced mode; leaving P unmounts the
 *    whole stay scene (SceneHost), measured fold <= 0.2 s.
 *  - Still picture by default: short one-shot transitions only inside [EMPHASIS_MS] after a
 *    climate change; no frame loop, nothing repeats.
 *  - Notices and the receipt freeze it: changes snap to the end picture.
 *  - Day length shows at most [CAP_MINUTES]; the "(추정)" number stays on
 *    the hero line above the picture. The picture is decoration only (legend chip).
 *  - Inputs: climate and seat heaters only. Never inputs: rear occupancy,
 *    doors, windows. Not used here: wiper (AUTO read pending), turn
 *    signal (dropped: the blind-spot camera window covers the app),
 *    cabin/reading light. Always daytime; no fire, moon or stars.
 */
/** Sun size from the set temperature only (K2 no longer swaps the sun: the boat carries tomorrow). */
enum class IslandSun(val layer: String) { S("is_sun_s"), M("is_sun_m"), L("is_sun_l") }

data class IslandLayer(val id: String, val alpha: Float = 1f)

data class IslandState(
    val sun: IslandSun,
    /** 0..2: waves and palm bucket from the fan speed. */
    val wind: Int,
    /** A/C on: cool breeze lines. */
    val cool: Boolean,
    /** Recirculation on: a cloud circling over the island. */
    val cloud: Boolean,
    /** 0..3 driver / passenger seat heater level: warm left / right rock. Never fire. */
    val heatDrv: Int,
    val heatPas: Int,
    /** 0..1 today's day-length band = min(minutes, 180) / 180; climate off -> 1. */
    val dayLength: Float,
    /** SOC at or under tomorrow's share (K2): band empty, amber crate outline. */
    val alert: Boolean,
    /** Near tomorrow's share (K1): amber crate outline. */
    val near: Boolean = false,
    /** Inside the 1.6 s mooring window after arriving in P from driving, nothing on top. */
    val mooring: Boolean = false,
    /** Inside the transition window and nothing on top. */
    val animating: Boolean,
)

object Island {
    const val CAP_MINUTES = 180
    /** Window in which a change may play its one-shot transition (longest transition 1.6 s). */
    const val EMPHASIS_MS = 1_600L
    const val FADE_MS = 400
    const val MOTION_MS = 1_600
    /** More moving layers than this at once -> crossfade only. */
    const val MAX_MOVING = 3

    /** Layer alpha per seat-heat level 1..3. */
    val HEAT_ALPHA = floatArrayOf(0f, 0.45f, 0.7f, 1f)

    /** Layers drawn once into a cached bitmap. The gull is not drawn in this version. */
    val STATIC_LAYERS = listOf("is_sky", "is_island", "is_rock_l", "is_rock_r", "is_lighthouse", "is_pier", "is_gull")

    /** Mooring: 1.0 s slide + 0.2 s rope + 0.4 s half bob. */
    const val MOOR_MS = 1_600L
    const val MOOR_SLIDE_MS = 1_000
    const val MOOR_ROPE_MS = 200
    const val MOOR_BOB_MS = 400
    /** Twice the distance so the arrival is seen; the boat keeps its size.
     *  Also used as the animation's 1 -> 0 progress scale; the drawn distance is [moorSlideUnits]. */
    const val MOOR_SLIDE_DP = 360f
    /** Picture geometry (extra-wide set): 2000 x 480 units, 960-space art shifted by 520. */
    const val PIC_W = 2000f
    const val PIC_H = 480f
    const val ART_OFF = 520f
    /** Right end of the boat body in the 960 art space (x 706..946). */
    const val BOAT_RIGHT = 946f

    /**
     * Start offset in picture units = min(360, visible right edge - 946 - 8), so the boat
     * always starts inside the visible picture. The picture is Crop-scaled (cover) and centred in the area.
     * Full / Large windows give 360, Small (792 x 340 dp) about 85.
     */
    fun moorSlideUnits(areaW: Float, areaH: Float): Float {
        val k = maxOf(areaW / PIC_W, areaH / PIC_H)
        val left = (areaW - PIC_W * k) / 2f
        val visibleRight = (areaW - left) / k - ART_OFF // in 960 art units
        return minOf(MOOR_SLIDE_DP, visibleRight - BOAT_RIGHT - 8f).coerceAtLeast(0f)
    }

    /** Drawn start distance in dp: the picture-unit offset scaled to the area, never more than 360 dp. 0 = no slide. */
    fun moorSlideDp(areaW: Float, areaH: Float): Float {
        val k = maxOf(areaW / PIC_W, areaH / PIC_H)
        return minOf(MOOR_SLIDE_DP, moorSlideUnits(areaW, areaH) * k)
    }

    /** Boat x offset (dp) at slide progress [f] (1 = start at sea, 0 = moored at the pier). */
    fun boatOffsetDp(f: Float, areaW: Float, areaH: Float): Float = f.coerceIn(0f, 1f) * moorSlideDp(areaW, areaH)

    /** Wake alpha follows the distance left: 1 at the start, 0 in the end picture. */
    fun wakeAlpha(f: Float): Float = f.coerceIn(0f, 1f)

    /** Right edge of the boat in the area (dp) at progress [f], to check it starts inside the visible picture. */
    fun boatRightDp(f: Float, areaW: Float, areaH: Float): Float {
        val k = maxOf(areaW / PIC_W, areaH / PIC_H)
        val left = (areaW - PIC_W * k) / 2f
        return left + (ART_OFF + BOAT_RIGHT) * k + boatOffsetDp(f, areaW, areaH)
    }
    /** The slide starts this long after the stay screen is visible, once N0, title and hint have settled. */
    const val MOOR_START_DELAY_MS = 500L
    /** Only a non-P state at least this long counts as "came back" (P->N->P toggles do not moor). */
    const val MOOR_MIN_AWAY_MS = 3_000L
    /** The bob after climate changes is deferred (it pulls the eye off
     *  the numbers). Kept behind this switch, off for now. */
    const val SWAY_ENABLED = false
    /** One bob after the controls stop for this long; events during the bob are ignored. */
    const val SWAY_DEBOUNCE_MS = 300L
    const val SWAY_MS = 1_200
    const val BOB_DP = 3f

    /**
     * Moor only when arriving in P after D/R, or after any non-P state that lasted at
     * least [MOOR_MIN_AWAY_MS]. App start, coming back from the background, or P->N->P draw the end picture.
     * The boat only slides in, bobs 3 dp and gets its rope: never tilts, sinks or leaves.
     */
    fun shouldMoor(awayFromParkMs: Long?, wasDrivingOrReverse: Boolean): Boolean =
        awayFromParkMs != null && (wasDrivingOrReverse || awayFromParkMs >= MOOR_MIN_AWAY_MS)

    fun sun(powerOn: Boolean, setTempC: Float?): IslandSun = when {
        !powerOn -> IslandSun.S
        setTempC == null -> IslandSun.M
        setTempC < 20f -> IslandSun.S
        setTempC < 24.5f -> IslandSun.M
        else -> IslandSun.L
    }

    fun wind(powerOn: Boolean, fanSpeed: Int?): Int {
        val f = fanSpeed ?: 0
        return when {
            !powerOn || f <= 2 -> 0
            f <= 5 -> 1
            else -> 2
        }
    }

    fun state(ui: StayUi, nowMs: Long): IslandState? {
        if (!ui.parked || !ui.policy.energyBudget) return null // P only
        val hero = ui.hero ?: return null
        val c = ui.climate
        val power = c?.powerOn == true
        val alert = ui.keepBanner == KeepBanner.BELOW
        val day = when {
            hero.hvacOff -> 1f
            alert -> 0f
            else -> ((hero.minutes ?: 0).coerceAtMost(CAP_MINUTES) / CAP_MINUTES.toFloat()).coerceIn(0f, 1f)
        }
        val covered = ui.notice != null || ui.receipt != null
        val emphasized = ui.lastClimateChangeMs?.let { nowMs - it in 0..EMPHASIS_MS } ?: false
        val mooring = ui.moorAtMs?.let { nowMs - it in 0..(MOOR_MS + MOOR_START_DELAY_MS) } ?: false
        return IslandState(
            sun = sun(power, c?.setTempC),
            wind = wind(power, c?.fanSpeed),
            cool = power && c?.acOn == true,
            cloud = power && c?.recircOn == true,
            heatDrv = (c?.driverSeatHeat ?: 0).coerceIn(0, 3),
            heatPas = (ui.passengerSeatHeat ?: 0).coerceIn(0, 3),
            dayLength = day,
            alert = alert,
            animating = emphasized && !covered,
            near = ui.keepBanner == KeepBanner.NEAR,
            mooring = mooring && !covered,
        )
    }

    /** Visible layers in z order (island_state.js `islandLayers`). */
    fun layers(s: IslandState): List<IslandLayer> = buildList {
        add(IslandLayer("is_sky"))
        add(IslandLayer(s.sun.layer))
        if (s.cool) add(IslandLayer("is_cool"))
        if (s.cloud) add(IslandLayer("is_cloud_recirc"))
        add(IslandLayer("is_waves_${s.wind}"))
        add(IslandLayer("is_island"))
        add(IslandLayer("is_palm_${s.wind}"))
        add(IslandLayer("is_rock_l")); add(IslandLayer("is_rock_r"))
        if (s.heatDrv > 0) add(IslandLayer("is_rock_l_warm", HEAT_ALPHA[s.heatDrv]))
        if (s.heatPas > 0) add(IslandLayer("is_rock_r_warm", HEAT_ALPHA[s.heatPas]))
        add(IslandLayer("is_lighthouse"))
        // The boat is pier + body + rope (same picture as the old single layer).
        add(IslandLayer("is_pier"))
        add(IslandLayer("is_boat"))
        add(IslandLayer("is_rope"))
        if (s.near || s.alert) add(IslandLayer("is_crate_mark"))
        add(IslandLayer("is_gull"))
    }
}
