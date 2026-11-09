package app.chaseom.ui.scenes

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
import kotlin.math.abs
import kotlin.math.roundToInt

// ---- shared header: N0 banner + title row ----------------------------------------------------

@Composable
private fun StayHeader(ui: StayUi, actions: StayActions, settingsPage: Page) {
    val c = Pleos.colors
    ui.banner?.let { b ->
        val text = stringResource(R.string.n0_banner) +
            (if (ui.policy.idlingNotice) stringResource(R.string.n0_banner_reduced_suffix) else "")
        if (b.expanded) {
            StatusStrip("⚠ $text", c.warning, 96.dp) {
                IconButton76(R.drawable.ic_close, stringResource(R.string.cd_close)) { actions.closeBanner() }
            }
        } else {
            StatusStrip(text, c.warning, 40.dp) // stays for the whole stay
        }
        Spacer(Modifier.height(PleosDimens.space16))
    }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        val title = when {
            ui.elapsedMinutes >= 1 -> stringResource(R.string.h_title, ui.elapsedMinutes)
            ui.restartedFromStale -> stringResource(R.string.h_title_resumed)
            else -> stringResource(R.string.h_title_new)
        }
        Text(title, style = PleosType.titleLarge, color = c.textPrimary,
            modifier = Modifier.weight(1f))
        IconButton76(R.drawable.ic_settings, stringResource(R.string.cd_settings)) { actions.open(settingsPage) }
    }
}

// ---- H: staying, EV ----------------------------------------------------------------------------

@Composable
fun StayFullScene(ui: StayUi, actions: StayActions, modifier: Modifier) {
    Column(modifier) {
        StayHeader(ui, actions, Page.SETTINGS)
        Spacer(Modifier.height(PleosDimens.space24))
        val hasSide = ui.exchange != null || ui.seatHeatLong || ui.airMinutes != null
        // HANDOFF 9.2.1 v6: with the island face there is no second column; the cards go under the island's
        // chips, so the island never changes size when a card appears (panel R15).
        val islandFace = app.chaseom.BuildConfig.KICK_ISLAND && ui.hero != null
        BoxWithConstraints(Modifier.fillMaxWidth().weight(1f)) {
            val twoCol = maxWidth >= 1200.dp && hasSide && !islandFace
            val heroSize = if (maxWidth >= 2000.dp) 96.sp else 60.sp
            // HANDOFF 9.6 v7 (and the 11-10 Small-window check): the toast covers no number or text line. Wide
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
                ui.toast?.let { t -> DialToast(t.items, t.deltaMinutes, t.heldAboveCap, Modifier.align(Alignment.BottomCenter).padding(bottom = PleosDimens.space24)) }
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
        app.chaseom.BuildConfig.KICK_ISLAND -> Face.ISLAND // CEO 2026-11-09
        else -> Face.BAR
    }
    MainColumnContent(ui, heroSize, modifier, face, actions, toastPlace)
}

private enum class Face { BAR, ISLAND }

/** The climate status line is not on the island screen (HQ 2026-11-11, "글 3줄"). */
internal fun climateLineShown(islandFace: Boolean): Boolean = !islandFace

/** HANDOFF 8 (round1b): up = ink circle with a white symbol, down = amber circle with an ink symbol. No green. */
internal val UP_CIRCLE = Color(0xFF131417)
internal val DOWN_CIRCLE = Color(0xFFFFB020)

/** HANDOFF 9.6 v7 toast positions. */
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
    Column(modifier, verticalArrangement = Arrangement.spacedBy(PleosDimens.space16)) {
        when (ui.keepBanner) {
            KeepBanner.NEAR -> InfoCard(stringResource(R.string.k1)) // HANDOFF 9.6: K1 is information, not an alarm
            KeepBanner.BELOW -> StatusStrip(stringResource(R.string.k2), c.negative, 72.dp)
            null -> Unit
        }
        val hero = ui.hero
        if (hero == null) {
            Body(stringResource(R.string.h_hvac_off), color = c.textSecondary)
        } else if (hero.belowKeep) {
            // QA E4-2 / REVIEW_COPY_B: below tomorrow's share the hero states the situation only (no "약 0분",
            // no "time you can stay" label); the K2 banner above asks for the action.
            Text(stringResource(R.string.h_k2_hero), style = PleosType.headlineMedium, color = c.textPrimary)
            hero.coveredKm?.let { Text(stringResource(R.string.h_k2_meta, ui.settings.keepKm, it), style = PleosType.titleMedium, color = c.textSecondary) }
        } else {
            // ISLAND_REDESIGN v2.4: with the island the label line goes; "머물 수 있어요" joins the number.
            if (face == Face.BAR) Text(stringResource(R.string.h_hero_label), style = PleosType.titleMedium, color = c.textSecondary)
            if (hero.hvacOff) {
                Text(stringResource(R.string.h_hvac_off), style = PleosType.titleMedium, color = c.textPrimary)
                Text(stringResource(R.string.h_hvac_off_sub), style = PleosType.bodyMedium, color = c.textSecondary)
            } else {
                val value = if (hero.overCap) stringResource(R.string.h_hero_cap)
                else stringResource(R.string.h_hero_value, duration(hero.minutes ?: 0))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
                    Text(value, style = TextStyle(fontSize = heroSize, lineHeight = heroSize * 1.2f, fontWeight = PleosType.headlineLarge.fontWeight),
                        color = c.textPrimary)
                    if (face == Face.ISLAND) {
                        // Same ink and weight as the number, smaller (Large 44 / Small 36 sp).
                        val stay = if (heroSize.value >= 90f) 44.sp else 36.sp
                        Spacer(Modifier.width(PleosDimens.space16))
                        Text(stringResource(R.string.h_hero_stay), style = TextStyle(fontSize = stay, lineHeight = stay * 1.2f, fontWeight = PleosType.headlineLarge.fontWeight),
                            color = c.textPrimary, modifier = Modifier.padding(bottom = 6.dp))
                    }
                    Spacer(Modifier.width(PleosDimens.space16))
                    Text(stringResource(R.string.estimate), style = PleosType.titleMedium, color = c.textSecondary,
                        modifier = Modifier.padding(bottom = 12.dp))
                    if (toastPlace == ToastPlace.HERO_ROW) {
                        Spacer(Modifier.weight(1f))
                        // v7: same row, bottom-aligned 4 dp, no shadow.
                        ui.toast?.let { t -> DialToast(t.items, t.deltaMinutes, t.heldAboveCap, Modifier.padding(bottom = 4.dp)) }
                    }
                }
                // Bundle 5: say why the number stops at 3 hours.
                if (hero.overCap) Text(stringResource(R.string.h_cap_note), style = PleosType.bodyMedium, color = c.textSecondary)
            }
        }
        // ISLAND_REDESIGN v2.5 line 2: one description line under the hero (the island's meta line moved here).
        if (face == Face.ISLAND && hero != null && !hero.belowKeep) ui.bar?.let { b ->
            Text(stringResource(R.string.h_island_meta, b.socPercent, b.keepKm), style = PleosType.bodyMedium, color = c.textSecondary)
        }
        when (face) {
            Face.BAR -> ui.bar?.let { KeepBar(it) }
            Face.ISLAND -> {
                IslandPanel(
                    ui, Modifier.fillMaxWidth(),
                    pictureTop = {
                        // v7 Small: inside the top-left sky of the picture (16 dp, shadow 0 4 16 25 %), up to the "설정 °C" label (H1).
                        if (toastPlace == ToastPlace.ISLAND_TOP) ui.toast?.let { t ->
                            DialToast(t.items, t.deltaMinutes, t.heldAboveCap,
                                Modifier.padding(top = 16.dp).shadow(16.dp, RoundedCornerShape(PleosDimens.radiusCard), ambientColor = Color.Black.copy(alpha = 0.25f), spotColor = Color.Black.copy(alpha = 0.25f)))
                        }
                    },
                ) { if (actions != null) IslandCards(ui, actions) }
            }
        }
        // HANDOFF 8 (round1b, panel §12-①): the island screen keeps three text lines; Pleos' own climate bar
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
    // Wrap only between items, never inside one (panel §12-①: a lone "0" fell to the next line on Small).
    androidx.compose.foundation.layout.FlowRow(Modifier.padding(top = PleosDimens.space16)) {
        items.forEachIndexed { n, item ->
            Text(if (n < items.lastIndex) "$item · " else item, style = PleosType.bodyMedium, color = Pleos.colors.textSecondary, softWrap = false)
        }
    }
}

@Composable
private fun DialToast(items: List<ClimateChange>, delta: Int, heldAboveCap: Boolean, modifier: Modifier) {
    val none = heldAboveCap || abs(delta) < 1
    val deltaText = when {
        heldAboveCap -> stringResource(R.string.d_toast_cap) // QA M-11: never an hours-long delta
        none -> stringResource(R.string.d_toast_none)
        else -> signedDuration(delta)
    }
    val label = items.map { changeLabel(it) }.joinToString(" · ")
    // Latency probe (QA IP-5): first frame showing this toast content.
    androidx.compose.runtime.LaunchedEffect(items, delta, heldAboveCap) {
        androidx.compose.runtime.withFrameNanos { }
        android.util.Log.i("PROBE", "toastFrame wall=${System.currentTimeMillis()}")
    }
    // HANDOFF 9.6 v1.1: neutral pill (red is for K2 only). Direction circle (HANDOFF 8, round1b): amber down,
    // ink up with a white symbol, grey dot for no change. The symbol always goes with the colour.
    val night = androidx.compose.foundation.isSystemInDarkTheme()
    val pill = if (night) Color(0xFFF4F5F7) else Color(0xFF131417)
    val ink = if (night) Color(0xFF131417) else Color.White
    val (dirColor, dirSymbol) = when {
        none -> Color(0xFF8A8E96) to "·"
        delta > 0 -> UP_CIRCLE to "▲"
        else -> DOWN_CIRCLE to "▼"
    }
    Row(
        modifier.clip(RoundedCornerShape(PleosDimens.radiusCard)).background(pill)
            .padding(start = PleosDimens.space16, end = PleosDimens.space40, top = PleosDimens.space16, bottom = PleosDimens.space16),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(48.dp).clip(RoundedCornerShape(50)).background(dirColor), contentAlignment = Alignment.Center) {
            Text(dirSymbol, style = PleosType.titleMedium, color = if (dirColor == UP_CIRCLE) Color.White else Color(0xFF131417))
        }
        Spacer(Modifier.width(PleosDimens.space16))
        Text(if (label.isEmpty()) deltaText else stringResource(R.string.d_toast_fmt, label, deltaText),
            style = PleosType.headlineSmall, color = ink)
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

/** K1 "near tomorrow's share" card (HANDOFF 9.6): basic surface, 8dp warning bar on the left, warning icon. */
@Composable
private fun InfoCard(text: String) {
    val c = Pleos.colors
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(c.buttonBasicEnabled).height(androidx.compose.foundation.layout.IntrinsicSize.Min),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(8.dp).fillMaxHeight().background(c.warning))
        Spacer(Modifier.width(PleosDimens.space24))
        Icon(painterResource(R.drawable.ic_warning), null, tint = c.warning, modifier = Modifier.size(40.dp))
        Spacer(Modifier.width(PleosDimens.space16))
        Text(text, style = PleosType.bodyLargeStrong, color = c.textPrimary, modifier = Modifier.padding(vertical = PleosDimens.space16).weight(1f))
    }
}

/**
 * HANDOFF 9.2.1 v6: air card first (safety), then the swap card (or the 30-minute seat-heat card); at most two,
 * one per row under the island's chips. First line large with the direction circle before the minutes,
 * second line small. Green only inside the up circle (QA).
 */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun IslandCards(ui: StayUi, actions: StayActions) {
    val c = Pleos.colors
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        ui.airMinutes?.let { m ->
            IslandCard(onClose = { actions.dismissAir() }) {
                Text(stringResource(R.string.air_card, m), style = PleosType.titleMedium, color = c.textPrimary)
            }
        }
        if (ui.seatHeatLong) {
            IslandCard(onClose = null) {
                Text(stringResource(R.string.x_burn_30), style = PleosType.titleMedium, color = c.textPrimary)
            }
        } else {
            ui.exchange?.let { x ->
                IslandCard(onClose = { actions.dismissExchange(x.rule) }) {
                    val gain = signedDuration(x.gainMinutes)
                    val full = when (x.rule) {
                        ExchangeRule.X1_SEAT_HEAT -> stringResource(R.string.x1, gain)
                        ExchangeRule.X2_FAN -> stringResource(R.string.x2, gain)
                        ExchangeRule.X3_AC -> stringResource(R.string.x3, gain)
                    }
                    // HANDOFF 8 (round1b) / panel §12-②: on the Small window X1 is two fixed lines, x1_l1 / x1_l2.
                    val small = with(androidx.compose.ui.platform.LocalDensity.current) {
                        androidx.compose.ui.platform.LocalWindowInfo.current.containerSize.width.toDp() < 1200.dp
                    }
                    val twoLines = small && x.rule == ExchangeRule.X1_SEAT_HEAT
                    if (twoLines) Text(stringResource(R.string.x1_l1), style = PleosType.titleMedium, color = c.textPrimary)
                    val line = if (twoLines) stringResource(R.string.x1_l2, gain) else full
                    val at = line.indexOf(gain).takeIf { it >= 0 } ?: line.length
                    // Small window (~840 dp, found 11-10 with a task resize): the circle + minutes move to the next
                    // line as one unit instead of being squeezed into a one-letter column.
                    androidx.compose.foundation.layout.FlowRow(itemVerticalAlignment = Alignment.CenterVertically) {
                        Text(line.substring(0, at), style = PleosType.titleMedium, color = c.textPrimary)
                        if (at < line.length) {
                            val up = x.gainMinutes > 0
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    Modifier.size(40.dp).clip(RoundedCornerShape(50))
                                        .background(if (up) UP_CIRCLE else DOWN_CIRCLE),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(if (up) "\u25B2" else "\u25BC", style = PleosType.bodyMedium, color = if (up) Color.White else Color(0xFF131417))
                                }
                                Spacer(Modifier.width(8.dp))
                                Text(line.substring(at), style = PleosType.titleMedium, color = c.textPrimary, softWrap = false)
                            }
                        }
                    }
                    val second = buildList {
                        if (x.rule == ExchangeRule.X1_SEAT_HEAT) add(stringResource(R.string.x_burn)) // always with X1
                        if (x.rule == ExchangeRule.X3_AC) add(stringResource(R.string.x3_sub))
                        add(stringResource(R.string.x_footer))
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (x.rule == ExchangeRule.X1_SEAT_HEAT) {
                            Icon(painterResource(R.drawable.ic_warning), null, tint = c.warning, modifier = Modifier.size(28.dp))
                            Spacer(Modifier.width(8.dp))
                        }
                        Text(second.joinToString(" \u00B7 "), style = PleosType.labelMedium.copy(fontSize = 26.sp), color = c.textSecondary)
                    }
                }
            }
        }
    }
}

@Composable
private fun IslandCard(onClose: (() -> Unit)?, content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    val c = Pleos.colors
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(c.buttonBasicEnabled)
            .padding(horizontal = 28.dp, vertical = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp), content = content)
        if (onClose != null) {
            Spacer(Modifier.width(16.dp))
            BigButton(stringResource(R.string.close), onClose, size = ButtonSize.Medium, kind = ButtonKind.Basic)
        }
    }
}
