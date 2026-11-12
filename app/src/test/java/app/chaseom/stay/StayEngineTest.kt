package app.chaseom.stay

import app.chaseom.cabin.CabinState
import app.chaseom.cabin.WINDOW_UNKNOWN
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Stay engine behaviour tests. */
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

    // N0
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

    // Leaving P
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

    // N1: at 60 min and every 60 min after
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

    // N3: backrest notice
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

    // No backrest capability
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

    // Receipt rows
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

    // The rear row shows the sensor state only, and the "뒷좌석 직접 확인" line always closes the receipt.
    @Test fun rearCheckLineIsAlwaysTheLastLine() {
        listOf(null, true, false).forEach { occ ->
            val e = StayEngine(); e.at(0)
            val r = e.at(5 * min, ev.copy(driverDoorOpen = true, rearLeftOccupied = occ)).receipt!!
            assertTrue(r.rows.last() is ReceiptRow.Rear)
            assertTrue(r.rearCheckAlways) // drawn after every row (Overlays.ReceiptOverlay)
        }
    }

    @Test fun rearRowNeverHasADotForAnyOccupancy() {
        listOf(null, true, false).forEach { l ->
            listOf(null, true, false).forEach { rr ->
                val e = StayEngine(); e.at(0)
                val rec = e.at(2 * min, ev.copy(driverDoorOpen = true, rearLeftOccupied = l, rearRightOccupied = rr)).receipt!!
                val rear = rec.rows.filterIsInstance<ReceiptRow.Rear>().single()
                assertFalse(rear.dot)
                val expected = if (l == true || rr == true) true else if (l == null || rr == null) null else false
                assertEquals(expected, rear.detected)
                assertTrue(rec.rearCheckAlways)
            }
        }
    }

    // A rear sensor read failure is "확인 안 됨" (null), never "감지 안 됨" (false); someone on either seat wins.
    @Test fun rearReadFailureIsUnknownNotNotDetected() {
        assertNull(rearDetected(null, null))
        assertNull(rearDetected(null, false))
        assertNull(rearDetected(false, null))
        assertEquals(false, rearDetected(false, false))
        assertEquals(true, rearDetected(true, null))
        assertEquals(true, rearDetected(null, true))
        val e = StayEngine(); e.at(0)
        val rec = e.at(2 * min, ev.copy(driverDoorOpen = true, rearLeftOccupied = null, rearRightOccupied = null)).receipt!!
        assertNull(rec.rows.filterIsInstance<ReceiptRow.Rear>().single().detected)
    }

    @Test fun receiptNeedsOneMinuteAndOpeningEdgeAndClosesWithDoor() {
        val e = StayEngine(); e.at(0)
        assertNull(e.at(30_000, ev.copy(driverDoorOpen = true)).receipt) // < 1 min
        e.at(40_000)
        assertNotNull(e.at(2 * min, ev.copy(driverDoorOpen = true)).receipt)
        assertNull(e.at(2 * min + 1000, ev).receipt) // door closed
    }

    // An unreadable driver door neither opens nor closes the receipt; only a known closed -> open edge opens it.
    @Test fun unknownDoorNeverOpensOrClosesTheReceipt() {
        val e = StayEngine(); e.at(0)
        assertNull(e.at(2 * min, ev.copy(driverDoorOpen = null)).receipt)
        assertNotNull(e.at(2 * min + 1000, ev.copy(driverDoorOpen = true)).receipt) // last known was closed
        assertNotNull("unknown does not close an open receipt", e.at(2 * min + 2000, ev.copy(driverDoorOpen = null)).receipt)
        assertNull(e.at(2 * min + 3000, ev).receipt) // known closed
        val fresh = StayEngine(); fresh.at(0, ev.copy(driverDoorOpen = null))
        assertNull("no known closed state yet: no receipt", fresh.at(2 * min, ev.copy(driverDoorOpen = true)).receipt)
    }

    // The receipt takes the light as read at the door-open moment (the repository re-reads the switch
    // first), and an open receipt follows later changes, including "unknown" when the re-read fails.
    @Test fun receiptLightRowFollowsTheSwitchBothWays() {
        fun light(r: Receipt) = r.rows.filterIsInstance<ReceiptRow.Light>().single().on
        val off = StayEngine(); off.at(0, ev.copy(cabinLightOn = true))
        assertEquals(false, light(off.at(2 * min, ev.copy(driverDoorOpen = true, cabinLightOn = false)).receipt!!))
        val on = StayEngine(); on.at(0, ev.copy(cabinLightOn = false))
        val r = on.at(2 * min, ev.copy(driverDoorOpen = true, cabinLightOn = true)).receipt!!
        assertEquals(true, light(r))
        assertEquals(false, light(on.at(2 * min + 500, ev.copy(driverDoorOpen = true, cabinLightOn = false)).receipt!!))
        assertEquals(null, light(on.at(2 * min + 900, ev.copy(driverDoorOpen = true, cabinLightOn = null)).receipt!!))
        assertEquals(r.createdAtMs, on.at(2 * min + 1000, ev.copy(driverDoorOpen = true)).receipt!!.createdAtMs) // same receipt
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

    // Reduced mode
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

    // Recirculation <-> outside air: the toast names the switch and shows no minutes, in both directions and
    // when bundled with another change; the hero moves at once as for any other dial.
    @Test fun airSourceSwitchToastHasNoMinutesButTheHeroMoves() {
        val low = ev.copy(socPercent = 14)
        val e = StayEngine(); val h0 = e.at(0, low).hero!!.minutes
        val on = e.at(1000, low.copy(recirculationOn = true))
        assertFalse(on.toast!!.showMinutes)
        assertEquals(listOf(ClimateChange.Recirc(true)), on.toast!!.items)
        assertTrue("hero updates with the switch", on.hero!!.minutes != h0)
        val off = e.at(10_000, low).toast!! // back to outside air, a new batch
        assertFalse(off.showMinutes)
        assertEquals(listOf(ClimateChange.Recirc(false)), off.items)

        val b = StayEngine(); b.at(0, low)
        val bundled = b.at(1000, low.copy(driverSetTempC = 26f, fanSpeed = 6, recirculationOn = true)).toast!!
        assertFalse("bundled minutes would carry the same message", bundled.showMinutes)
        assertTrue(bundled.items.contains(ClimateChange.Recirc(true)))
        assertTrue(bundled.items.size == 2)

        val plain = StayEngine(); plain.at(0, low)
        assertTrue(plain.at(1000, low.copy(driverSetTempC = 26f)).toast!!.showMinutes)
    }

    // Never an hours-long delta
    @Test fun toastDeltaUsesThreeHourCappedValues() {
        val e = StayEngine(); e.at(0) // 62 %: far above 3 h before and after
        val t = e.at(1000, ev.copy(driverSetTempC = 26f, fanSpeed = 7)).toast!!
        assertTrue(t.heldAboveCap)
        assertEquals(0, t.deltaMinutes)
        assertNotNull("above the cap the change is a percentage, not minutes", t.capRatePercent)
    }

    // Above the 3-hour cap the toast shows the stay-time change as a percentage of the same battery
    // (power before / after - 1), with the sign in the same direction as the minutes would have.
    @Test fun aboveCapToastShowsStayRatePercentBothDirections() {
        val e = StayEngine(); val start = e.at(0) // 62 %, 22 C / fan 3
        assertTrue(start.hero!!.overCap)
        val up = e.at(1000, ev.copy(driverSetTempC = 26f, fanSpeed = 6))
        assertEquals(-12, up.toast!!.capRatePercent) // 2.69 kW -> 3.07 kW
        assertTrue("the hero stays at the cap", up.hero!!.overCap)
        val down = e.at(10_000, ev).toast!! // back to 22 C / fan 3, a new batch
        assertEquals(14, down.capRatePercent)
        assertEquals(EnergyModel.stayRatePercent(3.07f, 2.69f), down.capRatePercent)
    }

    @Test fun stayRateIsNullUnderOnePercentAndBelowTheCap() {
        assertNull(EnergyModel.stayRatePercent(2.69f, 2.70f))
        assertNull(EnergyModel.stayRatePercent(0f, 2.70f))
        val low = ev.copy(socPercent = 14)
        val e = StayEngine(); e.at(0, low)
        val t = e.at(1000, low.copy(driverSetTempC = 26f)).toast!!
        assertFalse(t.heldAboveCap)
        assertNull("below the cap the toast keeps its minutes", t.capRatePercent)
        assertTrue(t.deltaMinutes < 0)
    }

    // (a) capped on both sides: no minutes, only the percentage path
    @Test fun aboveCapToastNeverCarriesMinutes() {
        val e = StayEngine(); e.at(0)
        val t = e.at(1000, ev.copy(driverSetTempC = 26f, fanSpeed = 6)).toast!!
        assertTrue(t.heldAboveCap && t.deltaMinutes == 0 && t.capRatePercent != null)
    }

    // (b) the sign of the percentage matches the minutes toast for the same change below the cap
    @Test fun stayRateSignMatchesTheMinutesToast() {
        listOf(ev.copy(driverSetTempC = 26f, fanSpeed = 6), ev.copy(driverSetTempC = 18f, fanSpeed = 1), ev.copy(driverSeatHeat = 3)).forEach { change ->
            val high = StayEngine(); high.at(0)
            val rate = high.at(1000, change).toast!!.capRatePercent!!
            val low = StayEngine(); low.at(0, ev.copy(socPercent = 14))
            val minutes = low.at(1000, change.copy(socPercent = 14)).toast!!.deltaMinutes
            assertEquals(Integer.signum(minutes), Integer.signum(rate))
        }
    }

    // (c) a climate power on/off in the batch: no percentage (off would read as a reward)
    @Test fun noStayRateWhenTheBatchSwitchesTheClimateOnOrOff() {
        val e = StayEngine(); e.at(0)
        val off = e.at(1000, ev.copy(hvacPowerOn = false)).toast!!
        assertTrue(off.heldAboveCap)
        assertNull(off.capRatePercent)
        val on = e.at(10_000, ev.copy(driverSetTempC = 26f)).toast!! // power back on with a temperature change
        assertNull(on.capRatePercent)
    }

    // (e) edges: under 1 % is "held", over 50 % is written as "±50% 이상"
    @Test fun stayRateEdges() {
        assertNull(EnergyModel.stayRatePercent(3.00f, 3.01f)) // -0.3 % rounds to 0
        assertNull(EnergyModel.stayRatePercent(2.69f, 2.70f))
        assertEquals(-1, EnergyModel.stayRatePercent(3.00f, 3.03f))
        assertEquals(100, EnergyModel.stayRatePercent(3.0f, 1.5f))
        assertTrue(100 > EnergyModel.STAY_RATE_SHOWN_MAX)
        assertEquals(50, EnergyModel.STAY_RATE_SHOWN_MAX)
    }

    // (d) an air-source switch in the batch: no number at all (minutes or percent)
    @Test fun aboveCapAirSourceSwitchStillShowsNoNumber() {
        val e = StayEngine(); e.at(0)
        val t = e.at(1000, ev.copy(driverSetTempC = 26f, recirculationOn = true)).toast!!
        assertFalse(t.showMinutes) // the toast names the changes only, whatever the cap state
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

    @Test fun heroSaysThreeHoursOrMoreFromExactly180Minutes() {
        // The hero never shows "4시간 0분"; at or above 180 min it is "3시간 이상" (overCap).
        var sawBelow = false
        var sawAbove = false
        for (soc in 0..100) {
            val h = StayEngine().at(0, ev.copy(socPercent = soc)).hero ?: continue
            val m = h.minutes ?: continue
            assertEquals("soc $soc minutes $m", m >= EnergyModel.DISPLAY_CAP_MINUTES, h.overCap)
            if (m in 1 until 180) sawBelow = true
            if (m >= 180) sawAbove = true
        }
        assertTrue(sawBelow && sawAbove)
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

    // Air card after 20 min, quiet exit and snooze
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

    // The circling cloud's minutes.
    @Test fun recircCloudAfter20MinIgnoresSnoozeAndLeavesQuietly() {
        val e = StayEngine()
        val recirc = ev.copy(recirculationOn = true)
        e.at(0, recirc)
        assertNull(e.at(19 * min, recirc).recircMinutes)
        assertEquals(20, e.at(20 * min, recirc).recircMinutes)
        e.dismissAir(20 * min)                                   // closing the card does not hide a fact
        assertEquals(30, e.at(30 * min, recirc).recircMinutes)
        val open = e.at(31 * min, recirc.copy(windows = ev.windows + ("driver" to 2)))
        assertNull(open.recircMinutes)                           // window opened: gone, no other change
        assertNull(open.airMinutes)
        e.at(32 * min, recirc)
        assertNull(e.at(33 * min, recirc.copy(recirculationOn = false)).recircMinutes) // outside air: gone
        assertNull(e.at(34 * min, recirc).recircMinutes)         // starts over from zero
        assertEquals(20, e.at(54 * min, recirc).recircMinutes)
    }

    // Far rain only while the lever is on AUTO and the wiper actually wipes; manual wiping,
    // AUTO without wiping and an unreadable wiper never count.
    @Test fun rainOnlyForAutoWiperActuallyWiping() {
        assertTrue(StayEngine().at(0, ev.copy(wiperAuto = true, wiperOn = true)).rain)
        assertFalse(StayEngine().at(0, ev.copy(wiperAuto = false, wiperOn = true)).rain)   // manual Low / High / once
        assertFalse(StayEngine().at(0, ev.copy(wiperAuto = true, wiperOn = false)).rain)   // AUTO, dry
        assertFalse(StayEngine().at(0, ev.copy(wiperAuto = null, wiperOn = true)).rain)    // lever unreadable
        assertFalse(StayEngine().at(0, ev.copy(wiperAuto = true, wiperOn = null)).rain)    // state unreadable
    }

    // The boat shows the car's trunk and charge port; unknown is drawn closed.
    @Test fun trunkAndChargePortReachTheIsland() {
        val e = StayEngine()
        val ui = e.at(0, ev.copy(doorsOpen = mapOf("trunk" to true), chargePortOpen = true))
        assertTrue(ui.trunkOpen && ui.chargePortOpen)
        val unknown = StayEngine().at(0, ev.copy(doorsOpen = emptyMap(), chargePortOpen = null))
        assertFalse(unknown.trunkOpen || unknown.chargePortOpen)
    }

    @Test fun recircCloudNeedsClimateOnAndKnownClosedWindows() {
        val e = StayEngine()
        e.at(0, ev.copy(recirculationOn = true, hvacPowerOn = false))
        assertNull(e.at(25 * min, ev.copy(recirculationOn = true, hvacPowerOn = false)).recircMinutes)
        val unknown = StayEngine()
        val noWindows = ev.copy(recirculationOn = true, windows = mapOf("driver" to WINDOW_UNKNOWN))
        unknown.at(0, noWindows)
        assertNull(unknown.at(25 * min, noWindows).recircMinutes)
    }

    @Test fun sessionRestoreKeepsHourSchedule() {
        val e = StayEngine(); e.at(0)
        val restored = StayEngine(restored = e.session)
        val ui = restored.at(60 * min)
        assertEquals(60, ui.elapsedMinutes)
        assertEquals(NoticeKind.HOUR, ui.notice)
    }

    // ---- Restored stay, charging and edge states ---------------------------------------------------

    private fun saved() = StaySession(startMs = 0, socAtStart = 62, nextHourMs = 60 * min)

    // Back within 10 minutes of the last save -> the same stay continues.
    @Test fun restoreContinuesWithin10Min() {
        val e = StayEngine(restored = saved(), restoredSeenAtMs = 5 * min, restoredSocAtSave = 62)
        e.at(14 * min)
        assertEquals(0L, e.session!!.startMs)
    }

    // More than 10 minutes since the last save -> a new stay.
    @Test fun restoreStartsNewStayAfter10Min() {
        val e = StayEngine(restored = saved(), restoredSeenAtMs = 5 * min, restoredSocAtSave = 62)
        val ui = e.at(16 * min)
        assertEquals(16 * min, e.session!!.startMs)
        assertEquals(0, ui.elapsedMinutes)
    }

    // Charged while the app was away (SOC rose) -> a new stay, energy baseline = new SOC.
    @Test fun restoreStartsNewStayWhenSocRose() {
        val e = StayEngine(restored = saved(), restoredSeenAtMs = 5 * min, restoredSocAtSave = 52)
        e.at(6 * min, ev.copy(socPercent = 62))
        assertEquals(6 * min, e.session!!.startMs)
        assertEquals(62, e.session!!.socAtStart)
    }

    // More than 5 %p below the last saved value -> a different stay.
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

    // Below tomorrow's share the hero carries a status, never "약 0분".
    @Test fun heroNeverZeroMinutesBelowKeep() {
        val hero = StayEngine().at(0, ev.copy(socPercent = 7)).hero!!
        assertTrue(hero.belowKeep)
        assertEquals(22, hero.coveredKm)
        assertFalse(StayEngine().at(0).hero!!.belowKeep)
    }

    // Battery rose during the stay -> charging line, never "0.0kWh".
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

    // Unknown windows are reported, never "닫힘" (unknown is never the safe-looking value).
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

    // Stay timers run on the controller's monotonic clock; the engine only sees elapsed time,
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

    // 12 %p (9.3 kWh) gone in one minute is not climate use: no made-up kWh.
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
