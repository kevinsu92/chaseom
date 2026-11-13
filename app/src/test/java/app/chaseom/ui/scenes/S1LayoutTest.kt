package app.chaseom.ui.scenes

import app.chaseom.stay.IslandInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Build G S1 (three always-on symbols) and S2 (buoys and flag at twice their size). */
class S1LayoutTest {
    // Art widths of the layers (drawable-nodpi WebP sizes): six-buoy row and flag, Large / Small.
    private val rowW = mapOf(H1Set.Large to 250f, H1Set.Small to 186f)
    private val flagW = mapOf(H1Set.Large to 40f, H1Set.Small to 34f)

    private fun info(waterStep: Int, k2: Boolean) = IslandInfo(
        sunLevel = 1, buoys = 4, waterStep = waterStep, crateOutline = k2, wind = 2, cool = false, cloud = false,
        heatDrv = 0, heatPas = 0,
    )

    @Test fun waterIsAFixedStripWhateverTheBattery() {
        // Battery is the header's "배터리 n%"; the picture's water never moves.
        for (step in 0 until IslandInfo.WATER_STEPS) assertEquals(S1_LEVEL, H1Pic.of(info(step, false)).level)
    }

    @Test fun pictureEndsTheStripBelowTheLowestWaterLine() {
        for (set in listOf(H1Set.Large, H1Set.Small)) assertEquals(set.waterTop[0] + WATER_STRIP, set.h)
    }

    @Test fun tomorrowsShareOutlineStillReachesThePicture() {
        // K1 / K2 moved from the crate frame to the chip border: the flag must still be carried.
        assertTrue(H1Pic.of(info(2, true)).k2)
    }

    @Test fun doubledBuoyRowAndLastFlagStayInsideTheCanvas() {
        for (set in listOf(H1Set.Large, H1Set.Small)) {
            val row = set.layers.getValue("buoys_n6_l1")
            assertTrue(buoyRowX(set, row.x + rowW.getValue(set)) <= set.w)
            val flag = set.layers.getValue("flag_row_l1")
            val lastFlagRight = flag.x + (app.chaseom.stay.WaitFlags.MAX_SLOT - 1f) * flagStep(set) + flagW.getValue(set)
            assertTrue(buoyRowX(set, lastFlagRight) <= set.w)
            // The row starts where the boat was, right of the dock chip.
            assertTrue(buoyRowX(set, row.x) > set.chipX)
        }
    }

    @Test fun buoysAreTwiceTheirArtSize() {
        val set = H1Set.Large
        val x0 = set.layers.getValue("buoys_n1_l1").x
        assertEquals(2f * flagStep(set), buoyRowX(set, x0 + flagStep(set)) - buoyRowX(set, x0), 0.001f)
    }
}
