package app.chaseom.ui.theme

// Reference implementation of 04_design/COMPONENTS.md. Engineering may restyle internals
// but must keep sizes, colors and the driving rules (DR-xx in DESIGN_SYSTEM.md).

import android.view.HapticFeedbackConstants
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/* ------------------------------------------------------------- BigButton */

enum class ButtonSize(val height: Dp, val minWidth: Dp, val icon: Dp) {
    /** Allowed while driving (T-2). */
    Large(PleosDimens.buttonLarge, PleosDimens.touchDrivingMinWidth, PleosDimens.iconInButton),
    /** Parked screens. */
    Medium(PleosDimens.buttonMedium, 120.dp, 32.dp),
}

enum class ButtonKind { Primary, Filled, Basic }

@Composable
fun BigButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: ButtonSize = ButtonSize.Large,
    kind: ButtonKind = ButtonKind.Filled,
    prefixIcon: Painter? = null,
    enabled: Boolean = true,
    loading: Boolean = false,
) {
    val c = Pleos.colors
    val view = LocalView.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val (bg, fg) = when {
        !enabled -> c.buttonFilledDisabled to c.textQuaternary
        kind == ButtonKind.Primary -> c.positive to (if (c.isNight) c.onStatus else PleosStatic.Light100)
        kind == ButtonKind.Filled -> (if (pressed) c.buttonFilledPressed else c.buttonFilledEnabled) to c.onButtonFilled
        else -> (if (pressed) c.buttonBasicPressed else c.buttonBasicEnabled) to c.textPrimary
    }
    Row(
        modifier = modifier
            .defaultMinSize(minWidth = size.minWidth, minHeight = size.height)
            .height(size.height)
            .clip(RoundedCornerShape(50))
            .background(bg)
            .clickable(
                interactionSource = interaction,
                indication = null, // pressed color above is the feedback (<= 250ms, no ripple loop)
                enabled = enabled && !loading,
                role = Role.Button,
            ) {
                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                onClick()
            }
            .padding(horizontal = PleosDimens.space32),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        if (loading) {
            Text("…", style = PleosType.titleSmall, color = fg) // static, no looping spinner while driving
            return@Row
        }
        if (prefixIcon != null) {
            Icon(prefixIcon, contentDescription = null, tint = fg, modifier = Modifier.size(size.icon))
            Spacer(Modifier.width(PleosDimens.space12))
        }
        Text(
            label,
            style = if (size == ButtonSize.Large) PleosType.titleSmall else PleosType.bodyLargeStrong,
            color = fg, maxLines = 1, overflow = TextOverflow.Clip,
        )
    }
}

/* ----------------------------------------------------------------- Card */

@Composable
fun InfoCard(
    title: String,
    modifier: Modifier = Modifier,
    value: String? = null,
    supporting: String? = null,
    icon: Painter? = null,
    onClick: (() -> Unit)? = null,
) {
    val c = Pleos.colors
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val clickMod = if (onClick != null) {
        Modifier.defaultMinSize(minHeight = PleosDimens.touchDriving)
            .clickable(interactionSource = interaction, indication = null, role = Role.Button, onClick = onClick)
    } else Modifier
    Row(
        modifier
            .clip(RoundedCornerShape(PleosDimens.radiusCard))
            .background(if (pressed) c.surfaceCardPressed else c.surfaceCard)
            .then(clickMod)
            .padding(PleosDimens.space24),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, null, tint = c.textPrimary, modifier = Modifier.size(PleosDimens.iconGrid))
            Spacer(Modifier.width(PleosDimens.space16))
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = PleosType.titleMedium, color = c.textPrimary, maxLines = 1)
            if (value != null) Text(value, style = PleosType.headlineSmall, color = c.textPrimary, maxLines = 1)
            if (supporting != null) Text(supporting, style = PleosType.labelMedium, color = c.textSecondary, maxLines = 1)
        }
    }
}

/* --------------------------------------------------------- Glance widget */

enum class GlanceState { Idle, OnTime, Missed, NotRecording }

/**
 * Non-interactive driving widget (DR-01, DR-10). One key value + max 2 supporting lines.
 * [statusIcon] is drawn on the filled circle; [label] <= 10 chars, [supporting] <= 20 chars.
 */
@Composable
fun GlanceWidget(
    state: GlanceState,
    statusIcon: Painter,
    value: String,
    label: String,
    modifier: Modifier = Modifier,
    supporting: String? = null,
    dotSize: Dp = 96.dp,
) {
    val c = Pleos.colors
    val target = when (state) {
        GlanceState.OnTime -> c.active
        GlanceState.Missed, GlanceState.NotRecording -> c.neutralState
        GlanceState.Idle -> Color.Transparent
    }
    val fill by animateColorAsState(target, PleosMotion.transition(), label = "glanceFill")
    val onFill = when (state) {
        GlanceState.OnTime -> c.onStatus
        GlanceState.Idle -> c.textPrimary
        else -> c.onNeutral
    }
    Row(
        modifier.semantics(mergeDescendants = true) { contentDescription = "$label $value ${supporting.orEmpty()}" },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(dotSize).clip(CircleShape).background(fill)
                .then(if (state == GlanceState.Idle) Modifier.border(2.dp, c.divider, CircleShape) else Modifier),
            contentAlignment = Alignment.Center,
        ) { Icon(statusIcon, null, tint = onFill, modifier = Modifier.size(PleosDimens.iconGrid)) }
        Spacer(Modifier.width(PleosDimens.space24))
        Column {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(value, style = PleosType.headlineLarge, color = c.textPrimary, maxLines = 1)
                Spacer(Modifier.width(PleosDimens.space8))
                Text(label, style = PleosType.labelMedium, color = c.textSecondary, maxLines = 1,
                    modifier = Modifier.padding(bottom = 12.dp))
            }
            if (supporting != null) Text(supporting, style = PleosType.bodyLarge, color = c.textSecondary, maxLines = 1)
        }
    }
}

/* ---------------------------------------------------------- Alert banner */

enum class AlertLevel { Info, Caution, Danger }

/** Driving-related alerts only (DR-12). Auto hides after 6s while driving (no close button). */
@Composable
fun AlertBanner(
    text: String,
    icon: Painter,
    level: AlertLevel,
    driving: Boolean,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = Pleos.colors
    val bg = when (level) { AlertLevel.Info -> c.positive; AlertLevel.Caution -> c.warning; AlertLevel.Danger -> c.negative }
    if (driving) LaunchedEffect(text) { delay(6_000); onDismiss() }
    Row(
        modifier.fillMaxWidth().height(PleosDimens.touchDriving)
            .clip(RoundedCornerShape(PleosDimens.radiusCard)).background(bg)
            .padding(horizontal = PleosDimens.space24),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = c.onStatus, modifier = Modifier.size(PleosDimens.iconGrid))
        Spacer(Modifier.width(PleosDimens.space16))
        Text(text, style = PleosType.bodyLargeStrong, color = c.onStatus, maxLines = 1, modifier = Modifier.weight(1f))
        if (!driving) {
            Box(
                Modifier.size(PleosDimens.touchMin).clickable(role = Role.Button, onClick = onDismiss),
                contentAlignment = Alignment.Center,
            ) { Text("✕", style = PleosType.titleSmall, color = c.onStatus) }
        }
    }
}

/* -------------------------------------------------------------- Progress */

/** Static value bar (no animation). [fraction] 0..1. */
@Composable
fun ValueBar(fraction: Float, color: Color, modifier: Modifier = Modifier) {
    val c = Pleos.colors
    Box(modifier.fillMaxWidth().height(PleosDimens.progressBarHeight).clip(RoundedCornerShape(8.dp)).background(c.divider)) {
        Box(Modifier.fillMaxWidth(fraction.coerceIn(0f, 1f)).height(PleosDimens.progressBarHeight)
            .clip(RoundedCornerShape(8.dp)).background(color))
    }
}
