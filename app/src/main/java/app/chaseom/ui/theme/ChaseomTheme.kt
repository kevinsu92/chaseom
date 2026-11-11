package app.chaseom.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo

/** Colour roles for the current theme. */
@Composable
fun cui(): ChaseomUi.Colors = if (isSystemInDarkTheme()) ChaseomUi.Night else ChaseomUi.Day

/** Large = window >= 1200 dp, Small = the 841 dp 1:1 split (same rule as the island layer sets). */
@Composable
fun uiSize(): ChaseomUi.Size {
    val w = with(LocalDensity.current) { LocalWindowInfo.current.containerSize.width.toDp().value }
    return uiSizeFor(w)
}

fun uiSizeFor(widthDp: Float): ChaseomUi.Size = if (widthDp >= 1200f) ChaseomUi.Size.Large else ChaseomUi.Size.Small
