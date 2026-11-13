package app.chaseom.ui.scenes

import app.chaseom.stay.IslandInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Build G S1 (three always-on symbols) and S2 (buoys and flag at twice their size), 04_design/S1_SPEC.md v3. */
class S1LayoutTest {
    private val sets = listOf(H1Set.Large, H1Set.Small)
    // Rightmost cloth column of the flag layer (drawable-nodpi WebP, alpha > 128): Large 36, Small 33.
    private val clothRight = mapOf(H1Set.Large to 37, H1Set.Small to 34)
    /** Picture scale on screen: Large 1.0, Small 760 / 960. */
    private val screen = mapOf(H1Set.Large to 1f, H1Set.Small to 760f / 960f)

    private fun info(waterStep: Int, outline: Boolean, wind: Int = 2) = IslandInfo(
        sunLevel = 1, buoys = 4, waterStep = waterStep, crateOutline = outline, wind = wind, cool = false, cloud = false,
        heatDrv = 0, heatPas = 0,
    )

    @Test fun waterAndTreesNeverMove() {
        // Battery is the header's "배터리 n%"; the fan is in the dial toast. Neither moves the picture.
        for (step in 0 until IslandInfo.WATER_STEPS) for (wind in 0..2) {
            val pic = H1Pic.of(info(step, false, wind))
            assertEquals(S1_LEVEL, pic.level)
            assertEquals(0, pic.wind)
        }
        for (set in sets) assertEquals(set.waterTop[S1_LEVEL - 1], set.waterY)
    }

    @Test fun seaStripIs22PxOnScreen() {
        for (set in sets) assertEquals(22f, (set.h - set.waterY) * screen.getValue(set), 0.2f)
        assertEquals(502f, H1Set.Large.h)
        assertEquals(484f, H1Set.Small.h)
    }

    @Test fun pictureIsAlwaysFullScale() {
        for (set in sets) {
            assertEquals(1f, set.zoom)
            assertEquals(1f, set.boxGrow)
        }
    }

    @Test fun tomorrowsShareOutlineStillReachesThePicture() {
        // K1 / K2 moved from the crate frame to the chip border.
        assertTrue(H1Pic.of(info(2, true)).k2)
        assertFalse(H1Pic.of(info(2, false)).k2)
    }

    @Test fun buoysAreTwiceTheCutOutAndBigEnoughToRead() {
        val (lw, lh) = buoySize(H1Set.Large)
        assertEquals(60f, lw); assertEquals(112f, lh)
        val (sw, sh) = buoySize(H1Set.Small)
        assertEquals(48f, sw); assertEquals(90f, sh)
        // UXR C6: buoy body >= 48 px at L, >= 24 px at S (on screen).
        assertTrue(lw * screen.getValue(H1Set.Large) >= 48f)
        assertTrue(sw * screen.getValue(H1Set.Small) >= 24f)
        assertEquals(1054.5f, buoyCenter(H1Set.Large, 1))
        assertEquals(85f, buoyCenter(H1Set.Large, 2) - buoyCenter(H1Set.Large, 1))
        assertEquals(56f, buoyCenter(H1Set.Small, 2) - buoyCenter(H1Set.Small, 1))
    }

    @Test fun sixBuoysAndALastSlotFlagStayInsideWithAMargin() {
        // UXR C7: >= 8 px from the picture's right edge.
        for (set in sets) {
            assertTrue(buoyCenter(set, 6) + buoySize(set).first / 2f <= set.w - 8f)
            val flagLeft = buoyCenter(set, 6) - BUOY_SCALE * (set.poleL + set.poleR) / 2f
            assertTrue(flagLeft + BUOY_SCALE * clothRight.getValue(set) <= set.w - 8f)
            assertTrue(flagTop(set) >= 8f)
        }
    }

    @Test fun clothEndsEightPxAboveTheBuoyHead() {
        for (set in sets) assertEquals(buoyTop(set) - FLAG_GAP, flagTop(set) + BUOY_SCALE * set.clothBottom, 0.001f)
    }

    @Test fun flagStandsInTheSlotHoldingTheChosenTime() {
        assertEquals(2, flagSlotIndex(1.57f)) // 47 min -> second slot
        assertEquals(5, flagSlotIndex(5.0f))  // 2:30 -> fifth slot
        assertEquals(5, flagSlotIndex(4.93f))
        assertEquals(1, flagSlotIndex(0.2f))
        assertEquals(6, flagSlotIndex(6f))
        // A reached flag (rowSlot = min(own, buoys)) never stands past the last buoy, so its buoy still counts (M-5).
        for (buoys in 1..6) assertTrue(flagSlotIndex(minOf(5.9f, buoys.toFloat())) <= buoys)
    }

    private fun fc(outcome: app.chaseom.stay.WaitOutcome, remaining: Int, short: Int = 0) = app.chaseom.stay.WaitForecast(
        app.chaseom.stay.WaitFlag(0L, 0L), remaining, remaining / 180f, outcome, shortMinutes = short)

    @Test fun shortFlagOpenWaterIsTheShortfallNotARoundedSlot() {
        // External S1S2 #6: 2:25 stay (4 buoys), 32 min short -> open water after buoy 4's own slot = 32 min (not 60).
        val p = flagPlace(fc(app.chaseom.stay.WaitOutcome.SHORT, 177, short = 32), buoys = 4)!!
        assertFalse(p.inRow)
        assertEquals(4f + 0.5f + 32f / 30f, p.pos, 0.001f)
        // A few minutes short still leaves >= 0.5 slot of open water (UXR C4): one slot past the last buoy.
        assertEquals(5f, flagPlace(fc(app.chaseom.stay.WaitOutcome.SHORT, 125, short = 4), buoys = 4)!!.pos, 0.001f)
        // Never past slot 6 (inside the picture).
        assertEquals(6f, flagPlace(fc(app.chaseom.stay.WaitOutcome.SHORT, 180, short = 90), buoys = 5)!!.pos, 0.001f)
    }

    @Test fun reachedFlagStandsInTheRowEvenWithoutABuoy() {
        // External S1S2 #7: stay under 30 min (0 buoys), flag 20 min away -> reached, no plate.
        val p = flagPlace(fc(app.chaseom.stay.WaitOutcome.REACHES, 20), buoys = 0)!!
        assertTrue(p.inRow)
        assertEquals(1f, p.pos)
        assertEquals(2f, flagPlace(fc(app.chaseom.stay.WaitOutcome.REACHES, 47), buoys = 5)!!.pos)
        assertEquals(null, flagPlace(fc(app.chaseom.stay.WaitOutcome.BELOW_KEEP, 47), buoys = 0))
    }

    @Test fun flagLineWrapsOnlyBetweenPhrases() {
        // External S1S2 #8: "약 33분 / 짧아요" must not split; only " · " is a break point.
        val line = keepPhrases("오후 7:35 깃발 · 오늘 머물 시간이 깃발보다 약 33분 짧아요 (추정)")
        val parts = line.split(" · ")
        assertEquals(2, parts.size)
        parts.forEach { assertFalse(it.contains(' ')) }
        assertEquals("오후 7:35 깃발 · 오늘 머물 시간이 깃발보다 약 33분 짧아요 (추정)", line.replace(' ', ' '))
    }
}
