package app.chaseom.stay

import app.chaseom.cabin.CabinState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Weather island rules: what the picture shows, what never drives it, and its motion limits. */
class IslandTest {

    private val ev = CabinState(
        connected = true, socPercent = 62, socReadable = true, batteryCapacityKwh = 77.4f, fuelTypes = listOf(10),
        hvacPowerOn = true, acOn = false, fanSpeed = 2, driverSetTempC = 26f, recirculationOn = false, driverSeatHeat = 0,
        driverDoorOpen = false, windows = mapOf("driver" to 0),
    )
    private val settings = StaySettings(30, "work")

    private fun ui(e: StayEngine, t: Long, c: CabinState = ev, parked: Boolean? = true) = e.update(parked, c, settings, t, 12)
    private fun st(c: CabinState = ev) = Island.state(ui(StayEngine(), 0, c), 0)!!

    @Test fun dayLengthCappedAtThreeHours() = assertEquals(1f, st().dayLength, 0f)

    @Test fun lowSocShowsPartialDay() = assertTrue(st(ev.copy(socPercent = 12)).dayLength in 0.01f..0.99f)

    @Test fun staticByDefaultTransitionsOnlyAfterAChange() {
        val e = StayEngine()
        assertFalse(Island.state(ui(e, 0), 0)!!.animating)
        val u = ui(e, 10_000, ev.copy(driverSetTempC = 20f))
        assertTrue(Island.state(u, 10_100)!!.animating)
        assertFalse(Island.state(u, 10_000 + Island.EMPHASIS_MS + 1)!!.animating)
        assertTrue(Island.MOTION_MS <= Island.EMPHASIS_MS && Island.EMPHASIS_MS <= 2_000L) // one-shot
    }

    @Test fun frozenUnderNoticeOrReceipt() {
        val e = StayEngine(); ui(e, 0)
        val n = ui(e, 60 * 60_000L, ev.copy(fanSpeed = 7))
        assertTrue(n.notice != null)
        assertFalse(Island.state(n, 60 * 60_000L + 100)!!.animating)
        e.acknowledgeNotice(60 * 60_000L)
        ui(e, 60 * 60_000L + 1000)
        val r = ui(e, 60 * 60_000L + 2000, ev.copy(fanSpeed = 2, driverDoorOpen = true))
        assertTrue(r.receipt != null)
        assertFalse(Island.state(r, 60 * 60_000L + 2100)!!.animating)
    }

    // Design state table (S0..S5, S11), ported from island_state.js.
    @Test fun climateInputsMapToDesignStates() {
        val s0 = st(ev.copy(driverSetTempC = 22f, fanSpeed = 3))
        assertEquals(IslandSun.M, s0.sun); assertEquals(1, s0.wind)
        assertEquals(listOf("is_sky", "is_sun_m", "is_waves_1", "is_island", "is_palm_1", "is_rock_l", "is_rock_r",
            "is_lighthouse", "is_pier", "is_boat", "is_rope", "is_gull"), Island.layers(s0).map { it.id })
        val s2 = st(ev.copy(driverSetTempC = 26f, fanSpeed = 7))
        assertEquals(IslandSun.L, s2.sun); assertEquals(2, s2.wind)
        assertEquals(IslandSun.S, st(ev.copy(driverSetTempC = 19.5f)).sun)
        assertEquals(IslandSun.M, st(ev.copy(driverSetTempC = 24f)).sun)
        assertEquals(IslandSun.L, st(ev.copy(driverSetTempC = 24.5f)).sun)
        assertEquals(0, st(ev.copy(fanSpeed = 2)).wind)
        assertEquals(1, st(ev.copy(fanSpeed = 5)).wind)
        val s4 = Island.layers(Island.state(ui(StayEngine(), 0, ev.copy(driverSeatHeat = 3, passengerSeatHeat = 1)), 0)!!)
        assertEquals(1f, s4.first { it.id == "is_rock_l_warm" }.alpha, 0f)
        assertEquals(0.45f, s4.first { it.id == "is_rock_r_warm" }.alpha, 0f)
        assertEquals(0.7f, Island.layers(st(ev.copy(driverSeatHeat = 2))).first { it.id == "is_rock_l_warm" }.alpha, 0f)
        val s5 = Island.layers(st(ev.copy(acOn = true, recirculationOn = true))).map { it.id }
        assertTrue(s5.containsAll(listOf("is_cool", "is_cloud_recirc")))
        // The boat is always there and is the only picture of tomorrow's share (no grey sun).
        assertTrue(Island.layers(st(ev.copy(socPercent = 8))).any { it.id == "is_boat" })
    }

    // The old vector island has no rain, moving gull or lamp. Far rain lives only in the H1 picture and only for the
    // AUTO wiper actually wiping (see StayEngineTest.rainOnlyForAutoWiperActuallyWiping); manual wiping, turn
    // signals and the cabin light never drive the picture.
    @Test fun noManualWiperTurnSignalOrLampInput() {
        val ids = Island.layers(st()).map { it.id }
        assertFalse(ids.any { it.startsWith("is_rain") || it == "is_cloud_rain" || it == "is_lighthouse_lamp" })
        assertEquals(1, ids.count { it == "is_gull" })
        assertFalse(IslandInfo.of(ui(StayEngine(), 0, ev.copy(wiperAuto = false, wiperOn = true)))!!.rain)
    }

    @Test fun passengerHeatOpensTransitionWindow() {
        val e = StayEngine()
        ui(e, 0, ev.copy(passengerSeatHeat = 0))
        val u = ui(e, 30_000, ev.copy(passengerSeatHeat = 2))
        assertEquals(30_000L, u.lastClimateChangeMs)
        assertTrue(Island.state(u, 30_100)!!.animating)
        assertNull(u.toast) // island only, no dial toast
    }

    @Test fun climateOffIsStillDayFullBandNoWind() {
        val s = st(ev.copy(hvacPowerOn = false, fanSpeed = 7, acOn = true, recirculationOn = true))
        assertEquals(IslandSun.S, s.sun); assertEquals(0, s.wind); assertFalse(s.cool); assertFalse(s.cloud)
        assertEquals(1f, s.dayLength, 0f)
    }

    @Test fun belowKeepEmptiesTheDayBandButNeverChangesTheBoatOrSun() {
        assertFalse(st().alert)
        val below = st(ev.copy(socPercent = 8))
        assertTrue(below.alert); assertEquals(0f, below.dayLength, 0f)
        assertEquals(st().sun, below.sun) // sun follows the set temperature only
        // Same boat layer, same alpha, whatever the battery: no sinking or filling picture.
        val boat = { c: CabinState -> Island.layers(st(c)).single { it.id == "is_boat" } }
        assertEquals(boat(ev), boat(ev.copy(socPercent = 8)))
        assertTrue("is_pier" in Island.STATIC_LAYERS)
    }

    // Amber crate outline near (K1) and below (K2) tomorrow's share; the picture of the boat stays.
    @Test fun crateOutlineOnlyNearOrBelowKeep() {
        assertFalse(Island.layers(st()).any { it.id == "is_crate_mark" })
        val near = Island.state(ui(StayEngine(), 0, ev.copy(socPercent = 10)), 0)!!
        assertTrue(near.near)
        assertTrue(Island.layers(near).any { it.id == "is_crate_mark" })
        assertTrue(Island.layers(st(ev.copy(socPercent = 8))).any { it.id == "is_crate_mark" })
    }

    // Moor after D/R, or after >= 3 s away from P; never on P->N->P or app start.
    @Test fun mooringOnlyWhenTheCarCameBack() {
        assertTrue(Island.shouldMoor(500L, wasDrivingOrReverse = true))
        assertTrue(Island.shouldMoor(3_000L, wasDrivingOrReverse = false))
        assertFalse(Island.shouldMoor(1_500L, wasDrivingOrReverse = false)) // P -> N -> P
        assertFalse(Island.shouldMoor(null, wasDrivingOrReverse = true)) // app (re)start: end picture
        assertTrue(Island.MOOR_MS <= 1_600L && Island.MOOR_SLIDE_MS + Island.MOOR_ROPE_MS + Island.MOOR_BOB_MS == 1_600)
        assertTrue(Island.SWAY_DEBOUNCE_MS + Island.SWAY_MS <= 1_600L) // inside the 1.6 s window
        assertFalse(Island.SWAY_ENABLED) // the bob after climate changes is not in this release
    }

    @Test fun mooringWindowAndCoverRules() {
        val e = StayEngine()
        val u = ui(e, 10_000).copy(moorAtMs = 10_000)
        assertTrue(Island.state(u, 10_100)!!.mooring)
        assertFalse(Island.state(u, 10_000 + Island.MOOR_MS + Island.MOOR_START_DELAY_MS + 1)!!.mooring) // end picture after the window
        assertFalse(Island.state(u.copy(moorAtMs = null), 10_100)!!.mooring)
        assertFalse(Island.state(u.copy(notice = NoticeKind.HOUR), 10_100)!!.mooring) // notice on top: skip
    }

    // Rear occupancy, windows and doors never change the picture.
    @Test fun rearSeatWindowsAndDoorsAreNotInputs() {
        val base = st()
        assertEquals(base, st(ev.copy(rearLeftOccupied = true, rearRightOccupied = true)))
        assertEquals(base, st(ev.copy(rearLeftOccupied = false, rearRightOccupied = false)))
        assertEquals(base, st(ev.copy(windows = mapOf("driver" to 10, "rear_left" to 5))))
        assertEquals(base, st(ev.copy(doorsOpen = mapOf("trunk" to true))))
    }

    @Test fun noIslandInReducedModeOrWhenNotParked() {
        assertNull(Island.state(ui(StayEngine(), 0, ev.copy(fuelTypes = listOf(1))), 0))
        assertNull(Island.state(ui(StayEngine(), 0, parked = false), 0))
    }

    // The boat starts inside the visible picture on every window size,
    // ends at the pier, never slides more than 360 dp, and the wake is gone in the end picture.
    @Test fun moorSlideStartsInsideThePicture() {
        assertEquals(360f, Island.moorSlideUnits(1900f, 520f), 0.5f) // Full
        assertEquals(360f, Island.moorSlideUnits(1600f, 420f), 0.5f) // Large
        assertEquals(85f, Island.moorSlideUnits(792f, 340f), 1.5f)   // Small (792 x 340 dp)
        assertEquals(0f, Island.moorSlideUnits(500f, 340f), 0f)     // too narrow: no slide, never negative
        for ((w, h) in listOf(1900f to 520f, 1600f to 420f, 792f to 340f, 500f to 340f)) {
            // Too narrow to show the boat moored: no slide at all (end picture), checked by offset 0 below.
            if (Island.moorSlideUnits(w, h) > 0f) assertTrue("$w x $h starts inside", Island.boatRightDp(1f, w, h) <= w)
            assertEquals("$w x $h ends at the pier", 0f, Island.boatOffsetDp(0f, w, h), 0f)
            assertTrue("$w x $h <= 360 dp", Island.boatOffsetDp(1f, w, h) <= 360f)
            assertTrue(Island.boatOffsetDp(1f, w, h) >= 0f)
        }
        assertEquals(0f, Island.boatOffsetDp(1f, 500f, 340f), 0f) // too narrow: no movement
        assertEquals(0f, Island.wakeAlpha(0f), 0f)
        assertEquals(1f, Island.wakeAlpha(1f), 0f)
    }
}
