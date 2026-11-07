package tj.app.quran_todo.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import tj.app.quran_todo.common.theme.ThemeMode
import tj.app.quran_todo.common.theme.ThemePalette

class QhColorsTest {
    private val combinations = ThemePalette.entries.flatMap { palette ->
        ThemeMode.entries.map { mode -> Triple(mode, palette, qhColors(mode, palette)) }
    }

    private fun contrast(a: Color, b: Color): Float {
        val l1 = maxOf(a.luminance(), b.luminance())
        val l2 = minOf(a.luminance(), b.luminance())
        return (l1 + 0.05f) / (l2 + 0.05f)
    }

    private fun assertContrast(minimum: Float, foreground: Color, background: Color, what: String) {
        val ratio = contrast(foreground, background)
        assertTrue(ratio >= minimum, "$what: contrast $ratio < $minimum")
    }

    @Test
    fun bodyTextHasAaContrastEverywhere() {
        combinations.forEach { (mode, palette, c) ->
            val where = "$palette/$mode"
            listOf(c.bg, c.surface, c.surfaceMuted, c.readerBg, c.readerSurface).forEach { background ->
                assertContrast(4.5f, c.ink, background, "$where ink")
            }
            listOf(c.bg, c.surface, c.readerBg).forEach { background ->
                assertContrast(4.5f, c.inkSecondary, background, "$where inkSecondary")
            }
        }
    }

    @Test
    fun filledControlsKeepReadableContent() {
        combinations.forEach { (mode, palette, c) ->
            val where = "$palette/$mode"
            assertContrast(4.5f, c.onPrimary, c.primary, "$where onPrimary/primary")
            assertContrast(4.5f, c.onPrimaryContainer, c.primaryContainer, "$where onPrimaryContainer")
            assertContrast(4.5f, c.onDanger, c.danger, "$where onDanger/danger")
            assertContrast(4.5f, c.onLearned, c.learned, "$where onLearned/learned")
            // Status pill labels are `ink` on the status containers.
            listOf(c.learnedContainer, c.learningContainer, c.dueContainer).forEach { container ->
                assertContrast(4.5f, c.ink, container, "$where ink on status container")
            }
        }
    }

    @Test
    fun statusColorsDoNotDependOnPalette() {
        ThemeMode.entries.forEach { mode ->
            val reference = qhColors(mode, ThemePalette.FOREST)
            ThemePalette.entries.forEach { palette ->
                val c = qhColors(mode, palette)
                assertEquals(reference.learned, c.learned)
                assertEquals(reference.learnedContainer, c.learnedContainer)
                assertEquals(reference.learning, c.learning)
                assertEquals(reference.due, c.due)
                assertEquals(reference.gold, c.gold)
                assertEquals(reference.danger, c.danger)
            }
        }
    }

    @Test
    fun paletteOnlyRecolorsBrandBackgroundAndPaper() {
        val forest = qhColors(ThemeMode.LIGHT, ThemePalette.FOREST)
        val sand = qhColors(ThemeMode.LIGHT, ThemePalette.SAND)
        assertEquals(Color(0xFF8C6A1E), sand.primary)
        assertEquals(Color(0xFFF7F2E6), sand.bg)
        assertEquals(Color(0xFFFBF6EA), sand.readerBg)
        assertEquals(forest.surface, sand.surface)
        assertEquals(forest.ink, sand.ink)
        assertEquals(forest.hairline, sand.hairline)
    }

    @Test
    fun readerHighlightIsTranslucentPrimary() {
        val light = qhColors(ThemeMode.LIGHT, ThemePalette.OCEAN)
        val dark = qhColors(ThemeMode.DARK, ThemePalette.OCEAN)
        assertEquals(0.07f, light.readerHighlight.alpha, 0.005f)
        assertEquals(0.12f, dark.readerHighlight.alpha, 0.005f)
        assertEquals(light.primary.copy(alpha = 1f), light.readerHighlight.copy(alpha = 1f))
        assertTrue(dark.isDark && !light.isDark)
    }
}
