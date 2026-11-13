package app.chaseom.stay

import app.chaseom.cabin.CabinState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.floor

/** H+calib (HQ addendum 7, 03_engineering/calibration.md): per-car correction from the SOC drop during a stay. */
class CalibrationTest {

    private val cap = 77.4f
    private val key = "22C fan3"

    /**
     * Feeds [c] every [stepS] seconds for [seconds], the car really drawing [realKw] from a continuous SOC that the
     * car reports as an integer (floor). Returns the end time.
     */
    private fun Calibration.drain(
        realKw: Float, modelKw: Float, seconds: Int, startSoc: Double = 80.0, t0: Long = 0L, stepS: Int = 10, k: Any = key,
    ): Long {
        var t = t0
        var soc = startSoc
        val pctPerS = realKw / cap * 100.0 / 3600.0
        repeat(seconds / stepS + 1) {
            observe(floor(soc).toInt(), cap, k, modelKw, t)
            soc -= pctPerS * stepS
            t += stepS * 1000L
        }
        return t
    }

    @Test fun constantSocNeverCalibrates() {
        val c = Calibration()
        repeat(3 * 3600) { observe(c, 19, it * 1000L) }
        assertEquals(1f, c.factor)
        assertFalse(c.applied)
        assertEquals(Calibration.Saved(), c.saved)
    }

    private fun observe(c: Calibration, soc: Int, t: Long) = c.observe(soc, cap, key, 1.0f, t)

    // Edge-to-edge windows measure the true ratio even though the SOC is an integer.
    @Test fun higherRealDrawRaisesTheFactor() {
        val c = Calibration()
        c.drain(realKw = 2.5f, modelKw = 2.0f, seconds = 2 * 3600)
        assertEquals(1.25f, c.factor, 0.03f)
        assertTrue(c.applied)
    }

    // A lower real draw would lengthen the stay: held at 1 until the evidence is strong (>= 6 %p and >= 60 min).
    @Test fun lowerRealDrawNeedsStrongEvidence() {
        val c = Calibration()
        c.drain(realKw = 1.7f, modelKw = 2.0f, seconds = 90 * 60) // about 3 %p: one 2 %p window
        assertTrue(c.saved.weightPct > 0f)
        assertFalse(Calibration.strong(c.saved))
        assertEquals(1f, c.factor)
        c.endStay() // a new stay
        c.drain(realKw = 1.7f, modelKw = 2.0f, seconds = 3 * 3600, startSoc = 70.0, t0 = 10_000_000L)
        assertTrue(Calibration.strong(c.saved))
        assertEquals(0.85f, c.factor, 0.03f)
    }

    @Test fun factorIsClampedTo1_5() {
        val c = Calibration()
        c.drain(realKw = 3.6f, modelKw = 2.0f, seconds = 2 * 3600) // 1.8x
        assertEquals(Calibration.MAX_FACTOR, c.factor)
    }

    @Test fun factorIsClampedTo0_8() {
        val c = Calibration()
        c.drain(realKw = 1.3f, modelKw = 2.0f, seconds = 8 * 3600, startSoc = 90.0) // 0.65x, strong evidence
        assertTrue(Calibration.strong(c.saved))
        assertEquals(Calibration.MIN_FACTOR, c.factor)
    }

    // A ratio far outside the plausible range is another consumer or a SOC jump: thrown away, not clamped.
    @Test fun implausibleRatioIsDiscarded() {
        val c = Calibration()
        c.drain(realKw = 6.0f, modelKw = 2.0f, seconds = 2 * 3600) // 3x
        assertEquals(Calibration.Saved(), c.saved)
        assertEquals(1f, c.factor)
    }

    // Changing the climate setting mid-window starts over: no sample mixes two settings.
    @Test fun settingChangeResetsTheWindow() {
        val c = Calibration()
        var t = c.drain(realKw = 2.5f, modelKw = 2.0f, seconds = 25 * 60, k = "A")
        t = c.drain(realKw = 2.5f, modelKw = 2.0f, seconds = 25 * 60, startSoc = 79.0 - 0.8, t0 = t, k = "B")
        assertEquals(Calibration.Saved(), c.saved) // neither 25-minute piece holds 2 %p edge to edge
    }

    @Test fun chargingResetsTheWindow() {
        val c = Calibration()
        c.observe(80, cap, key, 2f, 0)
        c.observe(79, cap, key, 2f, 60_000) // first edge
        c.observe(80, cap, key, 2f, 30 * 60_000L) // charging
        c.observe(78, cap, key, 2f, 50 * 60_000L)
        c.observe(77, cap, key, 2f, 60 * 60_000L)
        assertEquals(Calibration.Saved(), c.saved)
    }

    // Enough %p but too short a window: not yet a sample.
    @Test fun shortWindowIsNotASample() {
        val c = Calibration()
        c.observe(80, cap, key, 2f, 0)
        c.observe(79, cap, key, 2f, 60_000)
        c.observe(78, cap, key, 2f, 8 * 60_000L)
        assertFalse(c.observe(77, cap, key, 2f, 15 * 60_000L)) // 2 %p in 14 min
        assertEquals(Calibration.Saved(), c.saved)
    }

    // Virtual Console scenes set the SOC (19 -> 11 -> 19): jumps are never evidence, even 20+ minutes apart.
    @Test fun scriptedSocJumpsNeverCalibrate() {
        val c = Calibration()
        c.observe(25, cap, key, 2f, 0)
        c.observe(19, cap, key, 2f, 60_000)
        c.observe(17, cap, key, 2f, 30 * 60_000L)
        c.observe(11, cap, key, 2f, 60 * 60_000L)
        c.observe(9, cap, key, 2f, 90 * 60_000L)
        assertEquals(Calibration.Saved(), c.saved)
    }

    @Test fun endStayDropsTheOpenWindow() {
        val c = Calibration()
        c.observe(80, cap, key, 2f, 0)
        c.observe(79, cap, key, 2f, 60_000)
        c.endStay()
        c.observe(77, cap, key, 2f, 40 * 60_000L)
        c.observe(76, cap, key, 2f, 41 * 60_000L)
        assertEquals(Calibration.Saved(), c.saved)
    }

    // The weight is capped, so a car that changes (or a bad early sample) can still move the factor.
    @Test fun weightIsCappedAndNewEvidenceMoves() {
        var s = Calibration.Saved()
        repeat(20) { s = Calibration.merge(s, 1.4f, 2f, 30 * 60_000L) }
        assertEquals(Calibration.MAX_WEIGHT_PCT, s.weightPct, 0.001f)
        val after = Calibration.merge(s, 1.0f, 4f, 60 * 60_000L)
        assertTrue(after.ratio < 1.4f && after.ratio > 1.0f)
        assertEquals(Calibration.MAX_WEIGHT_PCT, after.weightPct, 0.001f)
    }

    // Persisted state gives the same factor after a restart.
    @Test fun savedStateRestoresTheFactor() {
        val c = Calibration()
        c.drain(realKw = 2.5f, modelKw = 2.0f, seconds = 2 * 3600)
        assertEquals(c.factor, Calibration(c.saved).factor)
    }

    // ---- engine wiring -----------------------------------------------------------------------------------------

    private val demo = CabinState(
        connected = true, socPercent = 19, socReadable = true, batteryCapacityKwh = 77.4f, fuelTypes = listOf(10),
        hvacPowerOn = true, acOn = false, fanSpeed = 3, driverSetTempC = 22f, recirculationOn = false, driverSeatHeat = 0,
        driverDoorOpen = false, windows = mapOf("driver" to 0, "passenger" to 0, "rearLeft" to 0, "rearRight" to 0),
    )
    private val settings = StaySettings(keepKm = 30, label = "work")
    private val scenes = listOf(demo, demo.copy(driverSetTempC = 26f, fanSpeed = 6), demo.copy(driverSetTempC = 21f, fanSpeed = 6, driverSeatHeat = 2))

    // HQ gate: the emulator's SOC is constant, so 3 hours of a stay, switching the demo scenes, keep 167/146/162.
    @Test fun demoNumbersUnchangedOnConstantSoc() {
        val e = StayEngine()
        var t = 0L
        repeat(3) { round -> scenes.forEach { sc -> repeat(1200) { e.update(true, sc, settings, t, 12); t += 1000 } } }
        val got = scenes.map { e.update(true, it, settings, t, 12).hero!! }
        assertEquals(listOf(167, 146, 162), got.map { it.minutes })
        assertTrue(got.none { it.calibrated })
        assertFalse(e.takeCalibrationChange())
    }

    // A restored factor shortens the hero, marks it, and the swap card uses the same factor.
    @Test fun engineAppliesARestoredFactor() {
        val strong = Calibration.Saved(ratio = 1.25f, weightPct = 10f, evidenceMs = 2 * 3600_000L)
        val hero = StayEngine(calibration = Calibration(strong)).update(true, demo, settings, 0, 12).hero!!
        assertTrue(hero.calibrated)
        assertTrue(hero.minutes!! < 167)
        assertEquals(167 / 1.25, hero.minutes!!.toDouble(), 2.0)
        val plain = EnergyModel.exchange(20, cap, 10f, ClimateSetting(true, 26f, 6, false, false, 0), emptySet())
        val scaled = EnergyModel.exchange(20, cap, 10f, ClimateSetting(true, 26f, 6, false, false, 0), emptySet(), 1.25f)
        assertNotNull(plain); assertNotNull(scaled)
        assertTrue(scaled!!.gainMinutes < plain!!.gainMinutes)
    }

    // The engine learns from a real drop during a stay and reports the change once for the controller to save.
    @Test fun engineLearnsAndReportsTheChangeOnce() {
        val e = StayEngine()
        val modelKw = EnergyModel.drawKw(ClimateSetting(true, 22f, 3, false, false, 0))
        val realKw = modelKw * 1.3f
        var soc = 60.0
        var t = 0L
        var changes = 0
        repeat(2 * 3600 / 10) {
            e.update(true, demo.copy(socPercent = floor(soc).toInt()), settings, t, 12)
            if (e.takeCalibrationChange()) changes++
            soc -= realKw / cap * 100.0 / 360.0
            t += 10_000
        }
        assertTrue(changes >= 1)
        assertEquals(1.3f, e.calibrationSaved.ratio, 0.05f)
        assertTrue(e.update(true, demo.copy(socPercent = floor(soc).toInt()), settings, t, 12).hero!!.calibrated)
    }
}
