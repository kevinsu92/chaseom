package app.chaseom.ui.scenes

import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.chaseom.R
import app.chaseom.stay.Island
import app.chaseom.stay.IslandState
import app.chaseom.stay.IslandSun
import app.chaseom.stay.StayUi
import app.chaseom.ui.theme.Pleos
import app.chaseom.ui.theme.PleosDimens
import app.chaseom.ui.theme.PleosType
import androidx.compose.ui.graphics.Canvas as GraphicsCanvas
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.flow.first
import androidx.compose.runtime.snapshotFlow

/**
 * Weather island face (flag BuildConfig.KICK_ISLAND, 04_design/HANDOFF.md section 8): the 04_design
 * VectorDrawable layers (res/drawable/is_*.xml) stacked in z order. Always-on layers are rasterised
 * once per size into three bitmaps; only the changing layers are vector-drawn on top. Changes play
 * one short, one-shot transition inside [Island.EMPHASIS_MS] and snap otherwise. No frame loop:
 * with no value change nothing is redrawn.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun IslandPanel(
    ui: StayUi,
    modifier: Modifier = Modifier,
    /** Drawn inside the top centre of the picture (HANDOFF 9.6 v7 Small toast). */
    pictureTop: @Composable () -> Unit = {},
    cards: @Composable () -> Unit = {},
) {
    val s0 = Island.state(ui, android.os.SystemClock.elapsedRealtime()) ?: return // same clock as the engine
    val ctx = LocalContext.current
    // System animation scale 0 -> picture swaps without transitions (HANDOFF 8.6 stop condition 5).
    val animOff = remember {
        runCatching { Settings.Global.getFloat(ctx.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f }
            .getOrDefault(false)
    }
    val s = if (animOff) s0.copy(animating = false) else s0
    // Latency probes (all builds, one log line per event like the cabin PROBE lines; QA measures the
    // release APK itself): first frame after a climate change, and the island leaving composition.
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
            bar.keepKm,
        )
    } else {
        ""
    }
    // HANDOFF 8.2: Full 2560 -> 520, Large 1700 -> 380, Small 840 -> 340 dp.
    val w = with(LocalDensity.current) { LocalWindowInfo.current.containerSize.width.toDp().value.toInt() }
    val h = when {
        w >= 2400 -> 520.dp
        w >= 1600 -> 420.dp // HANDOFF 9.3 v4
        else -> 340.dp
    }
    val night = isSystemInDarkTheme()
    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // HANDOFF 9.3 v4: the island uses the full card width; the 2000x480 extra-wide layers are
        // cropped from the centre (ContentScale.Crop). No side fill bands, so no seam.
        Box(
            Modifier.fillMaxWidth().height(h).clip(RoundedCornerShape(20.dp))
                .semantics { contentDescription = cd; liveRegion = LiveRegionMode.Polite },
        ) {
            IslandPicture(s, Modifier.matchParentSize(), ui.moorAtMs, ui.lastClimateChangeMs, motionOff = animOff)
            // Night theme: same daytime picture, black veil 0.3 only (HANDOFF 9.7, never night colours).
            if (night) Box(Modifier.matchParentSize().background(Color.Black.copy(alpha = 0.3f)))
            Box(Modifier.align(Alignment.TopCenter)) { pictureTop() }
        }
        // HANDOFF 9.2: chips outside the picture, right under it (QA E-08).
        FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            LegendChip(stringResource(R.string.is_key_follow)) // what it is (U-G5)
            LegendChip(stringResource(R.string.is_legend)) // what it is not: always (QA L-6, I-4)
            if (s.heatDrv > 0) LegendChip(stringResource(R.string.is_key_rock_l)) // island = car floor plan
            if (s.heatPas > 0) LegendChip(stringResource(R.string.is_key_rock_r))
            if (s.alert) LegendChip(stringResource(R.string.is_key_keep_k2)) // the boat itself never changes (I-6)
        }
        cards() // HANDOFF 9.2.1 v6: air / swap cards under the chips, above the hint and the band
        if (ui.firstStayHint) {
            // HANDOFF 9.5: basic surface, radius 16, fan icon in brand colour, bodyMedium.
            Row(
                Modifier.background(Pleos.colors.buttonBasicEnabled, RoundedCornerShape(16.dp)).padding(horizontal = 20.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                androidx.compose.material3.Icon(androidx.compose.ui.res.painterResource(R.drawable.ic_fan), null,
                    tint = Pleos.colors.brand, modifier = Modifier.size(32.dp))
                Spacer(Modifier.width(12.dp))
                // QA REVIEW_COPY_B 5 (UXR E-2): at the 3-hour cap the time cannot change, so only the island is promised.
                val hint = if (ui.hero?.overCap == true) R.string.is_hint_first_cap else R.string.is_hint_first
                Text(stringResource(hint), style = PleosType.bodyMedium, color = Pleos.colors.textPrimary)
            }
        }
        DayBand(s, ui)
    }
}

@Composable
private fun LegendChip(text: String) {
    Text(
        text, style = PleosType.bodyMedium.copy(fontSize = 26.sp), color = Pleos.colors.textPrimary,
        modifier = Modifier.background(Pleos.colors.buttonBasicEnabled, RoundedCornerShape(12.dp)).padding(horizontal = 14.dp, vertical = 6.dp),
    )
}

@Composable
private fun painter(id: Int): Painter {
    // No per-layer cache bitmap (QA B-4); see FlatVectorPainter.
    val vector = ImageVector.vectorResource(id)
    return remember(vector) { FlatVectorPainter(vector) }
}

private class Motion(arriving: Boolean) {
    // Boat (HANDOFF 10.3): slide in from the right, rope, 3 dp bob. Never rotates. When arriving, the
    // very first frame already shows the boat out at sea (no flash of the moored picture).
    val boatX = Animatable(if (arriving) Island.MOOR_SLIDE_DP else 0f)
    val boatY = Animatable(0f)
    val rope = Animatable(if (arriving) 0f else 1f)
    var swaying = false
    var swayJob: kotlinx.coroutines.Job? = null
    val sunScale = Animatable(1f)
    val windDx = Animatable(0f)
    val heatLDy = Animatable(0f)
    val heatRDy = Animatable(0f)
    var prev: IslandState? = null
}

@Composable
private fun IslandPicture(
    s: IslandState,
    modifier: Modifier,
    moorAtMs: Long? = null,
    lastChangeMs: Long? = null,
    motionOff: Boolean = false,
) {
    // Static groups (z order: sky | island | rocks + lighthouse + gull).
    val sky = painter(R.drawable.is_sky)
    val island = painter(R.drawable.is_island)
    val rockL = painter(R.drawable.is_rock_l)
    val rockR = painter(R.drawable.is_rock_r)
    val lighthouse = painter(R.drawable.is_lighthouse)
    val gull = painter(R.drawable.is_gull)
    val pier = painter(R.drawable.is_pier)
    // Changing layers.
    val boat = painter(R.drawable.is_boat)
    val rope = painter(R.drawable.is_rope)
    val crateMark = painter(R.drawable.is_crate_mark)
    val wake = painter(R.drawable.is_wake)
    val suns = listOf(painter(R.drawable.is_sun_s), painter(R.drawable.is_sun_m), painter(R.drawable.is_sun_l))
    val cool = painter(R.drawable.is_cool)
    val cloud = painter(R.drawable.is_cloud_recirc)
    val waves = listOf(painter(R.drawable.is_waves_0), painter(R.drawable.is_waves_1), painter(R.drawable.is_waves_2))
    val palms = listOf(painter(R.drawable.is_palm_0), painter(R.drawable.is_palm_1), painter(R.drawable.is_palm_2))
    val warmL = painter(R.drawable.is_rock_l_warm)
    val warmR = painter(R.drawable.is_rock_r_warm)

    val fade: AnimationSpec<Float> = if (s.animating) tween(Island.FADE_MS) else snap()
    @Composable fun a(target: Float, label: String) = animateFloatAsState(target, fade, label = label)
    val sunA = IslandSun.entries.map { a(if (s.sun == it) 1f else 0f, "sun$it") }
    val coolA = a(if (s.cool) 1f else 0f, "cool")
    val cloudA = a(if (s.cloud) 1f else 0f, "cloud")
    val waveA = (0..2).map { a(if (s.wind == it) 1f else 0f, "wave$it") }
    val palmA = (0..2).map { a(if (s.wind == it) 1f else 0f, "palm$it") }
    val warmLA = a(Island.HEAT_ALPHA[s.heatDrv], "warmL")
    val warmRA = a(Island.HEAT_ALPHA[s.heatPas], "warmR")
    // K1/K2 crate outline: 400 ms fade in, 200 ms out, never blinks (HANDOFF 10.3 ③).
    val markOn = s.near || s.alert
    val markA = animateFloatAsState(if (markOn) 1f else 0f, if (motionOff) snap() else tween(if (markOn) 400 else 200), label = "mark")

    // One-shot motions (HANDOFF 8.6). More than MAX_MOVING groups at once -> crossfade only.
    val m = remember { Motion(arriving = s.mooring && !motionOff) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val windowInfo = LocalWindowInfo.current

    // HANDOFF 10.3 ① mooring: only inside the window that started when the car came back to P.
    LaunchedEffect(moorAtMs) {
        if (!s.mooring || motionOff) {
            m.boatX.snapTo(0f); m.rope.snapTo(1f)
            return@LaunchedEffect
        }
        m.swayJob?.cancel()
        m.boatX.snapTo(Island.MOOR_SLIDE_DP); m.rope.snapTo(0f); m.boatY.snapTo(0f)
        // Arriving in P lifts the system's driving block a few frames after the gear callback. Start the
        // slide once our window is focused and has drawn a frame, so the whole arrival is visible.
        withTimeoutOrNull(400) { snapshotFlow { windowInfo.isWindowFocused }.first { it } }
        withFrameNanos { }
        // Panel R14 / UXR E-3: let the screen change settle first, so the arrival is not lost in it.
        kotlinx.coroutines.delay(Island.MOOR_START_DELAY_MS)
        android.util.Log.i("PROBE", "moorFrame wall=${System.currentTimeMillis()}")
        m.boatX.animateTo(0f, tween(Island.MOOR_SLIDE_MS, easing = androidx.compose.animation.core.CubicBezierEasing(0.33f, 1f, 0.68f, 1f)))
        m.rope.animateTo(1f, tween(Island.MOOR_ROPE_MS))
        m.boatY.animateTo(0f, keyframes { durationMillis = Island.MOOR_BOB_MS; Island.BOB_DP at Island.MOOR_BOB_MS / 2 })
    }

    // HANDOFF 10.3 ② sway: one bob 300 ms after the controls stop; events while bobbing are ignored.
    LaunchedEffect(lastChangeMs) {
        if (!Island.SWAY_ENABLED || lastChangeMs == null || motionOff || m.swaying || s.mooring || !s.animating) return@LaunchedEffect
        m.swayJob?.cancel()
        m.swayJob = scope.launch {
            kotlinx.coroutines.delay(Island.SWAY_DEBOUNCE_MS)
            m.swaying = true
            try {
                m.boatY.animateTo(0f, keyframes { durationMillis = Island.SWAY_MS; Island.BOB_DP at Island.SWAY_MS / 2 })
            } finally {
                m.boatY.snapTo(0f)
                m.swaying = false
            }
        }
    }
    LaunchedEffect(s.sun, s.wind, s.heatDrv, s.heatPas) {
        val p = m.prev
        m.prev = s
        val sunCh = p != null && p.sun != s.sun
        val windCh = p != null && p.wind != s.wind
        val lCh = p != null && p.heatDrv != s.heatDrv && s.heatDrv > 0
        val rCh = p != null && p.heatPas != s.heatPas && s.heatPas > 0
        val moving = listOf(sunCh, windCh, lCh, rCh).count { it }
        val play = s.animating && moving in 1..Island.MAX_MOVING
        if (!play) {
            m.sunScale.snapTo(1f); m.windDx.snapTo(0f); m.heatLDy.snapTo(0f); m.heatRDy.snapTo(0f)
            return@LaunchedEffect
        }
        val ms = Island.MOTION_MS
        kotlinx.coroutines.coroutineScope {
            if (sunCh) launch { m.sunScale.snapTo(1f); m.sunScale.animateTo(1f, keyframes { durationMillis = ms; 1.07f at ms / 2 }) }
            if (windCh) launch { m.windDx.snapTo(-14f); m.windDx.animateTo(0f, keyframes { durationMillis = ms; 8f at ms / 2 }) }
            if (lCh) launch { m.heatLDy.snapTo(10f); m.heatLDy.animateTo(0f, tween(ms)) }
            if (rCh) launch { m.heatRDy.snapTo(10f); m.heatRDy.animateTo(0f, tween(ms)) }
        }
    }

    // Stable draw modifier: the bitmaps are rebuilt only when the size changes; value changes
    // only invalidate the draw phase.
    val cur = rememberUpdatedState(s)
    val draw = remember {
        Modifier.drawWithCache {
            // Picture = 2000x480 extra-wide layers, Crop: scaled to cover the area, centred.
            val k = maxOf(size.width / PIC_W, size.height / PIC_H)
            val pic = Size(PIC_W * k, PIC_H * k)
            val left = (size.width - pic.width) / 2f
            val topOff = (size.height - pic.height) / 2f
            val skyBmp = raster(pic, listOf(sky))
            val islandBmp = raster(pic, listOf(island))
            val topBmp = raster(pic, listOf(rockL, rockR, lighthouse, pier, gull))
            onDrawBehind {
                val st = cur.value
                translate(left = left, top = topOff) {
                    drawImage(skyBmp)
                    val sunScale = m.sunScale.value
                    val sunCenter = Offset(pic.width * SUN_X, pic.height * SUN_Y)
                    suns.forEachIndexed { i, p ->
                        layer(p, pic, sunA[i]) { if (i == st.sun.ordinal) scale(sunScale, sunCenter) { it() } else it() }
                    }
                    layer(cool, pic, coolA)
                    layer(cloud, pic, cloudA)
                    val dx = m.windDx.value.dp.toPx()
                    waves.forEachIndexed { i, p -> layer(p, pic, waveA[i]) { if (i == st.wind) translate(left = dx) { it() } else it() } }
                    drawImage(islandBmp)
                    palms.forEachIndexed { i, p -> layer(p, pic, palmA[i]) { if (i == st.wind) translate(left = dx) { it() } else it() } }
                    drawImage(topBmp)
                    // The boat (= the car): only translate, never rotate (HANDOFF 10.1).
                    // HANDOFF 10.3 ① v5: start distance = min(360 dp, visible right edge - boat right (946) - 8),
                    // so the boat always starts inside the visible picture (about 44-85 dp on a Small window).
                    // Island.boatOffsetDp / wakeAlpha (unit-tested for Full, Large, Small and a too-narrow area).
                    val f = (m.boatX.value / Island.MOOR_SLIDE_DP).coerceIn(0f, 1f) // 1 at sea -> 0 moored
                    val offsetPx = Island.boatOffsetDp(f, size.width.toDp().value, size.height.toDp().value).dp.toPx()
                    translate(left = offsetPx, top = m.boatY.value.dp.toPx()) {
                        // v5: wake behind the boat only while it slides in, fading 1 -> 0; gone in the end picture.
                        val wakeA = Island.wakeAlpha(f)
                        if (wakeA > 0.01f) with(wake) { draw(pic, alpha = wakeA) }
                        with(boat) { draw(pic) }
                        if (markA.value > 0.01f) with(crateMark) { draw(pic, alpha = markA.value) }
                    }
                    if (m.rope.value > 0.01f) with(rope) { draw(pic, alpha = m.rope.value) }
                    layer(warmL, pic, warmLA) { translate(top = m.heatLDy.value.dp.toPx()) { it() } }
                    layer(warmR, pic, warmRA) { translate(top = m.heatRDy.value.dp.toPx()) { it() } }
                }
            }
        }
    }
    Spacer(modifier.then(draw))
}

/** Extra-wide layer set (04_design android_xwide, HANDOFF 9.3 v4): 2000x480, art shifted +520. */
private const val PIC_W = Island.PIC_W
private const val PIC_H = Island.PIC_H
private const val ART_OFF = Island.ART_OFF

private const val SUN_X = (770f + ART_OFF) / PIC_W
private const val SUN_Y = 104f / 480f

/** Rasterise always-on layers once per size (HANDOFF 8.3). The bitmap is the scaled picture. */
private fun androidx.compose.ui.draw.CacheDrawScope.raster(pic: Size, painters: List<Painter>): ImageBitmap {
    val bmp = ImageBitmap(pic.width.toInt().coerceAtLeast(1), pic.height.toInt().coerceAtLeast(1))
    CanvasDrawScope().draw(this, layoutDirection, GraphicsCanvas(bmp), pic) {
        painters.forEach { with(it) { draw(pic) } }
    }
    return bmp
}

private inline fun DrawScope.layer(p: Painter, pic: Size, alpha: State<Float>, wrap: DrawScope.(DrawScope.() -> Unit) -> Unit = { it() }) {
    val a = alpha.value
    if (a <= 0.01f) return
    wrap { with(p) { draw(pic, alpha = a) } }
}

/** Today's day-length band (bar of record, HANDOFF 8.1): amber = today, capped at 3 h; grey = tomorrow. */
@Composable
private fun DayBand(s: IslandState, ui: StayUi) {
    val c = Pleos.colors
    val crate = painter(R.drawable.ic_keep_crate)
    val day = s.dayLength
    Column(verticalArrangement = Arrangement.spacedBy(PleosDimens.space8)) {
        Text(stringResource(R.string.is_day), style = PleosType.bodyMedium, color = c.textSecondary)
        Spacer(
            Modifier.fillMaxWidth().height(40.dp).drawBehind {
                val rr = CornerRadius(size.height / 2)
                val keepW = size.width * 0.18f
                // HANDOFF 10.6 v3: a small crate right before the grey keep cell ties the band to the boat's crates.
                val iconPx = 24.dp.toPx()
                val gap = 8.dp.toPx()
                val todayW = size.width - keepW - iconPx - gap * 3
                drawRoundRect(c.neutralState.copy(alpha = 0.35f), size = Size(todayW, size.height), cornerRadius = rr)
                if (day > 0f) drawRoundRect(c.brand, size = Size(todayW * day, size.height), cornerRadius = rr)
                drawRoundRect(if (s.alert) c.negative else Color(0xFF8A9099), Offset(size.width - keepW, 0f), Size(keepW, size.height), rr)
                translate(left = todayW + gap, top = (size.height - iconPx) / 2f) {
                    with(crate) { draw(Size(iconPx, iconPx)) }
                }
            },
        )
        ui.bar?.let { b ->
            Text(
                if (ui.settings.saved) stringResource(R.string.is_keep, b.keepKm, labelText(b.label))
                else stringResource(R.string.is_keep_default, b.keepKm),
                style = PleosType.bodyMedium,
                color = c.textSecondary, modifier = Modifier.align(Alignment.End))
        }
    }
}

/**
 * Receipt header (HANDOFF 10.4): the island cropped to the pier, boat and plank, as one still picture.
 * Crop = wide-set viewBox (280, 205, 840, 158) = extra-wide (640, 205, 840, 158). Weather layers keep
 * their current state. Shown only with the receipt (door open after >= 1 min).
 */
@Composable
fun ReceiptHeaderPicture(ui: StayUi) {
    val s = Island.state(ui, android.os.SystemClock.elapsedRealtime()) ?: return
    val sky = painter(R.drawable.is_sky)
    val waves = painter(listOf(R.drawable.is_waves_0, R.drawable.is_waves_1, R.drawable.is_waves_2)[s.wind])
    val island = painter(R.drawable.is_island)
    val palm = painter(listOf(R.drawable.is_palm_0, R.drawable.is_palm_1, R.drawable.is_palm_2)[s.wind])
    val rocks = listOf(painter(R.drawable.is_rock_l), painter(R.drawable.is_rock_r))
    val lighthouse = painter(R.drawable.is_lighthouse)
    val pier = painter(R.drawable.is_pier)
    val boat = painter(R.drawable.is_boat)
    val rope = painter(R.drawable.is_rope)
    val plank = painter(R.drawable.is_plank)
    val mark = painter(R.drawable.is_crate_mark)
    val markOn = s.near || s.alert
    Spacer(
        Modifier.fillMaxWidth().height(120.dp).clip(RoundedCornerShape(24.dp)).drawWithCache {
            // HANDOFF 10.4 v3: 120 dp, crop = wide-set viewBox (280, 205, 840, 158) = extra-wide x + 360.
            val k = size.height / 158f
            val full = Size(PIC_W * k, PIC_H * k)
            val left = (size.width - 840f * k) / 2f - (280f + 360f) * k
            val top = -205f * k
            onDrawBehind {
                drawRect(Color(0xFF3B93BF)) // sea colour behind the crop when the card is wider than 640:160
                translate(left = left, top = top) {
                    listOf(sky, waves, island, palm) .forEach { with(it) { draw(full) } }
                    rocks.forEach { with(it) { draw(full) } }
                    listOf(lighthouse, pier, boat).forEach { with(it) { draw(full) } }
                    if (markOn) with(mark) { draw(full) }
                    with(rope) { draw(full) }
                    with(plank) { draw(full) }
                }
            }
        },
    )
}

