package tj.app.quran_todo.common.tajweed

/**
 * The six colour groups of the tajweed colours (task F3b). A group is what the person learns first: one colour per group, and inside
 * [Madd] and [Nasal] shades of that colour. The colours themselves live in the Android UI (`ui/tajweed/TajweedPalette`); this model has no
 * colour so that it is shared with every platform.
 */
enum class TajweedGroup {
    /** Written, not pronounced: hamzat al-wasl, the lam of a sun letter, silent letters. Grey. */
    Silent,

    /** Prolongations by length: normal 2, permissible 2/4/6, obligatory 4/5, necessary 6. From light to dark red. */
    Madd,

    /** Nasal sound and hiding: ghunna, idgham with ghunna, ikhfa, ikhfa shafawi. Shades of green. */
    Nasal,

    /** Iqlab: nun sakinah before ba turns into a meem. Blue. */
    Iqlab,

    /** Qalqalah: the echo of ق ط ب ج د with a sukun. Light blue. */
    Qalqalah,

    /** Merging of two letters without ghunna: idgham without ghunna, mutajanisayn, mutaqaribayn, shafawi. A calm violet. */
    Merge,
}

/**
 * The 17 rules that the markup of api.alquran.cloud (`quran-tajweed`) knows, one letter code each. The code of every rule was checked
 * against the classes of quran.com (`/quran/verses/uthmani_tajweed`, the same text and the same spans): on 15 surahs (1 360 ayahs)
 * every span of one markup is the span of the other with the class named in [quranComClass], one to one for all 17 codes; nothing
 * was guessed.
 *
 * | code | quran.com class | rule | group |
 * |---|---|---|---|
 * | h | ham_wasl | hamzat al-wasl | silent |
 * | l | laam_shamsiyah | lam of a sun letter | silent |
 * | s | slnt | silent letter | silent |
 * | n | madda_normal | madd tabii, 2 counts | madd |
 * | p | madda_permissible | permissible madd, 2/4/6 | madd |
 * | o | madda_obligatory | obligatory madd, 4/5 | madd |
 * | m | madda_necessary | necessary madd, 6 | madd |
 * | g | ghunnah | ghunna | nasal |
 * | a | idgham_ghunnah | idgham with ghunna | nasal |
 * | f | ikhafa | ikhfa | nasal |
 * | c | ikhafa_shafawi | ikhfa shafawi | nasal |
 * | i | iqlab | iqlab | iqlab |
 * | q | qalaqah | qalqalah | qalqalah |
 * | u | idgham_wo_ghunnah | idgham without ghunna | merge |
 * | d | idgham_mutajanisayn | idgham mutajanisayn | merge |
 * | b | idgham_mutaqaribayn | idgham mutaqaribayn | merge |
 * | w | idgham_shafawi | idgham shafawi | merge |
 */
enum class TajweedRule(val code: Char, val quranComClass: String, val group: TajweedGroup) {
    HamzatWasl('h', "ham_wasl", TajweedGroup.Silent),
    LamShamsiyah('l', "laam_shamsiyah", TajweedGroup.Silent),
    SilentLetter('s', "slnt", TajweedGroup.Silent),

    MaddNormal('n', "madda_normal", TajweedGroup.Madd),
    MaddPermissible('p', "madda_permissible", TajweedGroup.Madd),
    MaddObligatory('o', "madda_obligatory", TajweedGroup.Madd),
    MaddNecessary('m', "madda_necessary", TajweedGroup.Madd),

    Ghunna('g', "ghunnah", TajweedGroup.Nasal),
    IdghamWithGhunna('a', "idgham_ghunnah", TajweedGroup.Nasal),
    Ikhfa('f', "ikhafa", TajweedGroup.Nasal),
    IkhfaShafawi('c', "ikhafa_shafawi", TajweedGroup.Nasal),

    Iqlab('i', "iqlab", TajweedGroup.Iqlab),

    Qalqalah('q', "qalaqah", TajweedGroup.Qalqalah),

    IdghamWithoutGhunna('u', "idgham_wo_ghunnah", TajweedGroup.Merge),
    IdghamMutajanisayn('d', "idgham_mutajanisayn", TajweedGroup.Merge),
    IdghamMutaqaribayn('b', "idgham_mutaqaribayn", TajweedGroup.Merge),
    IdghamShafawi('w', "idgham_shafawi", TajweedGroup.Merge),
    ;

    companion object {
        private val byCode: Map<Char, TajweedRule> = entries.associateBy { it.code }

        /** The rule of a markup code, or null for a letter the markup does not use. */
        fun fromCode(code: Char): TajweedRule? = byCode[code]
    }
}
