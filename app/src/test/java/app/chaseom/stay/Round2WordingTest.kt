package app.chaseom.stay

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/** Round 2: QA recommendations R1 and R2 and the (d) about strings. */
class Round2WordingTest {

    private val strings: Map<String, String> by lazy {
        val dir = listOf(File("src/main/res/values"), File("app/src/main/res/values")).first { it.isDirectory }
        listOf("strings_stay.xml", "strings.xml").flatMap { name ->
            val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(File(dir, name))
            val nodes = doc.getElementsByTagName("string")
            (0 until nodes.length).map { i -> nodes.item(i).attributes.getNamedItem("name").nodeValue to nodes.item(i).textContent }
        }.toMap()
    }

    // R1: wf_long names the same subject as wf_short, so "깃발보다 길어요" cannot read as "the flag is long".
    @Test fun r1WfLongNamesItsSubject() {
        val subject = "오늘 머물 시간이 깃발보다 약 %2\$s "
        assertTrue(strings.getValue("wf_short").contains(subject))
        assertTrue(strings.getValue("wf_long").contains(subject))
        assertEquals(
            strings.getValue("wf_short").replace("짧아요", "길어요"),
            strings.getValue("wf_long"),
        )
    }

    // R2: the fold line carries the same qualifiers as the legend lines it summarises.
    @Test fun r2FoldLineCarriesTheLegendQualifiers() {
        val raw = strings.getValue("is_fold_line")
        val fold = raw.replace(' ', ' ')
        assertTrue(strings.getValue("is_lg_air").contains("(공기 측정 아님)"))
        assertTrue(strings.getValue("is_lg_rain").contains("AUTO"))
        assertTrue(fold.contains("구름 = 내기순환 (공기 측정 아님)"))
        assertTrue(fold.contains("먼 비 = 와이퍼 AUTO"))
        assertEquals("배터리 = 위 숫자 줄 · 내일 몫 = 부두 칩 · 구름 = 내기순환 (공기 측정 아님) · 먼 비 = 와이퍼 AUTO", fold)
    }

    // R2 follow-up: on the Small screen the line wrapped between "먼" and "비".
    // A no-break space keeps the term together; it is the only one in the line.
    @Test fun r2FarRainDoesNotWrap() {
        val raw = strings.getValue("is_fold_line")
        assertTrue(raw.contains("먼 비"))
        assertEquals(1, raw.count { it == ' ' })
    }

    // (d): the FULL-mode about strings state the 15 C clamp and the -10 C fallback, keep the rest of the table, and
    // keep the estimate wording; the default strings are unchanged.
    @Test fun fullModeAboutStrings() {
        val coeff = strings.getValue("about_coeff")
        val coeffFull = strings.getValue("about_coeff_full")
        assertTrue(coeffFull.contains("15°C보다 따뜻하면 15°C"))
        assertTrue(coeffFull.contains("못 읽으면 영하 10°C"))
        assertTrue(coeffFull.contains("출처 기반 추정"))
        // Everything after the heating term is the same table.
        assertEquals(coeff.substringAfter("· 냉방"), coeffFull.substringAfter("· 냉방"))
        val model = strings.getValue("about_model_full")
        assertTrue(model.contains("추정"))
        assertTrue(model.contains("15°C") && model.contains("영하 10°C"))
        assertFalse(model.contains("더 추우면")) // the build G-only phrase
    }
}
