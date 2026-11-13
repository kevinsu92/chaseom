package app.chaseom.stay

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Wording rules (banned words, estimate marks, notices, rear seat, exchange card)
 * enforced on the shipped string resources. Every user-facing sentence lives in
 * res/values/strings_stay.xml (stay screens) or res/values/strings.xml (engineering).
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

    /** Banned-word pattern, verbatim. */
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
        // Design rules add "보장", "안전", "완료", "좋음", "OK", "지켜 드려요" on top of the banned list.
        val extra = Regex("보장|안전|완료|좋음|\\bOK\\b|지켜 드려요")
        val offenders = strings.filterValues { extra.containsMatchIn(it) }
        assertEquals("design-rule words: $offenders", emptyMap<String, String>(), offenders)
    }

    // H+calib: the correction line is an estimate mark, never a claim of accuracy.
    @Test fun calibrationLineIsAnEstimate() {
        val line = strings.getValue("h_calibrated")
        assertEquals("이 차 기준으로 보정됨 (추정)", line)
        assertTrue(line.endsWith("(추정)"))
        assertFalse(Regex("정확|측정 완료|실측|검증").containsMatchIn(line))
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

    // Safety rule: the K2 banner names the risk and two real actions; it never tells the person to
    // turn the climate off or down (a cold cabin can be the bigger danger).
    @Test fun k1AndK2NeverTellThePersonToCutTheClimate() {
        assertEquals("곧 내일 몫에 닿아요 · 계속 켜 두면 내일 몫을 쓰게 돼요 (앱은 공조를 끄지 않아요)", strings.getValue("k1"))
        assertEquals("내일 몫 아래로 내려갔어요 · 충전하거나 출발을 고려하세요", strings.getValue("k2"))
        listOf("k1", "k2").forEach {
            // The only allowed mention is the statement that the app itself never turns the climate off.
            val text = strings.getValue(it).replace("(앱은 공조를 끄지 않아요)", "")
            assertFalse(it, Regex("끄|꺼|줄이|낮추").containsMatchIn(text))
        }
    }

    // An air-source switch toast shows the switch name only, never minutes.
    @Test fun airSourceSwitchToastShowsNoMinutes() {
        val toastCode = java.io.File("src/main/java/app/chaseom/ui/scenes/StayScenes.kt").readText()
            .substringAfter("fun DialToast(").substringBefore("\n}\n")
        assertTrue(toastCode.contains("!showMinutes -> label"))
    }

    // Wait flag: display only, no reassurance when the flag is reached, a comparison always says "(추정)".
    @Test fun waitFlagWordingIsDisplayOnlyAndNeverReassures() {
        val keys = listOf("wf_ask", "wf_choice", "wf_remove", "wf_reaches", "wf_long", "wf_short", "wf_short_swap", "wf_below", "wf_note")
        keys.forEach { key ->
            val text = strings.getValue(key)
            assertFalse(key, Regex("괜찮|충분|넉넉|보장|안심|좋아요").containsMatchIn(text))
        }
        assertEquals("%1\$s 깃발", strings.getValue("wf_reaches"))
        assertTrue(strings.getValue("wf_short").contains("(추정)"))
        // S5: the reached line pairs with wf_short; numbers only, no reassurance words.
        assertEquals("%1\$s 깃발 · 오늘 머물 시간이 깃발보다 약 %2\$s 길어요 (추정)", strings.getValue("wf_long"))
        listOf("여유", "괜찮", "충분", "보장").forEach { assertFalse(it, strings.getValue("wf_long").contains(it)) }
        assertTrue(strings.getValue("wf_short_swap").contains("(추정)"))
        assertEquals("표시만 해요 · 앱은 공조를 바꾸지 않아요", strings.getValue("wf_note"))
        assertEquals("깃발 = 내가 고른 시각", strings.getValue("is_lg_flag"))
        (keys + "is_lg_flag").forEach { assertFalse(it, Regex("출발|서두르|떠날|바닥|끝").containsMatchIn(strings.getValue(it))) }
    }

    @Test fun keepShareNeverLockedAndAppDoesNotControlClimate() {
        assertTrue(strings.getValue("k1").contains("앱은 공조를 끄지 않아요"))
    }

    @Test fun computedValuesMarkedEstimate() {
        listOf("h_bar_meta", "x1", "x2", "x3", "e_energy_val", "e_keep_ok", "e_keep_low", "s_margin",
            "h_k2_hero", "h_k2_meta", "e_energy_small", "h_hvac_off_sub", "x1_l2").forEach {
            assertTrue("missing 추정: $it", strings.getValue(it).contains("추정"))
        }
        assertEquals("(추정)", strings["estimate"])
        assertTrue(strings.getValue("h_hero_value").startsWith("약"))
    }

    @Test fun rearSeatWording() {
        assertEquals("탑승 감지됨", strings["e_rear_detected"])
        assertEquals("감지 안 됨", strings["e_rear_not_detected"]) // sensor state only
        assertEquals("뒷좌석 직접 확인", strings["e_rear_always"])
        listOf("e_rear", "e_rear_detected", "e_rear_not_detected", "e_rear_always").forEach {
            assertFalse(it, Regex("비었|비어|없음|없어요").containsMatchIn(strings.getValue(it)))
        }
    }

    @Test fun exchangeOnlyDriverSeatAndBurnLine() {
        assertTrue(strings.getValue("x1").contains("운전석 열선"))
        // Small window two-line X1: same pair, the burn line still goes with it.
        assertEquals("설정 21°C + 운전석 열선 2단", strings["x1_l1"])
        assertEquals("%1\$s · 비슷한 체감 (추정)", strings["x1_l2"])
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
    // The island is always day, never a campsite, and always says it is decoration.
    @Test fun islandWordingDaytimeAndDecoration() {
        val island = strings.filterKeys { it.startsWith("is_") }
        assertTrue(island.isNotEmpty())
        val night = Regex("모닥불|캠프|캠핑|텐트|달빛|별|밤|잠자")
        assertEquals(emptyMap<String, String>(), island.filterValues { night.containsMatchIn(it) })
        // The cause chip says what draws the island and that nothing is measured inside.
        assertEquals("섬은 공조 설정과 머물 시간으로 그려요 · 실내 측정 아님", strings["is_legend"])
        assertTrue(strings.getValue("is_cd").contains("추정"))
    }

    // The island chip already says what draws it, so onboarding item 2 has no bracket.
    @Test fun onboardingItemTwoHasNoBracketNote() {
        assertEquals("공조 설정을 바꾸면 섬과 머물 수 있는 시간이 바로 바뀌어요", strings["onb_p2_body"])
    }

    // The round-1 island draws no seat heat, so no rock or "follows the heat" wording.
    @Test fun roundOneIslandSaysNothingAboutSeatHeat() {
        assertEquals(emptyMap<String, String>(), strings.filterValues { it.contains("바위") || it.contains("열선을 따라") || it.contains("장식") })
        // No labels on the picture; the legend names what each shape stands for, with no numbers.
        assertEquals(emptyList<String>(), strings.keys.filter { it.startsWith("is_rail_") })
        // The confirmed legend strings, letter for letter.
        assertEquals("해 = 설정 온도", strings["is_lg_sun"])
        assertEquals("부표 1개 = 30분", strings["is_lg_buoy"])
        // Build G S1: water and crates leave the legend (battery = header number, tomorrow = dock chip "내일 지킬 몫 n km").
        assertFalse(strings.containsKey("is_lg_water") || strings.containsKey("is_lg_keep"))
        assertEquals("내일 지킬 몫 %1\$dkm", strings["h_bar_keep"])
        assertEquals("깃발 = 내가 고른 시각", strings["is_lg_flag"])
        assertEquals("구름 = 내기순환 %1\$d분째 (공기 측정 아님)", strings["is_lg_air"])
        assertEquals("먼 비 = 와이퍼 AUTO 작동", strings["is_lg_rain"]) // AUTO and actually wiping only
        // The toast's ± minutes carry "(추정)" -> "설정 26°C · 풍량 6 · −21분 (추정)".
        assertEquals("%1\$s (추정)", strings["d_toast_delta"])
        // The toast's temperature always has "설정" in front, Large and Small alike.
        assertTrue(strings.getValue("hvac_temp").startsWith("설정 "))
        val toastCode = java.io.File("src/main/java/app/chaseom/ui/scenes/StayScenes.kt").readText()
            .substringAfter("fun DialToast(").substringBefore("\n}\n")
        assertFalse("the toast never builds a bare temperature", toastCode.contains("}°C\""))
        assertEquals("설정 26°C · 풍량 6 · −21분 (추정)",
            String.format(strings.getValue("d_toast_fmt"), "설정 26°C · 풍량 6", String.format(strings.getValue("d_toast_delta"), "−21분")))
        assertTrue(strings.getValue("d_toast_cap").contains("(추정)"))
        // Above the cap: a percentage of the stay time, never minutes (no "분"), always "(추정)".
        assertEquals("머물 시간 %1\$s%2\$d%% (추정)", strings.getValue("d_toast_cap_rate"))
        assertEquals("머물 시간 %1\$s50%% 이상 (추정)", strings.getValue("d_toast_cap_rate_over"))
        listOf("d_toast_cap_rate", "d_toast_cap_rate_over").forEach {
            val text = strings.getValue(it)
            assertTrue(it, text.startsWith("머물 시간 ") && text.contains("(추정)"))
            assertFalse(it, Regex("분|\\d+시간|괜찮|충분|넉넉|좋아요").containsMatchIn(text.removePrefix("머물 시간 ")))
        }
        // The minus sign is U+2212, as in the minutes toast; the over-cap string is used beyond the shown maximum.
        val capToastCode = java.io.File("src/main/java/app/chaseom/ui/scenes/StayScenes.kt").readText()
            .substringAfter("fun DialToast(").substringBefore("\n}\n")
        assertTrue(capToastCode.contains("abs(capRate) > app.chaseom.stay.EnergyModel.STAY_RATE_SHOWN_MAX"))
        assertTrue(capToastCode.contains("if (capRate > 0) \"+\" else \"−\""))
        // The transient sun number always says "설정" (never a bare temperature that reads as the cabin's).
        assertEquals("설정 %1\$s → %2\$s°C", strings["is_sun_change"])
        assertTrue(strings.getValue("hvac_temp").startsWith("설정 "))
        // Air wording: no "inside", no air-quality word, no reassurance, no sleep link.
        val air = strings.getValue("is_lg_air")
        assertFalse(Regex("실내|공기질|좋|나쁨|안전|괜찮|맑|자는|밤새").containsMatchIn(air.replace("공기 측정 아님", "")))
    }

    // Restored-stay and edge-state wording, letter for letter.
    @Test fun bundleBConfirmedWording() {
        val confirmed = mapOf(
            "is_hint_first" to "공조 다이얼을 바꾸면 섬과 남은 시간이 함께 바뀌어요",
            "h_k2_hero" to "내일 몫을 쓰고 있어요 (추정)",
            "h_k2_meta" to "내일 몫 %1\$dkm 중 약 %2\$dkm 남음 (추정)",
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
        // The about text describes only what the code does (no "recent battery trend").
        assertFalse(strings.getValue("about_model").contains("추세"))
        assertTrue(strings.getValue("about_model").contains("영하 10°C"))
        assertTrue(strings.getValue("about_coeff").contains("영하 10°C"))
    }

    // No imperative that invites playing with the climate while parked.
    @Test fun noImperativeDialPrompt() {
        val offenders = strings.filterValues { Regex("돌려 보세요|켜 보세요|올려 보세요").containsMatchIn(it) }
        assertEquals(emptyMap<String, String>(), offenders)
    }
}
