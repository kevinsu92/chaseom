package app.chaseom.ui.theme

// Design tokens for the in-vehicle app.
// Source of values: Pleos Connect UI Design Guide (colors / typography / shadow / animation),
// gaps filled from Android for Cars guidance (touch target 76dp).
// Drop-in: copy this file and PleosComponents.kt into app/src/main/java/app/chaseom/ui/theme/
// and delete the old Theme.kt (this file defines PleosAppTheme with the same signature).

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/* ---------------------------------------------------------------- Colors */

/** Pleos grayscale scale. Values flip between day (light) and night (dark). */
@Immutable
data class PleosGrayscale(
    val basic00: Color, val basic50: Color, val basic100: Color, val basic200: Color,
    val basic300: Color, val basic400: Color, val basic500: Color, val basic600: Color,
    val basic700: Color, val basic800: Color, val basic900: Color,
)

private val DayGray = PleosGrayscale(
    basic00 = Color(0xFFFFFFFF), basic50 = Color(0xFFF7F8FA), basic100 = Color(0xFFEDEEF2),
    basic200 = Color(0xFFE0E1E6), basic300 = Color(0xFFC7C9CE), basic400 = Color(0xFFA4A8AE),
    basic500 = Color(0xFF686A72), basic600 = Color(0xFF44464E), basic700 = Color(0xFF313236),
    basic800 = Color(0xFF1D1E21), basic900 = Color(0xFF131417),
)
private val NightGray = PleosGrayscale(
    basic00 = Color(0xFF131417), basic50 = Color(0xFF1D1E21), basic100 = Color(0xFF313236),
    basic200 = Color(0xFF44464E), basic300 = Color(0xFF686A72), basic400 = Color(0xFFA4A8AE),
    basic500 = Color(0xFFC7C9CE), basic600 = Color(0xFFE0E1E6), basic700 = Color(0xFFEDEEF2),
    basic800 = Color(0xFFF7F8FA), basic900 = Color(0xFFFFFFFF),
)

/** Theme-independent colors (use on overlays / colored fills). */
object PleosStatic {
    val Light100 = Color(0xFFFFFFFF)
    val Light200 = Color(0xD6FFFFFF) // 84%
    val Light300 = Color(0xA3FFFFFF) // 64%
    val Light400 = Color(0x52FFFFFF) // 32%
    val Dark100 = Color(0xFF131417)
    val Dark200 = Color(0xD6131417)
    val Dark300 = Color(0xA3131417)
    val Dark400 = Color(0x52131417)
}

/**
 * Semantic tokens actually used by app UI. Contrast rules:
 * - textQuaternary is for disabled only (fails 4.5:1).
 * - status fills (active/warning/negative/brand) carry [onStatus] text, never white text.
 * - [positiveText] is the only colored text allowed, and only in day theme.
 */
@Immutable
data class PleosColors(
    val isNight: Boolean,
    val gray: PleosGrayscale,
    val backgroundPrimary: Color,
    val backgroundSecondary: Color,
    val backgroundPopup: Color,
    val surfaceCard: Color,
    val surfaceCardPressed: Color,
    val divider: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textTertiary: Color,
    val textQuaternary: Color,
    val buttonBasicEnabled: Color,
    val buttonBasicPressed: Color,
    val buttonFilledEnabled: Color,
    val buttonFilledPressed: Color,
    val buttonFilledDisabled: Color,
    val onButtonFilled: Color,
    /** informative_active: on-time / OK. Fill only. */
    val active: Color,
    /** informative_positive: info / primary action fill. */
    val positive: Color,
    /** Colored text allowed only in day (0064FF, 4.9:1). Night falls back to textPrimary. */
    val positiveText: Color,
    /** informative_negative: danger / error. Fill only. */
    val negative: Color,
    /** regulation_yellow: caution. Fill only. */
    val warning: Color,
    /** App brand accent (kkamppak signal amber). Fill only, max 1 use per screen. */
    val brand: Color,
    /** Text/icon on any status fill. */
    val onStatus: Color,
    /** Neutral "missed / no data" state. Never use red for a missed signal (praise tone). */
    val neutralState: Color,
    /** Text/icon on [neutralState]. */
    val onNeutral: Color,
    val scrim: Color,
)

val DayColors = PleosColors(
    isNight = false,
    gray = DayGray,
    backgroundPrimary = DayGray.basic50,
    backgroundSecondary = DayGray.basic00,
    backgroundPopup = DayGray.basic00,
    surfaceCard = DayGray.basic00,
    surfaceCardPressed = DayGray.basic100,
    divider = DayGray.basic200,
    textPrimary = PleosStatic.Dark100,
    textSecondary = PleosStatic.Dark200,
    textTertiary = PleosStatic.Dark300,
    textQuaternary = PleosStatic.Dark400,
    buttonBasicEnabled = Color(0x0D131417),
    buttonBasicPressed = Color(0x1A131417),
    buttonFilledEnabled = DayGray.basic600,
    buttonFilledPressed = DayGray.basic700,
    buttonFilledDisabled = Color(0x0D131417),
    onButtonFilled = PleosStatic.Light100,
    active = Color(0xFF02C265),
    positive = Color(0xFF0064FF),
    positiveText = Color(0xFF0064FF),
    negative = Color(0xFFFE3D16),
    warning = Color(0xFFFFC224),
    brand = Color(0xFFFFB020),
    onStatus = PleosStatic.Dark100,
    neutralState = DayGray.basic300,
    onNeutral = PleosStatic.Dark100,
    scrim = Color(0x99131417),
)

val NightColors = PleosColors(
    isNight = true,
    gray = NightGray,
    backgroundPrimary = NightGray.basic50,
    backgroundSecondary = NightGray.basic00,
    backgroundPopup = NightGray.basic100,
    surfaceCard = NightGray.basic100,
    surfaceCardPressed = NightGray.basic200,
    divider = NightGray.basic200,
    textPrimary = PleosStatic.Light100,
    textSecondary = PleosStatic.Light200,
    textTertiary = PleosStatic.Light300,
    textQuaternary = PleosStatic.Light400,
    buttonBasicEnabled = Color(0x14FFFFFF),
    buttonBasicPressed = Color(0x29FFFFFF),
    buttonFilledEnabled = NightGray.basic600,
    buttonFilledPressed = NightGray.basic500,
    buttonFilledDisabled = Color(0x14FFFFFF),
    onButtonFilled = PleosStatic.Dark100,
    active = Color(0xFF00E074),
    positive = Color(0xFF0082FF),
    positiveText = PleosStatic.Light100,
    negative = Color(0xFFFF4C28),
    warning = Color(0xFFFFC224),
    brand = Color(0xFFFFB020),
    onStatus = PleosStatic.Dark100,
    neutralState = NightGray.basic300,
    onNeutral = PleosStatic.Light100,
    scrim = Color(0xB3000000),
)

/* ------------------------------------------------------------ Typography */

private val Base = TextStyle(fontFamily = FontFamily.Default, fontFeatureSettings = "tnum")

/** Pleos type scale (sp). */
object PleosType {
    val headlineLarge = Base.copy(fontSize = 60.sp, lineHeight = 80.sp, fontWeight = FontWeight.ExtraBold)
    val headlineMedium = Base.copy(fontSize = 56.sp, lineHeight = 72.sp, fontWeight = FontWeight.ExtraBold)
    val headlineSmall = Base.copy(fontSize = 48.sp, lineHeight = 64.sp, fontWeight = FontWeight.Bold)
    val titleLarge = Base.copy(fontSize = 40.sp, lineHeight = 52.sp, fontWeight = FontWeight.Bold)
    val titleMedium = Base.copy(fontSize = 36.sp, lineHeight = 44.sp, fontWeight = FontWeight.Bold)
    val titleSmall = Base.copy(fontSize = 32.sp, lineHeight = 40.sp, fontWeight = FontWeight.Bold)
    val bodyLarge = Base.copy(fontSize = 30.sp, lineHeight = 38.sp, fontWeight = FontWeight.Normal)
    val bodyLargeStrong = bodyLarge.copy(fontWeight = FontWeight.SemiBold)
    val bodyMedium = Base.copy(fontSize = 28.sp, lineHeight = 36.sp, fontWeight = FontWeight.Normal)
    val bodySmall = Base.copy(fontSize = 26.sp, lineHeight = 34.sp, fontWeight = FontWeight.Normal)
    val labelMedium = Base.copy(fontSize = 24.sp, lineHeight = 28.sp, fontWeight = FontWeight.Normal)
    val labelSmall = Base.copy(fontSize = 20.sp, lineHeight = 24.sp, fontWeight = FontWeight.Normal)

    /** Smallest style allowed while driving for primary info (5mm rule). */
    val DrivingPrimaryMin = bodyLarge
    /** Smallest style allowed while driving for secondary info (3mm rule). */
    val DrivingSecondaryMin = labelMedium
}

private val M3Typography = Typography(
    displayLarge = PleosType.headlineLarge,
    displayMedium = PleosType.headlineMedium,
    displaySmall = PleosType.headlineSmall,
    headlineLarge = PleosType.titleLarge,
    headlineMedium = PleosType.titleMedium,
    headlineSmall = PleosType.titleSmall,
    titleLarge = PleosType.titleSmall,
    titleMedium = PleosType.bodyLargeStrong,
    titleSmall = PleosType.bodyMedium,
    bodyLarge = PleosType.bodyLarge,
    bodyMedium = PleosType.bodyMedium,
    bodySmall = PleosType.bodySmall,
    labelLarge = PleosType.bodyMedium,
    labelMedium = PleosType.labelMedium,
    labelSmall = PleosType.labelSmall,
)

/* ------------------------------------------------- Spacing, sizes, shape */

object PleosDimens {
    val unit = 8.dp
    val space4 = 4.dp
    val space8 = 8.dp
    val space12 = 12.dp
    val space16 = 16.dp
    val space24 = 24.dp
    val space32 = 32.dp
    val space40 = 40.dp
    val space48 = 48.dp

    /** Extra inset at top of the window (system panels may overlay). */
    val safeTop = 16.dp
    /** Extra inset at bottom of the window (keep fingers off the GNB). */
    val safeBottom = 24.dp

    /** Window margins by width class (Narrow < 600dp, Medium < 960dp, Wide). */
    val marginNarrow = 24.dp
    val marginMedium = 32.dp
    val marginWide = 40.dp
    val gutterNarrow = 16.dp
    val gutter = 24.dp

    /** Minimum touch target, any state. */
    val touchMin = 76.dp
    /** Minimum height of a button usable while driving. */
    val touchDriving = 96.dp
    val touchDrivingMinWidth = 160.dp
    /** Spacing between targets. */
    val targetGap = 16.dp
    val targetGapDriving = 24.dp

    val buttonLarge = 96.dp
    val buttonMedium = 76.dp
    val iconGrid = 48.dp
    val iconDrivingMin = 48.dp
    val iconParkedMin = 32.dp
    val iconInButton = 40.dp

    val radiusChip = 16.dp
    val radiusCard = 24.dp
    val radiusSheet = 32.dp

    val progressBarHeight = 16.dp
    val progressRingStroke = 16.dp
}

private val M3Shapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(PleosDimens.radiusChip),
    medium = RoundedCornerShape(PleosDimens.radiusCard),
    large = RoundedCornerShape(PleosDimens.radiusCard),
    extraLarge = RoundedCornerShape(PleosDimens.radiusSheet),
)

/* ---------------------------------------------------------------- Motion */

/** No looping animation anywhere; only single state transitions. */
object PleosMotion {
    const val TOUCH_FEEDBACK_MAX_MS = 250
    const val TRANSITION_MS = 200
    const val VALUE_REFRESH_MIN_MS = 1000L
    val Easing = CubicBezierEasing(0.0f, 0.0f, 0.2f, 1.0f) // LinearOutSlowIn
    fun <T> transition() = tween<T>(durationMillis = TRANSITION_MS, easing = Easing)
}

/** Pleos shadow levels (day only; at night separate layers with basic100/200 instead). */
object PleosElevation {
    data class Shadow(val y: Int, val blur: Int, val alpha: Float)
    val Level1 = listOf(Shadow(6, 24, 0.08f))
    val Level2 = listOf(Shadow(8, 32, 0.16f), Shadow(2, 2, 0.08f))
    val Level3 = listOf(Shadow(20, 40, 0.24f), Shadow(2, 4, 0.20f))
}

/* ----------------------------------------------------------------- Theme */

val LocalPleosColors = staticCompositionLocalOf { NightColors }

object Pleos {
    val colors: PleosColors @Composable get() = LocalPleosColors.current
}

/** Follows the vehicle day/night mode (uiMode) and, in round 2, the local clock (NightClock). No in-app theme toggle. */
@Composable
fun PleosAppTheme(night: Boolean = NightClock.isNight(isSystemInDarkTheme(), rememberLocalHour()), content: @Composable () -> Unit) {
    val c = if (night) NightColors else DayColors
    val m3 = if (night) {
        darkColorScheme(
            primary = c.positive, onPrimary = c.onStatus,
            secondary = c.active, onSecondary = c.onStatus,
            tertiary = c.brand, onTertiary = c.onStatus,
            background = c.backgroundPrimary, onBackground = c.textPrimary,
            surface = c.surfaceCard, onSurface = c.textPrimary,
            surfaceVariant = c.backgroundSecondary, onSurfaceVariant = c.textSecondary,
            outline = c.divider, error = c.negative, onError = c.onStatus, scrim = c.scrim,
        )
    } else {
        lightColorScheme(
            primary = c.positive, onPrimary = PleosStatic.Light100,
            secondary = c.active, onSecondary = c.onStatus,
            tertiary = c.brand, onTertiary = c.onStatus,
            background = c.backgroundPrimary, onBackground = c.textPrimary,
            surface = c.surfaceCard, onSurface = c.textPrimary,
            surfaceVariant = c.backgroundSecondary, onSurfaceVariant = c.textSecondary,
            outline = c.divider, error = c.negative, onError = c.onStatus, scrim = c.scrim,
        )
    }
    CompositionLocalProvider(LocalPleosColors provides c, LocalNight provides night) {
        MaterialTheme(colorScheme = m3, typography = M3Typography, shapes = M3Shapes, content = content)
    }
}
