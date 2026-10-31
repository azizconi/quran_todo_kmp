package tj.app.quran_todo.ui.tajweed

import androidx.compose.ui.graphics.Color
import tj.app.quran_todo.common.tajweed.TajweedGroup
import tj.app.quran_todo.common.tajweed.TajweedRule

/**
 * The colours of the tajweed rules (task F3b): six groups, one colour each, a light and a dark version. The theme has no slot for them
 * (a palette recolours the brand, not the Quran text), so they live here, next to what uses them.
 *
 * It is not a rainbow. The scheme is the one printed tajweed mushafs use:
 * - **grey**: written, not pronounced (hamzat al-wasl, the lam of a sun letter, silent letters);
 * - **red**, light to dark with the length: normal 2 counts, permissible 2/4/6, obligatory 4/5, necessary 6 (in the dark theme the
 *   strongest red is the most saturated, since a dark red would not be read on a dark page);
 * - **green**, four shades: ghunna, idgham with ghunna, ikhfa, ikhfa shafawi;
 * - **blue**: iqlab; **light blue**: qalqalah;
 * - **violet**: the merging of two letters without ghunna (idgham without ghunna, mutajanisayn, mutaqaribayn, shafawi).
 *
 * Every colour keeps at least 3:1 against every background of the reader and the lesson in both themes and all three palettes, and 2.2:1
 * against the same background under the wash of the word that is being recited (`TajweedPaletteTest`). The values were chosen for that, and
 * so that the neighbours of a group (the four reds, the four greens) stay apart.
 */
object TajweedPalette {
    /** The colour of [rule] in the light or the dark theme. */
    fun color(rule: TajweedRule, dark: Boolean): Color = if (dark) darkColors.getValue(rule) else lightColors.getValue(rule)

    /** The colour that stands for a whole [group] in the legend: the first rule of the group. */
    fun groupColor(group: TajweedGroup, dark: Boolean): Color =
        color(TajweedRule.entries.first { it.group == group }, dark)

    private val lightColors: Map<TajweedRule, Color> = mapOf(
        TajweedRule.HamzatWasl to Color(0xFF7F848D),
        TajweedRule.LamShamsiyah to Color(0xFF7F848D),
        TajweedRule.SilentLetter to Color(0xFF7F848D),

        TajweedRule.MaddNormal to Color(0xFFD4573F),
        TajweedRule.MaddPermissible to Color(0xFFBF3733),
        TajweedRule.MaddObligatory to Color(0xFF9C2330),
        TajweedRule.MaddNecessary to Color(0xFF6A0F22),

        TajweedRule.Ghunna to Color(0xFF12803F),
        TajweedRule.IdghamWithGhunna to Color(0xFF3F8A24),
        TajweedRule.Ikhfa to Color(0xFF2A8C74),
        TajweedRule.IkhfaShafawi to Color(0xFF6A8A1C),

        TajweedRule.Iqlab to Color(0xFF2F5DD0),

        TajweedRule.Qalqalah to Color(0xFF0A87AD),

        TajweedRule.IdghamWithoutGhunna to Color(0xFF7A4FB8),
        TajweedRule.IdghamMutajanisayn to Color(0xFF7A4FB8),
        TajweedRule.IdghamMutaqaribayn to Color(0xFF7A4FB8),
        TajweedRule.IdghamShafawi to Color(0xFF7A4FB8),
    )

    private val darkColors: Map<TajweedRule, Color> = mapOf(
        TajweedRule.HamzatWasl to Color(0xFF9CA3AD),
        TajweedRule.LamShamsiyah to Color(0xFF9CA3AD),
        TajweedRule.SilentLetter to Color(0xFF9CA3AD),

        TajweedRule.MaddNormal to Color(0xFFFFCDC4),
        TajweedRule.MaddPermissible to Color(0xFFFFAA9B),
        TajweedRule.MaddObligatory to Color(0xFFFF8878),
        TajweedRule.MaddNecessary to Color(0xFFF96A5B),

        TajweedRule.Ghunna to Color(0xFF4CD17A),
        TajweedRule.IdghamWithGhunna to Color(0xFF9BD45B),
        TajweedRule.Ikhfa to Color(0xFF52D1B0),
        TajweedRule.IkhfaShafawi to Color(0xFFC2CF5A),

        TajweedRule.Iqlab to Color(0xFF7EA2FF),

        TajweedRule.Qalqalah to Color(0xFF58C8F0),

        TajweedRule.IdghamWithoutGhunna to Color(0xFFC09BF5),
        TajweedRule.IdghamMutajanisayn to Color(0xFFC09BF5),
        TajweedRule.IdghamMutaqaribayn to Color(0xFFC09BF5),
        TajweedRule.IdghamShafawi to Color(0xFFC09BF5),
    )
}
