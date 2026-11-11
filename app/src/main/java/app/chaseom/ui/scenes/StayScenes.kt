package app.chaseom.ui.scenes

import app.chaseom.ui.theme.pleosShadow
import app.chaseom.ui.theme.pressable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.chaseom.R
import app.chaseom.stay.BarModel
import app.chaseom.stay.ClimateChange
import app.chaseom.stay.ClimateSetting
import app.chaseom.stay.ExchangeRule
import app.chaseom.stay.KeepBanner
import app.chaseom.stay.Page
import app.chaseom.stay.StayUi
import app.chaseom.ui.theme.BigButton
import app.chaseom.ui.theme.ButtonKind
import app.chaseom.ui.theme.ButtonSize
import app.chaseom.ui.theme.Pleos
import app.chaseom.ui.theme.PleosDimens
import app.chaseom.ui.theme.PleosType
import app.chaseom.ui.theme.ChaseomUi
import app.chaseom.ui.theme.cui
import app.chaseom.ui.theme.uiSize
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import kotlin.math.abs
import kotlin.math.roundToInt

// ---- shared header: N0 banner + title row ----------------------------------------------------

/**
 * N0 safety banner (stays for the whole stay). Also drawn again above the receipt / notice scrim by
 * [SceneHost] at the same place, so an overlay never dims it.
 */
@Composable
internal fun N0Banner(ui: StayUi, actions: StayActions) {
    ui.banner?.let { b ->
        val text = stringResource(R.string.n0_banner) +
            (if (ui.policy.idlingNotice) stringResource(R.string.n0_banner_reduced_suffix) else "")
        if (b.expanded) {
            WarnBanner(text, danger = false) {
                IconButton76(R.drawable.ic_close, stringResource(R.string.cd_close)) { actions.closeBanner() }
            }
        } else {
            WarnBanner(text, danger = false) // stays for the whole stay
        }
        // N0 -> 8 -> K2 -> 16 -> status line (the two warnings read as one group).
        Spacer(Modifier.height(if (ui.keepBanner == KeepBanner.BELOW && ui.policy.energyBudget) ChaseomUi.line else ChaseomUi.related))
    }
}

/**
 * One banner component, two tones (N0 safety yellow, K2 danger red). 56 dp, radius 12,
 * a 6 dp bar on the left, a 28 dp warning icon, caption SemiBold. Warning colours appear only here.
 */
@Composable
internal fun WarnBanner(text: String, danger: Boolean, trailing: (@Composable () -> Unit)? = null) {
    val k = cui()
    val bg = if (danger) k.dangerBg else k.safeBg
    val bar = if (danger) k.dangerBar else k.safeBar
    val fg = if (danger) k.dangerFg else k.safeFg
    // N0 = filled yellow + ink (56 dp); K2 = #C5221F + white 26 SemiBold, 64 dp, icon 32.
    Row(
        Modifier.fillMaxWidth().heightIn(min = if (danger) 64.dp else ChaseomUi.bannerHeight).clip(ChaseomUi.rBanner).background(bg)
            .height(androidx.compose.foundation.layout.IntrinsicSize.Min),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(ChaseomUi.bannerBar).fillMaxHeight().background(bar))
        Spacer(Modifier.width(ChaseomUi.related))
        // K2 (the one warning that asks for an action) = white octagon, red '!', on the red fill.
        if (danger) StopOctagon(Modifier.size(32.dp)) else
            Icon(painterResource(R.drawable.ic_warn_line), null, tint = fg, modifier = Modifier.size(32.dp)) // 48-grid icon: 2 dp line at 32
        Spacer(Modifier.width(ChaseomUi.related))
        // N0 and K2 share 26 sp SemiBold (W600); wording unchanged.
        Text(text, style = ChaseomUi.banner.copy(fontSize = 26.sp, lineHeight = 34.sp), color = fg,
            modifier = Modifier.weight(1f).padding(vertical = ChaseomUi.line))
        trailing?.invoke()
        Spacer(Modifier.width(ChaseomUi.line))
    }
}

/** White octagon with a red '!' (K2 icon on the red banner). */
@Composable
internal fun StopOctagon(modifier: Modifier) {
    val red = cui().dangerBg
    androidx.compose.foundation.Canvas(modifier) {
        val r = size.minDimension / 2f; val cx = size.width / 2f; val cy = size.height / 2f
        val p = androidx.compose.ui.graphics.Path()
        for (i in 0 until 8) {
            val a = Math.toRadians(22.5 + 45.0 * i)
            val x = cx + r * kotlin.math.cos(a).toFloat(); val y = cy + r * kotlin.math.sin(a).toFloat()
            if (i == 0) p.moveTo(x, y) else p.lineTo(x, y)
        }
        p.close()
        drawPath(p, Color.White)
        val u = size.width / 32f
        drawRoundRect(red, androidx.compose.ui.geometry.Offset(cx - 1.75f * u, cy - 9f * u), androidx.compose.ui.geometry.Size(3.5f * u, 11.5f * u),
            androidx.compose.ui.geometry.CornerRadius(1.75f * u))
        drawCircle(red, 2f * u, androidx.compose.ui.geometry.Offset(cx, cy + 6.5f * u))
    }
}

/**
 * White warning part (onboarding safety panel, seat-heat line, receipt check line only):
 * white surface, 1 dp #898E96 outline (3.10:1), 4 dp #FFC224 bar, 32 dp triangle (yellow fill, ink outline,
 * ink '!'), ink text. [inline] = inside a card (48 dp).
 */
@Composable
internal fun WhiteWarn(text: androidx.compose.ui.text.AnnotatedString, modifier: Modifier = Modifier, inline: Boolean = false,
                       style: androidx.compose.ui.text.TextStyle = ChaseomUi.banner.copy(fontSize = 26.sp, lineHeight = 34.sp),
                       icon: (@Composable () -> Unit)? = null) {
    val shape = RoundedCornerShape(12.dp)
    Row(
        modifier.heightIn(min = if (inline) 48.dp else 56.dp).clip(shape).background(Color.White)
            .border(1.dp, Color(0xFF898E96), shape).height(androidx.compose.foundation.layout.IntrinsicSize.Min),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(4.dp).fillMaxHeight().background(Color(0xFFFFC224)))
        Spacer(Modifier.width(if (inline) 14.dp else 18.dp))
        if (icon != null) icon() else WarnTriangle(Modifier.size(32.dp))
        Spacer(Modifier.width(14.dp))
        Text(text, style = style, color = Color(0xFF131417),
            modifier = Modifier.weight(1f, fill = false).padding(vertical = if (inline) 6.dp else 10.dp))
        Spacer(Modifier.width(if (inline) 16.dp else 24.dp))
    }
}

/** Yellow-filled triangle with a 1.5 dp ink outline and an ink '!'. */
@Composable
internal fun WarnTriangle(modifier: Modifier) {
    val ink = Color(0xFF131417)
    androidx.compose.foundation.Canvas(modifier) {
        val u = size.width / 32f
        val p = androidx.compose.ui.graphics.Path().apply { moveTo(16f * u, 3f * u); lineTo(30f * u, 28f * u); lineTo(2f * u, 28f * u); close() }
        drawPath(p, Color(0xFFFFC224))
        drawPath(p, ink, style = androidx.compose.ui.graphics.drawscope.Stroke(1.5f * u, join = androidx.compose.ui.graphics.StrokeJoin.Round))
        drawRoundRect(ink, androidx.compose.ui.geometry.Offset(14.8f * u, 11f * u), androidx.compose.ui.geometry.Size(2.4f * u, 9f * u),
            androidx.compose.ui.geometry.CornerRadius(1.2f * u))
        drawCircle(ink, 1.5f * u, androidx.compose.ui.geometry.Offset(16f * u, 23.5f * u))
    }
}

/** The banner goes above the scrim whenever an overlay is up (receipt N-E, notices N1/N2/N3). */
internal fun bannerAboveScrim(ui: StayUi): Boolean = ui.banner != null && (ui.receipt != null || ui.notice != null)

@Composable
private fun StayHeader(ui: StayUi, actions: StayActions, settingsPage: Page) {
    val c = Pleos.colors
    N0Banner(ui, actions)
    if (ui.keepBanner == KeepBanner.BELOW && ui.policy.energyBudget) {
        WarnBanner(stringResource(R.string.k2), danger = true)
        Spacer(Modifier.height(ChaseomUi.related))
    }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        val title = when {
            ui.elapsedMinutes >= 1 -> stringResource(R.string.h_title, ui.elapsedMinutes)
            ui.restartedFromStale -> stringResource(R.string.h_title_resumed)
            else -> stringResource(R.string.h_title_new)
        }
        Text(title, style = ChaseomUi.status, color = cui().text2,
            modifier = Modifier.weight(1f))
        IconButton76(R.drawable.ic_settings, stringResource(R.string.cd_settings)) { actions.open(settingsPage) }
    }
}

// ---- H: staying, EV ----------------------------------------------------------------------------

@Composable
fun StayFullScene(ui: StayUi, actions: StayActions, modifier: Modifier) {
    Column(modifier) {
        StayHeader(ui, actions, Page.SETTINGS)
        val hasSide = ui.exchange != null || ui.seatHeatLong || ui.airMinutes != null
        // With the island face there is no second column; the cards go under the island's
        // chips, so the island never changes size when a card appears.
        val islandFace = app.chaseom.BuildConfig.KICK_ISLAND && ui.hero != null
        BoxWithConstraints(Modifier.fillMaxWidth().weight(1f)) {
            val twoCol = maxWidth >= 1200.dp && hasSide && !islandFace
            val heroSize = if (maxWidth >= 2000.dp) 96.sp else 60.sp
            // The toast covers no number or text line. Wide
            // (>= 1200 dp): right end of the hero row. Small: inside the top of the island picture (sky only).
            val toastPlace = toastPlace(ui, wide = maxWidth >= 1200.dp, islandFace = islandFace)
            if (twoCol) {
                Row(Modifier.fillMaxWidth().fillMaxHeight(), horizontalArrangement = Arrangement.spacedBy(PleosDimens.space40)) {
                    MainColumn(ui, heroSize, Modifier.weight(1.35f).fillMaxHeight(), toastPlace = toastPlace)
                    SideColumn(ui, actions, Modifier.weight(1f))
                }
            } else {
                Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
                    MainColumn(ui, heroSize, Modifier.fillMaxWidth(), actions, toastPlace)
                    if (!islandFace) {
                        Spacer(Modifier.height(PleosDimens.space24))
                        SideColumn(ui, actions, Modifier.fillMaxWidth())
                    }
                }
            }
            if (toastPlace == ToastPlace.BOTTOM) {
                ui.toast?.let { t -> DialToast(t.items, t.deltaMinutes, t.heldAboveCap, t.showMinutes, Modifier.align(Alignment.BottomCenter).padding(bottom = PleosDimens.space24)) }
            }
        }
    }
}

@Composable
private fun MainColumn(
    ui: StayUi, heroSize: androidx.compose.ui.unit.TextUnit, modifier: Modifier, actions: StayActions? = null,
    toastPlace: ToastPlace = ToastPlace.BOTTOM,
) {
    // Face of the stay screen: only the keep bar is replaced; numbers "(추정)", toast and cards stay.
    val face = when {
        ui.hero == null -> Face.BAR
        app.chaseom.BuildConfig.KICK_ISLAND -> Face.ISLAND
        else -> Face.BAR
    }
    MainColumnContent(ui, heroSize, modifier, face, actions, toastPlace)
}

private enum class Face { BAR, ISLAND }

/** The climate status line is not on the island screen (three text lines only). */
internal fun climateLineShown(islandFace: Boolean): Boolean = !islandFace

/** Up = ink circle with a white symbol, down = amber circle with an ink symbol. No green. */
internal val UP_CIRCLE = Color(0xFF131417)
internal val DOWN_CIRCLE = Color(0xFFFFB020)

/** Toast positions. */
internal enum class ToastPlace { HERO_ROW, ISLAND_TOP, BOTTOM }

/** The hero row exists only with a number (not climate off, not below tomorrow's share). */
internal fun toastPlace(ui: StayUi, wide: Boolean, islandFace: Boolean): ToastPlace {
    val h = ui.hero
    val heroRow = h != null && !h.hvacOff && !h.belowKeep
    return when {
        wide && heroRow -> ToastPlace.HERO_ROW
        !wide && islandFace -> ToastPlace.ISLAND_TOP
        else -> ToastPlace.BOTTOM
    }
}

@Composable
private fun MainColumnContent(
    ui: StayUi, heroSize: androidx.compose.ui.unit.TextUnit, modifier: Modifier, face: Face, actions: StayActions? = null,
    toastPlace: ToastPlace = ToastPlace.BOTTOM,
) {
    val c = Pleos.colors
    val k = cui()
    val size = uiSize()
    // Status -> 8 -> hero -> 8 -> description -> 32 -> picture.
    Column(modifier, verticalArrangement = Arrangement.spacedBy(ChaseomUi.line)) {
        when (ui.keepBanner) {
            KeepBanner.NEAR -> InfoCard(stringResource(R.string.k1)) // K1 is information, not an alarm
            KeepBanner.BELOW -> Unit // drawn in the header right under N0
            null -> Unit
        }
        val hero = ui.hero
        if (hero == null) {
            Body(stringResource(R.string.h_hvac_off), color = c.textSecondary)
        } else if (hero.belowKeep) {
            // Below tomorrow's share the hero states the situation only (no "약 0분",
            // no "time you can stay" label); the K2 banner above asks for the action.
            // The "(추정)" at the end takes the estimate role (same string, two styles).
            val k2 = stringResource(R.string.h_k2_hero)
            val est = stringResource(R.string.estimate)
            val cut = k2.lastIndexOf(est).takeIf { it > 0 } ?: k2.length
            Text(
                androidx.compose.ui.text.buildAnnotatedString {
                    append(k2.substring(0, cut))
                    // 24 Regular #6E727B at both sizes (never removed).
                    pushStyle(ChaseomUi.estimate(ChaseomUi.Size.Small).toSpanStyle().copy(color = Color(0xFF6E727B))); append(k2.substring(cut)); pop()
                },
                style = ChaseomUi.heroSentence(size), color = k.text1,
            )
            hero.coveredKm?.let { Text(stringResource(R.string.h_k2_meta, ui.settings.keepKm, it), style = ChaseomUi.body, color = k.text2) }
        } else {
            // With the island the label line goes; "머물 수 있어요" joins the number.
            if (face == Face.BAR) Text(stringResource(R.string.h_hero_label), style = PleosType.titleMedium, color = c.textSecondary)
            if (hero.hvacOff) {
                Text(stringResource(R.string.h_hvac_off), style = ChaseomUi.heroSentence(size), color = k.text1)
                Text(stringResource(R.string.h_hvac_off_sub), style = ChaseomUi.body, color = k.text2)
            } else {
                val value = if (hero.overCap) stringResource(R.string.h_hero_cap)
                else stringResource(R.string.h_hero_value, duration(hero.minutes ?: 0))
                // Number, tail and "(추정)" on one baseline. The Large toast floats over the
                // right end of this row (out of the layout flow), so the hero never moves.
                Box(Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(value, style = ChaseomUi.hero(size), color = k.text1, modifier = Modifier.alignByBaseline())
                        if (face == Face.ISLAND) {
                            Spacer(Modifier.width(ChaseomUi.related))
                            Text(stringResource(R.string.h_hero_stay), style = ChaseomUi.heroTail(size).copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Medium),
                                color = k.text1, modifier = Modifier.alignByBaseline())
                        }
                        Spacer(Modifier.width(ChaseomUi.related))
                        Text(stringResource(R.string.estimate), style = ChaseomUi.estimate(size), color = k.text3, modifier = Modifier.alignByBaseline())
                    }
                    if (toastPlace == ToastPlace.HERO_ROW) {
                        ui.toast?.let { t -> DialToast(t.items, t.deltaMinutes, t.heldAboveCap, t.showMinutes, Modifier.align(Alignment.CenterEnd)) }
                    }
                }
                // Bundle 5: say why the number stops at 3 hours.
                if (hero.overCap) Text(stringResource(R.string.h_cap_note), style = ChaseomUi.body, color = k.text2)
            }
        }
        // One description line under the hero (the island's meta line moved here).
        if (face == Face.ISLAND && hero != null && !hero.belowKeep) ui.bar?.let { b ->
            Text(stringResource(R.string.h_island_meta, b.socPercent, b.keepKm), style = ChaseomUi.body, color = k.text2)
        }
        when (face) {
            Face.BAR -> ui.bar?.let { KeepBar(it) }
            Face.ISLAND -> {
                IslandPanel(
                    ui, Modifier.fillMaxWidth().padding(top = ChaseomUi.block - ChaseomUi.line),
                    pictureTop = {
                        // Small: inside the top-left sky of the picture (16 dp, shadow 0 4 16 25 %), up to the "설정 °C" label (H1).
                        if (toastPlace == ToastPlace.ISLAND_TOP) ui.toast?.let { t ->
                            // The one shadow in the app, day only (0 4 16 8 %).
                            val night = androidx.compose.foundation.isSystemInDarkTheme()
                            DialToast(t.items, t.deltaMinutes, t.heldAboveCap, t.showMinutes,
                                Modifier.padding(top = ChaseomUi.related), // the toast draws its own Level 2 shadow (day)
                                short = true)
                        }
                    },
                ) { if (actions != null) IslandCards(ui, actions) }
            }
        }
        // The island screen keeps three text lines; Pleos' own climate bar
        // already shows these values, so the status line stays only with the bar face.
        if (climateLineShown(islandFace = face == Face.ISLAND)) ui.climate?.let { ClimateLine(it) }
    }
}

/** Bar width = current SOC. Left grey = tomorrow's keep share (lock), right = today's share. */
@Composable
private fun KeepBar(b: BarModel) {
    val c = Pleos.colors
    val keepFrac = if (b.socPercent <= 0) 1f else (b.keepPercent / b.socPercent).coerceIn(0f, 1f)
    // 64dp headroom so the pin label above the bar never touches the hero line.
    Column(Modifier.padding(top = 64.dp)) {
        BoxWithConstraints(Modifier.fillMaxWidth().height(96.dp)) {
            val today = if (b.belowKeep) c.negative else c.active
            val keep = c.neutralState
            Canvas(Modifier.fillMaxWidth().height(96.dp)) {
                val r = CornerRadius(24.dp.toPx())
                drawRoundRect(today, cornerRadius = r)
                drawRoundRect(keep, size = Size(size.width * keepFrac, size.height), cornerRadius = r)
                val x = size.width * keepFrac
                drawLine(c.textPrimary, Offset(x, -10f), Offset(x, size.height + 10f), strokeWidth = 5.dp.toPx())
            }
            if (keepFrac > 0.08f) {
                Icon(painterResource(R.drawable.ic_lock), null, tint = c.onNeutral,
                    modifier = Modifier.align(Alignment.CenterStart).padding(start = PleosDimens.space24).size(48.dp))
            }
            // Pin with the user's label at the boundary; label flips inward near the ends.
            val pinX = maxWidth * keepFrac
            val inward = keepFrac > 0.8f
            Row(
                Modifier.offset(x = if (inward) pinX - 260.dp else pinX + 8.dp, y = (-56).dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(painterResource(R.drawable.ic_pin), null, tint = c.textPrimary, modifier = Modifier.size(40.dp))
                Text(labelText(b.label), style = PleosType.bodyLargeStrong, color = c.textPrimary)
            }
        }
        Spacer(Modifier.height(PleosDimens.space8))
        Row(Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.h_bar_keep, b.keepKm), style = PleosType.labelMedium, color = c.textSecondary,
                modifier = Modifier.weight(1f))
            Text(stringResource(R.string.h_bar_today), style = PleosType.labelMedium, color = c.textSecondary)
        }
        Text(stringResource(R.string.h_bar_meta, b.socPercent, b.keepPercent.roundToInt()),
            style = PleosType.bodyMedium, color = c.textSecondary)
    }
}

/** Read-only climate line: chip look, no border, no click (the app cannot control climate). */
@Composable
private fun ClimateLine(s: ClimateSetting) {
    val items = buildList {
        add(changeLabel(ClimateChange.Power(s.powerOn)))
        s.setTempC?.let { add(changeLabel(ClimateChange.Temp(it))) }
        s.fanSpeed?.let { add(changeLabel(ClimateChange.Fan(it))) }
        s.acOn?.let { add(changeLabel(ClimateChange.Ac(it))) }
        s.recircOn?.let { add(changeLabel(ClimateChange.Recirc(it))) }
        s.driverSeatHeat?.let { add(changeLabel(ClimateChange.SeatHeat(it))) }
    }
    // Wrap only between items, never inside one (a lone "0" fell to the next line on Small).
    androidx.compose.foundation.layout.FlowRow(Modifier.padding(top = PleosDimens.space16)) {
        items.forEachIndexed { n, item ->
            Text(if (n < items.lastIndex) "$item · " else item, style = PleosType.bodyMedium, color = Pleos.colors.textSecondary, softWrap = false)
        }
    }
}

@Composable
private fun DialToast(items: List<ClimateChange>, delta: Int, heldAboveCap: Boolean, showMinutes: Boolean, modifier: Modifier, short: Boolean = false) {
    val none = heldAboveCap || abs(delta) < 1
    val deltaText = when {
        heldAboveCap -> stringResource(R.string.d_toast_cap) // never an hours-long delta
        none -> stringResource(R.string.d_toast_none)
        // A computed ± minutes always carries "(추정)" in the toast too.
        else -> stringResource(R.string.d_toast_delta, signedDuration(delta))
    }
    // The temperature always reads "설정 24°C", on Small too; a bare number reads as the
    // cabin temperature. A long toast wraps to two lines instead of dropping the word.
    val label = items.map { changeLabel(it) }.joinToString(" · ")
    // Latency probe: first frame showing this toast content.
    androidx.compose.runtime.LaunchedEffect(items, delta, heldAboveCap) {
        androidx.compose.runtime.withFrameNanos { }
        app.chaseom.Probe.log("toastFrame")
    }
    // Neutral pill (red is for K2 only). Direction circle: amber down,
    // ink up with a white symbol, grey dot for no change. The symbol always goes with the colour.
    // One-line pill, 48 dp, caption 24 SemiBold, 28 dp direction circle with a vector triangle.
    // Pleos: a toast is its message only (no icon); the sign of the number says the direction.
    // Pleos Level 2 shadow by day.
    val k = cui()
    Row(
        modifier.heightIn(min = ChaseomUi.toastHeight).pleosShadow(2, 24.dp, enabled = !androidx.compose.foundation.isSystemInDarkTheme())
            .clip(androidx.compose.foundation.shape.RoundedCornerShape(24.dp)).background(k.toast)
            .padding(horizontal = 22.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // An air-source switch shows its name only, no minutes (see Toast.showMinutes).
        Text(when { !showMinutes -> label; label.isEmpty() -> deltaText; else -> stringResource(R.string.d_toast_fmt, label, deltaText) },
            style = ChaseomUi.banner, color = k.onToast, maxLines = 2)
    }
}

/**
 * Direction circle: up = text1 circle with a background-coloured triangle
 * (ink by day, white by night), down = amber with an ink triangle, no change = grey dot. Never colour alone.
 */
@Composable
internal fun DirectionCircle(delta: Int, on: Color, size: androidx.compose.ui.unit.Dp = ChaseomUi.dirCircle) {
    val k = cui()
    val (fill, mark) = when {
        delta > 0 -> k.text1 to k.bg
        delta < 0 -> DOWN_CIRCLE to Color(0xFF131417)
        else -> Color(0xFF8A8E96) to Color(0xFF131417)
    }
    Box(Modifier.size(size).clip(ChaseomUi.rPill).background(fill).then(
        if (delta > 0 && fill == on) Modifier.border(ChaseomUi.cardOutline, k.line, ChaseomUi.rPill) else Modifier),
        contentAlignment = Alignment.Center) {
        if (delta == 0) Box(Modifier.size(size / 5).clip(ChaseomUi.rPill).background(mark))
        else Icon(painterResource(if (delta > 0) R.drawable.ic_tri_up else R.drawable.ic_tri_down), null, tint = mark, modifier = Modifier.size(size * 0.44f))
    }
}

@Composable
private fun SideColumn(ui: StayUi, actions: StayActions, modifier: Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(PleosDimens.space24)) {
        if (ui.seatHeatLong) {
            Card { Body(stringResource(R.string.x_burn_30), strong = true) }
        } else {
            ui.exchange?.let { x ->
                Card {
                    val gain = signedDuration(x.gainMinutes)
                    val text = when (x.rule) {
                        ExchangeRule.X1_SEAT_HEAT -> stringResource(R.string.x1, gain)
                        ExchangeRule.X2_FAN -> stringResource(R.string.x2, gain)
                        ExchangeRule.X3_AC -> stringResource(R.string.x3, gain)
                    }
                    Text(text, style = PleosType.titleSmall, color = Pleos.colors.textPrimary)
                    if (x.rule == ExchangeRule.X1_SEAT_HEAT) Body(stringResource(R.string.x_burn))
                    if (x.rule == ExchangeRule.X3_AC) Body(stringResource(R.string.x3_sub))
                    Small(stringResource(R.string.x_footer))
                    BigButton(stringResource(R.string.close), { actions.dismissExchange(x.rule) },
                        size = ButtonSize.Medium, kind = ButtonKind.Basic)
                }
            }
        }
        ui.airMinutes?.let { AirCard(it, actions) }
    }
}

@Composable
private fun AirCard(minutes: Int, actions: StayActions) {
    Card {
        Text(stringResource(R.string.air_card, minutes), style = PleosType.titleSmall, color = Pleos.colors.textPrimary)
        BigButton(stringResource(R.string.close), { actions.dismissAir() }, size = ButtonSize.Medium, kind = ButtonKind.Basic)
    }
}

// ---- R: staying, reduced mode ------------------------------------------------------------------

@Composable
fun StayReducedScene(ui: StayUi, actions: StayActions, modifier: Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(PleosDimens.space24)) {
        StayHeader(ui, actions, Page.ABOUT)
        Card {
            Text(stringResource(R.string.r_card_title), style = PleosType.titleMedium, color = Pleos.colors.textPrimary)
            Body(stringResource(R.string.r_card_body))
        }
        ui.airMinutes?.let { AirCard(it, actions) }
        Spacer(Modifier.weight(1f))
        Small(stringResource(R.string.r_mode_note))
    }
}

/** K1 "near tomorrow's share" card: basic surface, 8dp warning bar on the left, warning icon. */
@Composable
private fun InfoCard(text: String) {
    val c = Pleos.colors
    // The same surface as the cards (radius 28, 1 dp outline), the yellow bar kept.
    val k = cui()
    Row(
        // On the #0B0C0E night page the card edge needs 3:1 -> a #6B6F76 outline at night
        // (about 3.8:1; the card surface alone is about 1.3:1).
        Modifier.fillMaxWidth().clip(ChaseomUi.rContainer).background(k.surface)
            .border(ChaseomUi.cardOutline, if (androidx.compose.foundation.isSystemInDarkTheme()) NIGHT_CARD_EDGE else k.line, ChaseomUi.rContainer)
            .height(androidx.compose.foundation.layout.IntrinsicSize.Min),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(ChaseomUi.cardBar).fillMaxHeight().background(k.safeBar))
        Spacer(Modifier.width(24.dp))
        Icon(painterResource(R.drawable.ic_warn_line), null, tint = ChaseomUi.amberRim, modifier = Modifier.size(32.dp))
        Spacer(Modifier.width(ChaseomUi.related))
        Text(text, style = ChaseomUi.title, color = k.text1, modifier = Modifier.padding(vertical = ChaseomUi.related).weight(1f))
    }
}

/**
 * Air card first (safety), then the swap card (or the 30-minute seat-heat card); at most two,
 * one per row under the island's chips. First line large with the direction circle before the minutes,
 * second line small. Green only inside the up circle.
 */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun IslandCards(ui: StayUi, actions: StayActions) {
    val c = Pleos.colors
    val k = cui()
    Column(verticalArrangement = Arrangement.spacedBy(ChaseomUi.related)) {
        ui.airMinutes?.let { m ->
            IslandCard(onClose = { actions.dismissAir() }) {
                Text(stringResource(R.string.air_card, m), style = ChaseomUi.title, color = k.text1)
            }
        }
        if (ui.seatHeatLong) {
            // The seat-heat warning uses the white warning part, like every other warning.
            WhiteWarn(androidx.compose.ui.text.AnnotatedString(stringResource(R.string.x_burn_30)), Modifier.fillMaxWidth())
        } else {
            ui.exchange?.let { x ->
                IslandCard(onClose = { actions.dismissExchange(x.rule) }) {
                    val gain = signedDuration(x.gainMinutes)
                    val full = when (x.rule) {
                        ExchangeRule.X1_SEAT_HEAT -> stringResource(R.string.x1, gain)
                        ExchangeRule.X2_FAN -> stringResource(R.string.x2, gain)
                        ExchangeRule.X3_AC -> stringResource(R.string.x3, gain)
                    }
                    // On the Small window X1 is two fixed lines, x1_l1 / x1_l2.
                    val small = with(androidx.compose.ui.platform.LocalDensity.current) {
                        androidx.compose.ui.platform.LocalWindowInfo.current.containerSize.width.toDp() < 1200.dp
                    }
                    val mainLines = @Composable {
                        // X1 is always two lines; line 2 = [▲] +16분 (32 Bold) · 비슷한 체감 (추정).
                        val twoLines = x.rule == ExchangeRule.X1_SEAT_HEAT
                        if (twoLines) Text(stringResource(R.string.x1_l1), style = ChaseomUi.title, color = k.text1)
                        val line = if (twoLines) stringResource(R.string.x1_l2, gain) else full
                        val at = line.indexOf(gain).takeIf { it >= 0 } ?: line.length
                        // Small window (~840 dp, seen with a task resize): the circle + minutes move to the next
                        // line as one unit instead of being squeezed into a one-letter column.
                        androidx.compose.foundation.layout.FlowRow(itemVerticalAlignment = Alignment.CenterVertically) {
                            if (at > 0) Text(line.substring(0, at), style = ChaseomUi.title, color = k.text1)
                            if (at < line.length) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    // A small ink arrow, no filled circle (the darkest shape came before the number).
                                    Icon(painterResource(if (x.gainMinutes >= 0) R.drawable.ic_tri_up else R.drawable.ic_tri_down), null,
                                        tint = k.text1, modifier = Modifier.size(20.dp))
                                    Spacer(Modifier.width(ChaseomUi.line))
                                    Text(gain, style = ChaseomUi.title.copy(fontSize = 32.sp, lineHeight = 40.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold),
                                        color = k.text1, softWrap = false)
                                    // The tail "· 비슷한 체감 (추정)" is 26 Regular #4A4E57 (the value stays 32 Bold).
                                    Text(line.substring(at + gain.length), style = ChaseomUi.body, color = Color(0xFF4A4E57), softWrap = false)
                                }
                            }
                        }
                    }
                    val notes = @Composable {
                        // The burn line with an amber caution icon, then the source line in text3.
                        // The burn line is the inline white warning part (same grammar as every warning).
                        if (x.rule == ExchangeRule.X1_SEAT_HEAT) // always with X1
                            WhiteWarn(androidx.compose.ui.text.AnnotatedString(stringResource(R.string.x_burn)), inline = true)
                        if (x.rule == ExchangeRule.X3_AC) Text(stringResource(R.string.x3_sub), style = ChaseomUi.caption, color = k.text2)
                        Text(stringResource(R.string.x_footer), style = ChaseomUi.caption, color = k.text3)
                    }
                    // Large = offer on the left, burn and source lines on the right; Small = one column.
                    if (!small) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(ChaseomUi.block)) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(ChaseomUi.line)) { mainLines() }
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(ChaseomUi.line)) { notes() }
                    } else {
                        mainLines(); notes()
                    }
                }
            }
        }
    }
}

@Composable
private fun IslandCard(onClose: (() -> Unit)?, content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    // White surface, soft shadow, 1 dp #ECEEF1 hairline, radius 16; no amber bar
    // (it read as a warning bar); close is a navy text button (the card's minor action), 76 dp touch area.
    val k = cui()
    val shape = RoundedCornerShape(16.dp)
    Row(
        Modifier.fillMaxWidth().pleosShadow(1, 16.dp, enabled = !androidx.compose.foundation.isSystemInDarkTheme()) // Pleos Level 1 shadow
            .clip(shape).background(k.surface).border(1.dp, if (androidx.compose.foundation.isSystemInDarkTheme()) k.line else Color(0xFFECEEF1), shape),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).padding(start = 28.dp, end = ChaseomUi.related, top = 24.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(ChaseomUi.line), content = content)
        if (onClose != null) TextButton76(stringResource(R.string.close), onClose)
        Spacer(Modifier.width(ChaseomUi.line))
    }
}

/** Navy text button, 26 SemiBold, 76 dp touch area (a card closes with a text button). */
@Composable
internal fun TextButton76(label: String, onClick: () -> Unit) {
    Box(
        Modifier.heightIn(min = ChaseomUi.touchMin).widthIn(min = ChaseomUi.touchMin)
            .pressable(shape = ChaseomUi.rPill, onClick = onClick).padding(horizontal = 18.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = ChaseomUi.body.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold), color = cui().primary)
    }
}

/** Light pill (#EEEFF1), 56 dp, 26 Medium, 76 dp touch area. Card close and receipt close. */
@Composable
internal fun LightPillButton(label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val night = androidx.compose.foundation.isSystemInDarkTheme()
    Box(
        modifier.heightIn(min = ChaseomUi.touchMin).pressable(shape = ChaseomUi.rPill, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier.height(56.dp).clip(ChaseomUi.rPill).background(if (night) Color(0xFF34373D) else Color(0xFFEEEFF1))
                .padding(horizontal = 28.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(label, style = ChaseomUi.body.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Medium), color = cui().text1)
        }
    }
}

/** Night card edge on #0B0C0E: contrast about 3.8:1 (K1 card boundary needs >= 3:1). */
internal val NIGHT_CARD_EDGE = Color(0xFF6B6F76)
