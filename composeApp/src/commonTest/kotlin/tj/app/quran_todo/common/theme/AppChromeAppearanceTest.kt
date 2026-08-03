package tj.app.quran_todo.common.theme

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AppChromeAppearanceTest {

    @Test
    fun forestLightChromeUsesTheLibraryBackground() {
        val appearance = appChromeAppearance(ThemeMode.LIGHT, ThemePalette.FOREST)

        assertEquals(0xFFF7F8F5L, appearance.backgroundArgb)
        assertEquals(0xFFFFFFFFL, appearance.surfaceArgb)
        assertEquals(0xFF1F6F5BL, appearance.primaryArgb)
        assertFalse(appearance.isDark)
    }

    @Test
    fun forestDarkChromeUsesTheLibraryBackground() {
        val appearance = appChromeAppearance(ThemeMode.DARK, ThemePalette.FOREST)

        assertEquals(0xFF121815L, appearance.backgroundArgb)
        assertEquals(0xFF1A211DL, appearance.surfaceArgb)
        assertEquals(0xFF8DD4B4L, appearance.primaryArgb)
        assertTrue(appearance.isDark)
    }

    @Test
    fun forestDarkReaderUsesItsWarmPlayerSurfaceForSystemNavigation() {
        val appearance = appChromeAppearance(ThemeMode.DARK, ThemePalette.FOREST)

        assertEquals(0xFF171510L, appearance.readerBackgroundArgb)
        assertEquals(0xFF201E1AL, appearance.readerPlayerSurfaceArgb)
    }
}
