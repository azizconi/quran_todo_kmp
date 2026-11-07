package tj.app.quran_todo.ui.tajweed

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import kotlin.math.pow
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import tj.app.quran_todo.common.tajweed.TajweedGroup
import tj.app.quran_todo.common.tajweed.TajweedRule
import tj.app.quran_todo.common.theme.ThemeMode
import tj.app.quran_todo.common.theme.ThemePalette
import tj.app.quran_todo.ui.theme.qhColors

class TajweedPaletteTest {
    private fun channel(argb: Int, shift: Int): Double {
        val c = ((argb shr shift) and 0xFF) / 255.0
        return if (c <= 0.04045) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
    }

    private fun luminance(color: Color): Double {
        val argb = color.toArgb()
        return 0.2126 * channel(argb, 16) + 0.7152 * channel(argb, 8) + 0.0722 * channel(argb, 0)
    }

    private fun contrast(a: Color, b: Color): Double {
        val x = luminance(a)
        val y = luminance(b)
        return (maxOf(x, y) + 0.05) / (minOf(x, y) + 0.05)
    }

    /** [top] with [alpha] of it over [bottom]. */
    private fun over(top: Color, bottom: Color, alpha: Float): Color {
        val t = top.toArgb()
        val b = bottom.toArgb()
        fun mix(shift: Int) = ((((t shr shift) and 0xFF) * alpha + ((b shr shift) and 0xFF) * (1 - alpha)) + 0.5f).toInt()
        return Color(0xFF shl 24 or (mix(16) shl 16) or (mix(8) shl 8) or mix(0))
    }

    private fun lab(color: Color): Triple<Double, Double, Double> {
        val argb = color.toArgb()
        val r = channel(argb, 16)
        val g = channel(argb, 8)
        val b = channel(argb, 0)
        val x = (0.4124 * r + 0.3576 * g + 0.1805 * b) / 0.95047
        val y = 0.2126 * r + 0.7152 * g + 0.0722 * b
        val z = (0.0193 * r + 0.1192 * g + 0.9505 * b) / 1.08883
        fun f(t: Double) = if (t > 0.008856) t.pow(1.0 / 3) else 7.787 * t + 16.0 / 116
        return Triple(116 * f(y) - 16, 500 * (f(x) - f(y)), 200 * (f(y) - f(z)))
    }

    private fun distance(a: Color, b: Color): Double {
        val (l1, a1, b1) = lab(a)
        val (l2, a2, b2) = lab(b)
        return sqrt((l1 - l2).pow(2) + (a1 - a2).pow(2) + (b1 - b2).pow(2))
    }

    private val modes = listOf(ThemeMode.LIGHT, ThemeMode.DARK)

    @Test
    fun everyRuleHasAColourInBothThemes() {
        TajweedRule.entries.forEach { rule ->
            assertTrue(TajweedPalette.color(rule, dark = false) != TajweedPalette.color(rule, dark = true), "$rule is the same in both themes")
        }
    }

    @Test
    fun everyColourKeepsThreeToOneAgainstEveryBackgroundOfTheReaderAndTheLesson() {
        for (mode in modes) for (palette in ThemePalette.entries) {
            val tokens = qhColors(mode, palette)
            val backgrounds = mapOf("readerBg" to tokens.readerBg, "bg" to tokens.bg, "surface" to tokens.surface, "readerSurface" to tokens.readerSurface)
            for (rule in TajweedRule.entries) {
                val color = TajweedPalette.color(rule, tokens.isDark)
                for ((name, background) in backgrounds) {
                    val ratio = contrast(color, background)
                    assertTrue(ratio >= 3.0, "$rule on $name ($mode, $palette): $ratio")
                }
            }
        }
    }

    @Test
    fun theColoursAreReadableUnderTheWashOfTheWordThatIsRecited() {
        // The word that sounds gets a wash of the primary colour (0.20 in the light theme, 0.34 in the dark one) behind it; the current
        // ayah has its own faint tint under that. The wash is short, so the bar is lower: 2.2 to 1.
        for (mode in modes) for (palette in ThemePalette.entries) {
            val tokens = qhColors(mode, palette)
            val alpha = if (tokens.isDark) 0.34f else 0.20f
            val rows = listOf(tokens.readerBg, tokens.bg, over(tokens.primary, tokens.readerBg, if (tokens.isDark) 0.12f else 0.07f))
            for (rule in TajweedRule.entries) {
                val color = TajweedPalette.color(rule, tokens.isDark)
                for (row in rows) {
                    val ratio = contrast(color, over(tokens.primary, row, alpha))
                    assertTrue(ratio >= 2.2, "$rule under the wash ($mode, $palette): $ratio")
                }
            }
        }
    }

    @Test
    fun theSixGroupsHaveSixDifferentColours() {
        for (dark in listOf(false, true)) {
            val colors = TajweedGroup.entries.map { TajweedPalette.groupColor(it, dark) }
            assertEquals(6, colors.toSet().size)
        }
    }

    @Test
    fun aGroupThatIsOneColourIsOneColour() {
        for (dark in listOf(false, true)) {
            listOf(TajweedGroup.Silent, TajweedGroup.Iqlab, TajweedGroup.Qalqalah, TajweedGroup.Merge).forEach { group ->
                val colors = TajweedRule.entries.filter { it.group == group }.map { TajweedPalette.color(it, dark) }.toSet()
                assertEquals(1, colors.size, "$group has ${colors.size} colours")
            }
        }
    }

    @Test
    fun theFourMaddsGoFromLightToStrongByLength() {
        for (dark in listOf(false, true)) {
            val order = listOf(TajweedRule.MaddNormal, TajweedRule.MaddPermissible, TajweedRule.MaddObligatory, TajweedRule.MaddNecessary)
            val shades = order.map { luminance(TajweedPalette.color(it, dark)) }
            // Light to dark: the luminance falls with every step, in both themes (in the dark one the strongest red is the most saturated).
            shades.zipWithNext().forEachIndexed { index, (a, b) -> assertTrue(a > b, "dark=$dark step $index: $a then $b") }
        }
    }

    @Test
    fun theFourGreensAndTheFourRedsStayApart() {
        for (dark in listOf(false, true)) {
            listOf(
                listOf(TajweedRule.MaddNormal, TajweedRule.MaddPermissible, TajweedRule.MaddObligatory, TajweedRule.MaddNecessary),
                listOf(TajweedRule.Ghunna, TajweedRule.IdghamWithGhunna, TajweedRule.Ikhfa, TajweedRule.IkhfaShafawi),
            ).forEach { family ->
                for (i in family.indices) for (j in i + 1 until family.size) {
                    val gap = distance(TajweedPalette.color(family[i], dark), TajweedPalette.color(family[j], dark))
                    assertTrue(gap >= 10.0, "${family[i]} and ${family[j]} (dark=$dark): $gap")
                }
            }
        }
    }

    @Test
    fun theGroupsDoNotLookLikeEachOther() {
        for (dark in listOf(false, true)) {
            val groups = TajweedGroup.entries
            for (i in groups.indices) for (j in i + 1 until groups.size) {
                val gap = distance(TajweedPalette.groupColor(groups[i], dark), TajweedPalette.groupColor(groups[j], dark))
                assertTrue(gap >= 20.0, "${groups[i]} and ${groups[j]} (dark=$dark): $gap")
            }
        }
    }
}
