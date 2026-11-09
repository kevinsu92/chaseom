package app.chaseom.ui.scenes

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 04_design art_v3 layers_h1: every state the island can show has its layer in both window sets. */
class H1LayersTest {
    private val expected = buildSet {
        addAll(listOf("base", "boat_l1", "k2_l1")) // v1.9: no sheen
        (0..2).forEach { add("trees_w$it") }
        listOf("S", "M", "L").forEach { add("sun_$it") }
        (1..2).forEach { add("wind_w$it") }
        (0..6).forEach { add("buoys_n${it}_l1") }
        listOf("wet", "water", "refl", "surf", "ticks").forEach { n -> (1..5).forEach { add("${n}_l$it") } }
    }

    @Test fun bothSetsHaveEveryLayer() {
        assertEquals(43, expected.size)
        assertEquals(expected, H1Layers.L.keys)
        assertEquals(expected, H1Layers.S.keys)
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
}
