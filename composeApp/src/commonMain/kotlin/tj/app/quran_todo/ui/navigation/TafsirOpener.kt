package tj.app.quran_todo.ui.navigation

import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Opens the commentary of an ayah (`Dest.Tafsir`, task F4b): given the surah and the ayah. The app root provides it; the ayah sheet,
 * which the text reader and the pages of the mushaf share, shows its "Tafsir" row only where there is one (the previews and tests have none).
 */
val LocalTafsirOpener = staticCompositionLocalOf<((surah: Int, ayah: Int) -> Unit)?> { null }
