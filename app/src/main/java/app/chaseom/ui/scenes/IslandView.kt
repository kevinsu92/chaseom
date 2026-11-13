package app.chaseom.ui.scenes

import android.graphics.BitmapFactory
import android.provider.Settings
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.aspectRatio
import app.chaseom.ui.theme.ChaseomUi
import app.chaseom.ui.theme.cui
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.chaseom.R
import app.chaseom.stay.Island
import app.chaseom.stay.IslandInfo
import app.chaseom.stay.StayUi
import app.chaseom.ui.theme.Pleos
import app.chaseom.ui.theme.PleosType
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.roundToInt

/** One H1 layer: a WebP cropped to its drawn area, placed at (x, y) in the layer set's canvas. */
internal data class H1Layer(val id: Int, val x: Float, val y: Float)

/**
 * The two H1 layer sets: Large 1600x600 for windows >= 1200 dp,
 * Small 960x600 for the 1:1 split (about 840 dp). Label anchors come from the H1 renderer art_h.html (d=4).
 */
internal class H1Set(
    val layers: Map<String, H1Layer>,
    val w: Float,
    /** Canvas px between two water steps; the boat, buoys and K2 frame move up by this per step. */
    val step: Float,
    val sunX: Float,
    val sunY: Float,
    /** Rail label centres: battery (start-anchored), buoy legend, crates. */
    val batX: Float, val batY: Float,
    val buoyX: Float, val buoyY: Float, val buoyAnchor: Float,
    val keepX: Float, val keepY: Float, val keepAnchor: Float,
    /** Picture scale about the bottom-right corner (water line fixed). 1 in build G (S1: always 1.0x). */
    val zoom: Float = 1f,
    /** Box height over the set's own ratio (the zoomed picture's top sky is cropped above this). */
    val boxGrow: Float = 1f,
    /** S1 (build G, 04_design/S1_SPEC.md §2): the fixed water line and the canvas height (= water line + sea strip). */
    val waterY: Float = 480f,
    val h: Float = 600f,
    /** S2: buoy i (1-based) stands centred at buoyX0 + (i - 0.5) * buoyPitch, drawn twice its art size. */
    val buoyX0: Float = 0f,
    val buoyPitch: Float = 0f,
    /** The one buoy cut out of buoys_n1_l1 (alpha > 200 area): left, top, right, bottom in the bitmap. */
    val buoySrc: IntArray = intArrayOf(0, 0, 1, 1),
    /** Flag layer rows drawn (cloth and the top of the pole, no dome), the cloth's last row, the pole's columns. */
    val flagRows: Int = 1, val clothBottom: Int = 0, val poleL: Int = 0, val poleR: Int = 1,
    /** In-row flag: the pole stands this far right of its slot's centre, behind the buoy (not on the head's line). */
    val poleShift: Float = 0f,
    /** Tomorrow chip: centre on the dock top and its text size, canvas px. */
    val chipX: Float = 0f, val chipY: Float = 0f, val chipFont: Float = 28f,
) {
    /** Canvas y of the water surface per step 1..5 (layers manifest water_top_y_by_step). */
    val waterTop: FloatArray get() = if (w == 1600f) floatArrayOf(520f, 480f, 440f, 400f, 360f) else floatArrayOf(480f, 456f, 432f, 408f, 384f)
    companion object {
        val Large = H1Set(H1Layers.L, 1600f, 40f, 1450f, 82f, // sun higher (108 -> 82)
            batX = 40f, batY = 569f, buoyX = 1466f, buoyY = 569f, buoyAnchor = 0.5f, keepX = 1172f, keepY = 569f, keepAnchor = 0.5f,
            waterY = 480f, h = 502f, buoyX0 = 1012f, buoyPitch = 85f, buoySrc = intArrayOf(0, 0, 30, 56),
            flagRows = 51, clothBottom = 24, poleL = 7, poleR = 11, poleShift = 18f, chipX = 957f, chipY = 236f, chipFont = 28f)
        // Small rail line 1 = battery (start) + buoys (end at 932), line 2 = crates.
        val Small = H1Set(H1Layers.S, 960f, 24f, 836f, 92f, // sun 118 -> 92
            batX = 28f, batY = 503f, buoyX = 932f, buoyY = 503f, buoyAnchor = 1f, keepX = 28f, keepY = 551f, keepAnchor = 0f,
            // Strip 28 canvas = 22 px on screen at the Small picture's 0.79x; chip text 24 px on screen.
            waterY = 456f, h = 484f, buoyX0 = 566f, buoyPitch = 56f, buoySrc = intArrayOf(1, 1, 25, 46),
            flagRows = 43, clothBottom = 20, poleL = 7, poleR = 9, poleShift = 14f, chipX = 508f, chipY = 252f, chipFont = 30f)
        /** Sky above a picture that is shorter than its box, and the sea under its lower 30 % (no seam). */
        val SKY_TOP = Color(0xFFF5F6F8) // The sky top is the page colour (the picture melts into it)
        val SEA_DEEP = Color(0xFF3B536E) // water bottom
        /** Sun disc radius per size (S / M / L), for the "설정 °C" label next to it. */
        val SUN_R = floatArrayOf(28f, 44f, 64f)
    }
}

/**
 * Build G, S1 (04_design/S1_SPEC.md): three always-on symbols, sun, buoys and flag. The water is a fixed sea strip
 * (battery is the header's "배터리 n%"), tomorrow's share is the dock chip "내일 몫 n km" (the crates' K1/K2 outline
 * becomes the chip's amber border and fill), and the boat, wind lines, water ticks, reflections and tree sway are not drawn.
 * Clouds and far rain stay events. The picture is always 1.0x of its width (no fit-to-height shrink).
 */
internal const val S1_LEVEL = 2 // the water step whose line is H1Set.waterY (L 480, S 456)
/** S2: buoys and the flag at twice their art size. */
internal const val BUOY_SCALE = 2
/** The in-row flag's cloth ends this far above the buoy head; the thin plate under an out-of-row flag. */
internal const val FLAG_GAP = 8f
internal const val PLATE_W = 36f
internal const val PLATE_H = 6f
/** Flag pole ink of flag_row_l1 (L and S), also the plate colour. */
internal val FLAG_POLE = Color(0xFF464F5C)

/** S2: canvas x of buoy slot [i]'s centre (1-based). */
internal fun buoyCenter(set: H1Set, i: Int): Float = set.buoyX0 + (i - 0.5f) * set.buoyPitch

/** Drawn buoy size (canvas px): twice the cut-out. */
internal fun buoySize(set: H1Set): Pair<Float, Float> =
    BUOY_SCALE * (set.buoySrc[2] - set.buoySrc[0]).toFloat() to BUOY_SCALE * (set.buoySrc[3] - set.buoySrc[1]).toFloat()

/** Buoy head y: buoys stand 4 px into the sea strip. */
internal fun buoyTop(set: H1Set): Float = set.waterY + 4f - buoySize(set).second

/** Flag top y: the cloth's lower edge [FLAG_GAP] above the buoy head (same height in and out of the row). */
internal fun flagTop(set: H1Set): Float = buoyTop(set) - FLAG_GAP - BUOY_SCALE * set.clothBottom

/** The slot the flag stands in: the buoy slot holding the chosen time (F2 rowSlot, rounded up), 1..6. */
internal fun flagSlotIndex(slot: Float): Int = kotlin.math.ceil(slot - 1e-4f).toInt().coerceIn(1, IslandInfo.MAX_BUOYS)

/** S2: canvas x of a (possibly fractional) slot position. */
internal fun buoyCenter(set: H1Set, pos: Float): Float = set.buoyX0 + (pos - 0.5f) * set.buoyPitch

/**
 * Canvas x of the flag pole's centre. In the row it stands [H1Set.poleShift] right of the slot centre, so it no longer
 * continues the buoy head's stick (read as one long head, 04_design/REVIEW_BUILD_G_RELEASE.md #2); drawn behind the
 * buoy, its foot disappears behind the dome. Out of the row it stands at the real time (no buoy there).
 */
internal fun flagPoleX(set: H1Set, place: FlagPlace): Float = buoyCenter(set, place.pos) + if (place.inRow) set.poleShift else 0f

/**
 * A buoy that comes or goes rises out of / sinks into the sea strip [BUOY_RISE] canvas px while it fades (one cause:
 * the stay time crossed a 30-minute step; once, then still; MOTION_PRINCIPLES §1.1-1.2). [alpha] 0..1 = how far in.
 */
internal fun buoyRise(alpha: Float): Float = (1f - alpha.coerceIn(0f, 1f)) * BUOY_RISE
internal const val BUOY_RISE = 20f

/** Where the flag stands: [pos] in slot units (1 = first buoy), [inRow] = reached (no plate, behind its buoy). */
internal data class FlagPlace(val pos: Float, val inRow: Boolean)

/**
 * Reached: the slot holding the chosen time, never past the last buoy (rowSlot), so its buoy still counts; with no
 * buoy (stay under 30 min) it still stands as reached, without the plate (external S1S2 #7).
 * Short: the open water after the last buoy's own slot is the shortfall (external #6: ceil drew up to 30 min too
 * much), but never closer than one slot to the last buoy (UXR C4: >= 0.5 slot of open water), at most slot 6.
 */
internal fun flagPlace(f: app.chaseom.stay.WaitForecast, buoys: Int): FlagPlace? = when (f.outcome) {
    app.chaseom.stay.WaitOutcome.BELOW_KEEP -> null
    app.chaseom.stay.WaitOutcome.REACHES ->
        FlagPlace(flagSlotIndex(app.chaseom.stay.WaitFlags.rowSlot(f, buoys) ?: 1f).toFloat(), inRow = true)
    app.chaseom.stay.WaitOutcome.SHORT ->
        FlagPlace((buoys + maxOf(1f, 0.5f + f.shortMinutes / IslandInfo.MINUTES_PER_BUOY.toFloat()))
            .coerceAtMost(IslandInfo.MAX_BUOYS.toFloat()), inRow = false)
}

/** Sun radius change: 600 ms, cubic-bezier(0, 0, 0.2, 1), only across an S/M/L step. */
internal const val SUN_MS = 600
/** Sun glow pulse: 200 ms, glow only, within one step, at most once per 1.2 s. */
internal const val GLOW_MS = 200
internal const val GLOW_GAP_MS = 1_200L
internal const val GLOW_EXTRA = 0.6f // glow 1 -> 1.6 (glow only, colour unchanged)

/** What the picture shows; changes are batched and cross-faded. */
internal data class H1Pic(val sun: Int, val wind: Int, val level: Int, val buoys: Int, val k2: Boolean, val air: Boolean = false,
                          val rain: Boolean = false) {
    companion object {
        // Boat = car (trunk hatch, charge cable) is off: the cable read as
        // "charging" for a port that is only open, and the hatch is ~25 px and repeats the Pleos 3D car.
        // S1: trees fixed (wind 0 -> trees_w0), water fixed at S1_LEVEL.
        fun of(i: IslandInfo) = H1Pic(i.sunLevel, 0, S1_LEVEL, i.buoys, i.crateOutline, i.cloud,
            rain = app.chaseom.BuildConfig.KICK_RAIN && i.rain)
    }
}

/** Window-width rule: below 1200 dp the Small set. */
internal fun h1SetFor(widthDp: Float): H1Set = if (widthDp >= 1200f) H1Set.Large else H1Set.Small

/** Picture batching after the last input (numbers and toast still change at once). */
private const val BATCH_MS = 300L
/** Water step change; other changes use [Island.FADE_MS]. */
private const val WATER_MS = 600
/**
 * Information island, H1 art. Each element has one meaning (IslandInfo):
 * sun size = set temperature, buoys = stay time, water = battery (5 steps), crate frame = tomorrow's share.
 * Labels are screen text (24 sp) laid on the picture, never baked into it. Bitmaps are decoded once and
 * drawn as they are; no frame loop: with no value change nothing is redrawn.
 */
@Composable
fun IslandPanel(
    ui: StayUi,
    modifier: Modifier = Modifier,
    /** Drawn inside the top-left sky of the picture, never over the "설정 °C" label (Small toast). */
    pictureTop: @Composable () -> Unit = {},
    /** Wait-flag row (ask / choices / forecast line), under the caption lines and above the cards. */
    flagRow: @Composable () -> Unit = {},
    cards: @Composable () -> Unit = {},
) {
    val s = Island.state(ui, android.os.SystemClock.elapsedRealtime()) ?: return // same clock as the engine
    // Water hysteresis needs the previous step; it lives as long as the island (reset when the stay ends).
    val water = remember { intArrayOf(-1) }
    val info = IslandInfo.of(ui, water[0].takeIf { it >= 0 }) ?: return
    water[0] = info.waterStep
    val ctx = LocalContext.current
    // System animation scale 0 -> picture swaps without transitions.
    val animOff = remember {
        runCatching { Settings.Global.getFloat(ctx.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f }
            .getOrDefault(false)
    }
    // Latency probes (all builds, one log line per event like the cabin PROBE lines).
    LaunchedEffect(ui.lastClimateChangeMs) {
        if (ui.lastClimateChangeMs != null) {
            withFrameNanos { }
            app.chaseom.Probe.log("islandFrame")
        }
    }
    DisposableEffect(Unit) { onDispose { app.chaseom.Probe.log("islandGone") } }
    val hero = ui.hero
    val bar = ui.bar
    val cd = if (hero != null && bar != null) {
        stringResource(
            R.string.is_cd,
            if (hero.overCap) stringResource(R.string.h_hero_cap) else stringResource(R.string.h_hero_value, duration(hero.minutes ?: 0)),
            bar.socPercent,
            bar.keepKm,
        )
    } else {
        ""
    }
    val night = app.chaseom.ui.theme.isNight()
    // Picture -> 16 -> cause line -> 32 -> cards.
    // With cards the whole block must fit the window, so the picture takes only the height
    // left after what is under it (measured), never below 75 % of its natural height; the sky side gives.
    Column(modifier) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val set = h1SetFor(with(LocalDensity.current) { LocalWindowInfo.current.containerSize.width.toDp().value })
            // Large: card width, at most 1760 dp so a full-screen window keeps the picture under 660 dp tall.
            val fullW: Dp = if (set === H1Set.Large) minOf(maxWidth, 1760.dp) else maxWidth
            // No outline at night (it read as a 1 px seam); see the night light below.
            val outline = Modifier
            // S1: the picture is always 1.0x of the column width at its own height (no fill, no shrink to fit).
            val natural = fullW * (set.h / set.w) * set.boxGrow
            val boxH = natural
            val picW: Dp = fullW
            val k = picW.value / set.w // dp per canvas px
            // Arriving in P, the whole picture block (sky, island, water, buoys together,
            // so the water never seems to drop) rises 24 dp -> 0 and fades in, 300 ms, cubic-bezier(0, 0, 0.2, 1),
            // once per arrival, the first time it is not covered (a night notice opening the stay is read first),
            // within 2 min of arriving; never with animations off. Leaving P still folds at once.
            val rise = rememberRise(ui.moorAtMs, covered = ui.notice != null || ui.receipt != null, enabled = !animOff)
            Box(
                // The whole picture is clipped once here (no per-layer clip).
                // Centred under the picture's own width (fullW), so a window wider than 1760 dp keeps the column.
                Modifier.width(picW).height(boxH)
                    .graphicsLayer { translationY = (1f - rise) * RISE_DP.dp.toPx(); alpha = rise }
                    .clip(ChaseomUi.rContainer).then(outline)
                    .semantics { contentDescription = cd; liveRegion = LiveRegionMode.Polite },
            ) {
                // In a taller Small box the sun (and its label) sit at 24 % and the wind at 36 % of
                // the box height. The sun's position carries no meaning; only its size does.
                val skyLift = if (boxH > natural) skyLiftFor(set, k * set.zoom, boxH.value) else SkyLift.NONE
                // Wait flag on the buoy row: fades in once over 400 ms when planted; a one-time 0.95 -> 1 pulse when
                // it is reached (no colour change). No flag picture under tomorrow's share.
                val wf = ui.waitForecast
                val flagPlace = wf?.let { flagPlace(it, info.buoys) }
                val flagAlpha = remember(wf?.flag?.atMs) { Animatable(if (animOff) 1f else 0f) }
                LaunchedEffect(wf?.flag?.atMs) { if (wf != null) flagAlpha.animateTo(1f, tween(400)) }
                val reached = wf?.outcome == app.chaseom.stay.WaitOutcome.REACHES
                val flagScale = remember(wf?.flag?.atMs) { Animatable(1f) }
                LaunchedEffect(wf?.flag?.atMs, reached) {
                    if (reached && !animOff) { flagScale.snapTo(0.95f); flagScale.animateTo(1f, tween(400)) }
                }
                val shown = islandPicture(set, info, ui.moorAtMs, s.mooring, animOff, Modifier.matchParentSize(), sunMotion = !animOff && s.animating,
                    outsideAir = ui.climate?.recircOn == false, night = night,
                    setTemp = ui.climate?.takeIf { it.powerOn == true }?.setTempC, lift = skyLift,
                    flagPlace = flagPlace, flagScale = flagScale.value, flagAlpha = flagAlpha.value)
                // H1: the sun and its label sit top right, so the toast takes the top-left sky up to the label.
                var sunLabelLeft by remember { mutableFloatStateOf(Float.NaN) }
                IslandLabels(ui, set, k, picW.value, boxH.value, shown, sunLiftDp = skyLift.sun * k * set.zoom, animOff = animOff) { sunLabelLeft = it }
                ui.bar?.let { b -> KeepChip(b.keepKm, ui.hero?.coveredKm, outline = info.crateOutline, night = night, set = set, k = k, animOff = animOff) }
                val toastMax = if (sunLabelLeft.isNaN()) Modifier else Modifier.widthIn(max = with(LocalDensity.current) { sunLabelLeft.toDp() } - 32.dp)
                Box(Modifier.align(Alignment.TopStart).padding(start = 16.dp).then(toastMax)) { pictureTop() }
            }
        }
        // Lines and cards under the picture (fold line, first hint, flag row, swap / air cards) open and close in
        // 400 ms decelerate instead of pushing the page at once (MOTION_PRINCIPLES §3.5, A-5); the picture never moves.
        // Under a notice or the receipt, and with animations off, they change at once.
        val still = animOff || ui.notice != null || ui.receipt != null
        Column(Modifier.fillMaxWidth().then(if (still) Modifier else Modifier.animateContentSize(
            tween(Island.FADE_MS, easing = androidx.compose.animation.core.CubicBezierEasing(0f, 0f, 0.2f, 1f))))) {
        // No text on the picture. One caption row under it = legend swatches
        // (picture shapes, no numbers) + the cause chip (always shown) at the right end.
        // Small: the chip goes to a second line.
        Spacer(Modifier.height(ChaseomUi.related))
        val k = cui()
        val small = h1SetFor(with(LocalDensity.current) { LocalWindowInfo.current.containerSize.width.toDp().value }) === H1Set.Small
        // S1: the symbols that left the picture are named on one line folded under the (i) note (tap to open).
        var folded by remember { mutableStateOf(true) }
        val chip: @Composable () -> Unit = {
            Row(verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.clickable(onClickLabel = stringResource(R.string.is_fold_open)) { folded = !folded }) {
                // Legend and chip icons at 32 dp (48-grid icon, 2 dp line).
                androidx.compose.material3.Icon(androidx.compose.ui.res.painterResource(R.drawable.ic_info), null,
                    tint = k.text2, modifier = Modifier.size(32.dp))
                Spacer(Modifier.width(ChaseomUi.line))
                Text(stringResource(R.string.is_legend), style = ChaseomUi.caption, color = k.text2, softWrap = false)
            }
        }
        val flagInLegend = ui.waitForecast != null && ui.waitForecast.outcome != app.chaseom.stay.WaitOutcome.BELOW_KEEP
        val legend: @Composable () -> Unit = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(28.dp)) {
                // S1: three symbols on one line. The flag item is there while a flag is planted.
                LegendItem(Swatch.SUN, stringResource(R.string.is_lg_sun))
                LegendItem(Swatch.BUOY, stringResource(R.string.is_lg_buoy))
                if (flagInLegend) LegendItem(Swatch.FLAG, stringResource(R.string.is_lg_flag), night = night)
            }
        }
        // The cloud item exists only while the cloud is drawn; its own line under the legend.
        // The far-rain item likewise, on its own line ("먼 비 = 와이퍼 AUTO 작동").
        val showRain = app.chaseom.BuildConfig.KICK_RAIN && ui.rain
        val cloudLine: @Composable () -> Unit = {
            ui.recircMinutes?.let {
                Spacer(Modifier.height(ChaseomUi.line))
                LegendItem(Swatch.CLOUD, stringResource(R.string.is_lg_air, it))
            }
            if (showRain) {
                Spacer(Modifier.height(ChaseomUi.line))
                LegendItem(Swatch.RAIN, stringResource(R.string.is_lg_rain))
            }
        }
        if (small) {
            legend()
            cloudLine()
            Spacer(Modifier.height(ChaseomUi.line))
            chip()
        } else {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                legend()
                Spacer(Modifier.weight(1f).widthIn(min = 28.dp))
                chip()
            }
            cloudLine()
        }
        if (!folded) {
            Spacer(Modifier.height(ChaseomUi.line))
            Text(stringResource(R.string.is_fold_line), style = ChaseomUi.caption, color = k.text2)
        }
        if (ui.firstStayHint) {
            // No chip; one caption line like the cause line (28 dp fan, 24 Medium #4A4E57).
            // The fan is ink #4A4E57, not amber (amber = a value the user changed).
            Spacer(Modifier.height(ChaseomUi.related))
            Row(verticalAlignment = Alignment.CenterVertically) {
                androidx.compose.material3.Icon(androidx.compose.ui.res.painterResource(R.drawable.ic_fan), null,
                    tint = k.text2, modifier = Modifier.size(ChaseomUi.iconInline))
                Spacer(Modifier.width(ChaseomUi.line))
                // At the 3-hour cap the time cannot change, so only the island is promised.
                val hint = if (ui.hero?.overCap == true) R.string.is_hint_first_cap else R.string.is_hint_first
                // Day text2 = #4A4E57 as before; at night text2 (#4A4E57 on the night page was about 2.2:1).
                Text(stringResource(hint), style = ChaseomUi.caption, color = k.text2)
            }
        }
        Box(Modifier.fillMaxWidth()) { flagRow() }
        Column(Modifier.padding(top = ChaseomUi.block), verticalArrangement = Arrangement.spacedBy(ChaseomUi.related)) {
            cards() // air / swap cards under the cause line
        }
        }
    }
}

/** Screen text on the picture (24 sp): "설정 °C" next to the sun, and the rail under the water. */
@Composable
private fun IslandLabels(ui: StayUi, set: H1Set, k: Float, picW: Float, boxHdp: Float, shown: H1Shown, sunLiftDp: Float = 0f,
                         animOff: Boolean = false, sunLabelLeft: (Float) -> Unit) {
    val pic = shown.pic
    // Canvas px -> dp in the box: scaled by zoom about the bottom-right corner (water line and buoys stay).
    val z = k * set.zoom
    val boxH = boxHdp
    fun x(cx: Float) = picW - (set.w - cx) * z
    fun y(cy: Float) = boxH - (set.h - cy) * z
    if (ui.bar == null) return
    val c = ui.climate
    val temp = c?.takeIf { it.powerOn == true }?.setTempC
    // The sun has no label at rest (the legend says "sun = set temperature"). Only when the set
    // temperature changes does "22 -> 24°C" show next to it, for 2.5 s after the last change (the bottom climate
    // bar keeps the number at all times).
    val change = sunChange(temp) ?: run { sunLabelLeft(Float.NaN); return }
    val bare = ChaseomUi.pictureLabel.copy(color = Color(0xFF131417), shadow = Shadow(Color.White, Offset.Zero, 8f))
    // With system animations off, no arrow: "설정 26°C" for the same 2.5 s.
    val text = if (animOff) stringResource(R.string.hvac_temp, tempText(change.second))
    else stringResource(R.string.is_sun_change, tempText(change.first), tempText(change.second))
    // The label keeps 22 px from the disc while the sun radius animates.
    PicText(AnnotatedString(text), bare, x(set.sunX - shown.sunR - 22f), y(set.sunY) + sunLiftDp, 1f, sunLabelLeft)
}

/**
 * S1: tomorrow's share as one chip on the dock top ("내일 몫 30km"), replacing the boat's three crates. At K1 / K2
 * ([outline], IslandInfo.crateOutline: keep banner NEAR or BELOW) the chip border turns amber (day #A86C00 4.37:1,
 * night #F0B850; #E0A030 was 2.27:1) and the chip is filled with an amber tint (F8). Size and place follow the picture's scale [k] (dp per canvas px).
 */
@Composable
private fun KeepChip(keepKm: Int, leftKm: Int?, outline: Boolean, night: Boolean, set: H1Set, k: Float, animOff: Boolean = false) {
    // F8: at K1 / K2 the chip is also filled amber (a light tint by day, a dark tint at night) so it reads as a warning.
    val bg = when {
        outline && night -> Color(0xFF4A3A1C)
        outline -> Color(0xFFFFE9B8)
        night -> Color(0xFF2B3140)
        else -> Color.White
    }
    val ink = if (night) Color(0xFFD6DAE0) else Color(0xFF4A4E57)
    val edge = when {
        outline && night -> Color(0xFFF0B850)
        outline -> Color(0xFFA86C00)
        night -> Color(0xFF4A5263)
        else -> Color(0xFFC9CED6)
    }
    // Entering / leaving K1 / K2 the fill, text and border fade 400 ms (MOTION_PRINCIPLES 'standard'; no blink).
    val spec = tween<Color>(if (animOff) 0 else Island.FADE_MS)
    val bgA by androidx.compose.animation.animateColorAsState(bg, spec, label = "chipBg")
    val inkA by androidx.compose.animation.animateColorAsState(ink, spec, label = "chipInk")
    val edgeA by androidx.compose.animation.animateColorAsState(edge, spec, label = "chipEdge")
    val font = with(LocalDensity.current) { (set.chipFont * k).dp.toSp() }
    val chipH = set.chipFont * 44f / 28f * k
    val shape = RoundedCornerShape((chipH / 2f).dp)
    Box(Modifier.layout { m, cons ->
        val p = m.measure(cons.copy(minWidth = 0, minHeight = 0))
        layout(p.width, p.height) {
            p.place(((set.chipX * k).dp.toPx() - p.width / 2f).roundToInt(), ((set.chipY * k).dp.toPx() - p.height / 2f).roundToInt())
        }
    }.height(chipH.dp).background(bgA, shape).border(if (outline) 2.dp else 1.dp, edgeA, shape)
        .padding(horizontal = (16f * k).dp), contentAlignment = Alignment.Center) {
        // K2 (below the keep share): the same km as the line under the hero ("약 29km 남음"), not the target (external #5).
        Text(if (leftKm != null) stringResource(R.string.is_keep_chip_left, leftKm) else stringResource(R.string.is_keep_chip, keepKm), style = ChaseomUi.caption.copy(fontSize = font, lineHeight = font),
            color = inkA, softWrap = false, maxLines = 1)
    }
}

/** Shown for [SUN_LABEL_MS] after the last change of the set temperature: (before the first change, now). */
@Composable
private fun sunChange(temp: Float?): Pair<Float, Float>? {
    val last = remember { floatArrayOf(Float.NaN) }   // the value at rest before the current burst of changes
    var shown by remember { mutableStateOf<Pair<Float, Float>?>(null) }
    LaunchedEffect(temp) {
        if (temp == null) { shown = null; last[0] = Float.NaN; return@LaunchedEffect }
        val from = shown?.first ?: last[0]
        last[0] = temp
        if (from.isNaN() || from == temp) { shown = null; return@LaunchedEffect } // back where it started: nothing to say
        shown = from to temp
        kotlinx.coroutines.delay(SUN_LABEL_MS)
        shown = null
    }
    return shown
}

internal const val SUN_LABEL_MS = 2_500L

/** Text whose anchor point (0 start, 0.5 centre, 1 end; vertical centre) sits at (x, y) dp in the picture. */
@Composable
private fun PicText(text: AnnotatedString, style: TextStyle, xDp: Float, yDp: Float, anchor: Float, onLeft: ((Float) -> Unit)? = null, icon: Int? = null) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.layout { m, cons ->
        val p = m.measure(cons.copy(minWidth = 0, minHeight = 0))
        layout(p.width, p.height) {
            val left = (xDp.dp.toPx() - anchor * p.width).roundToInt()
            onLeft?.invoke(left.toFloat())
            p.place(left, (yDp.dp.toPx() - p.height / 2f).roundToInt())
        }
    }) {
        if (icon != null) {
            androidx.compose.material3.Icon(androidx.compose.ui.res.painterResource(icon), null, tint = style.color, modifier = Modifier.size(28.dp))
            Spacer(Modifier.width(ChaseomUi.line))
        }
        Text(text, style = style, softWrap = false, maxLines = 1)
    }
}

/**
 * Decoded on first use; drawable-nodpi so 1 canvas px stays 1 bitmap px. [trim] keeps only what the last
 * frame drew, so after a transition only the current state's layers stay in memory (about 13 of 44).
 */
private class H1Bitmaps(private val res: android.content.res.Resources) : (Int) -> ImageBitmap {
    private val cache = HashMap<Int, ImageBitmap>()
    private val used = HashSet<Int>()
    fun frame() = used.clear()
    override fun invoke(id: Int): ImageBitmap {
        used.add(id)
        return cache.getOrPut(id) { BitmapFactory.decodeResource(res, id, BitmapFactory.Options().apply { inScaled = false }).asImageBitmap() }
    }
    fun trim() { if (used.isNotEmpty()) cache.keys.retainAll(used) }
}

@Composable
private fun rememberBitmaps(): H1Bitmaps {
    val res = LocalResources.current
    return remember(res) { H1Bitmaps(res) }
}

/**
 * Draws the layer stack and returns the picture state now on screen (the labels follow it). A new
 * state waits [BATCH_MS] after the last input, then cross-fades; the boat, buoys and K2 frame move with
 * the water. Mooring slides the boat in from the right.
 */
@Composable
private fun islandPicture(set: H1Set, info: IslandInfo, moorAtMs: Long?, mooring: Boolean, motionOff: Boolean, modifier: Modifier,
                          sunMotion: Boolean = false, outsideAir: Boolean = false, night: Boolean = false, setTemp: Float? = null,
                          lift: SkyLift = SkyLift.NONE, flagPlace: FlagPlace? = null, flagScale: Float = 1f, flagAlpha: Float = 1f): H1Shown {
    val target = H1Pic.of(info)
    val bmp = rememberBitmaps()
    var shown by remember { mutableStateOf(target) } // first value at once: never an empty island first
    var prev by remember { mutableStateOf<H1Pic?>(null) }
    val t = remember { Animatable(1f) }
    LaunchedEffect(target) {
        if (target == shown) return@LaunchedEffect
        delay(BATCH_MS) // a newer input cancels this and starts the wait again
        prev = shown
        shown = target
        if (motionOff) t.snapTo(1f)
        else {
            t.snapTo(0f)
            t.animateTo(1f, tween(if (prev?.level != target.level) WATER_MS else Island.FADE_MS))
        }
        withFrameNanos { } // one frame of the end state, then drop the faded-out layers
        bmp.trim()
    }
    // Signature motion: the sun radius moves 600 ms when the set temperature crosses a step.
    // Once, never repeated; only right after a dial change (Island.state animating: not under a notice or the receipt),
    // never with animations off; leaving P removes the island, so the motion ends with it.
    val sunR = remember { Animatable(H1Set.SUN_R[shown.sun]) }
    LaunchedEffect(shown.sun) {
        val to = H1Set.SUN_R[shown.sun]
        if (!sunMotion || sunR.value == to) sunR.snapTo(to)
        else sunR.animateTo(to, tween(SUN_MS, easing = androidx.compose.animation.core.CubicBezierEasing(0f, 0f, 0.2f, 1f)))
    }
    // Glow pulse: the set temperature changed but stayed in the same S/M/L step (the sun keeps its
    // size), so only the glow brightens 1 -> 1.6 -> 1 in 200 ms. Same batching as the picture; at most once per
    // 1.2 s; never together with the 600 ms size change; same gate as the size motion (P, no notice / receipt, animator).
    val glow = remember { Animatable(0f) }
    val lastTemp = remember { floatArrayOf(Float.NaN) }
    val lastPulse = remember { longArrayOf(0L) }
    LaunchedEffect(setTemp, shown.sun) {
        val was = lastTemp[0]
        if (setTemp == null) return@LaunchedEffect
        delay(BATCH_MS)
        lastTemp[0] = setTemp
        val now = android.os.SystemClock.elapsedRealtime()
        val sameStep = H1Set.SUN_R[shown.sun] == sunR.targetValue && !sunR.isRunning
        if (was.isNaN() || was == setTemp || !sunMotion || !sameStep || now - lastPulse[0] < GLOW_GAP_MS) return@LaunchedEffect
        lastPulse[0] = now
        glow.snapTo(0f)
        glow.animateTo(0f, keyframes { durationMillis = GLOW_MS; 1f at GLOW_MS / 2 })
    }
    // S1 draws no boat, so there is no mooring slide; the arrival keeps its probe line and the picture rise.
    val windowInfo = LocalWindowInfo.current
    LaunchedEffect(moorAtMs) {
        if (!mooring || motionOff) return@LaunchedEffect
        withTimeoutOrNull(400) { snapshotFlow { windowInfo.isWindowFocused }.first { it } }
        withFrameNanos { }
        delay(Island.MOOR_START_DELAY_MS)
        app.chaseom.Probe.log("moorFrame")
    }
    // Switching to outside air while the cloud is up, the cloud drifts off the picture once and is
    // gone (no text, no other change). A window opening just fades it, like every other state change.
    val drift = remember { Animatable(-1f) } // -1 = none, 0..1 = drifting
    val hadAir = remember { booleanArrayOf(target.air) }
    LaunchedEffect(target.air) {
        val was = hadAir[0]; hadAir[0] = target.air
        if (!(was && !target.air && outsideAir) || motionOff || !set.layers.containsKey("air")) return@LaunchedEffect
        drift.snapTo(0f)
        drift.animateTo(1f, tween(DRIFT_MS, easing = androidx.compose.animation.core.CubicBezierEasing(0.4f, 0f, 1f, 1f)))
        drift.snapTo(-1f)
    }
    val cur = rememberUpdatedState(shown)
    val before = rememberUpdatedState(prev)
    Spacer(modifier.drawBehind {
        bmp.frame()
        // Sky above a picture shorter than its box; deep sea under the lower 30 % so no rounding gap shows sky.
        drawRect(H1Set.SKY_TOP)
        drawRect(H1Set.SEA_DEEP, topLeft = Offset(0f, size.height * 0.7f))
        val k = size.width / set.w * set.zoom
        // Zoom about the bottom-right corner: the canvas right and bottom edges stay at the box's edges.
        val ox = size.width - set.w * k; val oy = size.height - set.h * k
        translate(left = ox, top = oy) {
            scale(k, k, pivot = Offset.Zero) {
                drawH1(set, bmp, cur.value, before.value, t.value,
                    sunScale = sunR.value / H1Set.SUN_R[cur.value.sun], glow = glow.value * GLOW_EXTRA, lift = lift,
                    driftT = drift.value, withSun = !night, flagPlace = flagPlace, flagScale = flagScale, flagAlpha = flagAlpha)
            }
        }
        if (night) {
            // ONE navy light #121E38 over the whole picture block, 76 % under the
            // current water line and 50 % above it; then the sun above it, brightness lowered only (#C98A2E).
            val waterTop = set.waterTop[(cur.value.level - 1).coerceIn(0, set.waterTop.size - 1)]
            val y = (oy + waterTop * k).coerceIn(0f, size.height)
            drawRect(NIGHT_LIGHT.copy(alpha = 0.5f), size = androidx.compose.ui.geometry.Size(size.width, y))
            drawRect(NIGHT_LIGHT.copy(alpha = 0.76f), Offset(0f, y), androidx.compose.ui.geometry.Size(size.width, size.height - y))
            translate(left = ox, top = oy) {
                scale(k, k, pivot = Offset.Zero) {
                    val c = cur.value; val b = before.value ?: c; val a = if (before.value == null) 1f else t.value
                    set.layers["rain_night"]?.let { l ->
                        val alpha = when { c.rain && b.rain -> 1f; c.rain -> a; b.rain -> 1f - a; else -> 0f }
                        if (alpha > 0.01f) drawImage(bmp(l.id), Offset(l.x, l.y), alpha = alpha)
                    }
                    drawSun(set, bmp, cur.value, sunR.value / H1Set.SUN_R[cur.value.sun], glow.value * GLOW_EXTRA, lift, night = true)
                    // The night flag (light cloth) is drawn over the night light, like the night sun and rain.
                    // The night flag (white cloth) goes over the night light; in the row its buoy is drawn again
                    // in front of it, lit like the rest (S1_SPEC v3, M-5).
                    if (flagPlace != null) {
                        drawFlagS1(set, bmp, "flag_row_l1_night", flagPlace, flagScale, flagAlpha)
                        val i = flagPlace.pos.toInt()
                        if (flagPlace.inRow && i in 1..c.buoys) drawBuoy(set, bmp, i, 1f, NIGHT_BUOY)
                    }
                }
            }
        }
    })
    return H1Shown(shown, sunR.value)
}

/** The picture state on screen and the sun radius now (canvas px), for the labels. */
internal data class H1Shown(val pic: H1Pic, val sunR: Float)

/**
 * The S1 layer stack in canvas px: base, far rain, trees, sun, cloud, sea strip, flag and buoys (S2: buoys drawn one
 * by one at twice their size). [t] cross-fades [prev] -> [cur]. [flag]: the flag's slot and whether it is in the row
 * (drawn here by day only; at night it goes over the night light, see islandPicture).
 */
internal fun DrawScope.drawH1(set: H1Set, bmp: (Int) -> ImageBitmap, cur: H1Pic, prev: H1Pic?, t: Float,
                              sunScale: Float = 1f, glow: Float = 0f, lift: SkyLift = SkyLift.NONE,
                              driftT: Float = -1f, withSun: Boolean = true, flagPlace: FlagPlace? = null, flagScale: Float = 1f,
                              flagAlpha: Float = 1f) {
    val p = prev ?: cur
    val a = if (prev == null) 1f else t
    fun one(name: String, alpha: Float, x: Float = 0f, y: Float = 0f) {
        if (alpha <= 0.01f) return
        val l = set.layers[name] ?: return
        drawImage(bmp(l.id), Offset(l.x + x, l.y + y), alpha = alpha)
    }
    /** Same layer name -> drawn once; else old fades out under the new one. */
    fun slot(old: String?, new: String?, x: Float = 0f, y: Float = 0f) {
        if (old == new) { if (new != null) one(new, 1f, x, y); return }
        if (old != null) one(old, 1f - a, x, y)
        if (new != null) one(new, a, x, y)
    }
    one("base", 1f)
    // Far rain: a still layer behind the island's right shoulder, faded once on change.
    if (withSun) slot(if (p.rain) "rain" else null, if (cur.rain) "rain" else null)
    one("trees_w0", 1f) // S1: no sway (the fan is in the dial toast and the climate bar)
    if (withSun) drawSun(set, bmp, cur, sunScale, glow, lift)
    // One fixed cloud layer, same lift as the sun (only in layer sets that have it).
    if (driftT >= 0f) {
        // Drifting out to the right edge, fading over the last half.
        val air = set.layers["air"]
        if (air != null) one("air", (2f - 2f * driftT).coerceIn(0f, 1f), x = (set.w - air.x) * driftT, y = lift.sun)
    } else if (set.layers.containsKey("air")) slot(if (p.air) "air" else null, if (cur.air) "air" else null, y = lift.sun)
    // S1: the sea is one fixed strip; its height never changes with the battery.
    drawRect(H1Set.SEA_DEEP, Offset(0f, set.waterY), androidx.compose.ui.geometry.Size(set.w, set.h - set.waterY + 2f))
    val inRow = flagPlace?.inRow == true
    // In the row the flag stands behind its slot's buoy (the buoy still counts); out of the row on a thin plate.
    if (withSun && flagPlace != null && inRow) drawFlagS1(set, bmp, "flag_row_l1", flagPlace, flagScale, flagAlpha)
    // A buoy that comes or goes rises from / sinks into the water (clipped at the resting foot), fading as it moves.
    val foot = buoyTop(set) + buoySize(set).second
    for (i in 1..IslandInfo.MAX_BUOYS) {
        val was = if (i <= p.buoys) 1f else 0f
        val now = if (i <= cur.buoys) 1f else 0f
        val al = was + (now - was) * a
        if (was == now) drawBuoy(set, bmp, i, al)
        else clipRect(0f, 0f, set.w, foot) { drawBuoy(set, bmp, i, al, dy = buoyRise(al)) }
    }
    if (withSun && flagPlace != null && !inRow) drawFlagS1(set, bmp, "flag_row_l1", flagPlace, flagScale, flagAlpha)
}

/** S2: buoy [i] cut out of buoys_n1_l1 and drawn twice its size, its foot 4 px into the sea strip. */
internal fun DrawScope.drawBuoy(set: H1Set, bmp: (Int) -> ImageBitmap, i: Int, alpha: Float,
                                filter: androidx.compose.ui.graphics.ColorFilter? = null, dy: Float = 0f) {
    if (alpha <= 0.01f) return
    val l = set.layers["buoys_n1_l1"] ?: return
    val src = set.buoySrc
    val (w, h) = buoySize(set)
    drawImage(bmp(l.id), srcOffset = androidx.compose.ui.unit.IntOffset(src[0], src[1]),
        srcSize = androidx.compose.ui.unit.IntSize(src[2] - src[0], src[3] - src[1]),
        dstOffset = androidx.compose.ui.unit.IntOffset((buoyCenter(set, i) - w / 2f).roundToInt(), (buoyTop(set) + dy).roundToInt()),
        dstSize = androidx.compose.ui.unit.IntSize(w.roundToInt(), h.roundToInt()), alpha = alpha, colorFilter = filter)
}

/**
 * S2 flag at buoy slot [slot]: the layer's cloth and pole top (no dome) at twice its size, the pole continued in code
 * down to the water, and under an out-of-row flag a thin plate 36 x 6 in the pole's colour (a dome read as a buoy, B-1).
 * Pulse [scale] is about the foot.
 */
internal fun DrawScope.drawFlagS1(set: H1Set, bmp: (Int) -> ImageBitmap, name: String, place: FlagPlace,
                                  scale: Float, alpha: Float) {
    val l = set.layers[name] ?: return
    val img = bmp(l.id)
    val cx = flagPoleX(set, place)
    val inRow = place.inRow
    val top = flagTop(set)
    val left = cx - BUOY_SCALE * (set.poleL + set.poleR) / 2f
    val poleW = BUOY_SCALE * (set.poleR - set.poleL).toFloat()
    // The layer's own pole colour (measured from the WebP): ink by day, white on the night asset.
    val poleColor = if (name.endsWith("_night")) Color.White else FLAG_POLE
    scale(scale, scale, pivot = Offset(cx, set.waterY)) {
        drawImage(img, srcOffset = androidx.compose.ui.unit.IntOffset.Zero,
            srcSize = androidx.compose.ui.unit.IntSize(img.width, set.flagRows),
            dstOffset = androidx.compose.ui.unit.IntOffset(left.roundToInt(), top.roundToInt()),
            dstSize = androidx.compose.ui.unit.IntSize(img.width * BUOY_SCALE, set.flagRows * BUOY_SCALE), alpha = alpha)
        val poleTop = top + BUOY_SCALE * set.flagRows - 1f
        drawRect(poleColor, Offset(cx - poleW / 2f, poleTop), androidx.compose.ui.geometry.Size(poleW, set.waterY - poleTop), alpha = alpha)
        if (!inRow) drawRect(poleColor, Offset(cx - PLATE_W / 2f, set.waterY - PLATE_H),
            androidx.compose.ui.geometry.Size(PLATE_W, PLATE_H), alpha = alpha)
    }
}

/** Night: a buoy as it looks under the 50 % night light (#121E38), drawn again over the night flag in the row. */
internal val NIGHT_BUOY = androidx.compose.ui.graphics.ColorFilter.colorMatrix(androidx.compose.ui.graphics.ColorMatrix(floatArrayOf(
    0.5f, 0f, 0f, 0f, 0.5f * 0x12,
    0f, 0.5f, 0f, 0f, 0.5f * 0x1E,
    0f, 0f, 0.5f, 0f, 0.5f * 0x38,
    0f, 0f, 0f, 1f, 0f,
)))

/**
 * The sun: one layer scaled about its centre, not a cross-fade; its glow is in the bitmap.
 * At night it is drawn above the night light with only its brightness lowered (centre ~#C98A2E).
 */
internal fun DrawScope.drawSun(set: H1Set, bmp: (Int) -> ImageBitmap, cur: H1Pic, sunScale: Float, glow: Float, lift: SkyLift,
                               night: Boolean = false) {
    // Night: the design's own night sun layer (night_sun, centre #C8892D),
    // same position and S/M/L rule; the colour filter is only a fallback for a layer set without it.
    val nightLayer = if (night) set.layers["sun_night_${"SML"[cur.sun]}"] else null
    val l = nightLayer ?: set.layers["sun_${"SML"[cur.sun]}"] ?: return
    val filter = if (night && nightLayer == null) NIGHT_SUN else null
    fun draw(alpha: Float) = drawImage(bmp(l.id), Offset(l.x, l.y), alpha = alpha, colorFilter = filter)
    translate(top = lift.sun) {
        if (sunScale == 1f) draw(1f) else scale(sunScale, sunScale, pivot = Offset(set.sunX, set.sunY)) { draw(1f) }
        if (glow > 0.01f) {
            // Glow only: the same sun bitmap again, with the disc cut out, so the disc and its colour never change.
            val disc = androidx.compose.ui.graphics.Path().apply {
                addOval(androidx.compose.ui.geometry.Rect(Offset(set.sunX, set.sunY), H1Set.SUN_R[cur.sun] * sunScale + 1f))
            }
            clipPath(disc, androidx.compose.ui.graphics.ClipOp.Difference) { draw(glow) }
        }
    }
}

/** Night sun: day sun centre (251, 198, 100) x these = (201, 138, 46) = #C98A2E; brightness only, same hue family. */
internal val NIGHT_SUN = androidx.compose.ui.graphics.ColorFilter.colorMatrix(androidx.compose.ui.graphics.ColorMatrix().apply {
    setToScale(0.80f, 0.70f, 0.46f, 1f)
})

/** The whole picture block rises 24 dp -> 0 and fades in, 300 ms, once. */
internal const val RISE_MS = 300
internal const val RISE_DP = 24f
/** The rise belongs to the arrival: not after this long in P. */
internal const val RISE_WINDOW_MS = 120_000L

/**
 * Receipt header: the whole H1 island in its current state, scaled
 * down (never cropped). Shown only with the receipt (door open after >= 1 min).
 */
@Composable
fun ReceiptHeaderPicture(ui: StayUi) {
    val info = IslandInfo.of(ui) ?: return
    StillIsland(H1Set.Large, H1Pic.of(info), Modifier.fillMaxWidth().clip(ChaseomUi.rInline))
}

/** First-use screen: the whole Small-set island, no labels, a calm mid state. */
@Composable
fun OnboardingIsland(modifier: Modifier) {
    StillIsland(H1Set.Small, H1Pic(sun = 1, wind = 1, level = 3, buoys = 5, k2 = false), modifier.clip(ChaseomUi.rContainer))
}

/** One still picture of the layer stack at the set's own aspect ratio; night = the same 0.3 veil. */
@Composable
private fun StillIsland(set: H1Set, pic: H1Pic, modifier: Modifier) {
    val bmp = rememberBitmaps()
    val night = app.chaseom.ui.theme.isNight()
    Spacer(
        modifier.aspectRatio(set.w / set.h).drawBehind {
            bmp.frame()
            val k = size.width / set.w
            scale(k, k, pivot = Offset.Zero) { drawH1(set, bmp, pic, null, 1f) }
            if (night) drawRect(Color.Black.copy(alpha = 0.1f))
        },
    )
}

/** Canvas-px lift (negative = up) for the sun and the wind lines when a Small box is taller than its picture. */
internal data class SkyLift(val sun: Float, val wind: Float) {
    companion object { val NONE = SkyLift(0f, 0f) }
}

/** Sun centre at 24 % and wind lines at 36 % of the box height, never lower than drawn. */
internal fun skyLiftFor(set: H1Set, k: Float, boxHdp: Float): SkyLift {
    val boxPx = boxHdp / k                       // box height in canvas px ([k] includes the set's zoom)
    val top = set.h - boxPx                      // canvas y of the box top (negative: taller box, positive: shorter)
    // Taller box: lift up to 24 % / 36 % of the box, never lower than drawn. Shorter box (cards): move
    // down only as far as needed to keep the largest sun (and the wind lines) 16 px inside the box top.
    fun lift(canvasY: Float, frac: Float, reach: Float) =
        if (top < 0f) minOf(0f, top + frac * boxPx - canvasY) else maxOf(0f, top + 16f + reach - canvasY)
    val windY = set.layers["wind_w1"]?.let { it.y + 20f } ?: 131f
    return SkyLift(sun = lift(set.sunY, 0.24f, H1Set.SUN_R.last()), wind = lift(windY, 0.36f, 20f))
}

/** Legend swatches: the picture's own shapes at 24 dp (can buoy, water #4B627C). */
internal enum class Swatch { SUN, WATER, BUOY, CRATE, CLOUD, RAIN, FLAG }

@Composable
private fun LegendItem(sw: Swatch, label: String, night: Boolean = false) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        androidx.compose.foundation.Canvas(Modifier.size(32.dp)) { // swatches 32 dp (legend icons >= 32)
            val u = size.width / 24f
            when (sw) {
                Swatch.SUN -> drawCircle(
                    androidx.compose.ui.graphics.Brush.verticalGradient(listOf(Color(0xFFFFDA85), Color(0xFFF7A01C))),
                    radius = 7f * u, center = Offset(12f * u, 12f * u))
                Swatch.WATER -> drawRoundRect(Color(0xFF4B627C), Offset(3f * u, 8f * u),
                    androidx.compose.ui.geometry.Size(18f * u, 9f * u), androidx.compose.ui.geometry.CornerRadius(2f * u))
                Swatch.BUOY -> {
                    val ink = Color(0xFF4F5A68)
                    drawLine(ink, Offset(12f * u, 3f * u), Offset(12f * u, 9f * u), strokeWidth = 1.6f * u)
                    drawCircle(ink, 1.6f * u, Offset(12f * u, 3f * u))
                    drawRoundRect(Color(0xFFF2F4F7), Offset(8f * u, 9f * u), androidx.compose.ui.geometry.Size(8f * u, 12f * u),
                        androidx.compose.ui.geometry.CornerRadius(4f * u, 3f * u))
                    drawRoundRect(ink, Offset(8f * u, 9f * u), androidx.compose.ui.geometry.Size(8f * u, 12f * u),
                        androidx.compose.ui.geometry.CornerRadius(4f * u, 3f * u), style = androidx.compose.ui.graphics.drawscope.Stroke(1.2f * u))
                    drawRect(ink, Offset(8f * u, 14f * u), androidx.compose.ui.geometry.Size(8f * u, 2.5f * u))
                }
                Swatch.CRATE -> {
                    val wood = Color(0xFFD9C3A0); val edge = Color(0xFF8C7656)
                    drawRect(wood, Offset(5f * u, 7f * u), androidx.compose.ui.geometry.Size(14f * u, 12f * u))
                    drawRect(edge, Offset(5f * u, 7f * u), androidx.compose.ui.geometry.Size(14f * u, 12f * u),
                        style = androidx.compose.ui.graphics.drawscope.Stroke(1.3f * u))
                    drawLine(edge, Offset(5f * u, 13f * u), Offset(19f * u, 13f * u), strokeWidth = 1.2f * u)
                }
                // Night: white pole and cloth (the navy cloth vanished on the dark page, B-2).
                Swatch.FLAG -> if (night) waitFlagMark(9f * u, 3f * u, 21f * u, Color(0xFFD6DAE0), Color.White)
                else waitFlagMark(9f * u, 3f * u, 21f * u, Color(0xFF4A4E57), Color(0xFF1F3B5C))
                Swatch.RAIN -> {
                    // a flat far cloud with a soft veil under it (the layer's own shapes, small)
                    drawRoundRect(Color(0xFFA7B1BE), Offset(3f * u, 6f * u), androidx.compose.ui.geometry.Size(18f * u, 5f * u),
                        androidx.compose.ui.geometry.CornerRadius(2.5f * u))
                    drawRect(Color(0xFF7A8798), Offset(4f * u, 10f * u), androidx.compose.ui.geometry.Size(16f * u, 1.2f * u))
                    val veil = androidx.compose.ui.graphics.Path().apply {
                        moveTo(6f * u, 11f * u); lineTo(18f * u, 11f * u); lineTo(15f * u, 21f * u); lineTo(3f * u, 21f * u); close()
                    }
                    drawPath(veil, androidx.compose.ui.graphics.Brush.verticalGradient(
                        listOf(Color(0xFF8E9BAA).copy(alpha = 0.6f), Color(0xFF8E9BAA).copy(alpha = 0.1f)), 11f * u, 21f * u))
                }
                Swatch.CLOUD -> {
                    val c = Color(0xFFDCE1E8); val ink = Color(0xFF8A939E)
                    drawCircle(c, 4.5f * u, Offset(9f * u, 14f * u)); drawCircle(c, 5.5f * u, Offset(14f * u, 12f * u))
                    drawRoundRect(c, Offset(4.5f * u, 13f * u), androidx.compose.ui.geometry.Size(15f * u, 5.5f * u),
                        androidx.compose.ui.geometry.CornerRadius(2.75f * u))
                    // the curled tail: one stroke from the right end back over the cloud, no arrowhead
                    val path = androidx.compose.ui.graphics.Path().apply {
                        moveTo(19.5f * u, 15.5f * u); cubicTo(23f * u, 13f * u, 22f * u, 6f * u, 16.5f * u, 6.2f * u)
                    }
                    drawPath(path, ink, style = androidx.compose.ui.graphics.drawscope.Stroke(1.5f * u, cap = androidx.compose.ui.graphics.StrokeCap.Round))
                }
            }
        }
        Spacer(Modifier.width(ChaseomUi.line))
        Text(label, style = ChaseomUi.caption, color = cui().text2, softWrap = false)
    }
}

/** Night light colour (applied at 50 % / 76 %). */
internal val NIGHT_LIGHT = Color(0xFF121E38)

/** The cloud's one drift out of the picture on outside air. */
internal const val DRIFT_MS = 1_400

/** 0 -> 1 rise progress for the arrival (1 at rest). */
@Composable
private fun rememberRise(moorAtMs: Long?, covered: Boolean, enabled: Boolean): Float {
    val done = remember { longArrayOf(Long.MIN_VALUE) }
    val a = remember { Animatable(1f) }
    val windowInfo = LocalWindowInfo.current
    LaunchedEffect(moorAtMs, covered) {
        val at = moorAtMs ?: return@LaunchedEffect
        if (!enabled || covered || done[0] == at) return@LaunchedEffect
        if (android.os.SystemClock.elapsedRealtime() - at !in 0..RISE_WINDOW_MS) return@LaunchedEffect
        done[0] = at
        a.snapTo(0f)
        withTimeoutOrNull(400) { snapshotFlow { windowInfo.isWindowFocused }.first { it } }
        withFrameNanos { }
        app.chaseom.Probe.log("riseFrame")
        a.animateTo(1f, tween(RISE_MS, easing = androidx.compose.animation.core.CubicBezierEasing(0f, 0f, 0.2f, 1f)))
    }
    // The frame the island is first uncovered must already be the start state (no one-frame flash of the full
    // picture before the effect runs).
    val at = moorAtMs
    val pending = at != null && enabled && !covered && done[0] != at &&
        android.os.SystemClock.elapsedRealtime() - at in 0..RISE_WINDOW_MS
    return if (pending) 0f else a.value
}
