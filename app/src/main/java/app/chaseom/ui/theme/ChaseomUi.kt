package app.chaseom.ui.theme

// One world: island palette = UI palette.
// Drop this file next to PleosTokens.kt (which stays). Screens use these roles instead of raw sp/dp.
//   Type   : 5 roles (hero, title, body, caption, picture label) + hero tail / estimate.
//   Space  : 32 between blocks, 16 between related items, 8 between lines. Window margin 40 (Large) / 24 (Small).
//   Radius : 16 inline (banner 12 is the one exception, inside the inline family), 28 container, pill.
//   Colour : ink, navy (island water), amber (island sun), warning yellow / danger red (banners only). No blue, no green.
//   Line break: Korean by word (Compose TextStyle(lineBreak = LineBreak.Paragraph.copy(wordBreak = LineBreak.WordBreak.Phrase)), API 33+;
//               below 33 keep softWrap and avoid long unbroken runs). Never split a word.
//   Shadow : one level, only for the Small toast over the picture, day theme only.

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

object ChaseomUi {

    /** Large = window >= 1200 dp, Small = the 841 dp split. */
    enum class Size { Large, Small }

    /* ---------------------------------------------------------------- Type */
    // Korean breaks by word (API 33+; older platforms ignore it and keep softWrap).
    private val Base = TextStyle(fontFamily = ChaseomFonts.family, fontFeatureSettings = "tnum",
        lineBreak = androidx.compose.ui.text.style.LineBreak.Paragraph.copy(wordBreak = androidx.compose.ui.text.style.LineBreak.WordBreak.Phrase))

    // Pleos: four role families (Headline, Title, Body, Label). Sizes are unchanged; a role's variants differ
    // by weight only. The heaviest weight is Bold (700), never ExtraBold.
    private val Headline = Base.copy(fontWeight = FontWeight.Bold)
    private val TitleRole = Base.copy(fontWeight = FontWeight.SemiBold)
    private val BodyRole = Base.copy(fontWeight = FontWeight.Normal)
    private val LabelRole = Base.copy(fontWeight = FontWeight.Medium)

    /**
     * H2 (round 2, ISO 15008-level glance): the three key outputs read at a glance in the car. Their sizes live here,
     * in one place, as design set them. Large = full window, Small = the 841 dp split.
     */
    object Key {
        // Hero number: unchanged (already 30.5'/23.7' of arc at 750 mm on a 14.6" screen).
        const val HERO_L = 72; const val HERO_S = 56; const val HERO_LINE_L = 80; const val HERO_LINE_S = 64
        // "머물 수 있어요" after the number: 32/26 -> 40/36 (part of the hero group).
        const val TAIL_L = 40; const val TAIL_S = 36; const val TAIL_LINE_L = 48; const val TAIL_LINE_S = 44
        // ±분 toast: 24 -> 36 (17.1' on 14.6", ~20' on 17"; ISO 15008 minimum 16', recommended 20'). Pill min 48 dp grows.
        // Large line 40 / vertical padding 4 (design §5): a 2-line toast is 88 dp beside the 80 dp hero row, so it reaches
        // 4 dp into the 8 dp gaps above and below and touches nothing. One line stays 48 dp (the minimum).
        const val TOAST_L = 36; const val TOAST_S = 36; const val TOAST_LINE_L = 40; const val TOAST_LINE_S = 44
        const val TOAST_H_L = 48; const val TOAST_H_S = 48; const val TOAST_PAD_V_L = 4; const val TOAST_PAD_V_S = 7
        // Flag line and its swap line under it: 26 -> 36.
        const val FLAG_L = 36; const val FLAG_S = 36; const val FLAG_LINE_L = 44; const val FLAG_LINE_S = 44
    }

    /** Headline: hero number "약 2시간 47분". The one display-size text. */
    fun hero(s: Size) = Headline.copy(fontSize = (if (s == Size.Large) Key.HERO_L else Key.HERO_S).sp,
        lineHeight = (if (s == Size.Large) Key.HERO_LINE_L else Key.HERO_LINE_S).sp, letterSpacing = (-0.5).sp)
    /** The ±분 dial toast text (SemiBold). */
    fun toast(s: Size) = LabelRole.copy(fontSize = (if (s == Size.Large) Key.TOAST_L else Key.TOAST_S).sp,
        lineHeight = (if (s == Size.Large) Key.TOAST_LINE_L else Key.TOAST_LINE_S).sp, fontWeight = FontWeight.SemiBold)
    fun toastHeight(s: Size): Dp = (if (s == Size.Large) Key.TOAST_H_L else Key.TOAST_H_S).dp
    fun toastPadV(s: Size): Dp = (if (s == Size.Large) Key.TOAST_PAD_V_L else Key.TOAST_PAD_V_S).dp
    /** The flag line "7:00 깃발 · 오늘 머물 시간이 깃발보다 약 n 길어요 (추정)" (SemiBold). */
    fun flagLine(s: Size) = BodyRole.copy(fontSize = (if (s == Size.Large) Key.FLAG_L else Key.FLAG_S).sp,
        lineHeight = (if (s == Size.Large) Key.FLAG_LINE_L else Key.FLAG_LINE_S).sp, fontWeight = FontWeight.SemiBold)
    /** The swap line under a short flag ("자리를 바꾸면 …"), same size, Regular. */
    fun flagSub(s: Size) = flagLine(s).copy(fontWeight = FontWeight.Normal)
    /** K2 hero sentence "내일 몫을 쓰고 있어요". */
    fun heroSentence(s: Size) = hero(s).copy(fontSize = if (s == Size.Large) 56.sp else 44.sp, lineHeight = if (s == Size.Large) 64.sp else 52.sp)
    /** "머물 수 있어요" after the number, on its baseline. */
    fun heroTail(s: Size) = TitleRole.copy(fontSize = (if (s == Size.Large) Key.TAIL_L else Key.TAIL_S).sp,
        lineHeight = (if (s == Size.Large) Key.TAIL_LINE_L else Key.TAIL_LINE_S).sp)
    /** "(추정)" = 40 % of the hero, Regular, text3. Same size wherever it follows a hero.
     *  Every "(추정)" stays (17 lines, all different estimates). text3 #6E727B passes only on bg #F7F8FA / white (4.54:1);
     *  on any darker grey surface use text2. */
    fun estimate(s: Size) = LabelRole.copy(fontSize = if (s == Size.Large) 29.sp else 24.sp,   // Small 24 = the 24 sp floor, not 40 %
        lineHeight = 36.sp, fontWeight = FontWeight.Normal)

    /** Title: card first line, sheet title, toast. [status] = the status line (Medium, text2). */
    val title = TitleRole.copy(fontSize = 28.sp, lineHeight = 36.sp)
    val status = title.copy(fontWeight = FontWeight.Medium)
    /** Body: description line under the hero, receipt rows (label Regular / value Medium), settings help. */
    val body = BodyRole.copy(fontSize = 26.sp, lineHeight = 34.sp)
    val bodyValue = body.copy(fontWeight = FontWeight.Medium)
    /** Caption: cause line under the picture, card second line, source lines. 24 sp floor. */
    val caption = LabelRole.copy(fontSize = 24.sp, lineHeight = 32.sp)
    val banner = caption.copy(fontWeight = FontWeight.SemiBold)
    /** Picture label: "설정 °C" and the sea rail (24 sp Bold, 24 dp line icon in front of each rail label). */
    val pictureLabel = LabelRole.copy(fontSize = 24.sp, lineHeight = 32.sp, fontWeight = FontWeight.Bold)
    /** Driving glance keeps the driving sizes: title 36, sub 30; app icon 160 dp above. */
    val glanceTitle = Headline.copy(fontSize = 36.sp, lineHeight = 44.sp)
    val glanceSub = BodyRole.copy(fontSize = 30.sp, lineHeight = 38.sp)
    /** Onboarding headline (Large 48 / Small 36). */
    fun onboardingTitle(s: Size) = Headline.copy(fontSize = if (s == Size.Large) 48.sp else 36.sp, lineHeight = if (s == Size.Large) 60.sp else 46.sp)

    /* ---------------------------------------------------------------- Space */
    val block: Dp = 32.dp      // between blocks (hero group / picture / cards / sections)
    val related: Dp = 16.dp    // between related items (picture -> cause line, items in a row)
    val line: Dp = 8.dp        // between lines of one group (status -> hero -> description; banners)
    fun margin(s: Size): Dp = if (s == Size.Large) 40.dp else 24.dp
    val readableMax: Dp = 960.dp

    /* ---------------------------------------------------------------- Shape */
    val rInline = RoundedCornerShape(16.dp)
    val rBanner = RoundedCornerShape(12.dp)
    val rContainer = RoundedCornerShape(28.dp)
    val rPill = RoundedCornerShape(percent = 50)

    /* ---------------------------------------------------------------- Sizes */
    val bannerHeight = 56.dp; val bannerBar = 6.dp; val bannerIcon = 28.dp; val bannerGap = 8.dp
    val dirCircle = 32.dp; val toastDirCircle = 28.dp   // toast height and text: toastHeight(s) / toast(s), Key above
    val buttonHeight = 64.dp; val pillHeight = 48.dp; val touchMin = 76.dp   // visual 64 / 48, hit area always 76
    val cardBar = 8.dp; val cardPadH = 32.dp; val cardPadV = 24.dp; val cardOutline = 1.dp
    val iconInline = 28.dp; val iconPoint = 36.dp; val iconPointCircle = 64.dp; val iconButton = 40.dp; val glanceIcon = 160.dp
    val receiptWidthLarge = 800.dp; val receiptRow = 56.dp
    fun receiptLabelCol(s: Size): Dp = if (s == Size.Large) 224.dp else 168.dp
    val divider = 1.dp
    /** Onboarding island band: 62 % (Large) / 72 % (Small) of the picture height, bottom-aligned, no labels. */
    fun onboardingPictureFraction(s: Size) = if (s == Size.Large) 0.62f else 0.72f

    /* ---------------------------------------------------------------- Colour (4 roles) */
    data class Colors(
        val bg: Color, val surface: Color, val line: Color, val tint: Color,
        val text1: Color, val text2: Color, val text3: Color,
        val primary: Color, val onPrimary: Color, val selected: Color, val onSelected: Color,
        val safeBg: Color, val safeBar: Color, val safeFg: Color,
        val dangerBg: Color, val dangerBar: Color, val dangerFg: Color,
        val toast: Color, val onToast: Color, val scrim: Color,
        val pictureSkyTop: Color,
    )
    val amber = Color(0xFFFFB020)          // island sun = brand: sun, down circle, card bar, caution icons
    val amberRim = Color(0xFFE08A00)
    val navy = Color(0xFF1F3B5C)           // island deep water = primary button (day)
    val Day = Colors(
        bg = Color(0xFFF7F8FA), surface = Color.White, line = Color(0xFFE3E6EB), tint = Color(0x0D131417),
        text1 = Color(0xFF131417), text2 = Color(0xFF4A4E57), text3 = Color(0xFF6E727B),
        primary = navy, onPrimary = Color.White, selected = Color(0xFF131417), onSelected = Color.White,
        safeBg = Color(0xFFFFC224), safeBar = Color(0xFFFFC224),   // N0 filled warning yellow (safety strength)
           // = warning token, same as the crate K2 outline
         safeFg = Color(0xFF131417),
        dangerBg = Color(0xFFA8231C), dangerBar = Color(0xFFA8231C), dangerFg = Color.White,   // #A8231C + white 7.19:1
        toast = Color(0xF01C2129), onToast = Color.White, scrim = Color(0x730B0F14),
        pictureSkyTop = Color(0xFFC9E0F3),
    )
    val Night = Colors(
        bg = Color(0xFF0B0C0E), surface = Color(0xFF24272C), line = Color(0xFF2F3338), tint = Color(0x14FFFFFF),
        text1 = Color(0xF5FFFFFF), text2 = Color(0xFFC3C7CE),   // white 96 % (AAOS)
         text3 = Color(0xFF9AA0A8),
        primary = Color(0xFF8DB4D9), onPrimary = Color(0xFF0F1D2E), selected = Color(0xFFF2F3F5), onSelected = Color(0xFF131417),
        safeBg = Color(0xFFFFC224), safeBar = Color(0xFFFFC224), safeFg = Color(0xFF131417),
        dangerBg = Color(0xFFA8231C), dangerBar = Color(0xFFA8231C), dangerFg = Color.White,   // banners keep their day fills and text at night
        toast = Color(0xF5F2F3F5), onToast = Color(0xFF131417), scrim = Color(0x99000000),
        pictureSkyTop = Color(0xFF8D9DAA),   // #C9E0F3 under the 0.3 night veil
    )
    // Up circle: text1 fill with a bg-coloured triangle (ink by day, white by night). Down circle: amber with an ink triangle.
}
