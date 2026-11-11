package app.chaseom.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pleos design-token values, pinned so they cannot drift back. */
class PleosTokensTest {
    @Test fun pressedTargetsShrinkTo95Percent() = assertEquals(0.95f, PRESSED_SCALE, 0f) // pressed state scale

    @Test fun heaviestWeightIsBold() { // no ExtraBold anywhere in the app's own roles
        val all = listOf(
            ChaseomUi.hero(ChaseomUi.Size.Large), ChaseomUi.hero(ChaseomUi.Size.Small),
            ChaseomUi.heroSentence(ChaseomUi.Size.Large), ChaseomUi.onboardingTitle(ChaseomUi.Size.Large),
            ChaseomUi.onboardingTitle(ChaseomUi.Size.Small), ChaseomUi.glanceTitle,
        )
        all.forEach { assertEquals(FontWeight.Bold, it.fontWeight) }
        val rest = listOf(ChaseomUi.title, ChaseomUi.status, ChaseomUi.body, ChaseomUi.bodyValue, ChaseomUi.caption,
            ChaseomUi.banner, ChaseomUi.heroTail(ChaseomUi.Size.Large), ChaseomUi.estimate(ChaseomUi.Size.Large))
        rest.forEach { assertTrue((it.fontWeight ?: FontWeight.Normal) < FontWeight.Bold) }
    }

    @Test fun sizesUnchangedByTheRoleGrouping() { // grouping changed weights only
        assertEquals(72f, ChaseomUi.hero(ChaseomUi.Size.Large).fontSize.value, 0f)
        assertEquals(28f, ChaseomUi.title.fontSize.value, 0f)
        assertEquals(26f, ChaseomUi.body.fontSize.value, 0f)
        assertEquals(24f, ChaseomUi.caption.fontSize.value, 0f)
    }

    @Test fun nightBackgroundIsNearBlack() = assertEquals(Color(0xFF0B0C0E), ChaseomUi.Night.bg)

    // The K1 card edge on the night page is at least 3:1.
    @Test fun nightCardEdgeHasThreeToOne() {
        fun lum(c: Color): Double {
            fun ch(v: Float) = if (v <= 0.03928f) v / 12.92 else Math.pow((v + 0.055) / 1.055, 2.4)
            return 0.2126 * ch(c.red) + 0.7152 * ch(c.green) + 0.0722 * ch(c.blue)
        }
        val ratio = (lum(app.chaseom.ui.scenes.NIGHT_CARD_EDGE) + 0.05) / (lum(ChaseomUi.Night.bg) + 0.05)
        assertTrue("ratio $ratio", ratio >= 3.0)
    }
}
