package app.chaseom.ui.scenes

import android.text.format.DateFormat
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.chaseom.R
import app.chaseom.stay.StayUi
import app.chaseom.stay.WaitFlags
import app.chaseom.stay.WaitOutcome
import app.chaseom.ui.theme.ChaseomUi
import app.chaseom.ui.theme.cui
import java.util.Date

/**
 * Wait forecast row under the island (P only): "몇 시까지 기다려요? (선택)" opens a few clock-time choices
 * (no keyboard); with a flag, one line says whether today's stay time passes it (estimate). Display only.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WaitFlagRow(ui: StayUi, actions: StayActions) {
    val f = ui.waitForecast
    if (f == null && !ui.waitFlagAvailable) return
    val k = cui()
    val ctx = LocalContext.current
    fun clock(ms: Long) = DateFormat.getTimeFormat(ctx).format(Date(ms))
    var choosing by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().padding(top = ChaseomUi.related), verticalArrangement = Arrangement.spacedBy(ChaseomUi.line)) {
        if (f == null) {
            if (!choosing) {
                LightPillButton(stringResource(R.string.wf_ask), { choosing = true })
                return@Column
            }
            Text(stringResource(R.string.wf_ask), style = ChaseomUi.body, color = k.text2)
            val now = System.currentTimeMillis()
            val cal = java.util.Calendar.getInstance().apply { timeInMillis = now }
            val choices = WaitFlags.choicesAt(cal.get(java.util.Calendar.HOUR_OF_DAY) * 60 + cal.get(java.util.Calendar.MINUTE))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                choices.forEach { m ->
                    // Same rounding as the flag itself, so the choice shows the time the flag will carry.
                    val wall = WaitFlags.plant(m, 0L, now).wallMs
                    LightPillButton(stringResource(R.string.wf_choice, clock(wall), choiceSpan(m)), {
                        choosing = false
                        actions.plantWaitFlag(m)
                    })
                }
                LightPillButton(stringResource(R.string.cd_close), { choosing = false })
            }
            return@Column
        }
        val t = clock(f.flag.wallMs)
        val line = when {
            f.airQuiet -> stringResource(R.string.wf_reaches, t)
            else -> when (f.outcome) {
            WaitOutcome.REACHES -> f.longMinutes?.let { stringResource(R.string.wf_long, t, duration(it)) }
                ?: stringResource(R.string.wf_reaches, t)
            WaitOutcome.SHORT -> stringResource(R.string.wf_short, t, duration(f.shortMinutes))
            WaitOutcome.BELOW_KEEP -> stringResource(R.string.wf_below, t)
            }
        }
        val size = app.chaseom.ui.theme.uiSize()
        val lines: @Composable (Modifier) -> Unit = { m ->
            Column(m.semantics { liveRegion = LiveRegionMode.Polite }) {
                // H2: flag line and its swap line at 36 sp (ISO 15008 glance); no maxLines, phrases kept together.
                Text(keepPhrases(line), style = ChaseomUi.flagLine(size), color = k.text1)
                if (!f.airQuiet && f.outcome == WaitOutcome.SHORT && f.swapGainMinutes != null) {
                    Text(keepPhrases(stringResource(R.string.wf_short_swap, signedDuration(f.swapGainMinutes))), style = ChaseomUi.flagSub(size), color = k.text1)
                }
                Text(stringResource(R.string.wf_note), style = ChaseomUi.caption, color = k.text2)
            }
        }
        if (size == ChaseomUi.Size.Small) {
            // H2 (design): on the 841 dp split the remove pill goes under the lines, so the 36 sp line gets the full
            // column (~790 dp instead of ~630) and the longest sentence stays within two lines.
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(ChaseomUi.line)) {
                lines(Modifier.fillMaxWidth())
                LightPillButton(stringResource(R.string.wf_remove), { actions.removeWaitFlag() })
            }
        } else {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                lines(Modifier.weight(1f))
                Spacer(Modifier.width(ChaseomUi.related))
                LightPillButton(stringResource(R.string.wf_remove), { actions.removeWaitFlag() })
            }
        }
    }
}

/**
 * External S1S2 #8: on Small "약 33분 / 짧아요" split mid-statement. Only the short tail of a phrase is bound with
 * no-break spaces: from "약 " to the phrase end ("약 2시간 29분 짧아요 (추정)", about 300 px), or else just "(추정)" to
 * the word before it. The rest still wraps at spaces, so a long phrase never breaks inside a Korean word (UAX 14
 * lets Hangul break between syllables once a no-break run is wider than the line).
 */
internal fun keepPhrases(text: String): String = text.split(" · ").joinToString(" · ") { phrase ->
    val i = phrase.indexOf("약 ")
    if (i >= 0) phrase.substring(0, i) + phrase.substring(i).replace(' ', ' ')
    else phrase.replace(" (추정)", " (추정)")
}

/** Flag mark: a pole from [top] to [bottom] at [x] with a small pennant to the right. */
fun DrawScope.waitFlagMark(x: Float, top: Float, bottom: Float, pole: Color, pennant: Color) {
    val w = 3.dp.toPx()
    drawLine(pole, Offset(x, top), Offset(x, bottom), strokeWidth = w)
    val ph = (bottom - top) * 0.38f
    val pw = ph * 1.3f
    val p = Path().apply {
        moveTo(x + w / 2, top)
        lineTo(x + w / 2 + pw, top + ph / 2)
        lineTo(x + w / 2, top + ph)
        close()
    }
    drawPath(p, pennant)
}

/** "+30분", "+1시간", "+1시간 30분": whole hours without a trailing "0분". */
@Composable
internal fun choiceSpan(minutes: Int): String = if (minutes >= 60 && minutes % 60 == 0) "${minutes / 60}시간" else duration(minutes)
