package app.chaseom.ui.scenes

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.chaseom.R
import app.chaseom.stay.AppMode
import app.chaseom.stay.Page
import app.chaseom.stay.StaySettings
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.draw.clip

// ---- G: driving glance. One static view: no numbers, no buttons, no click handlers, no motion. -

@Composable
fun GlanceScene(firstRun: Boolean) {
    val c = Pleos.colors
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        // POLISH_PASS v1.2 §0.5: the only thing on this screen is the app icon, so it carries the brand (DR sizes kept).
        AppIcon(ChaseomUi.glanceIcon)
        Spacer(Modifier.height(PleosDimens.space24))
        Text(stringResource(R.string.drv_title), style = ChaseomUi.glanceTitle, color = c.textPrimary, textAlign = TextAlign.Center)
        Text(stringResource(if (firstRun) R.string.drv_sub_first_run else R.string.drv_sub),
            style = ChaseomUi.glanceSub, color = c.textSecondary, textAlign = TextAlign.Center)
    }
}

/** Before any gear value has been read: no numbers, no buttons, just the state (QA E4-5). */
@Composable
fun ConnectingScene() {
    val c = Pleos.colors
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(stringResource(R.string.st_waiting_car), style = PleosType.titleMedium, color = c.textPrimary, textAlign = TextAlign.Center)
    }
}

// ---- O1: onboarding ------------------------------------------------------------------------------

@Composable
fun OnboardingScene(mode: AppMode, actions: StayActions, modifier: Modifier) {
    val k = cui()
    val size = uiSize()
    val ev = mode == AppMode.FULL
    // POLISH_PASS v1.2 §0.5: the island scene first (left on Large, top on Small), no labels; point icons are
    // crate / sun-dial / receipt in tinted circles (no lock); the manual quote stays with an amber caution icon.
    val points = if (ev) {
        listOf(
            Triple(R.drawable.ic_crate, R.string.onb_p1_title, R.string.onb_p1_body),
            Triple(R.drawable.ic_sundial, R.string.onb_p2_title, R.string.onb_p2_body),
            Triple(R.drawable.ic_receipt, R.string.onb_p3_title, R.string.onb_p3_body),
        )
    } else {
        listOf(
            Triple(R.drawable.ic_warn_line, R.string.r_card_title, R.string.onb_reduced_p1_body),
            Triple(R.drawable.ic_receipt, R.string.onb_p3_title, R.string.onb_p3_body),
        )
    }
    val picture = @Composable { m: Modifier ->
        Column(m, verticalArrangement = Arrangement.spacedBy(ChaseomUi.related)) {
            OnboardingIsland(Modifier.fillMaxWidth())
            // Pleos fit v2.1: the safety quote is the white warning part (quote W600 ink, source W500 #4A4E57).
            val quote = androidx.compose.ui.text.buildAnnotatedString {
                append(stringResource(R.string.manual_quote))
                pushStyle(androidx.compose.ui.text.SpanStyle(fontWeight = androidx.compose.ui.text.font.FontWeight.Medium,
                    color = androidx.compose.ui.graphics.Color(0xFF4A4E57)))
                append(" — " + stringResource(R.string.manual_source)); pop()
            }
            WhiteWarn(quote, Modifier.fillMaxWidth(), style = ChaseomUi.caption.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold))
        }
    }
    val text = @Composable { m: Modifier ->
        Column(m, verticalArrangement = Arrangement.spacedBy(ChaseomUi.block)) {
            Text(stringResource(if (ev) R.string.onb_title else R.string.onb_reduced_title), style = ChaseomUi.onboardingTitle(size), color = k.text1)
            points.forEach { (icon, title, body) ->
                Row(verticalAlignment = Alignment.Top) {
                    Box(Modifier.size(ChaseomUi.iconPointCircle).clip(ChaseomUi.rPill).background(k.tint), contentAlignment = Alignment.Center) {
                        // pleos_icons48: 48 dp so the 3-line grid draws a 3 dp line, like Pleos' own panel icons.
                        Icon(painterResource(icon), null, tint = k.text1, modifier = Modifier.size(48.dp))
                    }
                    Spacer(Modifier.width(ChaseomUi.related + ChaseomUi.line))
                    Column(verticalArrangement = Arrangement.spacedBy(ChaseomUi.line)) {
                        Text(stringResource(title), style = ChaseomUi.title, color = k.text1)
                        Text(stringResource(body), style = ChaseomUi.body, color = k.text2)
                    }
                }
            }
            BigButton(
                stringResource(if (ev) R.string.onb_start_ev else R.string.onb_start_reduced),
                { actions.finishOnboarding(toSettings = ev) }, kind = ButtonKind.Primary, modifier = Modifier.fillMaxWidth(),
            )
        }
    }
    if (size == ChaseomUi.Size.Large) {
        Row(modifier.verticalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(ChaseomUi.block * 2),
            verticalAlignment = Alignment.CenterVertically) {
            // POLISH v1.4: picture 890 dp on the left, text column at most 720, both centred vertically.
            picture(Modifier.width(890.dp))
            text(Modifier.weight(1f).widthIn(max = 720.dp))
        }
    } else {
        Column(modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(ChaseomUi.block)) {
            picture(Modifier.fillMaxWidth())
            text(Modifier.fillMaxWidth())
        }
    }
}

// ---- S: settings (keep share). No keyboard: stepper + chips. --------------------------------------

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun SettingsScene(current: StaySettings, actions: StayActions, modifier: Modifier) {
    val c = Pleos.colors
    var km by remember { mutableIntStateOf(current.keepKm) }
    var label by remember { mutableStateOf(current.label) }
    // POLISH_PASS v1.2 §0.6: the same 960 dp reading width as the first-use screen.
    Column(modifier.widthIn(max = ChaseomUi.readableMax).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(PleosDimens.space32)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton76(R.drawable.ic_back, stringResource(R.string.cd_back)) { actions.open(Page.NONE) }
            Spacer(Modifier.width(PleosDimens.space24))
            Text(stringResource(R.string.s_title), style = ChaseomUi.title, color = c.textPrimary)
        }
        Text(stringResource(R.string.s_question), style = PleosType.titleMedium, color = c.textPrimary)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(PleosDimens.space32)) {
            BigButton("−", { km = (km - StaySettings.STEP_KM).coerceAtLeast(StaySettings.MIN_KM) }, kind = ButtonKind.Basic)
            Text(stringResource(R.string.s_km, km), style = ChaseomUi.hero(uiSize()), color = c.textPrimary,
                modifier = Modifier.width(300.dp), textAlign = TextAlign.Center)
            BigButton("+", { km = (km + StaySettings.STEP_KM).coerceAtMost(StaySettings.MAX_KM) }, kind = ButtonKind.Basic)
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(PleosDimens.space16), verticalArrangement = Arrangement.spacedBy(PleosDimens.space16)) {
            StaySettings.PRESETS.forEach { p ->
                BigButton(stringResource(R.string.s_km, p), { km = p }, size = ButtonSize.Medium,
                    kind = if (p == km) ButtonKind.Filled else ButtonKind.Basic)
            }
        }
        Text(stringResource(R.string.s_label_q), style = PleosType.titleMedium, color = c.textPrimary)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(PleosDimens.space16), verticalArrangement = Arrangement.spacedBy(PleosDimens.space16)) {
            StaySettings.LABELS.forEach { l ->
                BigButton(labelText(l), { label = l }, size = ButtonSize.Medium,
                    kind = if (l == label) ButtonKind.Filled else ButtonKind.Basic)
            }
        }
        Body(stringResource(R.string.s_margin), color = c.textSecondary)
        // HANDOFF 9.8 v5 (UXR E-5): save is the only primary button; "use defaults" is a small text link
        // above it on the left (76 dp touch), shown until the user has chosen once; "about" is a list row.
        if (!current.saved) {
            Box(Modifier.heightIn(min = 76.dp).clickable { actions.useDefaults() }, contentAlignment = Alignment.CenterStart) {
                Text(stringResource(R.string.s_use_default_link), style = PleosType.bodyMedium.copy(textDecoration = TextDecoration.Underline),
                    color = c.textSecondary)
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            BigButton(stringResource(R.string.s_save), { actions.saveSettings(km, label) }, kind = ButtonKind.Primary)
        }
        Box(Modifier.fillMaxWidth().height(2.dp).background(c.divider))
        Row(
            Modifier.fillMaxWidth().heightIn(min = 76.dp).clickable { actions.open(Page.ABOUT) },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(stringResource(R.string.s_about), style = PleosType.bodyLarge, color = c.textPrimary, modifier = Modifier.weight(1f))
            Text("›", style = PleosType.titleMedium, color = c.textSecondary)
        }
    }
}

// ---- ABOUT --------------------------------------------------------------------------------------

@Composable
fun AboutScene(ui: StayUi, actions: StayActions, modifier: Modifier) {
    val c = Pleos.colors
    Column(modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(PleosDimens.space24)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton76(R.drawable.ic_back, stringResource(R.string.cd_back)) {
                actions.open(if (ui.policy.mode == AppMode.FULL) Page.SETTINGS else Page.NONE)
            }
            Spacer(Modifier.width(PleosDimens.space24))
            Text(stringResource(R.string.s_about), style = ChaseomUi.title, color = c.textPrimary)
        }
        Text(stringResource(R.string.about_name, app.chaseom.BuildConfig.VERSION_NAME), style = PleosType.titleMedium, color = c.textPrimary)
        Card {
            Body(stringResource(R.string.manual_quote))
            Small(stringResource(R.string.manual_source))
        }
        Body(stringResource(if (ui.policy.mode == AppMode.FULL) R.string.about_fuel_ev else R.string.about_fuel_reduced), strong = true)
        Body(stringResource(R.string.about_model))
        Small(stringResource(R.string.about_coeff))
        Body(stringResource(R.string.about_v2l))
        if (!ui.backrestSupported) Small(stringResource(R.string.about_backrest_none))
        Box(Modifier.height(PleosDimens.space24))
    }
}

/** The adaptive app icon (04_design POLISH_PASS v1.2) drawn from its two layers, clipped like a launcher tile. */
@Composable
fun AppIcon(size: androidx.compose.ui.unit.Dp) {
    Box(Modifier.size(size).clip(androidx.compose.foundation.shape.RoundedCornerShape(size / 4)), contentAlignment = Alignment.Center) {
        // Adaptive layers are 108 dp with the visible tile in the middle 72: draw them 1.5x and let the clip trim.
        androidx.compose.foundation.Image(painterResource(R.drawable.ic_launcher_background), null, Modifier.requiredSize(size * 1.5f))
        androidx.compose.foundation.Image(painterResource(R.drawable.ic_launcher_foreground), null, Modifier.requiredSize(size * 1.5f))
    }
}
