package app.chaseom.ui.scenes

import android.graphics.BitmapFactory
import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
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
    /** Picture scale about the bottom-right corner (water line fixed). */
    val zoom: Float = 1f,
    /** Box height over the set's own ratio (the zoomed picture's top sky is cropped above this). */
    val boxGrow: Float = 1f,
    /**
     * Canvas height drawn (build G, S1): the water is a fixed strip [WATER_STRIP] px under the lowest water line,
     * so the picture ends there; the layers below it are cut by the box clip.
     */
    val h: Float = 600f,
    /** S2: canvas x of the first buoy (where the boat was) and the dock-top centre of the tomorrow chip. */
    val buoyLeft: Float = 0f,
    val chipX: Float = 0f, val chipY: Float = 0f,
) {
    /** Canvas y of the water surface per step 1..5 (layers manifest water_top_y_by_step). */
    val waterTop: FloatArray get() = if (w == 1600f) floatArrayOf(520f, 480f, 440f, 400f, 360f) else floatArrayOf(480f, 456f, 432f, 408f, 384f)
    companion object {
        val Large = H1Set(H1Layers.L, 1600f, 40f, 1450f, 82f, // sun higher (108 -> 82)
            batX = 40f, batY = 569f, buoyX = 1466f, buoyY = 569f, buoyAnchor = 0.5f, keepX = 1172f, keepY = 569f, keepAnchor = 0.5f,
            zoom = 1.08f, boxGrow = 1.08f, // one scale per window size; the whole zoomed picture fits
            h = 520f + WATER_STRIP, buoyLeft = 1060f, chipX = 965f, chipY = 285f)
        // Small rail line 1 = battery (start) + buoys (end at 932), line 2 = crates.
        val Small = H1Set(H1Layers.S, 960f, 24f, 836f, 92f, // sun 118 -> 92
            batX = 28f, batY = 503f, buoyX = 932f, buoyY = 503f, buoyAnchor = 1f, keepX = 28f, keepY = 551f, keepAnchor = 0f,
            h = 480f + WATER_STRIP, buoyLeft = 560f, chipX = 470f, chipY = 300f)
        /** Sky above a picture that is shorter than its box, and the sea under its lower 30 % (no seam). */
        val SKY_TOP = Color(0xFFF5F6F8) // The sky top is the page colour (the picture melts into it)
        val SEA_DEEP = Color(0xFF3B536E) // water bottom
        /** Sun disc radius per size (S / M / L), for the "설정 °C" label next to it. */
        val SUN_R = floatArrayOf(28f, 44f, 64f)
    }
}

/**
 * Build G, S1 (three always-on symbols: sun, buoys, flag). The water is a fixed strip (battery is the header's
 * "배터리 n%"), tomorrow's share is the dock chip "내일 지킬 몫 n km" (the crates and their K1/K2 outline move into
 * the chip's amber border), and the boat, wind lines and water ticks are not drawn. Clouds and far rain stay events.
 */
internal const val WATER_STRIP = 22f
/** The fixed water step under S1 (the lowest water line, so the island shows whole). */
internal const val S1_LEVEL = 1
/** S2: buoys and flag twice their art size, scaled about the first buoy's foot on the water line. */
internal const val BUOY_SCALE = 2f

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
        fun of(i: IslandInfo) = H1Pic(i.sunLevel, i.wind, S1_LEVEL, i.buoys, i.crateOutline, i.cloud,
            rain = app.chaseom.BuildConfig.KICK_RAIN && i.rain)
    }
}

/** Window-width rule: below 1200 dp the Small set. */
internal fun h1SetFor(widthDp: Float): H1Set = if (widthDp >= 1200f) H1Set.Large else H1Set.Small

/** Picture batching after the last input (numbers and toast still change at once). */
private const val BATCH_MS = 300L
/** Water step change; other changes use [Island.FADE_MS]. */
private const val WATER_MS = 600
/** The picture never shrinks below this share of its natural height (scaled, not cropped). */
internal const val MIN_FIT = 0.75f

/**
 * The page scroll around the island. The space left on the first screen is measured from the picture's top in the
 * unscrolled page, so scrolling never resizes the picture.
 */
val LocalPageScroll = androidx.compose.runtime.staticCompositionLocalOf<androidx.compose.foundation.ScrollState?> { null }

/**
 * Picture box height in dp. [left] is the height left for the box on the first screen (null = not measured yet).
 * Without cards ([fill]) the box takes the height left; with cards it never grows past [natural].
 * Either way, when [left] is under [natural] the box shrinks to it, never below [MIN_FIT] of [natural].
 * No separate minimum: on Small the natural height (about 525 dp at 840 dp) is under the old 560 dp minimum, and a
 * minimum above [left] would push the flag line off the first screen and make the height jump at [natural].
 */
internal fun islandBoxHeight(natural: Float, fill: Boolean, left: Float?): Float = when {
    left == null -> natural
    // Without cards the box is exactly the height left (taller than natural: more sky; shorter: scaled).
    fill -> maxOf(natural * MIN_FIT, left)
    else -> minOf(natural, maxOf(natural * MIN_FIT, left))
}

/** Picture width in dp: a box shorter than [natural] scales the whole picture by the same ratio (no crop). */
internal fun islandPicWidth(fullW: Float, natural: Float, boxH: Float): Float =
    if (boxH < natural) fullW * (boxH / natural) else fullW

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
    val night = isSystemInDarkTheme()
    // Picture -> 16 -> cause line -> 32 -> cards.
    // With cards the whole block must fit the window, so the picture takes only the height
    // left after what is under it (measured), never below 75 % of its natural height; the sky side gives.
    var belowH by remember { mutableFloatStateOf(0f) }
    var flagH by remember { mutableFloatStateOf(0f) }
    Column(modifier) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val set = h1SetFor(with(LocalDensity.current) { LocalWindowInfo.current.containerSize.width.toDp().value })
            // Large: card width, at most 1760 dp so a full-screen window keeps the picture under 660 dp tall.
            val fullW: Dp = if (set === H1Set.Large) minOf(maxWidth, 1760.dp) else maxWidth
            // No outline at night (it read as a 1 px seam); see the night light below.
            val outline = Modifier
            // Without cards the picture box fills the height left on screen (24 dp bottom margin below the cause
            // line); taller than natural, the picture stays 1.0x, bottom-aligned, with plain sky above.
            val natural = fullW * (set.h / set.w) * set.boxGrow
            val density = LocalDensity.current
            val windowH = with(density) { LocalWindowInfo.current.containerSize.height.toDp() }
            var topInWindow by remember { mutableFloatStateOf(Float.NaN) }
            val pageScroll = LocalPageScroll.current
            // Large without cards fills the height left too (shorter than natural: scaled, at least MIN_FIT).
            val fill = ui.exchange == null && ui.airMinutes == null && !ui.seatHeatLong
            // The first-stay hint is one caption line under the cause line; the picture fills around it.
            val hintH = if (ui.firstStayHint) ChaseomUi.related + 32.dp else 0.dp
            // On Small the caption row is two lines (legend, then the cause chip).
            val chipLineH = (if (set === H1Set.Small) ChaseomUi.line + 32.dp else 0.dp) +
                (if (ui.recircMinutes != null) ChaseomUi.line + 32.dp else 0.dp) +
                (if (app.chaseom.BuildConfig.KICK_RAIN && ui.rain) ChaseomUi.line + 32.dp else 0.dp)
            // When what is under the picture (K1 card, flag lines, cards) leaves less than the natural height,
            // the whole picture shrinks to fit (never below 75 %) instead of cropping the sky and the trees,
            // so the flag line, "표시만 해요" and "깃발 내리기" stay on the first screen.
            // Without cards the box fills the height left: taller than natural, the extra is sky (full width, so the
            // buoy x 955/960 limit is untouched; sun and wind lift, see skyLiftFor); shorter, the whole picture scales.
            val left: Dp? = when {
                topInWindow.isNaN() -> null
                fill -> windowH - with(density) { topInWindow.toDp() } - ChaseomUi.related - 32.dp - 24.dp - hintH - chipLineH -
                    with(density) { flagH.toDp() }
                belowH <= 0f -> null
                else -> windowH - with(density) { topInWindow.toDp() } - with(density) { belowH.toDp() } - 24.dp
            }
            val boxH = islandBoxHeight(natural.value, fill, left?.value).dp
            // Shorter than natural: scale the whole picture (same aspect), centred; nothing is cut off.
            val picW: Dp = islandPicWidth(fullW.value, natural.value, boxH.value).dp
            val k = picW.value / set.w // dp per canvas px
            // Arriving in P, the whole picture block (sky, island, water, buoys together,
            // so the water never seems to drop) rises 24 dp -> 0 and fades in, 300 ms, cubic-bezier(0, 0, 0.2, 1),
            // once per arrival, the first time it is not covered (a night notice opening the stay is read first),
            // within 2 min of arriving; never with animations off. Leaving P still folds at once.
            val rise = rememberRise(ui.moorAtMs, covered = ui.notice != null || ui.receipt != null, enabled = !animOff)
            Box(
                // The whole picture is clipped once here (no per-layer clip).
                // Centred under the picture's own width (fullW), so a window wider than 1760 dp keeps the column.
                Modifier.padding(start = (fullW - picW) / 2)
                    .width(picW).height(boxH).onGloballyPositioned { topInWindow = it.positionInWindow().y + (pageScroll?.value ?: 0) }
                    // Hidden until the space is measured (one frame), so the picture never shows at a size it then leaves.
                    .graphicsLayer { translationY = (1f - rise) * RISE_DP.dp.toPx(); alpha = if (left == null) 0f else rise }
                    .clip(ChaseomUi.rContainer).then(outline)
                    .semantics { contentDescription = cd; liveRegion = LiveRegionMode.Polite },
            ) {
                // In a taller Small box the sun (and its label) sit at 24 % and the wind at 36 % of
                // the box height. The sun's position carries no meaning; only its size does.
                val skyLift = if (boxH > natural) skyLiftFor(set, k * set.zoom, boxH.value) else SkyLift.NONE
                // Wait flag on the buoy row: fades in once over 400 ms when planted; a one-time 0.95 -> 1 pulse when
                // it is reached (no colour change). No flag picture under tomorrow's share.
                val wf = ui.waitForecast
                val flagSlot = wf?.let { app.chaseom.stay.WaitFlags.rowSlot(it, info.buoys) }
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
                    flagSlot = flagSlot, flagScale = flagScale.value, flagAlpha = flagAlpha.value)
                // H1: the sun and its label sit top right, so the toast takes the top-left sky up to the label.
                var sunLabelLeft by remember { mutableFloatStateOf(Float.NaN) }
                IslandLabels(ui, set, k, picW.value, boxH.value, shown, sunLiftDp = skyLift.sun * k * set.zoom, animOff = animOff) { sunLabelLeft = it }
                ui.bar?.let { b -> KeepChip(b.keepKm, k2 = info.crateOutline, night = night, set = set, k = k, picW = picW.value, boxH = boxH.value) }
                val toastMax = if (sunLabelLeft.isNaN()) Modifier else Modifier.widthIn(max = with(LocalDensity.current) { sunLabelLeft.toDp() } - 32.dp)
                Box(Modifier.align(Alignment.TopStart).padding(start = 16.dp).then(toastMax)) { pictureTop() }
            }
        }
        Column(Modifier.fillMaxWidth().onGloballyPositioned { belowH = it.size.height.toFloat() }) {
        // No text on the picture. One caption row under it = legend swatches
        // (picture shapes, no numbers) + the cause chip (always shown) at the right end.
        // Small: the chip goes to a second line.
        Spacer(Modifier.height(ChaseomUi.related))
        val k = cui()
        val small = h1SetFor(with(LocalDensity.current) { LocalWindowInfo.current.containerSize.width.toDp().value }) === H1Set.Small
        val chip: @Composable () -> Unit = {
            Row(verticalAlignment = Alignment.CenterVertically) {
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
                if (flagInLegend) LegendItem(Swatch.FLAG, stringResource(R.string.is_lg_flag))
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
        if (ui.firstStayHint) {
            // No chip; one caption line like the cause line (28 dp fan, 24 Medium #4A4E57).
            // The fan is ink #4A4E57, not amber (amber = a value the user changed).
            Spacer(Modifier.height(ChaseomUi.related))
            Row(verticalAlignment = Alignment.CenterVertically) {
                androidx.compose.material3.Icon(androidx.compose.ui.res.painterResource(R.drawable.ic_fan), null,
                    tint = Color(0xFF4A4E57), modifier = Modifier.size(ChaseomUi.iconInline))
                Spacer(Modifier.width(ChaseomUi.line))
                // At the 3-hour cap the time cannot change, so only the island is promised.
                val hint = if (ui.hero?.overCap == true) R.string.is_hint_first_cap else R.string.is_hint_first
                Text(stringResource(hint), style = ChaseomUi.caption, color = Color(0xFF4A4E57))
            }
        }
        Box(Modifier.fillMaxWidth().onGloballyPositioned { flagH = it.size.height.toFloat() }) { flagRow() }
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
 * S1: tomorrow's share as one chip on the dock ("내일 지킬 몫 30km", the bottom bar's own words), replacing the boat's
 * three crates. At K1 / K2 the chip takes the crates' amber outline (#E0A030, 2 dp); the words do not change.
 */
@Composable
private fun KeepChip(keepKm: Int, k2: Boolean, night: Boolean, set: H1Set, k: Float, picW: Float, boxH: Float) {
    val z = k * set.zoom
    val cx = picW - (set.w - set.chipX) * z
    val cy = boxH - (set.h - set.chipY) * z
    val bg = if (night) Color(0xFF1E2A40) else Color.White
    val ink = if (night) Color(0xFFE6E9EE) else Color(0xFF131417)
    val edge = when { k2 -> KEEP_AMBER; night -> Color(0xFF4A5568); else -> Color(0xFFC9CED6) }
    Box(Modifier.layout { m, cons ->
        val p = m.measure(cons.copy(minWidth = 0, minHeight = 0))
        layout(p.width, p.height) {
            p.place((cx.dp.toPx() - p.width / 2f).roundToInt(), (cy.dp.toPx() - p.height / 2f).roundToInt())
        }
    }.background(bg, RoundedCornerShape(8.dp)).border(if (k2) 2.dp else 1.dp, edge, RoundedCornerShape(8.dp))
        .padding(horizontal = 12.dp, vertical = 4.dp)) {
        Text(stringResource(R.string.h_bar_keep, keepKm), style = ChaseomUi.caption, color = ink, softWrap = false, maxLines = 1)
    }
}

/** K1 / K2 outline of tomorrow's share (was the crate frame). */
internal val KEEP_AMBER = Color(0xFFE0A030)

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
                          lift: SkyLift = SkyLift.NONE, flagSlot: Float? = null, flagScale: Float = 1f, flagAlpha: Float = 1f): H1Shown {
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
                    driftT = drift.value, withSun = !night, flagSlot = flagSlot, flagScale = flagScale, flagAlpha = flagAlpha)
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
                    if (flagSlot != null) onBuoyRow(set) { drawFlag(set, bmp, "flag_row_l1_night", flagSlot, -(c.level - 1) * set.step, flagScale, flagAlpha) }
                }
            }
        }
    })
    return H1Shown(shown, sunR.value)
}

/** The picture state on screen and the sun radius now (canvas px), for the labels. */
internal data class H1Shown(val pic: H1Pic, val sunR: Float)

/** The 13-layer stack (design layer order), in canvas px. [t] cross-fades [prev] -> [cur]. */
internal fun DrawScope.drawH1(set: H1Set, bmp: (Int) -> ImageBitmap, cur: H1Pic, prev: H1Pic?, t: Float,
                              sunScale: Float = 1f, glow: Float = 0f, lift: SkyLift = SkyLift.NONE,
                              driftT: Float = -1f, withSun: Boolean = true, flagSlot: Float? = null, flagScale: Float = 1f,
                              flagAlpha: Float = 1f) {
    val p = prev ?: cur
    val a = if (prev == null) 1f else t
    val dyCur = -(cur.level - 1) * set.step
    val dyPrev = -(p.level - 1) * set.step
    val dy = dyPrev + (dyCur - dyPrev) * a
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
    slot("trees_w${p.wind}", "trees_w${cur.wind}")
    if (withSun) drawSun(set, bmp, cur, sunScale, glow, lift)
    // S1: no wind lines (the fan had no legend item; the bottom climate bar shows it).
    // One fixed cloud layer after the wind, same lift as the sun (only in layer sets that have it).
    if (driftT >= 0f) {
        // Drifting out to the right edge, fading over the last half.
        val air = set.layers["air"]
        if (air != null) one("air", (2f - 2f * driftT).coerceIn(0f, 1f), x = (set.w - air.x) * driftT, y = lift.sun)
    } else if (set.layers.containsKey("air")) slot(if (p.air) "air" else null, if (cur.air) "air" else null, y = lift.sun)
    slot("wet_l${p.level}", "wet_l${cur.level}")
    // S1: no boat and no crates (tomorrow's share is the dock chip); S2: the buoy row at twice its size.
    onBuoyRow(set) {
        slot("buoys_n${p.buoys}_l1", "buoys_n${cur.buoys}_l1", y = dy)
        // Wait flag on the buoy row: after the buoys, before the water (its foot sits on the water line).
        if (withSun && flagSlot != null) drawFlag(set, bmp, "flag_row_l1", flagSlot, dy, flagScale, flagAlpha)
    }
    slot("water_l${p.level}", "water_l${cur.level}")
    slot("refl_l${p.level}", "refl_l${cur.level}")
    slot("surf_l${p.level}", "surf_l${cur.level}")
    // S1: no water ticks (the water no longer moves).
}

/** S2: draws [block] scaled [BUOY_SCALE]x about the first buoy's foot on the lowest water line, moved to [H1Set.buoyLeft]. */
internal fun DrawScope.onBuoyRow(set: H1Set, block: DrawScope.() -> Unit) {
    val row = set.layers["buoys_n1_l1"] ?: return block()
    translate(left = set.buoyLeft - row.x) { scale(BUOY_SCALE, BUOY_SCALE, pivot = buoyPivot(set)) { block() } }
}

/** Pivot of the S2 buoy row: the first buoy layer's left edge on the S1 water line. */
internal fun buoyPivot(set: H1Set): Offset = Offset(set.layers.getValue("buoys_n1_l1").x, set.waterTop[S1_LEVEL - 1])

/** Canvas x where art x [x] of the buoy row lands after the S2 move and scale. */
internal fun buoyRowX(set: H1Set, x: Float): Float = set.buoyLeft + (x - buoyPivot(set).x) * BUOY_SCALE

/** Canvas px between two buoy slots for the flag layer (flag_manifest.json step_px). */
internal fun flagStep(set: H1Set): Float = if (set === H1Set.Large) 44f else 32f

/** The flag layer at [slot] (1 = first buoy slot), moved with the water like the buoys, scaled about its foot. */
internal fun DrawScope.drawFlag(set: H1Set, bmp: (Int) -> ImageBitmap, name: String, slot: Float, dy: Float, scale: Float, alpha: Float) {
    val l = set.layers[name] ?: return
    val img = bmp(l.id)
    val x = l.x + (slot - 1f) * flagStep(set)
    val y = l.y + dy
    val foot = Offset(x + img.width / 2f, y + img.height)
    scale(scale, scale, pivot = foot) { drawImage(img, Offset(x, y), alpha = alpha) }
}

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
    val night = isSystemInDarkTheme()
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
private fun LegendItem(sw: Swatch, label: String) {
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
                Swatch.FLAG -> waitFlagMark(9f * u, 3f * u, 21f * u, Color(0xFF4A4E57), Color(0xFF1F3B5C))
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
