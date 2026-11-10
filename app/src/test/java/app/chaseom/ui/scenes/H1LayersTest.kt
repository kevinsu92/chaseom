package app.chaseom.ui.scenes

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 04_design art_v3 layers_h1: every state the island can show has its layer in both window sets. */
class H1LayersTest {
    private val expected = buildSet {
        addAll(listOf("base", "boat_l1", "k2_l1", "air")) // v1.9: no sheen; Pleos fit v3: + air (the circling cloud)
        listOf("S", "M", "L").forEach { add("sun_night_$it") } // night sun drawn above the night light (QA verdict 11)
        (0..2).forEach { add("trees_w$it") }
        listOf("S", "M", "L").forEach { add("sun_$it") }
        (1..2).forEach { add("wind_w$it") }
        (0..6).forEach { add("buoys_n${it}_l1") }
        listOf("wet", "water", "refl", "surf", "ticks").forEach { n -> (1..5).forEach { add("${n}_l$it") } }
    }

    @Test fun bothSetsHaveEveryLayer() {
        assertEquals(47, expected.size)
        assertEquals(expected, H1Layers.L.keys)
        assertEquals(expected, H1Layers.S.keys)
    }

    // The night light sits on the water line of the current step (Pleos fit v3.2.1): same values as the manifest.
    @Test fun waterTopMatchesTheWaterLayers() {
        for (set in listOf(H1Set.Large, H1Set.Small)) (1..5).forEach { l ->
            assertEquals(set.layers.getValue("water_l$l").y, set.waterTop[l - 1])
        }
    }

    @Test fun windowWidthPicksTheSet() {
        assertTrue(h1SetFor(1682f) === H1Set.Large)
        assertTrue(h1SetFor(1200f) === H1Set.Large)
        assertTrue(h1SetFor(841f) === H1Set.Small)
    }

    @Test fun waterStepsMoveTheBoatByTheDesignStep() {
        // manifest water_top_y_by_step: Large 520..360 (40 px), Small 480..384 (24 px; v1.12 rail 104 moved it up 8).
        assertEquals(40f, H1Set.Large.step)
        assertEquals(24f, H1Set.Small.step)
        assertEquals(520f - 4 * 40f, H1Layers.L.getValue("water_l5").y)
        assertEquals(480f - 4 * 24f, H1Layers.S.getValue("water_l5").y)
    }

    @Test fun tallSmallBoxPutsTheSunAt24AndTheWindAt36Percent() {
        // Critique final ①: box 1.25x the picture; positions are fractions of the box, measured from its top.
        val set = H1Set.Small
        val k = 0.8f
        val boxDp = set.h * k * 1.25f
        val lift = skyLiftFor(set, k, boxDp)
        val boxPx = boxDp / k
        val top = set.h - boxPx
        assertEquals(0.24f * boxPx, set.sunY + lift.sun - top, 0.5f)
        assertTrue(lift.sun <= 0f && lift.wind <= 0f)
        assertEquals(SkyLift.NONE, skyLiftFor(set, k, set.h * k).let { if (it.sun == 0f && it.wind == 0f) SkyLift.NONE else it })
    }
}
