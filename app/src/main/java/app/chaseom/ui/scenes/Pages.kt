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

// ---- G: driving glance. One static view: no numbers, no buttons, no click handlers, no motion. -

@Composable
fun GlanceScene(firstRun: Boolean) {
    val c = Pleos.colors
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(painterResource(R.drawable.ic_pause), null, tint = c.textPrimary, modifier = Modifier.size(96.dp))
        Spacer(Modifier.height(PleosDimens.space24))
        Text(stringResource(R.string.drv_title), style = PleosType.titleMedium, color = c.textPrimary, textAlign = TextAlign.Center)
        Text(stringResource(if (firstRun) R.string.drv_sub_first_run else R.string.drv_sub),
            style = PleosType.bodyLarge, color = c.textSecondary, textAlign = TextAlign.Center)
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
    val c = Pleos.colors
    val ev = mode == AppMode.FULL
    Column(modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(PleosDimens.space32)) {
        Text(stringResource(if (ev) R.string.onb_title else R.string.onb_reduced_title), style = PleosType.headlineSmall, color = c.textPrimary)
        val points = if (ev) {
            listOf(
                Triple(R.drawable.ic_lock, R.string.onb_p1_title, R.string.onb_p1_body),
                Triple(R.drawable.ic_settings, R.string.onb_p2_title, R.string.onb_p2_body),
                Triple(R.drawable.ic_eye, R.string.onb_p3_title, R.string.onb_p3_body),
            )
        } else {
            listOf(
                Triple(R.drawable.ic_warning, R.string.r_card_title, R.string.onb_reduced_p1_body),
                Triple(R.drawable.ic_eye, R.string.onb_p3_title, R.string.onb_p3_body),
            )
        }
        points.forEach { (icon, title, body) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(painterResource(icon), null, tint = c.textPrimary, modifier = Modifier.size(64.dp))
                Spacer(Modifier.width(PleosDimens.space24))
                Column {
                    Text(stringResource(title), style = PleosType.titleMedium, color = c.textPrimary)
                    Body(stringResource(body), color = c.textSecondary)
                }
            }
        }
        Card {
            StatusIcon(R.drawable.ic_warning, c.warning, 56.dp)
            Body(stringResource(R.string.manual_quote))
            Small(stringResource(R.string.manual_source))
        }
        BigButton(
            stringResource(if (ev) R.string.onb_start_ev else R.string.onb_start_reduced),
            { actions.finishOnboarding(toSettings = ev) }, kind = ButtonKind.Primary,
        )

    }
}

// ---- S: settings (keep share). No keyboard: stepper + chips. --------------------------------------

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun SettingsScene(current: StaySettings, actions: StayActions, modifier: Modifier) {
    val c = Pleos.colors
    var km by remember { mutableIntStateOf(current.keepKm) }
    var label by remember { mutableStateOf(current.label) }
    Column(modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(PleosDimens.space32)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton76(R.drawable.ic_back, stringResource(R.string.cd_back)) { actions.open(Page.NONE) }
            Spacer(Modifier.width(PleosDimens.space24))
            Text(stringResource(R.string.s_title), style = PleosType.titleLarge, color = c.textPrimary)
        }
        Text(stringResource(R.string.s_question), style = PleosType.titleMedium, color = c.textPrimary)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(PleosDimens.space32)) {
            BigButton("−", { km = (km - StaySettings.STEP_KM).coerceAtLeast(StaySettings.MIN_KM) }, kind = ButtonKind.Basic)
            Text(stringResource(R.string.s_km, km), style = PleosType.headlineLarge, color = c.textPrimary,
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
            Text(stringResource(R.string.s_about), style = PleosType.titleLarge, color = c.textPrimary)
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
