package app.chaseom.stay

import app.chaseom.cabin.CabinState
import app.chaseom.cabin.RefreshFill
import app.chaseom.cabin.RefreshFill.Verdict
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * internal note: the 10 s climate-power re-read (H2 item 8) passed any differing reply straight to the
 * listener. Pleos SDK 2.3.2 parses the reply with Boolean.parseBoolean, so a reply other than "true" is `false`, and
 * the hero said "공조 꺼짐" with the climate on.
 *
 * Rule (QA review §5, R1/R2): a re-read never overwrites a value a change event set or a re-read confirmed; a value
 * that is null or came only from the start-up get may be filled after 2 agreeing re-reads; power has its own stamp.
 *
 * [Car] mirrors PleosCabinRepository: start-up get = `onStartup` + apply, listener = `onEvent` + apply,
 * re-read = `refresh` (CONFIRM renews the stamp, FILL applies, IGNORE does nothing).
 */
class HvacRefreshTest {

    private val s = 1_000L
    private val settings = StaySettings(keepKm = 30, label = "work")

    private class Car(var st: CabinState) {
        val socFill = RefreshFill<Int>()
        val powerFill = RefreshFill<Boolean>()

        private fun applyPower(v: Boolean?, t: Long) {
            st = st.copy(hvacPowerOn = v).let { if (v == null) it else it.copy(powerConfirmedAtMs = t, climateConfirmedAtMs = t) }
        }
        private fun applySoc(v: Int?, t: Long) {
            st = st.copy(socPercent = v, socReadable = v != null).let { if (v == null) it else it.copy(socConfirmedAtMs = t) }
        }

        fun startupPower(v: Boolean?, t: Long) { powerFill.onStartup(); applyPower(v, t) }
        fun eventPower(v: Boolean?, t: Long) { powerFill.onEvent(v); applyPower(v, t) }
        fun readPower(v: Boolean?, t: Long) = when (powerFill.onRead(v, st.hvacPowerOn)) {
            Verdict.CONFIRM -> st = st.copy(powerConfirmedAtMs = t, climateConfirmedAtMs = t)
            Verdict.FILL -> applyPower(v, t)
            Verdict.IGNORE -> Unit
        }

        fun startupSoc(v: Int?, t: Long) { socFill.onStartup(); applySoc(v, t) }
        fun eventSoc(v: Int?, t: Long) { socFill.onEvent(v); applySoc(v, t) }
        fun readSoc(v: Int?, t: Long) = when (socFill.onRead(v, st.socPercent)) {
            Verdict.CONFIRM -> st = st.copy(socConfirmedAtMs = t)
            Verdict.FILL -> applySoc(v, t)
            Verdict.IGNORE -> Unit
        }

        fun fanEvent(v: Int, t: Long) { st = st.copy(fanSpeed = v, climateConfirmedAtMs = t) }
    }

    /** The demo climate (22 C, fan 3, AC off) with SOC and power still to be delivered. */
    private fun car() = Car(
        CabinState(
            connected = true, socPercent = null, socReadable = false, batteryCapacityKwh = 77.4f, fuelTypes = listOf(10),
            hvacPowerOn = null, acOn = false, fanSpeed = 3, driverSetTempC = 22f, recirculationOn = false, driverSeatHeat = 0,
            driverDoorOpen = false, windows = mapOf("driver" to 0, "passenger" to 0, "rearLeft" to 0, "rearRight" to 0),
            climateConfirmedAtMs = 0,
        ),
    )

    /** Start-up gets that both came back right: SOC 19, power on. */
    private fun startedCar() = car().apply { startupSoc(19, 0); startupPower(true, 0) }

    private fun hero(e: StayEngine, t: Long, c: CabinState) = e.update(true, c, settings, t, 12).hero!!

    @Test fun reproducesTheBug_559d2deRuleShowsClimateOffOnOneWrongRead() {
        // 559d2de: `if (p != current) listener(p) else stamp`.
        val c = startedCar().st
        val e = StayEngine()
        assertEquals(167, hero(e, 0, c).minutes)
        assertTrue(hero(e, 10 * s, c.copy(hvacPowerOn = false)).hvacOff)
        // A null power value used to count as off as well; since M1 it is "확인 안 됨" (see a_...).
        val n = hero(StayEngine(), 0, c.copy(hvacPowerOn = null))
        assertTrue(n.unconfirmed)
        assertFalse(n.hvacOff)
    }

    // (a) start-up power null, then re-reads true, true -> on.
    @Test fun a_startupPowerNullThenTrueTrueTurnsOn() {
        val car = car().apply { startupSoc(19, 0); startupPower(null, 0) }
        val e = StayEngine()
        // M1 (QA §6): an unread power value shows 확인 안 됨, never 공조 꺼짐.
        hero(e, 0, car.st).let { assertTrue(it.unconfirmed); assertFalse(it.hvacOff) }
        car.readPower(true, 10 * s)
        car.readSoc(19, 10 * s)
        hero(e, 10 * s, car.st).let { assertTrue("one read is not enough", it.unconfirmed); assertFalse(it.hvacOff) }
        car.readPower(true, 20 * s)
        car.readSoc(19, 20 * s)
        val h = hero(e, 20 * s, car.st)
        assertFalse(h.hvacOff)
        assertFalse(h.unconfirmed)
        assertEquals(167, h.minutes)
    }

    // (b) Start-up power false while the climate is on, then true, true -> recovers.
    @Test fun b_startupPowerFalseThenTrueTrueRecovers() {
        val car = car().apply { startupSoc(19, 0); startupPower(false, 0) }
        val e = StayEngine()
        assertTrue(hero(e, 0, car.st).hvacOff)
        car.readPower(true, 10 * s)
        car.readPower(true, 20 * s)
        car.readSoc(19, 20 * s)
        assertEquals(true, car.st.hvacPowerOn)
        assertEquals(167, hero(e, 20 * s, car.st).minutes)
    }

    // (c) Event-set true, then re-reads false xN -> never off, 확인 안 됨 after 60 s.
    @Test fun c_eventTrueThenFalseRunNeverOffThenUnconfirmed() {
        val car = startedCar().apply { eventPower(true, 0) }
        val e = StayEngine()
        hero(e, 0, car.st)
        for (i in 1..12) {
            val t = i * 10 * s
            car.readPower(false, t)
            car.readSoc(19, t)
            val h = hero(e, t, car.st)
            assertFalse("read $i", h.hvacOff)
            assertEquals(true, car.st.hvacPowerOn)
            if (t <= 60 * s) assertEquals(167, h.minutes) else assertTrue("stale after 60 s", h.unconfirmed)
        }
    }

    // (c') A start-up value confirmed by one equal re-read is locked the same way.
    @Test fun c2_startupTrueConfirmedByAReadIsNeverOverwritten() {
        val car = startedCar()
        car.readPower(true, 10 * s)
        repeat(5) { car.readPower(false, (2 + it) * 10 * s) }
        assertEquals(true, car.st.hvacPowerOn)
    }

    // (d) Start-up SOC null, then 19, 19 -> the estimate appears.
    @Test fun d_startupSocNullThen19And19ShowsTheEstimate() {
        val car = car().apply { startupSoc(null, 0); startupPower(true, 0) }
        val e = StayEngine()
        e.update(true, car.st, settings, 0, 12)
        car.readSoc(19, 10 * s)
        car.readPower(true, 10 * s)
        assertEquals(null, car.st.socPercent)
        car.readSoc(19, 20 * s)
        car.readPower(true, 20 * s)
        assertEquals(19, car.st.socPercent)
        assertEquals(167, hero(e, 20 * s, car.st).minutes)
    }

    // (e) Event-set SOC 19, then null / 0 xN -> unchanged.
    @Test fun e_eventSoc19ThenNullAndZeroRunUnchanged() {
        val car = startedCar().apply { eventSoc(19, 0) }
        val e = StayEngine()
        hero(e, 0, car.st)
        listOf(null, 0, 0, null, 0).forEachIndexed { i, r ->
            val t = (i + 1) * 10 * s
            car.readSoc(r, t)
            car.readPower(true, t)
            assertEquals(19, car.st.socPercent)
            assertEquals(167, hero(e, t, car.st).minutes)
        }
        assertEquals(0L, car.st.socConfirmedAtMs)
        assertTrue(hero(e, 61 * s, car.st).unconfirmed) // never confirmed again: 확인 안 됨, not a wrong number
    }

    // QA (e): start-up true (unconfirmed), then false, then true -> stays on.
    @Test fun startupTrueThenFalseThenTrueStaysOn() {
        val car = startedCar()
        val e = StayEngine()
        car.readPower(false, 10 * s)
        assertEquals(true, car.st.hvacPowerOn)
        car.readPower(true, 20 * s)
        assertEquals(true, car.st.hvacPowerOn)
        car.readPower(false, 30 * s) // now confirmed: a later false pair can never fill
        car.readPower(false, 40 * s)
        car.readSoc(19, 40 * s)
        assertEquals(true, car.st.hvacPowerOn)
        assertFalse(hero(e, 40 * s, car.st).hvacOff)
    }

    @Test fun nullRunNeverFillsAndKeepsTheMinutes() {
        val car = startedCar()
        val e = StayEngine()
        hero(e, 0, car.st)
        for (i in 1..6) {
            val t = i * 10 * s
            car.readPower(null, t)
            car.readSoc(null, t)
            assertEquals(167, hero(e, t, car.st).minutes)
        }
        // A pair broken by a null never fills.
        val c2 = car().apply { startupSoc(19, 0); startupPower(null, 0) }
        c2.readPower(true, 10 * s); c2.readPower(null, 20 * s); c2.readPower(true, 30 * s)
        assertEquals(null, c2.st.hvacPowerOn)
        c2.readPower(true, 40 * s)
        assertEquals(true, c2.st.hvacPowerOn)
    }

    @Test fun aRealListenerOffSwitchesAtOnce() {
        val car = startedCar().apply { eventPower(true, 0) }
        val e = StayEngine()
        hero(e, 0, car.st)
        car.eventPower(false, 5 * s)
        assertTrue(hero(e, 5 * s, car.st).hvacOff)
        car.readPower(true, 10 * s) // a wrong "on" pair cannot switch it back
        car.readPower(true, 20 * s)
        assertEquals(false, car.st.hvacPowerOn)
        car.eventPower(true, 25 * s)
        assertFalse(hero(e, 25 * s, car.st).hvacOff)
    }

    // R2: fan events keep the shared climate stamp fresh, but a power value nobody confirms still goes stale.
    @Test fun r2_powerHasItsOwnStamp() {
        val car = startedCar().apply { eventPower(true, 0) }
        val e = StayEngine()
        hero(e, 0, car.st)
        for (i in 1..7) {
            val t = i * 10 * s
            car.fanEvent(if (i % 2 == 0) 3 else 4, t)
            car.readPower(false, t) // ignored: power not confirmed
            car.readSoc(19, t)
        }
        assertEquals(70 * s, car.st.climateConfirmedAtMs)
        val h = hero(e, 70 * s, car.st)
        assertTrue(h.unconfirmed)
        assertFalse(h.hvacOff)
    }

    @Test fun ruleTable() {
        val f = RefreshFill<Boolean>()
        assertEquals(Verdict.IGNORE, f.onRead(null, null))
        assertEquals(Verdict.IGNORE, f.onRead(true, null))
        assertEquals(Verdict.FILL, f.onRead(true, null))
        assertTrue(f.isConfirmed())
        assertEquals(Verdict.CONFIRM, f.onRead(true, true))
        assertEquals(Verdict.IGNORE, f.onRead(false, true))
        assertEquals(Verdict.IGNORE, f.onRead(false, true))
        f.onStartup()
        assertFalse(f.isConfirmed())
        f.onEvent(null)
        assertFalse(f.isConfirmed())
        f.onEvent(false)
        assertTrue(f.isConfirmed())
    }

    @Test fun demoNumbersUnchanged() {
        fun minutes(temp: Float, fan: Int, seat: Int): Int? {
            val car = startedCar()
            car.st = car.st.copy(driverSetTempC = temp, fanSpeed = fan, driverSeatHeat = seat)
            val e = StayEngine()
            e.update(true, car.st, settings, 0, 12)
            car.readSoc(19, 10 * s)
            car.readPower(true, 10 * s)
            return e.update(true, car.st, settings, 10 * s, 12).hero!!.minutes
        }
        assertEquals(167, minutes(22f, 3, 0))
        assertEquals(146, minutes(26f, 6, 0))
        assertEquals(162, minutes(21f, 6, 2))
    }
}
