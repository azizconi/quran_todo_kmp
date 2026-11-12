package tj.app.quran_todo.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import tj.app.quran_todo.common.settings.LocalAppSettings

/** The reading size at which the fixed Arabic sizes of the sheets and cards were drawn. */
const val ArabicBaseReadingSize = 28

/**
 * [base] sp (the size drawn for the default reading size of 28) scaled by the reader's Arabic size setting. The factor is kept
 * between 0.85 and 1.3, so a card never gets huge and never gets smaller than the design.
 */
fun scaledArabicSize(base: Float, readingFontSize: Int): Float =
    base * (readingFontSize.toFloat() / ArabicBaseReadingSize).coerceIn(0.85f, 1.3f)

/** [scaledArabicSize] for the current setting, as sp. */
@Composable
fun rememberArabicSize(base: Float): TextUnit {
    val size = LocalAppSettings.current.readingFontSize
    return remember(base, size) { scaledArabicSize(base, size).sp }
}
