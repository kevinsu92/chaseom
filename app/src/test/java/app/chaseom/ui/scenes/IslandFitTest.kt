package app.chaseom.ui.scenes

import org.junit.Assert.assertEquals
import org.junit.Test

/** F-3 / F-4: a short space shrinks the whole picture (same aspect, at least 75 %); it is never cropped. */
class IslandFitTest {
    private val natural = 600f
    private val fullW = 1600f
    private val eps = 0.001f

    @Test fun notMeasuredYetKeepsTheNaturalHeight() {
        assertEquals(natural, islandBoxHeight(natural, fill = true, left = null), eps)
        assertEquals(natural, islandBoxHeight(natural, fill = false, left = null), eps)
    }

    @Test fun withoutCardsTheBoxIsExactlyTheHeightLeft() {
        // Build F behaviour for the default screen: fill the height left, full width, more sky above.
        assertEquals(700f, islandBoxHeight(natural, fill = true, left = 700f), eps)
        assertEquals(fullW, islandPicWidth(fullW, natural, 700f), eps)
        // No minimum above the space left (review D1): the box never pushes the flag line off the first screen.
        assertEquals(620f, islandBoxHeight(natural, fill = true, left = 620f), eps)
    }

    @Test fun noJumpWhenTheSpaceCrossesTheNaturalHeight() {
        for (fill in listOf(true, false)) {
            val below = islandBoxHeight(natural, fill, left = natural - 0.5f)
            val at = islandBoxHeight(natural, fill, left = natural)
            assertEquals(0.5f, at - below, eps)
        }
    }

    @Test fun withCardsTheBoxNeverGrowsPastNatural() {
        assertEquals(natural, islandBoxHeight(natural, fill = false, left = 900f), eps)
        assertEquals(fullW, islandPicWidth(fullW, natural, natural), eps)
    }

    @Test fun shortSpaceShrinksTheWholePictureWithTheSameAspect() {
        // F-3 (flag line + swap card) and F-4 (K1 + flag): both modes shrink instead of cropping.
        for (fill in listOf(true, false)) {
            val h = islandBoxHeight(natural, fill, left = 510f)
            assertEquals(510f, h, eps)
            val w = islandPicWidth(fullW, natural, h)
            assertEquals(fullW * 0.85f, w, eps)
            assertEquals(natural / fullW, h / w, eps) // same aspect: nothing is cut off
        }
    }

    @Test fun neverBelowSeventyFivePercent() {
        for (fill in listOf(true, false)) {
            val h = islandBoxHeight(natural, fill, left = 200f)
            assertEquals(natural * MIN_FIT, h, eps)
            assertEquals(fullW * MIN_FIT, islandPicWidth(fullW, natural, h), eps)
        }
    }
}
