package app.chaseom.stay

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * QA wording rules (QA 조건(내부 문서) section 5, S1-a, S2-a/b, S4-a, S7-a, condition 3/4/13)
 * enforced on the shipped string resources. Every user-facing sentence lives in
 * res/values/strings_stay.xml (04_design + 05_qa) or res/values/strings.xml (engineering).
 */
class WordingRulesTest {

    private val strings: Map<String, String> by lazy {
        val dir = listOf(File("src/main/res/values"), File("app/src/main/res/values")).first { it.isDirectory }
        listOf("strings_stay.xml", "strings.xml").flatMap { name ->
            val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(File(dir, name))
            val nodes = doc.getElementsByTagName("string")
            (0 until nodes.length).map { i ->
                val n = nodes.item(i)
                n.attributes.getNamedItem("name").nodeValue to n.textContent
            }
        }.toMap()
    }

    /** Section 5 banned-word pattern, verbatim. */
    private val banned = Regex(
        "잠금|잠들|잠든|낮잠|수면|차박|밤샘|숙박|안전 보장|사고 방지|방치 예방|이상 없음|모두 확인|확인 완료|최초|유일|아무도|테슬라.*아이 감지|경고음 하나|숙면|이완|힐링|충전 필요 없|충분해요|이상 징후",
    )

    /** The owner's-manual warning and the "do not fall asleep" notices are the only allowed hits. */
    private val warningKeys = setOf("manual_quote", "n0_banner", "n_body")

    @Test fun noBannedWordsOutsideTheWarnings() {
        val offenders = strings.filterKeys { it !in warningKeys }.filterValues { banned.containsMatchIn(it) }
        assertEquals("banned words: $offenders", emptyMap<String, String>(), offenders)
    }

    @Test fun designRuleWordsAbsent() {
        // FLOWS W3/W8 add "보장", "안전", "완료", "좋음", "OK", "지켜 드려요" on top of the QA list.
        val extra = Regex("보장|안전|완료|좋음|\\bOK\\b|지켜 드려요")
        val offenders = strings.filterValues { extra.containsMatchIn(it) }
        assertEquals("design-rule words: $offenders", emptyMap<String, String>(), offenders)
    }

    @Test fun capWordingIsThreeHoursOrMore() {
        assertEquals("3시간 이상", strings["h_hero_cap"]) // shown instead of any value >= 180 min
    }

    @Test fun manualQuoteVerbatimWithSource() {
        assertEquals("\"히터나 에어컨을 켠 채 차량 안에서 수면을 취하지 마십시오. 질식으로 인해 사망할 수 있습니다.\"", strings["manual_quote"])
        assertEquals("현대자동차 취급설명서", strings["manual_source"])
        assertTrue(strings.getValue("n0_banner").contains("취급설명서"))
    }

    @Test fun noticesInformOnly() {
        listOf("n1_title", "n2_title", "n3_title", "n_body").forEach {
            assertFalse(it, Regex("감지|방지|보호|지켜").containsMatchIn(strings.getValue(it)))
        }
    }

    @Test fun keepShareNeverLockedAndAppDoesNotControlClimate() {
        assertTrue(strings.getValue("k1").contains("공조를 꺼야 지켜집니다"))
        assertTrue(strings.getValue("k1").contains("앱은 공조를 끄지 않아요"))
    }

    @Test fun computedValuesMarkedEstimate() {
        listOf("h_bar_meta", "x1", "x2", "x3", "e_energy_val", "e_keep_ok", "e_keep_low", "s_margin",
            "h_k2_hero", "h_k2_meta", "e_energy_small", "is_key_keep_k2", "h_hvac_off_sub").forEach {
            assertTrue("missing 추정: $it", strings.getValue(it).contains("추정"))
        }
        assertEquals("(추정)", strings["estimate"])
        assertTrue(strings.getValue("h_hero_value").startsWith("약"))
    }

    @Test fun rearSeatWording() {
        assertEquals("탑승 감지됨", strings["e_rear_detected"])
        assertEquals("감지 안 됨 — 직접 확인하세요", strings["e_rear_not_detected"])
        assertEquals("뒷좌석 직접 확인", strings["e_rear_always"])
        listOf("e_rear", "e_rear_detected", "e_rear_not_detected", "e_rear_always").forEach {
            assertFalse(it, Regex("비었|비어|없음|없어요").containsMatchIn(strings.getValue(it)))
        }
    }

    @Test fun exchangeOnlyDriverSeatAndBurnLine() {
        assertTrue(strings.getValue("x1").contains("운전석 열선"))
        listOf("x1", "x2", "x3").forEach { assertFalse(Regex("뒷좌석|2열|조수석").containsMatchIn(strings.getValue(it))) }
        assertTrue(strings.getValue("x_burn").contains("저온 화상"))
        assertTrue(strings.getValue("x_footer").contains("개인차"))
    }

    @Test fun reducedModeIdlingNotice() {
        assertTrue(strings.getValue("n_body_reduced").contains("시동을 켠 채 오래 머무르지 마세요"))
        assertTrue(strings.getValue("n0_banner_reduced_suffix").contains("시동"))
    }

    @Test fun drivingGlanceShortAndNumberFree() {
        listOf("drv_title", "drv_sub", "drv_sub_first_run").forEach {
            val s = strings.getValue(it)
            assertTrue("$it too long", s.length <= 20)
            assertFalse("$it has digits", s.any(Char::isDigit))
        }
    }
    // QA 10.2 L-3 / L-6: island is always day, never a campsite, and always says it is decoration.
    @Test fun islandWordingDaytimeAndDecoration() {
        val island = strings.filterKeys { it.startsWith("is_") }
        assertTrue(island.isNotEmpty())
        val night = Regex("모닥불|캠프|캠핑|텐트|달빛|별|밤|잠자")
        assertEquals(emptyMap<String, String>(), island.filterValues { night.containsMatchIn(it) })
        assertTrue(strings.getValue("is_legend").contains("장식"))
        assertTrue(strings.getValue("is_legend").contains("실내 온도"))
        assertTrue(strings.getValue("is_cd").contains("추정"))
    }

    // 05_qa REVIEW_COPY_B section 1: bundle B wording, letter for letter.
    @Test fun bundleBConfirmedWording() {
        val confirmed = mapOf(
            "is_key_follow" to "공조 설정·열선을 따라 바뀌어요",
            "is_legend" to "장식 그림 · 실내 온도·공기질 아님",
            "is_day" to "오늘 머물 시간 (3시간까지 표시)",
            "is_key_rock_l" to "왼쪽 바위 = 운전석 열선",
            "is_key_rock_r" to "오른쪽 바위 = 조수석 열선",
            "is_hint_first" to "공조 다이얼을 바꾸면 섬과 남은 시간이 함께 바뀌어요",
            "h_k2_hero" to "내일 몫을 쓰고 있어요 (추정)",
            "h_k2_meta" to "내일 몫 %1\$dkm 중 약 %2\$dkm 남음 (추정)",
            "is_key_keep_k2" to "배의 짐 = 내일 몫 · 지금 짐을 쓰고 있어요 (추정)",
            "is_keep_default" to "배의 짐 = 내일 몫 %1\$dkm · 기본값 (설정에서 바꿀 수 있어요)",
            "h_title_resumed" to "머무는 중 · 새로 시작했어요",
            "h_cap_note" to "3시간까지만 계산해 보여 줘요 · 1시간마다 경고를 다시 띄워요",
            "st_waiting_car" to "차와 연결하는 중이에요",
            "h_title_new" to "머무는 중 · 방금 시작했어요",
            "h_hvac_off" to "공조 꺼짐",
            "h_hvac_off_sub" to "지금은 공조로 쓰는 전력이 없어요 (추정)",
            "e_energy_charging" to "충전 중이라 계산하지 않았어요",
            "e_energy_small" to "1kWh 미만 (추정)",
            "e_energy_implausible" to "배터리 값이 크게 바뀌어 계산하지 않았어요",
            "is_hint_first_cap" to "공조 다이얼을 바꾸면 섬이 바뀌어요",
            "s_use_default_link" to "기본값(30km · 회사)으로 시작하기",
        )
        confirmed.forEach { (k, v) -> assertEquals(k, v, strings[k]) }
        // E-10: the about text describes only what the code does (no "recent battery trend").
        assertFalse(strings.getValue("about_model").contains("추세"))
        assertTrue(strings.getValue("about_model").contains("영하 10°C"))
        assertTrue(strings.getValue("about_coeff").contains("영하 10°C"))
    }

    // REVIEW_COPY_B: no imperative that invites playing with the climate while parked.
    @Test fun noImperativeDialPrompt() {
        val offenders = strings.filterValues { Regex("돌려 보세요|켜 보세요|올려 보세요").containsMatchIn(it) }
        assertEquals(emptyMap<String, String>(), offenders)
    }
}
