package app.chaseom.ui.scenes

import app.chaseom.ui.theme.pleosShadow
import app.chaseom.ui.theme.pressable
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.chaseom.R
import app.chaseom.stay.ModePolicy
import app.chaseom.stay.NoticeKind
import app.chaseom.stay.Receipt
import app.chaseom.stay.ReceiptRow
import app.chaseom.ui.theme.BigButton
import app.chaseom.ui.theme.ButtonKind
import app.chaseom.ui.theme.ButtonSize
import app.chaseom.ui.theme.Pleos
import app.chaseom.ui.theme.PleosDimens
import app.chaseom.ui.theme.PleosType
import app.chaseom.ui.theme.ChaseomUi
import app.chaseom.ui.theme.cui
import app.chaseom.ui.theme.uiSize
import androidx.compose.foundation.layout.heightIn
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Window-wide scrim that swallows touches to the scene below. */
@Composable
private fun Scrim(light: Boolean = false, content: @Composable () -> Unit) {
    Box(
        // The receipt dims the screen only lightly (25 %); its sheet edge is its shadow.
        Modifier.fillMaxSize().background(if (light) Color(0xFF0B0F14).copy(alpha = 0.25f) else Pleos.colors.scrim)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
        contentAlignment = Alignment.Center,
    ) { content() }
}

// ---- N1 / N2 / N3: notice card. Informs only: no detection/prevention wording. ------

@Composable
fun NoticeOverlay(kind: NoticeKind, policy: ModePolicy, actions: StayActions) {
    val c = Pleos.colors
    Scrim {
        Column(
            Modifier.widthIn(max = 1100.dp).padding(PleosDimens.space40).clip(RoundedCornerShape(PleosDimens.radiusSheet))
                .background(c.backgroundPopup).padding(PleosDimens.space48),
            verticalArrangement = Arrangement.spacedBy(PleosDimens.space24),
        ) {
            StatusIcon(R.drawable.ic_warning, c.warning)
            val title = when (kind) {
                NoticeKind.HOUR -> R.string.n1_title
                NoticeKind.NIGHT -> R.string.n2_title
                NoticeKind.BACKREST -> R.string.n3_title
            }
            // One overlay title size (28), the quote as a caption.
            Text(stringResource(title), style = ChaseomUi.title, color = c.textPrimary)
            Body(stringResource(R.string.n_body))
            Text(stringResource(R.string.manual_quote) + " — " + stringResource(R.string.manual_source),
                style = ChaseomUi.caption, color = c.textSecondary)
            if (policy.idlingNotice) Body(stringResource(R.string.n_body_reduced), strong = true)
            BigButton(stringResource(R.string.n_ok), { actions.acknowledgeNotice() }, size = ButtonSize.Large,
                kind = ButtonKind.Filled, modifier = Modifier.fillMaxWidth())
        }
    }
}

// ---- E: leave receipt. No total / verdict line; rear seat always grey; "check" line always. ----

@Composable
fun ReceiptOverlay(r: Receipt, policy: ModePolicy, actions: StayActions, ui: app.chaseom.stay.StayUi? = null) {
    val c = Pleos.colors
    val k = cui()
    val size = uiSize()
    Scrim(light = true) {
        // 800 dp sheet (Small: window width minus margins), radius 28, padding 32.
        // The sheet edge is a soft shadow (the scrim is only 25 %).
        Column(
            Modifier.widthIn(max = ChaseomUi.receiptWidthLarge).padding(vertical = 24.dp, horizontal = ChaseomUi.margin(size))
                .pleosShadow(2, 28.dp, enabled = !app.chaseom.ui.theme.isNight()) // Pleos Level 2 shadow
                .clip(ChaseomUi.rContainer).background(k.surface).padding(ChaseomUi.block).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(ChaseomUi.line),
        ) {
            // No island picture in the sheet (the same island stays visible behind the light scrim).
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.e_title), style = ChaseomUi.title.copy(fontSize = 32.sp, lineHeight = 40.sp), color = k.text1, modifier = Modifier.weight(1f))
                Text(SimpleDateFormat("HH:mm", Locale.KOREA).format(Date(remember(r) { System.currentTimeMillis() })) /* wall time for display only; engine times are monotonic */, style = ChaseomUi.body, color = k.text3)
            }
            Column {
                // No line under the last row; the one line above the check line closes the table.
                r.rows.forEachIndexed { i, row -> ReceiptLine(row, size, last = i == r.rows.lastIndex && r.rearCheckAlways) }
            }
            if (r.rearCheckAlways) {
                // The always-last check line is the white warning part (the one thing the app
                // cannot check), 16 dp under the table. Text stays 30 sp SemiBold
                // and wraps on a narrow window instead of shrinking.
                Spacer(Modifier.height(ChaseomUi.related - ChaseomUi.line))
                WhiteWarn(androidx.compose.ui.text.AnnotatedString(stringResource(R.string.e_rear_always)), Modifier.fillMaxWidth(),
                    style = ChaseomUi.title.copy(fontSize = 30.sp, lineHeight = 38.sp))
            }
            Spacer(Modifier.height(ChaseomUi.line))
            // Closing the receipt is the sheet's one action -> navy primary, 64 dp, full width.
            Box(
                Modifier.fillMaxWidth().heightIn(min = ChaseomUi.touchMin).pressable(shape = ChaseomUi.rPill) { actions.closeReceipt() },
                contentAlignment = Alignment.Center,
            ) {
                Box(Modifier.fillMaxWidth().height(ChaseomUi.buttonHeight).clip(ChaseomUi.rPill).background(k.primary), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.close), style = ChaseomUi.title, color = k.onPrimary)
                }
            }
        }
    }
}

@Composable
private fun ReceiptLine(row: ReceiptRow, size: ChaseomUi.Size, last: Boolean = false) {
    val c = Pleos.colors
    val sep = stringResource(R.string.list_sep)
    val (label, value) = when (row) {
        is ReceiptRow.Stayed -> stringResource(R.string.e_stay) to duration(row.minutes)
        is ReceiptRow.Energy -> stringResource(R.string.e_energy) to when (row.kind) {
            app.chaseom.stay.EnergyKind.CHARGED -> stringResource(R.string.e_energy_charging) // never "0.0kWh"
            app.chaseom.stay.EnergyKind.SMALL -> stringResource(R.string.e_energy_small)
            app.chaseom.stay.EnergyKind.IMPLAUSIBLE -> stringResource(R.string.e_energy_implausible)
            app.chaseom.stay.EnergyKind.VALUE -> stringResource(R.string.e_energy_val, row.kwh)
        }
        is ReceiptRow.Keep -> stringResource(R.string.e_keep) to
            (row.coveredKm?.let { stringResource(R.string.e_keep_low, row.km, it) } ?: stringResource(R.string.e_keep_ok, row.km))
        is ReceiptRow.Windows -> stringResource(R.string.e_window) to
            (if (row.open.isEmpty()) stringResource(if (row.known) R.string.e_window_closed else R.string.e_unknown)
            else stringResource(R.string.e_window_open, row.open.map { windowName(it) }.joinToString(sep)))
        is ReceiptRow.Light -> stringResource(R.string.e_light) to stringResource(
            when (row.on) { true -> R.string.e_on; false -> R.string.e_off; null -> R.string.e_unknown },
        )
        is ReceiptRow.ChargePort -> stringResource(R.string.e_port) to stringResource(
            when (row.open) { true -> R.string.e_port_open; false -> R.string.e_port_closed; null -> R.string.e_unknown },
        )
        is ReceiptRow.Rear -> stringResource(R.string.e_rear) to
            stringResource(when (row.detected) { true -> R.string.e_rear_detected; false -> R.string.e_rear_not_detected; null -> R.string.e_unknown })
    }
    val grey = row is ReceiptRow.Rear && row.detected != true // detected: dot + normal ink (F6); never green
    // Rows 56 dp with a 1 dp line, label Regular / value Medium 26, label column 224 (Small 168).
    // The attention dot stays (an open window etc.) in the sun amber: warning colours belong to banners only.
    val k = cui()
    Column {
        Row(Modifier.heightIn(min = ChaseomUi.receiptRow), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(12.dp).clip(ChaseomUi.rPill).background(if (row.dot) ChaseomUi.amber else Color.Transparent))
            Spacer(Modifier.width(ChaseomUi.line))
            Text(label, style = ChaseomUi.body, color = if (grey) k.text3 else k.text2, modifier = Modifier.width(ChaseomUi.receiptLabelCol(size)))
            Text(value, style = ChaseomUi.bodyValue, color = if (grey) k.text3 else k.text1)
        }
        // Rows are divided by their spacing only, like the Pleos settings list (no lines).
    }
}
