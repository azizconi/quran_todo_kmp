package tj.app.quran_todo.common.similar

import tj.app.quran_todo.common.mushaf.AyahKey

/** Real texts of the bundled Quran (`quran-uthmani.tsv`) for the tests of similar ayahs, as the screens draw them. */
object SimilarFixtures {
    const val AL_BAQARA_35 = "وَقُلْنَا يَٰٓـَٔادَمُ ٱسْكُنْ أَنتَ وَزَوْجُكَ ٱلْجَنَّةَ وَكُلَا مِنْهَا رَغَدًا حَيْثُ شِئْتُمَا وَلَا تَقْرَبَا هَٰذِهِ ٱلشَّجَرَةَ فَتَكُونَا مِنَ ٱلظَّٰلِمِينَ"
    const val AL_ARAF_19 = "وَيَٰٓـَٔادَمُ ٱسْكُنْ أَنتَ وَزَوْجُكَ ٱلْجَنَّةَ فَكُلَا مِنْ حَيْثُ شِئْتُمَا وَلَا تَقْرَبَا هَٰذِهِ ٱلشَّجَرَةَ فَتَكُونَا مِنَ ٱلظَّٰلِمِينَ"
    const val AL_BAQARA_58 = "وَإِذْ قُلْنَا ٱدْخُلُوا۟ هَٰذِهِ ٱلْقَرْيَةَ فَكُلُوا۟ مِنْهَا حَيْثُ شِئْتُمْ رَغَدًۭا وَٱدْخُلُوا۟ ٱلْبَابَ سُجَّدًۭا وَقُولُوا۟ حِطَّةٌۭ نَّغْفِرْ لَكُمْ خَطَٰيَٰكُمْ ۚ وَسَنَزِيدُ ٱلْمُحْسِنِينَ"
    const val AL_ARAF_161 = "وَإِذْ قِيلَ لَهُمُ ٱسْكُنُوا۟ هَٰذِهِ ٱلْقَرْيَةَ وَكُلُوا۟ مِنْهَا حَيْثُ شِئْتُمْ وَقُولُوا۟ حِطَّةٌۭ وَٱدْخُلُوا۟ ٱلْبَابَ سُجَّدًۭا نَّغْفِرْ لَكُمْ خَطِيٓـَٰٔتِكُمْ ۚ سَنَزِيدُ ٱلْمُحْسِنِينَ"
    const val AR_RAHMAN_13 = "فَبِأَىِّ ءَالَآءِ رَبِّكُمَا تُكَذِّبَانِ"

    val BAQARA_35 = AyahKey(2, 35)
    val ARAF_19 = AyahKey(7, 19)
    val BAQARA_58 = AyahKey(2, 58)
    val ARAF_161 = AyahKey(7, 161)

    /** A small index in the format of the file: two pairs, one refrain, a comment and a damaged line. */
    const val INDEX = """# comment
R	3	55:13	55:16	55:18
P	2:35	7:19=81	2:58=20
P	7:19	2:35=81
P	2:58	2:35=20	7:161=60
P	7:161	2:58=60
P	bad	line
P	3:4	not-an-entry
"""

    /** Texts by key, for the tests of the pairs. */
    val texts: Map<AyahKey, String> = mapOf(
        BAQARA_35 to AL_BAQARA_35,
        ARAF_19 to AL_ARAF_19,
        BAQARA_58 to AL_BAQARA_58,
        ARAF_161 to AL_ARAF_161,
    )
}
