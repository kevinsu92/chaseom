package app.chaseom.stay

import app.chaseom.cabin.CabinState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * H2 item 8: the stay engine hides the estimate when SOC or the climate has not been confirmed by the car for 60 s,
 * and shows it again (with the same numbers, no toast) once a value arrives.
 */
class StaleValueTest {

    private val s = 1_000L
    // The demo state (SOC 19 %, 22 C fan 3): 167 min at the build G rule.
    private val demo = CabinState(
        connected = true, socPercent = 19, socReadable = true, batteryCapacityKwh = 77.4f, fuelTypes = listOf(10),
        hvacPowerOn = true, acOn = false, fanSpeed = 3, driverSetTempC = 22f, recirculationOn = false, driverSeatHeat = 0,
        driverDoorOpen = false, windows = mapOf("driver" to 0, "passenger" to 0, "rearLeft" to 0, "rearRight" to 0),
    )
    private val settings = StaySettings(keepKm = 30, label = "work")
    private fun StayEngine.at(t: Long, c: CabinState) = update(true, c, settings, t, 12)
    private fun stamped(c: CabinState, soc: Long?, climate: Long?) = c.copy(socConfirmedAtMs = soc, climateConfirmedAtMs = climate)

    @Test fun unstampedStateKeepsTheDemoNumbers() {
        val e = StayEngine()
        assertEquals(167, e.at(0, demo).hero!!.minutes)
        assertEquals(167, e.at(10 * 60 * s, demo).hero!!.minutes) // no stamps = not judged, at any age
    }

    @Test fun freshStampsGiveTheSameNumbersAsBefore() {
        val e = StayEngine()
        val t = 5 * 60 * s
        e.at(0, stamped(demo, 0, 0))
        val h = e.at(t, stamped(demo, t - 10 * s, t - 10 * s)).hero!!
        assertFalse(h.unconfirmed)
        assertEquals(167, h.minutes)
        // The other two demo scenes as well.
        assertEquals(146, StayEngine().at(0, stamped(demo.copy(driverSetTempC = 26f, fanSpeed = 6), 0, 0)).hero!!.minutes)
        assertEquals(162, StayEngine().at(0, stamped(demo.copy(driverSetTempC = 21f, fanSpeed = 6, driverSeatHeat = 2), 0, 0)).hero!!.minutes)
    }

    @Test fun socOlderThanSixtySecondsHidesTheEstimate() {
        val e = StayEngine()
        e.at(0, stamped(demo, 0, 0))
        assertFalse(e.at(60 * s, stamped(demo, 0, 60 * s)).hero!!.unconfirmed)
        val ui = e.at(61 * s, stamped(demo, 0, 61 * s))
        val h = ui.hero!!
        assertTrue(h.unconfirmed)
        assertNull(h.minutes)
        assertNull(ui.bar)
        assertNull(ui.keepBanner)
        assertNull(ui.exchange)
        assertNull(ui.toast)
    }

    @Test fun climateOlderThanSixtySecondsHidesTheEstimate() {
        val e = StayEngine()
        e.at(0, stamped(demo, 0, 0))
        assertTrue(e.at(61 * s, stamped(demo, 61 * s, 0)).hero!!.unconfirmed)
    }

    @Test fun aNewValueBringsTheSameEstimateBackWithoutAToast() {
        val e = StayEngine()
        e.at(0, stamped(demo, 0, 0))
        assertTrue(e.at(90 * s, stamped(demo, 0, 0)).hero!!.unconfirmed)
        val back = e.at(91 * s, stamped(demo, 91 * s, 91 * s))
        assertFalse(back.hero!!.unconfirmed)
        assertEquals(167, back.hero!!.minutes)
        assertNotNull(back.bar)
        assertNull("the gap is not a climate change by the driver", back.toast)
        // A real dial change after that still raises the toast as before.
        assertNotNull(e.at(92 * s, stamped(demo.copy(driverSetTempC = 26f, fanSpeed = 6), 92 * s, 92 * s)).toast)
    }

    // Climate unchanged for 30 min of driving: its stamp is old, but the stay start gives it the full 60 s to be re-read.
    @Test fun oldStampAtStayStartGetsTheFullWindow() {
        val e = StayEngine()
        val park = 30 * 60 * s
        assertFalse(e.at(park, stamped(demo, park, 0)).hero!!.unconfirmed)
        assertFalse(e.at(park + 60 * s, stamped(demo, park + 60 * s, 0)).hero!!.unconfirmed)
        assertTrue(e.at(park + 61 * s, stamped(demo, park + 61 * s, 0)).hero!!.unconfirmed)
    }

    @Test fun graceRestartsWithTheNextStay() {
        val e = StayEngine()
        e.at(0, stamped(demo, 0, 0))
        assertTrue(e.at(61 * s, stamped(demo, 0, 0)).hero!!.unconfirmed)
        e.update(false, demo, settings, 62 * s, 12) // drove off
        assertFalse(e.at(100 * s, stamped(demo, 0, 0)).hero!!.unconfirmed)
    }

    @Test fun reducedModeIsUnchanged() {
        val gas = stamped(demo.copy(fuelTypes = listOf(1)), 0, 0)
        val e = StayEngine()
        e.at(0, gas)
        assertNull(e.at(5 * 60 * s, gas).hero) // no energy budget, nothing to hide
    }

    @Test fun noWaitForecastFromAnUnconfirmedHero() {
        val hero = Hero(minutes = null, overCap = false, hvacOff = false, unconfirmed = true)
        val flag = WaitFlag(atMs = 30 * 60 * s, wallMs = 0)
        assertNull(WaitFlags.forecast(flag, 0, hero, null, night = false))
    }

    @Test fun wording() {
        val dir = listOf(File("src/main/res/values"), File("app/src/main/res/values")).first { it.isDirectory }
        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(File(dir, "strings_stay.xml"))
        val nodes = doc.getElementsByTagName("string")
        val strings = (0 until nodes.length).associate { i ->
            nodes.item(i).attributes.getNamedItem("name").nodeValue to nodes.item(i).textContent
        }
        assertEquals("확인 안 됨", strings["h_unconfirmed"])
        val sub = strings.getValue("h_unconfirmed_sub")
        assertTrue(sub.contains("1분"))
        assertFalse("no number of minutes left in the line", Regex("약 |\\(추정\\)").containsMatchIn(sub))
    }
}
