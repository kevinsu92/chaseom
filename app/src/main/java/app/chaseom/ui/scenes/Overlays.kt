package app.chaseom.ui.scenes

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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Window-wide scrim that swallows touches to the scene below. */
@Composable
private fun Scrim(content: @Composable () -> Unit) {
    Box(
        Modifier.fillMaxSize().background(Pleos.colors.scrim)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
        contentAlignment = Alignment.Center,
    ) { content() }
}

// ---- N1 / N2 / N3: notice card. Informs only (QA S4-a): no detection/prevention wording. ------

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
            Text(stringResource(title), style = PleosType.titleLarge, color = c.textPrimary)
            Body(stringResource(R.string.n_body))
            Text(stringResource(R.string.manual_quote) + " — " + stringResource(R.string.manual_source),
                style = PleosType.bodyMedium, color = c.textSecondary)
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
    Scrim {
        Column(
            Modifier.width(760.dp).padding(vertical = PleosDimens.space24).clip(RoundedCornerShape(PleosDimens.radiusCard))
                .background(c.backgroundPopup).padding(PleosDimens.space40).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(PleosDimens.space16),
        ) {
            // HANDOFF 10.4: pier + boat + plank still picture on top of the receipt only. The island itself
            // never reacts to the door; no people, no check marks.
            ui?.let { ReceiptHeaderPicture(it) }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.e_title), style = PleosType.titleLarge, color = c.textPrimary, modifier = Modifier.weight(1f))
                Text(SimpleDateFormat("HH:mm", Locale.KOREA).format(Date(remember(r) { System.currentTimeMillis() })) /* wall time for display only; engine times are monotonic */, style = PleosType.bodyLarge, color = c.textSecondary)
            }
            Divider()
            r.rows.forEach { row ->
                if (row is ReceiptRow.Windows) Divider()
                ReceiptLine(row)
            }
            Box(Modifier.fillMaxWidth().height(4.dp).background(c.divider))
            if (r.rearCheckAlways) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(painterResource(R.drawable.ic_eye), null, tint = c.textPrimary, modifier = Modifier.size(48.dp))
                    Spacer(Modifier.width(PleosDimens.space16))
                    Text(stringResource(R.string.e_rear_always), style = PleosType.titleMedium, color = c.textPrimary)
                }
            }
            Spacer(Modifier.height(PleosDimens.space8))
            BigButton(stringResource(R.string.close), { actions.closeReceipt() }, size = ButtonSize.Large,
                kind = ButtonKind.Filled, modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun Divider() = Box(Modifier.fillMaxWidth().height(2.dp).background(Pleos.colors.divider))

@Composable
private fun ReceiptLine(row: ReceiptRow) {
    val c = Pleos.colors
    val sep = stringResource(R.string.list_sep)
    val (label, value) = when (row) {
        is ReceiptRow.Stayed -> stringResource(R.string.e_stay) to duration(row.minutes)
        is ReceiptRow.Energy -> stringResource(R.string.e_energy) to when (row.kind) {
            app.chaseom.stay.EnergyKind.CHARGED -> stringResource(R.string.e_energy_charging) // QA E4-3: never "0.0kWh"
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
            stringResource(if (row.detected) R.string.e_rear_detected else R.string.e_rear_not_detected)
    }
    val grey = row is ReceiptRow.Rear // condition 4: neutral grey either way, never green
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(16.dp).clip(RoundedCornerShape(50)).background(if (row.dot) c.warning else Color.Transparent))
        Spacer(Modifier.width(PleosDimens.space16))
        Text(label, style = PleosType.bodyLarge, color = if (grey) c.textTertiary else c.textSecondary, modifier = Modifier.width(200.dp))
        Text(value, style = PleosType.bodyLargeStrong, color = if (grey) c.textTertiary else c.textPrimary)
    }
}
