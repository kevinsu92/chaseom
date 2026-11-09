package app.chaseom.stay

import app.chaseom.cabin.CabinState
import app.chaseom.cabin.WINDOW_UNKNOWN
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 04_design/HANDOFF.md section 3 + QA 조건(내부 문서) test cases (C-xx). */
class StayEngineTest {

    private val min = 60_000L
    private val ev = CabinState(
        connected = true, socPercent = 62, socReadable = true, batteryCapacityKwh = 77.4f, fuelTypes = listOf(10),
        hvacPowerOn = true, acOn = false, fanSpeed = 3, driverSetTempC = 22f, recirculationOn = false, driverSeatHeat = 0,
        driverBackrest = 60, passengerBackrest = 60, backrestMin = 0, backrestMax = 100,
        rearLeftOccupied = false, rearRightOccupied = false, driverDoorOpen = false,
        windows = mapOf("driver" to 0, "passenger" to 0, "rearLeft" to 0, "rearRight" to 0),
        cabinLightOn = false, chargePortOpen = false,
    )
    private val gasoline = ev.copy(fuelTypes = listOf(1))
    private val settings = StaySettings(keepKm = 30, label = "work")
    private val noon = 12

    private fun StayEngine.at(t: Long, c: CabinState = ev, parked: Boolean? = true, hour: Int = noon) =
        update(parked, c, settings, t, hour)

    // C-01 / N0
    @Test fun parkingStartsStayWithExpandedBannerThatCollapsesAfter10s() {
        val e = StayEngine()
        val ui = e.at(0)
        assertTrue(ui.parked)
        assertTrue(ui.banner!!.expanded)
        assertNull(ui.notice)
        assertFalse(e.at(10_001).banner!!.expanded)
        assertNotNull(e.at(30 * min).banner) // permanent thin line for the whole stay
    }

    @Test fun closingBannerCollapsesImmediately() {
        val e = StayEngine(); e.at(0); e.closeBanner()
        assertFalse(e.at(1000).banner!!.expanded)
    }

    // C-G1 / FLOWS 1
    @Test fun leavingParkEndsStayAndClosesEverything() {
        val e = StayEngine(); e.at(0)
        val ui = e.at(1000, parked = false)
        assertFalse(ui.parked)
        assertNull(ui.notice); assertNull(ui.hero); assertNull(ui.receipt); assertNull(ui.banner)
        assertNull(e.session)
    }

    @Test fun unknownParkStateKeepsSession() {
        val e = StayEngine(); e.at(0)
        assertFalse(e.at(1000, parked = null).parked)
        assertNotNull(e.session)
    }

    // C-09 N1: at 60 min and every 60 min after
    @Test fun hourNoticeEvery60Minutes() {
        val e = StayEngine(); e.at(0)
        assertNull(e.at(59 * min).notice)
        assertEquals(NoticeKind.HOUR, e.at(60 * min).notice)
        e.acknowledgeNotice(60 * min)
        assertNull(e.at(100 * min).notice)
        assertEquals(NoticeKind.HOUR, e.at(120 * min).notice)
    }

    // N2: night at start -> card immediately, then every 60 min while night
    @Test fun nightNoticeAtStartAndHourly() {
        val e = StayEngine()
        assertEquals(NoticeKind.NIGHT, e.at(0, hour = 23).notice)
        e.acknowledgeNotice(0)
        assertNull(e.at(30 * min, hour = 23).notice)
        assertEquals(NoticeKind.NIGHT, e.at(60 * min, hour = 0).notice.takeIf { it == NoticeKind.NIGHT } ?: e.at(60 * min, hour = 0).notice)
    }

    @Test fun nightBeginningDuringStayShowsNightCard() {
        val e = StayEngine(); e.at(0, hour = 21)
        assertNull(e.at(10 * min, hour = 21).notice)
        assertEquals(NoticeKind.NIGHT, e.at(20 * min, hour = 22).notice)
    }

    // N3 (S4-b/c, HANDOFF 3.6)
    @Test fun backrestFirstThenAtMost30MinApartOnlyOnCrossing() {
        val e = StayEngine(); e.at(0)
        val reclined = ev.copy(driverBackrest = 30)
        assertEquals(NoticeKind.BACKREST, e.at(min, reclined).notice)
        e.acknowledgeNotice(min)
        assertNull(e.at(2 * min, reclined).notice) // still reclined: no re-fire without crossing
        e.at(3 * min, ev) // upright
        assertNull(e.at(10 * min, reclined).notice) // crossing again, but within 30 min
        e.at(20 * min, ev)
        assertEquals(NoticeKind.BACKREST, e.at(32 * min, reclined).notice)
    }

    @Test fun passengerBackrestCounts() {
        val e = StayEngine(); e.at(0)
        assertEquals(NoticeKind.BACKREST, e.at(min, ev.copy(passengerBackrest = 20)).notice)
    }

    @Test fun backrestIgnoredWhenClimateOff() {
        val e = StayEngine(); e.at(0, ev.copy(hvacPowerOn = false))
        assertNull(e.at(min, ev.copy(hvacPowerOn = false, driverBackrest = 10)).notice)
    }

    @Test fun backrestSmallMoveIgnored() {
        val e = StayEngine(); e.at(0)
        assertNull(e.at(min, ev.copy(driverBackrest = 50)).notice) // 10 % < 15 %
    }

    // S4-e
    @Test fun noBackrestCapabilityMeansNoN3() {
        val e = StayEngine()
        val noCap = ev.copy(backrestMin = null, backrestMax = null)
        assertFalse(e.at(0, noCap).backrestSupported)
        assertNull(e.at(min, noCap.copy(driverBackrest = 0)).notice)
    }

    // Overlap: N3 > N2 > N1, lower ones within 5 min of an ack are skipped
    @Test fun overlapShowsHighestPriorityOnly() {
        val e = StayEngine(); e.at(0, hour = 21)
        val ui = e.at(60 * min, ev.copy(driverBackrest = 20), hour = 22) // hour + night + backrest together
        assertEquals(NoticeKind.BACKREST, ui.notice)
        e.acknowledgeNotice(60 * min)
        assertNull(e.at(60 * min + 1000, ev.copy(driverBackrest = 20), hour = 22).notice)
    }

    @Test fun lowerNoticeWithin5MinutesOfAckIsSkipped() {
        val e = StayEngine(); e.at(0)
        e.at(58 * min, ev.copy(driverBackrest = 20)); e.acknowledgeNotice(58 * min)
        assertNull(e.at(60 * min, ev.copy(driverBackrest = 20)).notice) // N1 due 2 min after N3 ack
    }

    // S7 / C-04a / C-04b
    @Test fun receiptRowsOrderAndRearAlwaysCheck() {
        val e = StayEngine(); e.at(0)
        val ui = e.at(41 * min, ev.copy(driverDoorOpen = true, rearLeftOccupied = true, socPercent = 59, cabinLightOn = true,
            chargePortOpen = true, windows = ev.windows + ("driver" to 3)))
        val r = ui.receipt!!
        assertEquals(
            listOf(ReceiptRow.Stayed::class, ReceiptRow.Energy::class, ReceiptRow.Keep::class, ReceiptRow.Windows::class,
                ReceiptRow.Light::class, ReceiptRow.ChargePort::class, ReceiptRow.Rear::class),
            r.rows.map { it::class },
        )
        assertEquals(ReceiptRow.Stayed(41), r.rows[0])
        assertEquals(ReceiptRow.Energy("2.3"), r.rows[1])
        assertEquals(ReceiptRow.Windows(listOf("driver")), r.rows[3])
        assertTrue(r.rows[3].dot && r.rows[4].dot && r.rows[5].dot)
        assertEquals(ReceiptRow.Rear(true), r.rows.last())
        assertTrue(r.rearCheckAlways)
    }

    @Test fun rearRowNeverHasADotForAnyOccupancy() {
        listOf(null, true, false).forEach { l ->
            listOf(null, true, false).forEach { rr ->
                val e = StayEngine(); e.at(0)
                val rec = e.at(2 * min, ev.copy(driverDoorOpen = true, rearLeftOccupied = l, rearRightOccupied = rr)).receipt!!
                val rear = rec.rows.filterIsInstance<ReceiptRow.Rear>().single()
                assertFalse(rear.dot)
                assertEquals(l == true || rr == true, rear.detected)
                assertTrue(rec.rearCheckAlways)
            }
        }
    }

    @Test fun receiptNeedsOneMinuteAndOpeningEdgeAndClosesWithDoor() {
        val e = StayEngine(); e.at(0)
        assertNull(e.at(30_000, ev.copy(driverDoorOpen = true)).receipt) // < 1 min
        e.at(40_000)
        assertNotNull(e.at(2 * min, ev.copy(driverDoorOpen = true)).receipt)
        assertNull(e.at(2 * min + 1000, ev).receipt) // door closed
    }

    @Test fun lightUnknownIsReportedNotDropped() {
        val e = StayEngine(); e.at(0)
        val rows = e.at(2 * min, ev.copy(driverDoorOpen = true, cabinLightOn = null)).receipt!!.rows
        assertEquals(ReceiptRow.Light(null), rows.filterIsInstance<ReceiptRow.Light>().single())
    }

    @Test fun keepRowShowsCoveredKmWhenBelowKeep() {
        val e = StayEngine(); e.at(0)
        val keep = e.at(2 * min, ev.copy(driverDoorOpen = true, socPercent = 7)).receipt!!.rows.filterIsInstance<ReceiptRow.Keep>().single()
        assertEquals(ReceiptRow.Keep(30, 22), keep)
        assertTrue(keep.dot)
    }

    // Condition 10 / 12 / C-10 / C-12
    @Test fun reducedModeHasNoEnergyAnywhere() {
        val e = StayEngine(StayTiming(airAfterMs = 0))
        val g = gasoline.copy(recirculationOn = true, driverSetTempC = 27f, fanSpeed = 7)
        val ui = e.at(0, g)
        assertEquals(AppMode.REDUCED, ui.policy.mode)
        assertNull(ui.hero); assertNull(ui.bar); assertNull(ui.exchange); assertNull(ui.keepBanner)
        assertNull(e.at(1000, g.copy(driverSetTempC = 20f)).toast)
        assertNotNull(ui.airMinutes) // air card is common to both modes
        val rows = e.at(2 * min, g.copy(driverDoorOpen = true, socPercent = 50)).receipt!!.rows
        assertTrue(rows.none { it is ReceiptRow.Energy || it is ReceiptRow.Keep || it is ReceiptRow.ChargePort })
    }

    @Test fun fuelReadFailureStartsReduced() =
        assertEquals(AppMode.REDUCED, StayEngine().at(0, ev.copy(fuelTypes = null, fuelTypeReadFailed = true)).policy.mode)

    // H·D toast: immediate, merged within 1.2 s, last two items, shown 3 s
    @Test fun dialToastMergesBatchAndExpires() {
        val low = ev.copy(socPercent = 14) // below the 3 h cap so deltas are real
        val e = StayEngine(); e.at(0, low)
        val t1 = e.at(1000, low.copy(driverSetTempC = 24f)).toast!!
        assertTrue(t1.deltaMinutes < 0)
        assertEquals(listOf(ClimateChange.Temp(24f)), t1.items)
        val t2 = e.at(1800, low.copy(driverSetTempC = 26f, fanSpeed = 6)).toast!!
        assertEquals(listOf(ClimateChange.Temp(26f), ClimateChange.Fan(6)), t2.items)
        assertTrue(t2.deltaMinutes < t1.deltaMinutes) // measured from the batch start
        assertNull(e.at(1800 + 3_001, low.copy(driverSetTempC = 26f, fanSpeed = 6)).toast)
    }

    // QA M-11 / FINDINGS E-01: never an hours-long delta
    @Test fun toastDeltaUsesThreeHourCappedValues() {
        val e = StayEngine(); e.at(0) // 62 %: far above 3 h before and after
        val t = e.at(1000, ev.copy(driverSetTempC = 26f, fanSpeed = 7)).toast!!
        assertTrue(t.heldAboveCap)
        assertEquals(0, t.deltaMinutes)
    }

    @Test fun toastDeltaCrossingTheCapIsBounded() {
        val e = StayEngine()
        val low = ev.copy(socPercent = 14, driverSetTempC = 20f, fanSpeed = 1)
        e.at(0, low)
        val t = e.at(1000, low.copy(driverSetTempC = 28f, fanSpeed = 8)).toast!!
        assertFalse(t.heldAboveCap)
        assertTrue(t.deltaMinutes < 0 && t.deltaMinutes >= -EnergyModel.DISPLAY_CAP_MINUTES)
    }

    @Test fun noExchangeCardWhenAboveCap() =
        assertNull(StayEngine().at(0, ev.copy(driverSetTempC = 26f, fanSpeed = 7)).exchange)

    @Test fun nonClimateChangeMakesNoToast() {
        val e = StayEngine(); e.at(0)
        assertNull(e.at(1000, ev.copy(cabinLightOn = true)).toast)
    }

    // Hero / K1 / K2
    @Test fun heroCapAndHvacOff() {
        val e = StayEngine()
        assertTrue(e.at(0).hero!!.overCap) // 62 % of 77.4 kWh at low draw
        assertTrue(e.at(1000, ev.copy(hvacPowerOn = false)).hero!!.hvacOff)
    }

    @Test fun keepBanners() {
        assertEquals(KeepBanner.BELOW, StayEngine().at(0, ev.copy(socPercent = 9)).keepBanner)
        // 10 % SOC, keep ~9.3 %: ~0.54 kWh at ~1.3 kW -> under 30 minutes
        assertEquals(KeepBanner.NEAR, StayEngine().at(0, ev.copy(socPercent = 10)).keepBanner)
        assertNull(StayEngine().at(0).keepBanner)
    }

    // H·X / seat heat 30 min
    @Test fun exchangeDismissStaysDismissedForTheStay() {
        val e = StayEngine()
        val hot = ev.copy(driverSetTempC = 26f, socPercent = 20) // sourced coefficients: gain >= 10 min from ~20 %
        val x = e.at(0, hot).exchange!!
        assertEquals(ExchangeRule.X1_SEAT_HEAT, x.rule)
        e.dismissExchange(x.rule)
        assertTrue(e.at(1000, hot).exchange?.rule != ExchangeRule.X1_SEAT_HEAT)
    }

    @Test fun seatHeatLongReplacesExchange() {
        val e = StayEngine()
        val c = ev.copy(driverSeatHeat = 2, fanSpeed = 7)
        e.at(0, c)
        assertFalse(e.at(29 * min, c).seatHeatLong)
        val ui = e.at(30 * min, c)
        assertTrue(ui.seatHeatLong)
        assertNull(ui.exchange)
    }

    // Condition 2 / 11 + snooze
    @Test fun airCardAfter20MinQuietExitAndSnooze() {
        val e = StayEngine()
        val recirc = ev.copy(recirculationOn = true)
        e.at(0, recirc)
        assertNull(e.at(19 * min, recirc).airMinutes)
        assertEquals(20, e.at(20 * min, recirc).airMinutes)
        e.dismissAir(20 * min)
        assertNull(e.at(30 * min, recirc).airMinutes)
        assertEquals(40, e.at(40 * min, recirc).airMinutes)
        assertNull(e.at(41 * min, recirc.copy(windows = ev.windows + ("driver" to 2))).airMinutes)
    }

    @Test fun sessionRestoreKeepsHourSchedule() {
        val e = StayEngine(); e.at(0)
        val restored = StayEngine(restored = e.session)
        val ui = restored.at(60 * min)
        assertEquals(60, ui.elapsedMinutes)
        assertEquals(NoticeKind.HOUR, ui.notice)
    }

    // ---- Bundle B (05_qa B_GATE 3, REVIEW_COPY_B) -------------------------------------------------

    private fun saved() = StaySession(startMs = 0, socAtStart = 62, nextHourMs = 60 * min)

    // E4-1b: back within 10 minutes of the last save -> the same stay continues.
    @Test fun restoreContinuesWithin10Min() {
        val e = StayEngine(restored = saved(), restoredSeenAtMs = 5 * min, restoredSocAtSave = 62)
        e.at(14 * min)
        assertEquals(0L, e.session!!.startMs)
    }

    // E4-1b: more than 10 minutes since the last save -> a new stay.
    @Test fun restoreStartsNewStayAfter10Min() {
        val e = StayEngine(restored = saved(), restoredSeenAtMs = 5 * min, restoredSocAtSave = 62)
        val ui = e.at(16 * min)
        assertEquals(16 * min, e.session!!.startMs)
        assertEquals(0, ui.elapsedMinutes)
    }

    // E4-1: charged while the app was away (SOC rose) -> a new stay, energy baseline = new SOC.
    @Test fun restoreStartsNewStayWhenSocRose() {
        val e = StayEngine(restored = saved(), restoredSeenAtMs = 5 * min, restoredSocAtSave = 52)
        e.at(6 * min, ev.copy(socPercent = 62))
        assertEquals(6 * min, e.session!!.startMs)
        assertEquals(62, e.session!!.socAtStart)
    }

    // REVIEW_COPY_B: more than 5 %p below the last saved value -> a different stay.
    @Test fun restoreStartsNewStayWhenSocDifferentByMoreThan5Points() {
        val e = StayEngine(restored = saved(), restoredSeenAtMs = 5 * min, restoredSocAtSave = 70)
        e.at(6 * min, ev.copy(socPercent = 62))
        assertEquals(6 * min, e.session!!.startMs)
        val kept = StayEngine(restored = saved(), restoredSeenAtMs = 5 * min, restoredSocAtSave = 66)
        kept.at(6 * min, ev.copy(socPercent = 62))
        assertEquals(0L, kept.session!!.startMs)
    }

    // A reboot restarts the monotonic clock: a saved time in the "future" is never continued.
    @Test fun restoreAfterRebootStartsNewStay() {
        val e = StayEngine(restored = saved().copy(startMs = 50 * min), restoredSeenAtMs = 55 * min, restoredSocAtSave = 62)
        e.at(1 * min)
        assertEquals(1 * min, e.session!!.startMs)
    }

    // E4-2: below tomorrow's share the hero carries a status, never "약 0분".
    @Test fun heroNeverZeroMinutesBelowKeep() {
        val hero = StayEngine().at(0, ev.copy(socPercent = 7)).hero!!
        assertTrue(hero.belowKeep)
        assertEquals(22, hero.coveredKm)
        assertFalse(StayEngine().at(0).hero!!.belowKeep)
    }

    // E4-3: battery rose during the stay -> charging line, never "0.0kWh".
    @Test fun receiptShowsChargingInsteadOfZeroKwh() {
        val e = StayEngine(); e.at(0, ev.copy(socPercent = 40))
        val energy = e.at(2 * min, ev.copy(socPercent = 45, driverDoorOpen = true)).receipt!!.rows.filterIsInstance<ReceiptRow.Energy>().single()
        assertEquals(EnergyKind.CHARGED, energy.kind)
    }

    @Test fun receiptSmallUseIsUnderOneKwhNotZero() {
        val e = StayEngine(); e.at(0)
        val same = e.at(2 * min, ev.copy(driverDoorOpen = true)).receipt!!.rows.filterIsInstance<ReceiptRow.Energy>().single()
        assertEquals(EnergyKind.SMALL, same.kind)
        val f = StayEngine(); f.at(0)
        val used = f.at(60 * min, ev.copy(socPercent = 59, driverDoorOpen = true)).receipt!!.rows.filterIsInstance<ReceiptRow.Energy>().single()
        assertEquals(ReceiptRow.Energy("2.3", EnergyKind.VALUE), used)
    }

    // Unknown windows are reported, never "닫힘" (W-Q3 table: unknown is never the safe-looking value).
    @Test fun windowsUnknownIsReportedNotClosed() {
        val e = StayEngine(); e.at(0)
        val w = e.at(2 * min, ev.copy(driverDoorOpen = true, windows = mapOf("driver" to WINDOW_UNKNOWN, "passenger" to 0)))
            .receipt!!.rows.filterIsInstance<ReceiptRow.Windows>().single()
        assertFalse(w.known)
        assertTrue(w.open.isEmpty())
        val none = StayEngine().also { it.at(0) }.at(2 * min, ev.copy(driverDoorOpen = true, windows = emptyMap()))
            .receipt!!.rows.filterIsInstance<ReceiptRow.Windows>().single()
        assertFalse(none.known)
    }

    // The air card needs every window known and closed; unknown is not "closed".
    @Test fun unknownWindowDoesNotCountAsClosed() {
        assertNull(ev.copy(windows = mapOf("driver" to WINDOW_UNKNOWN)).anyWindowOpen)
        assertEquals(false, ev.anyWindowOpen)
        assertEquals(true, ev.copy(windows = mapOf("driver" to 3, "passenger" to WINDOW_UNKNOWN)).anyWindowOpen)
    }

    // E4-4: stay timers run on the controller's monotonic clock; the engine only sees elapsed time,
    // so the 60-minute warning depends on elapsed minutes, not on the wall clock or hour of day.
    @Test fun hourNoticeDependsOnElapsedTimeOnly() {
        val e = StayEngine(); e.at(0, hour = 12)
        assertNull(e.at(59 * min, hour = 3).notice.takeIf { it == NoticeKind.HOUR })
        assertEquals(NoticeKind.HOUR, StayEngine().also { it.at(0, hour = 12) }.at(60 * min, hour = 12).notice)
    }

    // "새로 시작했어요" only when a saved stay was actually dropped, not after a normal drive -> P.
    @Test fun restartTitleOnlyWhenASavedStayWasDropped() {
        val e = StayEngine(restored = saved(), restoredSeenAtMs = 5 * min, restoredSocAtSave = 62)
        e.at(40 * min, parked = false) // drove away: the saved stay simply ended
        assertFalse(e.at(41 * min).restartedFromStale)
        val f = StayEngine(restored = saved(), restoredSeenAtMs = 5 * min, restoredSocAtSave = 62)
        assertTrue(f.at(40 * min).restartedFromStale)
    }

    // UXR E-1: 12 %p (9.3 kWh) gone in one minute is not climate use: no made-up kWh.
    @Test fun receiptEnergyImplausibleDropIsNotShownAsUsed() {
        val e = StayEngine(); e.at(0, ev.copy(socPercent = 20))
        val energy = e.at(min + 1000, ev.copy(socPercent = 8, driverDoorOpen = true)).receipt!!.rows.filterIsInstance<ReceiptRow.Energy>().single()
        assertEquals(EnergyKind.IMPLAUSIBLE, energy.kind)
        val f = StayEngine(); f.at(0, ev.copy(socPercent = 20))
        val real = f.at(60 * min, ev.copy(socPercent = 17, driverDoorOpen = true)).receipt!!.rows.filterIsInstance<ReceiptRow.Energy>().single()
        assertEquals(EnergyKind.VALUE, real.kind) // 2.3 kWh in an hour is plausible
        // 1 %p in one minute is integer-SOC resolution, not an implausible drop: "1kWh 미만".
        val g = StayEngine(); g.at(0, ev.copy(socPercent = 20))
        val onePoint = g.at(min + 1000, ev.copy(socPercent = 19, driverDoorOpen = true)).receipt!!.rows.filterIsInstance<ReceiptRow.Energy>().single()
        assertEquals(EnergyKind.SMALL, onePoint.kind)
        val h = StayEngine(); h.at(0, ev.copy(socPercent = 20))
        val twoPoints = h.at(min + 1000, ev.copy(socPercent = 18, driverDoorOpen = true)).receipt!!.rows.filterIsInstance<ReceiptRow.Energy>().single()
        assertEquals(EnergyKind.IMPLAUSIBLE, twoPoints.kind)
    }
}
