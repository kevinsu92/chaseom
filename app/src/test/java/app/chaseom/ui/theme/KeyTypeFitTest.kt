package app.chaseom.ui.theme

import app.chaseom.ui.theme.ChaseomUi.Key
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * H2 type (round 2, ISO 15008-level glance, internal note): the hero time, the ±분 toast and the flag
 * line at design's sizes still fit both windows (Large = 1682 dp app area, Small = the 841 dp 1:1 split).
 *
 * The car font (AstaSans, /product/fonts) is not on the JVM, so widths use upper-bound advances in em:
 * Hangul 1.0, digit and other Latin/punctuation 0.62, space 0.3. Design measured the same strings with Malgun Gothic
 * Bold (wider than AstaSans) and got a little less, so a pass here is a pass on the car.
 */
class KeyTypeFitTest {
    private fun em(c: Char): Float = when {
        c in '가'..'힣' -> 1.0f
        c == ' ' || c == ' ' -> 0.3f
        else -> 0.62f
    }
    private fun width(text: String, sp: Int): Float = text.sumOf { em(it).toDouble() }.toFloat() * sp
    private fun lines(text: String, sp: Int, avail: Float) = Math.ceil((width(text, sp) / avail).toDouble()).toInt()

    private val L = ChaseomUi.Size.Large
    private val S = ChaseomUi.Size.Small
    private fun column(s: ChaseomUi.Size) = if (s == L) 1682f - 2 * 40f else 841f - 2 * 24f

    private fun heroRow(s: ChaseomUi.Size, v: String) =
        width(v, if (s == L) Key.HERO_L else Key.HERO_S) + 16f +
            width("머물 수 있어요", if (s == L) Key.TAIL_L else Key.TAIL_S) + 16f + width("(추정)", if (s == L) 29 else 24)

    // The widest values the app shows (3 h cap; "3시간 이상" at the cap) and the longest real toast/flag strings.
    private val heroValues = listOf("약 2시간 59분", "3시간 이상", "약 59분")
    private val longToast = "설정 26°C · 풍량 6 · 운전석 열선 2 · −1시간 21분 (추정)"
    private val longFlags = listOf(
        "오후 7:00 깃발 · 오늘 머물 시간이 깃발보다 약 1시간 17분 길어요 (추정)",
        "오후 12:30 깃발 · 오늘 머물 시간이 깃발보다 약 2시간 59분 짧아요 (추정)",
        "오후 7:00 깃발 · 지금은 내일 몫 아래라 비교하지 않아요",
    )
    private val toastPad = 2 * 22f

    @Test fun designSizes() {
        assertEquals(72, Key.HERO_L); assertEquals(56, Key.HERO_S)
        assertEquals(40, Key.TAIL_L); assertEquals(36, Key.TAIL_S)
        assertEquals(36, Key.TOAST_L); assertEquals(36, Key.TOAST_S)
        assertEquals(36, Key.FLAG_L); assertEquals(36, Key.FLAG_S)
    }

    @Test fun heroRowFitsOneLineOnBothWindows() {
        for (s in listOf(L, S)) for (v in heroValues) {
            val row = heroRow(s, v)
            assertTrue("$s hero row '$v' ${row}dp > column ${column(s)}dp", row <= column(s))
        }
    }

    @Test fun largeToastGetsWhatTheHeroLeavesAndStaysWithinTwoLines() {
        // HeroRowWithToast: the toast is measured with (column - hero row - 24 dp gap); its text has 2 x 22 dp padding.
        for (v in heroValues) {
            val avail = column(L) - heroRow(L, v) - 24f - toastPad
            assertTrue("hero '$v' leaves only ${avail}dp for the toast", avail >= 300f)
            val n = lines(longToast, Key.TOAST_L, avail)
            assertTrue("hero '$v': longest toast takes $n lines in ${avail}dp", n <= 2)
        }
    }

    @Test fun smallToastFitsThreeLinesEvenBesideTheSunLabel() {
        // ISLAND_TOP: the toast box ends 32 dp before the "설정 °C" label while it shows (worst ~470 dp), else the column.
        val worst = 470f - toastPad
        assertTrue(lines(longToast, Key.TOAST_S, worst) <= 3)
        assertTrue(lines(longToast, Key.TOAST_S, column(S) - toastPad) <= 2)
    }

    @Test fun toastPillGrowsFromItsMinimum() {
        // The pill is heightIn(min = 48 dp) and grows with its lines, never clips. One Large line stays at the minimum.
        assertEquals(48, Key.TOAST_H_L); assertEquals(48, Key.TOAST_H_S)
        assertTrue(Key.TOAST_LINE_L + 2 * Key.TOAST_PAD_V_L <= Key.TOAST_H_L)
    }

    @Test fun largeTwoLineToastStaysInsideTheGapsAroundTheHeroRow() {
        // design §5: centred on the hero row, a 2-line Large toast may reach into the 8 dp gaps above and below, not past.
        val twoLines = 2 * Key.TOAST_LINE_L + 2 * Key.TOAST_PAD_V_L
        assertTrue("2-line toast ${twoLines}dp vs hero row ${Key.HERO_LINE_L}dp", twoLines - Key.HERO_LINE_L <= 2 * 8)
    }

    @Test fun flagLineStaysWithinTwoLines() {
        // Large: beside the 깃발 내리기 pill (~200 dp + 16 dp gap). Small: the pill is under the line (full column).
        for (f in longFlags) {
            assertTrue("L '$f'", lines(f, Key.FLAG_L, column(L) - 216f) <= 2)
            assertTrue("S '$f'", lines(f, Key.FLAG_S, column(S)) <= 2)
        }
    }

    @Test fun keyOutputsAreNeverBelowTheOldSizes() {
        // H2 only enlarges: hero 72/56, tail 32/26, toast 24, flag 26 were G's sizes.
        assertTrue(Key.HERO_L >= 72 && Key.HERO_S >= 56)
        assertTrue(Key.TAIL_L >= 32 && Key.TAIL_S >= 26)
        assertTrue(Key.TOAST_L >= 24 && Key.TOAST_S >= 24)
        assertTrue(Key.FLAG_L >= 26 && Key.FLAG_S >= 26)
    }
}
