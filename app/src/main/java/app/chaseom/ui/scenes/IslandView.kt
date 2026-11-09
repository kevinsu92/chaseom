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
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
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
 * The two H1 layer sets (04_design art_v3/ART_H.md section 8): Large 1600x600 for windows >= 1200 dp,
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
) {
    val h = 600f
    companion object {
        val Large = H1Set(H1Layers.L, 1600f, 40f, 1450f, 108f,
            batX = 40f, batY = 569f, buoyX = 1466f, buoyY = 569f, buoyAnchor = 0.5f, keepX = 1172f, keepY = 569f, keepAnchor = 0.5f)
        val Small = H1Set(H1Layers.S, 960f, 24f, 836f, 118f,
            batX = 28f, batY = 530f, buoyX = 28f, buoyY = 576f, buoyAnchor = 0f, keepX = 932f, keepY = 576f, keepAnchor = 1f)
        /** Sun disc radius per size (S / M / L), for the "설정 °C" label next to it. */
        val SUN_R = floatArrayOf(28f, 44f, 64f) // H1 v1.7 (POLISH_PASS §0.6)
    }
}

/** What the picture shows; changes are batched and cross-faded (platform ISLAND_V2_SIGNALS, MOTION v3). */
internal data class H1Pic(val sun: Int, val wind: Int, val level: Int, val buoys: Int, val k2: Boolean) {
    companion object {
        fun of(i: IslandInfo) = H1Pic(i.sunLevel, i.wind, i.waterStep + 1, i.buoys, i.crateOutline)
    }
}

/** Window-width rule (ISLAND_REDESIGN 3.1): below 1200 dp the Small set. */
internal fun h1SetFor(widthDp: Float): H1Set = if (widthDp >= 1200f) H1Set.Large else H1Set.Small

/** Picture batching after the last input (numbers and toast still change at once). */
private const val BATCH_MS = 300L
/** Water step change (MOTION v3); other changes use [Island.FADE_MS]. */
private const val WATER_MS = 600

/**
 * Information island, H1 art (04_design art_v3 layers_h1). Each element has one meaning (IslandInfo):
 * sun size = set temperature, buoys = stay time, water = battery (5 steps), crate frame = tomorrow's share.
 * Labels are screen text (24 sp) laid on the picture, never baked into it. Bitmaps are decoded once and
 * drawn as they are; no frame loop: with no value change nothing is redrawn.
 */
@Composable
fun IslandPanel(
    ui: StayUi,
    modifier: Modifier = Modifier,
    /** Drawn inside the top-left sky of the picture, never over the "설정 °C" label (HANDOFF 9.6 v7 Small toast). */
    pictureTop: @Composable () -> Unit = {},
    cards: @Composable () -> Unit = {},
) {
    val s = Island.state(ui, android.os.SystemClock.elapsedRealtime()) ?: return // same clock as the engine
    // Water hysteresis needs the previous step; it lives as long as the island (reset when the stay ends).
    val water = remember { intArrayOf(-1) }
    val info = IslandInfo.of(ui, water[0].takeIf { it >= 0 }) ?: return
    water[0] = info.waterStep
    val ctx = LocalContext.current
    // System animation scale 0 -> picture swaps without transitions (HANDOFF 8.6 stop condition 5).
    val animOff = remember {
        runCatching { Settings.Global.getFloat(ctx.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f }
            .getOrDefault(false)
    }
    // Latency probes (all builds, one log line per event like the cabin PROBE lines).
    LaunchedEffect(ui.lastClimateChangeMs) {
        if (ui.lastClimateChangeMs != null) {
            withFrameNanos { }
            android.util.Log.i("PROBE", "islandFrame wall=${System.currentTimeMillis()}")
        }
    }
    DisposableEffect(Unit) { onDispose { android.util.Log.i("PROBE", "islandGone wall=${System.currentTimeMillis()}") } }
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
    // POLISH_PASS v1.2 §0.2: picture -> 16 -> cause line -> 32 -> cards.
    Column(modifier) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val set = h1SetFor(with(LocalDensity.current) { LocalWindowInfo.current.containerSize.width.toDp().value })
            // Large: card width, at most 1760 dp so a full-screen window keeps the picture under 660 dp tall.
            val picW: Dp = if (set === H1Set.Large) minOf(maxWidth, 1760.dp) else maxWidth
            val k = picW.value / set.w // dp per canvas px
            Box(
                Modifier.width(picW).height(picW * (set.h / set.w)).clip(ChaseomUi.rContainer)
                    .semantics { contentDescription = cd; liveRegion = LiveRegionMode.Polite },
            ) {
                val shown = islandPicture(set, info, ui.moorAtMs, s.mooring, animOff, Modifier.matchParentSize())
                // Night theme: same daytime picture, black veil 0.3 only (HANDOFF 9.7, never night colours).
                if (night) Box(Modifier.matchParentSize().background(Color.Black.copy(alpha = 0.3f)))
                // H1: the sun and its label sit top right, so the toast takes the top-left sky up to the label.
                var sunLabelLeft by remember { mutableFloatStateOf(Float.NaN) }
                IslandLabels(ui, set, k, shown) { sunLabelLeft = it }
                val toastMax = if (sunLabelLeft.isNaN()) Modifier else Modifier.widthIn(max = with(LocalDensity.current) { sunLabelLeft.toDp() } - 32.dp)
                Box(Modifier.align(Alignment.TopStart).padding(start = 16.dp).then(toastMax)) { pictureTop() }
            }
        }
        // ISLAND_REDESIGN v2.5 cause line (QA M-3 wording), always shown. POLISH v1.2: no chip, an info icon + caption.
        Spacer(Modifier.height(ChaseomUi.related))
        val k = cui()
        Row(verticalAlignment = Alignment.CenterVertically) {
            androidx.compose.material3.Icon(androidx.compose.ui.res.painterResource(R.drawable.ic_info), null,
                tint = k.text2, modifier = Modifier.size(ChaseomUi.iconInline))
            Spacer(Modifier.width(ChaseomUi.line))
            Text(stringResource(R.string.is_legend), style = ChaseomUi.caption, color = k.text2)
        }
        Column(Modifier.padding(top = ChaseomUi.block), verticalArrangement = Arrangement.spacedBy(ChaseomUi.related)) {
            cards() // HANDOFF 9.2.1 v6: air / swap cards under the cause line
            if (ui.firstStayHint) {
                // HANDOFF 9.5 / POLISH §2-14: tint surface, radius 16, padding 24 x 16, gap 16, fan icon in the sun amber.
                Row(
                    Modifier.background(k.tint, ChaseomUi.rInline).padding(horizontal = 24.dp, vertical = ChaseomUi.related),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    androidx.compose.material3.Icon(androidx.compose.ui.res.painterResource(R.drawable.ic_fan), null,
                        tint = ChaseomUi.amberRim, modifier = Modifier.size(ChaseomUi.iconInline))
                    Spacer(Modifier.width(ChaseomUi.related))
                    // QA REVIEW_COPY_B 5 (UXR E-2): at the 3-hour cap the time cannot change, so only the island is promised.
                    val hint = if (ui.hero?.overCap == true) R.string.is_hint_first_cap else R.string.is_hint_first
                    Text(stringResource(hint), style = ChaseomUi.body, color = k.text1)
                }
            }
        }
    }
}

/** Screen text on the picture (24 sp): "설정 °C" next to the sun, and the rail under the water. */
@Composable
private fun IslandLabels(ui: StayUi, set: H1Set, k: Float, pic: H1Pic, sunLabelLeft: (Float) -> Unit) {
    val bar = ui.bar ?: return
    val c = ui.climate
    val sunText = if (c?.powerOn == true && c.setTempC != null) stringResource(R.string.hvac_temp, tempText(c.setTempC))
    else stringResource(R.string.h_hvac_off)
    // H1: ink text with a white glow on the sky; white text on the navy rail (POLISH: picture label role).
    val bare = ChaseomUi.pictureLabel.copy(color = Color(0xFF131417), shadow = Shadow(Color.White, Offset.Zero, 8f))
    val rail = bare.copy(color = Color.White, shadow = Shadow(Color.Black.copy(alpha = 0.35f), Offset(0f, 1f), 2f))
    PicText(sunText, bare, (set.sunX - H1Set.SUN_R[pic.sun] - 22f) * k, set.sunY * k, 1f, sunLabelLeft)
    // POLISH_PASS v1.2 §0.4: a 24 dp line icon in front of each rail label makes "A = B" read as a legend.
    PicText(stringResource(R.string.is_rail_bat, bar.socPercent), rail, set.batX * k, set.batY * k, 0f, icon = R.drawable.ic_rail_wave)
    if (pic.buoys > 0) PicText(stringResource(R.string.is_rail_buoy), rail, set.buoyX * k, set.buoyY * k, set.buoyAnchor, icon = R.drawable.ic_rail_buoy)
    PicText(stringResource(R.string.is_rail_keep, bar.keepKm), rail, set.keepX * k, set.keepY * k, set.keepAnchor, icon = R.drawable.ic_crate)
}

/** Text whose anchor point (0 start, 0.5 centre, 1 end; vertical centre) sits at (x, y) dp in the picture. */
@Composable
private fun PicText(text: String, style: TextStyle, xDp: Float, yDp: Float, anchor: Float, onLeft: ((Float) -> Unit)? = null, icon: Int? = null) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.layout { m, cons ->
        val p = m.measure(cons.copy(minWidth = 0, minHeight = 0))
        layout(p.width, p.height) {
            val left = (xDp.dp.toPx() - anchor * p.width).roundToInt()
            onLeft?.invoke(left.toFloat())
            p.place(left, (yDp.dp.toPx() - p.height / 2f).roundToInt())
        }
    }) {
        if (icon != null) {
            androidx.compose.material3.Icon(androidx.compose.ui.res.painterResource(icon), null, tint = style.color, modifier = Modifier.size(24.dp))
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
 * the water. Mooring slides the boat in from the right (HANDOFF 10.3 ① v5 distance rule).
 */
@Composable
private fun islandPicture(set: H1Set, info: IslandInfo, moorAtMs: Long?, mooring: Boolean, motionOff: Boolean, modifier: Modifier): H1Pic {
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
    // 1 = out at sea, 0 = moored.
    val moor = remember { Animatable(if (mooring && !motionOff) 1f else 0f) }
    val bob = remember { Animatable(0f) }
    val windowInfo = LocalWindowInfo.current
    LaunchedEffect(moorAtMs) {
        if (!mooring || motionOff) { moor.snapTo(0f); return@LaunchedEffect }
        moor.snapTo(1f); bob.snapTo(0f)
        // Start once our window is focused and has drawn a frame, so the whole arrival is visible.
        withTimeoutOrNull(400) { snapshotFlow { windowInfo.isWindowFocused }.first { it } }
        withFrameNanos { }
        delay(Island.MOOR_START_DELAY_MS) // panel R14 / UXR E-3
        android.util.Log.i("PROBE", "moorFrame wall=${System.currentTimeMillis()}")
        moor.animateTo(0f, tween(Island.MOOR_SLIDE_MS, easing = androidx.compose.animation.core.CubicBezierEasing(0.33f, 1f, 0.68f, 1f)))
        bob.animateTo(0f, keyframes { durationMillis = Island.MOOR_BOB_MS; Island.BOB_DP at Island.MOOR_BOB_MS / 2 })
    }
    val cur = rememberUpdatedState(shown)
    val before = rememberUpdatedState(prev)
    Spacer(modifier.drawBehind {
        bmp.frame()
        val k = size.width / set.w
        val boat = set.layers.getValue("boat_l1")
        val boatRight = boat.x + bmp(boat.id).width
        // v5: the boat starts inside the visible picture: min(360 dp, right edge - boat right - 8 dp).
        val slide = minOf(Island.MOOR_SLIDE_DP.dp.toPx(), size.width - boatRight * k - 8.dp.toPx()).coerceAtLeast(0f)
        scale(k, k, pivot = Offset.Zero) {
            drawH1(set, bmp, cur.value, before.value, t.value, boatDx = moor.value * slide / k, boatDy = bob.value.dp.toPx() / k)
        }
    })
    return shown
}

/** The 13-layer stack (ART_H.md section 8 order), in canvas px. [t] cross-fades [prev] -> [cur]. */
internal fun DrawScope.drawH1(set: H1Set, bmp: (Int) -> ImageBitmap, cur: H1Pic, prev: H1Pic?, t: Float, boatDx: Float = 0f, boatDy: Float = 0f) {
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
    val sun = "SML"
    one("base", 1f)
    slot("trees_w${p.wind}", "trees_w${cur.wind}")
    slot("sun_${sun[p.sun]}", "sun_${sun[cur.sun]}")
    slot(if (p.wind > 0) "wind_w${p.wind}" else null, if (cur.wind > 0) "wind_w${cur.wind}" else null)
    slot("wet_l${p.level}", "wet_l${cur.level}")
    one("boat_l1", 1f, boatDx, dy + boatDy)
    slot("buoys_n${p.buoys}_l1", "buoys_n${cur.buoys}_l1", y = dy)
    slot("water_l${p.level}", "water_l${cur.level}")
    slot("refl_l${p.level}", "refl_l${cur.level}")
    slot("surf_l${p.level}", "surf_l${cur.level}")
    slot("ticks_l${p.level}", "ticks_l${cur.level}")
    // The crate frame rides on the boat (K1 / K2: IslandInfo.crateOutline).
    slot(if (p.k2) "k2_l1" else null, if (cur.k2) "k2_l1" else null, boatDx, dy + boatDy)
    one("sheen", 1f)
}

/**
 * Receipt header (HANDOFF 10.4, POLISH_PASS v1.2 §0.6): the whole H1 island in its current state, scaled
 * down (never cropped). Shown only with the receipt (door open after >= 1 min).
 */
@Composable
fun ReceiptHeaderPicture(ui: StayUi) {
    val info = IslandInfo.of(ui) ?: return
    StillIsland(H1Set.Large, H1Pic.of(info), Modifier.fillMaxWidth().clip(ChaseomUi.rInline))
}

/** First-use screen (POLISH_PASS v1.2 §0.5): the whole Small-set island, no labels, a calm mid state. */
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
            if (night) drawRect(Color.Black.copy(alpha = 0.3f))
        },
    )
}
