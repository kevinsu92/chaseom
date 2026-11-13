package app.chaseom.stay

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/** Round 2 minor fixes C-1, C-2, C-4 (05_qa/PLEOS_COMPLIANCE_G.md). G2, G3, G5, G6 are in cabin/ and vehicle/. */
class Round2MinorFixesTest {

    // ---- C-1: CCP not supported is declared on <application> -----------------------------------------------------

    @Test fun c1RotaryNotSupportedIsDeclaredOnTheApplication() {
        val f = listOf(File("src/main/AndroidManifest.xml"), File("app/src/main/AndroidManifest.xml")).first { it.isFile }
        val doc = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }.newDocumentBuilder().parse(f)
        val app = doc.getElementsByTagName("application").item(0)
        val ns = "http://schemas.android.com/apk/res/android"
        val metas = (0 until app.childNodes.length).map { app.childNodes.item(it) }
            .filter { it.nodeName == "meta-data" }
            .associate { it.attributes.getNamedItemNS(ns, "name").nodeValue to it.attributes.getNamedItemNS(ns, "value")?.nodeValue }
        assertEquals("false", metas["com.hmg.rotary.ROTARY_SUPPORTED"])
    }

    // ---- C-2: Back goes one page back before the platform hides the app ------------------------------------------

    @Test fun c2BackFromSettingsReturnsToTheStay() {
        assertEquals(Page.NONE, backTarget(Page.SETTINGS, AppMode.FULL))
    }

    @Test fun c2BackFromAboutReturnsToWhereItWasOpened() {
        assertEquals(Page.SETTINGS, backTarget(Page.ABOUT, AppMode.FULL)) // opened from Settings
        AppMode.entries.filter { it != AppMode.FULL }.forEach {
            assertEquals(Page.NONE, backTarget(Page.ABOUT, it)) // no Settings in that mode
        }
    }

    @Test fun c2TopScreensLeaveBackToThePlatform() {
        AppMode.entries.forEach {
            assertNull(backTarget(Page.NONE, it))
            assertNull(backTarget(Page.ONBOARDING, it)) // the first screen has no earlier step
        }
    }

    // ---- C-4: the wait flag survives a process kill within the same stay -----------------------------------------

    private val flag = WaitFlag(atMs = 5_000_000L, wallMs = 1_763_000_000_000L)

    @Test fun c4RecordRoundTrips() {
        val r = WaitFlagRecord(stayStartMs = 3_000_000L, flag = flag)
        assertEquals(r, WaitFlagRecord.decode(r.encode()))
    }

    @Test fun c4FlagComesBackOnlyInTheStayItWasPlantedIn() {
        val r = WaitFlagRecord(stayStartMs = 3_000_000L, flag = flag)
        assertTrue(r.belongsTo(3_000_000L))
        assertFalse(r.belongsTo(3_000_001L)) // a new stay
        assertFalse(r.belongsTo(null)) // no stay restored (e.g. after a reboot, or over 10 minutes away)
    }

    @Test fun c4BrokenOrForeignRecordsAreDropped() {
        listOf(
            null, "", "garbage", "1,2,3", "2,3000000,5000000,1763000000000", // wrong version
            "1,0,5000000,1763000000000", // no stay start
            "1,3000000,2000000,1763000000000", // flag before the stay began
            "1,3000000,5000000,0", "1,3000000,x,1763000000000",
        ).forEach { assertNull(it, WaitFlagRecord.decode(it)) }
    }
}
