package app.chaseom.ui.scenes

import app.chaseom.ui.theme.pressable
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.chaseom.R
import app.chaseom.stay.ClimateChange
import app.chaseom.ui.theme.Pleos
import app.chaseom.ui.theme.PleosDimens
import app.chaseom.ui.theme.PleosType
import kotlin.math.abs

// ---- formatting (all wording from strings_stay.xml) ------------------------------------------

@Composable
fun duration(minutes: Int): String {
    val m = minutes.coerceAtLeast(0)
    return if (m >= 60) stringResource(R.string.dur_hm, m / 60, m % 60) else stringResource(R.string.dur_m, m)
}

@Composable
fun signedDuration(minutes: Int): String = (if (minutes >= 0) "+" else "−") + duration(abs(minutes))

fun tempText(c: Float): String = if (c % 1f == 0f) c.toInt().toString() else "%.1f".format(c)

@Composable
fun changeLabel(ch: ClimateChange): String = when (ch) {
    is ClimateChange.Power -> stringResource(if (ch.on) R.string.hvac_on else R.string.hvac_off)
    is ClimateChange.Temp -> stringResource(R.string.hvac_temp, tempText(ch.celsius))
    is ClimateChange.Fan -> stringResource(R.string.hvac_fan, ch.level)
    is ClimateChange.Ac -> stringResource(if (ch.on) R.string.hvac_ac_on else R.string.hvac_ac_off)
    is ClimateChange.Recirc -> stringResource(if (ch.on) R.string.hvac_recirc else R.string.hvac_fresh)
    is ClimateChange.SeatHeat -> stringResource(R.string.seat_heat_driver, ch.level)
}

@Composable
fun labelText(key: String): String = stringResource(
    when (key) {
        "school" -> R.string.s_label_school
        "home" -> R.string.s_label_home
        "pickup" -> R.string.s_label_pickup
        "etc" -> R.string.s_label_etc
        else -> R.string.s_label_work
    },
)

@Composable
fun windowName(key: String): String = stringResource(
    when (key) {
        "passenger" -> R.string.win_passenger
        "rearLeft" -> R.string.win_rear_left
        "rearRight" -> R.string.win_rear_right
        else -> R.string.win_driver
    },
)

// ---- building blocks -------------------------------------------------------------------------

@Composable
fun Card(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Column(
        modifier.fillMaxWidth().clip(RoundedCornerShape(PleosDimens.radiusCard)).background(Pleos.colors.surfaceCard)
            .padding(PleosDimens.space32),
        verticalArrangement = Arrangement.spacedBy(PleosDimens.space12),
    ) { content() }
}

/** 76dp touch target icon button. */
@Composable
fun IconButton76(icon: Int, contentDescription: String, onClick: () -> Unit) {
    val c = Pleos.colors
    Box(
        // A bare icon, no grey circle; the 76 dp touch area stays.
        Modifier.size(PleosDimens.touchMin).clip(RoundedCornerShape(50))
            .pressable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(painterResource(icon), contentDescription, tint = c.textPrimary, modifier = Modifier.size(40.dp))
    }
}

/** Filled status circle with an icon in onStatus colour. */
@Composable
fun StatusIcon(icon: Int, fill: Color, size: Dp = 64.dp) {
    Box(Modifier.size(size).clip(RoundedCornerShape(50)).background(fill), contentAlignment = Alignment.Center) {
        Icon(painterResource(icon), null, tint = Pleos.colors.onStatus, modifier = Modifier.size(size * 0.62f))
    }
}

@Composable
fun Body(text: String, strong: Boolean = false, color: Color = Pleos.colors.textPrimary) =
    Text(text, style = if (strong) PleosType.bodyLargeStrong else PleosType.bodyLarge, color = color)

@Composable
fun Small(text: String) = Text(text, style = PleosType.labelMedium, color = Pleos.colors.textSecondary)

/** Full-width coloured strip with onStatus text (N0 banner, K1/K2). */
@Composable
fun StatusStrip(text: String, fill: Color, height: Dp, trailing: (@Composable () -> Unit)? = null) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(PleosDimens.radiusChip)).background(fill)
            .height(height).padding(horizontal = PleosDimens.space24),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text, color = Pleos.colors.onStatus, modifier = Modifier.weight(1f),
            style = if (height >= 64.dp) PleosType.bodyLargeStrong else PleosType.labelMedium, maxLines = 1,
        )
        trailing?.invoke()
    }
}
