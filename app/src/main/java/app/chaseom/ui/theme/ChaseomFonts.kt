package app.chaseom.ui.theme

import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import java.io.File

/**
 * Pleos system font Asta Sans (with Hangul) loaded by weight from the platform's own files, so 500 / 600 / 700
 * render as real Medium / SemiBold / Bold instead of collapsing to Regular / Bold through the CJK fallback
 * (independent critique final verdict ②, QA ROUND1B_GATE: system font first, nothing bundled, no licence to add).
 * Any other device without these files keeps the default family.
 */
object ChaseomFonts {
    private const val DIR = "/product/fonts"
    private val weights = listOf(
        FontWeight.Light to "AstaSansOTF-Light.otf",
        FontWeight.Normal to "AstaSansOTF-Regular.otf",
        FontWeight.Medium to "AstaSansOTF-Medium.otf",
        FontWeight.SemiBold to "AstaSansOTF-SemiBold.otf",
        FontWeight.Bold to "AstaSansOTF-Bold.otf",
        FontWeight.ExtraBold to "AstaSansOTF-ExtraBold.otf",
    )

    val family: FontFamily by lazy {
        val fonts = weights.mapNotNull { (w, f) -> File(DIR, f).takeIf { it.canRead() }?.let { Font(it, w) } }
        if (fonts.size == weights.size) FontFamily(fonts) else FontFamily.Default
    }
}
