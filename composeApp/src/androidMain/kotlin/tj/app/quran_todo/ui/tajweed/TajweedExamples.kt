package tj.app.quran_todo.ui.tajweed

import tj.app.quran_todo.common.tajweed.TajweedRule

/**
 * One word or two from the Quran with the rule of the legend ([TajweedLegendSheet]) coloured in them: [text] is a stretch of the text of the
 * bundled database as the reader draws it ([ref] "surah:ayah" says where it is from), [spans] the ranges of [text] (end exclusive) that carry
 * the rule. The legend has to work without the markup file (it is there to be read before the switch is turned on), so the examples are
 * written down; `TajweedExamplesTest` checks that every one of them is in the bundled text at its place, and the full check of
 * `scripts/check_tajweed_alignment.py` that the markup puts the rule on exactly these letters.
 */
internal class TajweedExample(val text: String, val ref: String, val spans: List<IntRange>)

internal object TajweedExamples {
    /** One example per rule, from places that are well known by heart. */
    val byRule: Map<TajweedRule, TajweedExample> = mapOf(
        TajweedRule.HamzatWasl to TajweedExample("ٱللَّهِ", "1:1", listOf(0 until 1)),
        TajweedRule.LamShamsiyah to TajweedExample("ٱلشَّمْسُ", "55:5", listOf(1 until 2)),
        TajweedRule.SilentLetter to TajweedExample("كَانُوا۟", "2:10", listOf(6 until 8)),
        TajweedRule.MaddNormal to TajweedExample("مَٰلِكِ", "1:4", listOf(2 until 3)),
        TajweedRule.MaddPermissible to TajweedExample("دِينِ", "109:6", listOf(2 until 3)),
        TajweedRule.MaddObligatory to TajweedExample("بِمَآ", "2:4", listOf(4 until 6)),
        TajweedRule.MaddNecessary to TajweedExample("حَآجَّ", "2:258", listOf(2 until 4)),
        TajweedRule.Ghunna to TajweedExample("إِنَّ", "2:6", listOf(2 until 5)),
        TajweedRule.IdghamWithGhunna to TajweedExample("مَن يَقُولُ", "2:8", listOf(2 until 6)),
        TajweedRule.Ikhfa to TajweedExample("عِندَ", "2:54", listOf(2 until 5)),
        TajweedRule.IkhfaShafawi to TajweedExample("رَبَّهُم بِهِمْ", "100:11", listOf(7 until 11)),
        TajweedRule.Iqlab to TajweedExample("مِنۢ بَعْدِ", "2:27", listOf(2 until 7)),
        TajweedRule.Qalqalah to TajweedExample("قَدْ", "2:60", listOf(2 until 4)),
        TajweedRule.IdghamWithoutGhunna to TajweedExample("مَن لَّا", "36:21", listOf(2 until 7)),
        TajweedRule.IdghamMutajanisayn to TajweedExample("عَبَدتُّمْ", "109:4", listOf(4 until 5)),
        TajweedRule.IdghamMutaqaribayn to TajweedExample("بَل رَّفَعَهُ", "4:158", listOf(2 until 3)),
        TajweedRule.IdghamShafawi to TajweedExample("كَم مِّن", "2:249", listOf(2 until 7)),
    )
}
