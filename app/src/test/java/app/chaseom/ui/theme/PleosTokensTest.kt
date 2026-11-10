package app.chaseom.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** PLEOS_COMPLIANCE fixes that are values, pinned so they cannot drift back. */
class PleosTokensTest {
    @Test fun pressedTargetsShrinkTo95Percent() = assertEquals(0.95f, PRESSED_SCALE, 0f) // I-02

    @Test fun heaviestWeightIsBold() { // T-03 / panel B2: no ExtraBold anywhere in the app's own roles
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

    @Test fun sizesUnchangedByTheRoleGrouping() { // T-07: grouping changed weights only
        assertEquals(72f, ChaseomUi.hero(ChaseomUi.Size.Large).fontSize.value, 0f)
        assertEquals(28f, ChaseomUi.title.fontSize.value, 0f)
        assertEquals(26f, ChaseomUi.body.fontSize.value, 0f)
        assertEquals(24f, ChaseomUi.caption.fontSize.value, 0f)
    }

    @Test fun nightBackgroundIsNearBlack() = assertEquals(Color(0xFF0B0C0E), ChaseomUi.Night.bg)
}
