package app.chaseom.ui.theme

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Pleos (required): a pressed touch target shrinks to 95 % and takes the mode's pressed fill
 * (buttonBasicPressed: day #1A131417, night #29FFFFFF), drawn over its own background. One modifier for every
 * button, close and chip; no ripple (the fill is the feedback, <= 100 ms in and out).
 */
fun Modifier.pressable(
    enabled: Boolean = true,
    role: Role = Role.Button,
    shape: Shape = RoundedCornerShape(50),
    onClick: () -> Unit,
): Modifier = composed {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed && enabled) PRESSED_SCALE else 1f, tween(100), label = "press")
    val fill = Pleos.colors.buttonBasicPressed
    this
        .graphicsLayer { scaleX = scale; scaleY = scale }
        .drawWithContent {
            drawContent()
            if (pressed && enabled) drawOutline(shape.createOutline(size, layoutDirection, this), fill)
        }
        .clickable(interactionSource = interaction, indication = null, enabled = enabled, role = role, onClick = onClick)
}

internal const val PRESSED_SCALE = 0.95f

/**
 * Pleos Level 1 / Level 2 shadows drawn as specified (Y offset, blur, alpha), not Compose elevation:
 * Level 1 = Y 6 / Blur 24 / 8 %; Level 2 = Y 8 / Blur 32 / 16 % + Y 2 / Blur 2 / 8 %.
 * Put it before the clip and the background of the surface it belongs to. Day only (night surfaces have none).
 */
fun Modifier.pleosShadow(level: Int, corner: Dp, enabled: Boolean = true): Modifier =
    if (!enabled) this else drawBehind {
        val layers = when (level) {
            1 -> listOf(Triple(6.dp, 24.dp, 0.08f))
            else -> listOf(Triple(8.dp, 32.dp, 0.16f), Triple(2.dp, 2.dp, 0.08f))
        }
        val r = corner.toPx()
        drawIntoCanvas { canvas ->
            for ((y, blur, alpha) in layers) {
                val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                    color = android.graphics.Color.WHITE
                    // Figma blur = CSS blur radius; Android shadow radius is about half of it.
                    setShadowLayer(blur.toPx() / 2f, 0f, y.toPx(), Color.Black.copy(alpha = alpha).toArgb())
                }
                canvas.nativeCanvas.drawRoundRect(0f, 0f, size.width, size.height, r, r, paint)
            }
        }
    }
