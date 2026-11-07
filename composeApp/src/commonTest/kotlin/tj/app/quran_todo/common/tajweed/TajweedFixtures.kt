package tj.app.quran_todo.common.tajweed

/**
 * Real ayahs: [Fixture.theirs] is the markup of api.alquran.cloud `quran-tajweed` as it came (2026-10-31), [Fixture.ours] the text the
 * screens draw (the bundled `quran-uthmani` text after `splitBismillah` and `hideRubSign`), [Fixture.expected] the rules that reach our
 * text: the code of the rule and the text of our range. They were worked out by an independent implementation of the alignment (a script)
 * and read through by eye against the markup; together the ayahs hold every one of the 17 rules and every difference between the two
 * texts that the alignment has to bridge:
 * - tatweel before the dagger alif (`[n[ـٰ]` against our `مَٰ`) in most of them;
 * - U+0672 for U+0670 (1:1, 2:2);
 * - small signs that only our text has: U+06ED (56:22, 78:26, 80:16), U+06E2 (83:5), U+06DF against the sukun (20:28, 8:59);
 * - U+200C before a pause sign where ours has a space (2:2);
 * - an alef that only our text has (53:57, 75:21: `ٱلْءَازِفَةُ`);
 * - `ء` against `أ` (3:148, 53:57), `ى` against `ٮ` (32:3), U+06E6 against U+06E7 (75:40), the small waw and yeh as letters (2:255);
 * - a rule that opens inside another rule (2:190, 8:59, 32:3, 47:31) and a `[` `]` without a code (32:3).
 */
internal object TajweedFixtures {
    class Fixture(
        val ref: String,
        val ruleCount: Int,
        val theirs: String,
        val ours: String,
        val expected: List<Pair<Char, String>>,
    )

    val all: List<Fixture> = listOf(
        Fixture(
            ref = "1:1",
            ruleCount = 7,
            theirs = "بِسْمِ [h:1[ٱ]للَّهِ [h:2[ٱ][l[ل]رَّحْمَ[n[ـٰ]نِ [h:3[ٱ][l[ل]رَّح[p[ِي]مِ",
            ours = "بِسْمِ ٱللَّهِ ٱلرَّحْمَٰنِ ٱلرَّحِيمِ",
            expected = listOf('h' to "ٱ", 'h' to "ٱ", 'l' to "ل", 'n' to "ٰ", 'h' to "ٱ", 'l' to "ل", 'p' to "ي"),
        ),
        Fixture(
            ref = "1:7",
            ruleCount = 7,
            theirs = "صِر[n[َٲ]طَ [h:8[ٱ]لَّذِينَ أَنْعَمْتَ عَلَيْهِمْ غَيْرِ [h:9[ٱ]لْمَغْضُوبِ عَلَيْهِمْ وَلَا [h:10[ٱ][l[ل]ضّ[m[َا]ٓلّ[p[ِي]نَ",
            ours = "صِرَٰطَ ٱلَّذِينَ أَنْعَمْتَ عَلَيْهِمْ غَيْرِ ٱلْمَغْضُوبِ عَلَيْهِمْ وَلَا ٱلضَّآلِّينَ",
            expected = listOf('n' to "ٰ", 'h' to "ٱ", 'h' to "ٱ", 'h' to "ٱ", 'l' to "ل", 'm' to "آ", 'p' to "ي"),
        ),
        Fixture(
            ref = "2:2",
            ruleCount = 5,
            theirs = "ذ[n[َٲ]لِكَ [h:11[ٱ]لْكِتَ[n[ـٰ]بُ لَا رَيْبَ\u200Cۛ فِيهِ\u200Cۛ هُ[u:12[دًى ل]ِّلْمُتَّق[p[ِي]نَ",
            ours = "ذَٰلِكَ ٱلْكِتَٰبُ لَا رَيْبَ ۛ فِيهِ ۛ هُدًۭى لِّلْمُتَّقِينَ",
            expected = listOf('n' to "ٰ", 'h' to "ٱ", 'n' to "ٰ", 'u' to "دًۭى لِّ", 'p' to "ي"),
        ),
        Fixture(
            ref = "2:255",
            ruleCount = 30,
            theirs = "ٱللَّهُ ل[o[َآ] إِلَ[n[ـٰ]هَ إِلَّا هُوَ [h:1468[ٱ]لْحَىُّ [h:1469[ٱ]لْقَيُّومُ\u200Cۚ لَا تَأْخُذُهُ[n[ۥ] سِنَ[a:1470[ةٌ و]َلَا نَوْ[u:1471[مٌ\u200Cۚ ل]َّهُ[n[ۥ] مَا فِى [h:609[ٱ][l[ل]سَّمَ[n[ـٰ]و[n[َٲ]تِ وَمَا فِى [h:44[ٱ]لْأَرْضِ\u200Cۗ مَ[f:1375[ن ذ]َا [h:1376[ٱ]لَّذِى يَشْفَعُ عِ[f:719[ند]َهُ[o[ۥٓ] إِلَّا بِإِذْنِه[n[ِۦ]\u200Cۚ يَعْلَمُ مَا بَيْنَ أَيْدِيهِمْ وَمَا خَلْفَهُمْ\u200Cۖ وَلَا يُحِيطُونَ بِش[a:1472[َىْءٍ م]ِّنْ عِلْمِه[o[ِۦٓ] إِلَّا بِمَا ش[o[َا]ٓءَ\u200Cۚ وَسِعَ كُرْسِيُّهُ [h:1473[ٱ][l[ل]سَّمَ[n[ـٰ]و[n[َٲ]تِ وَ[h:1474[ٱ]لْأَرْضَ\u200Cۖ وَلَا يَــُٔودُهُ[n[ۥ] حِفْظُهُمَا\u200Cۚ وَهُوَ [h:1475[ٱ]لْعَلِىُّ [h:1476[ٱ]لْعَظ[p[ِي]مُ",
            ours = "ٱللَّهُ لَآ إِلَٰهَ إِلَّا هُوَ ٱلْحَىُّ ٱلْقَيُّومُ ۚ لَا تَأْخُذُهُۥ سِنَةٌۭ وَلَا نَوْمٌۭ ۚ لَّهُۥ مَا فِى ٱلسَّمَٰوَٰتِ وَمَا فِى ٱلْأَرْضِ ۗ مَن ذَا ٱلَّذِى يَشْفَعُ عِندَهُۥٓ إِلَّا بِإِذْنِهِۦ ۚ يَعْلَمُ مَا بَيْنَ أَيْدِيهِمْ وَمَا خَلْفَهُمْ ۖ وَلَا يُحِيطُونَ بِشَىْءٍۢ مِّنْ عِلْمِهِۦٓ إِلَّا بِمَا شَآءَ ۚ وَسِعَ كُرْسِيُّهُ ٱلسَّمَٰوَٰتِ وَٱلْأَرْضَ ۖ وَلَا يَـُٔودُهُۥ حِفْظُهُمَا ۚ وَهُوَ ٱلْعَلِىُّ ٱلْعَظِيمُ",
            expected = listOf('o' to "آ", 'n' to "ٰ", 'h' to "ٱ", 'h' to "ٱ", 'n' to "ۥ", 'a' to "ةٌۭ وَ", 'u' to "مٌۭ ۚ لَّ", 'n' to "ۥ", 'h' to "ٱ", 'l' to "ل", 'n' to "ٰ", 'n' to "ٰ", 'h' to "ٱ", 'f' to "ن ذَ", 'h' to "ٱ", 'f' to "ندَ", 'o' to "ۥٓ", 'n' to "ۦ", 'a' to "ىْءٍۢ مِّ", 'o' to "ۦٓ", 'o' to "آ", 'h' to "ٱ", 'l' to "ل", 'n' to "ٰ", 'n' to "ٰ", 'h' to "ٱ", 'n' to "ۥ", 'h' to "ٱ", 'h' to "ٱ", 'p' to "ي"),
        ),
        Fixture(
            ref = "36:1",
            ruleCount = 1,
            theirs = "ي[m[سٓ]",
            ours = "يسٓ",
            expected = listOf('m' to "سٓ"),
        ),
        Fixture(
            ref = "112:1",
            ruleCount = 2,
            theirs = "قُلْ هُوَ [h:8078[ٱ]للَّهُ أَحَ[q[د]ٌ",
            ours = "قُلْ هُوَ ٱللَّهُ أَحَدٌ",
            expected = listOf('h' to "ٱ", 'q' to "دٌ"),
        ),
        Fixture(
            ref = "112:2",
            ruleCount = 3,
            theirs = "ٱللَّهُ [h:14675[ٱ][l[ل]صَّمَ[q[د]ُ",
            ours = "ٱللَّهُ ٱلصَّمَدُ",
            expected = listOf('h' to "ٱ", 'l' to "ل", 'q' to "دُ"),
        ),
        Fixture(
            ref = "113:2",
            ruleCount = 2,
            theirs = "مِ[f:14679[ن ش]َرِّ مَا خَلَ[q[ق]َ",
            ours = "مِن شَرِّ مَا خَلَقَ",
            expected = listOf('f' to "ن شَ", 'q' to "قَ"),
        ),
        Fixture(
            ref = "114:2",
            ruleCount = 4,
            theirs = "مَلِكِ [h:14684[ٱ][l[ل][g[نّ][p[َا]سِ",
            ours = "مَلِكِ ٱلنَّاسِ",
            expected = listOf('h' to "ٱ", 'l' to "ل", 'g' to "نَّ", 'p' to "ا"),
        ),
        Fixture(
            ref = "74:21",
            ruleCount = 1,
            theirs = "ثُ[g[مّ]َ نَظَرَ",
            ours = "ثُمَّ نَظَرَ",
            expected = listOf('g' to "مَّ"),
        ),
        Fixture(
            ref = "80:28",
            ruleCount = 1,
            theirs = "وَعِنَ[a:14319[بًا و]َقَضْبًا",
            ours = "وَعِنَبًۭا وَقَضْبًۭا",
            expected = listOf('a' to "بًۭا وَ"),
        ),
        Fixture(
            ref = "23:93",
            ruleCount = 4,
            theirs = "قُ[b:9283[ل] رَّبِّ إِ[g[مّ]َا تُرِيَ[g[نّ]ِى مَا يُوعَد[p[ُو]نَ",
            ours = "قُل رَّبِّ إِمَّا تُرِيَنِّى مَا يُوعَدُونَ",
            expected = listOf('b' to "ل", 'g' to "مَّ", 'g' to "نِّ", 'p' to "و"),
        ),
        Fixture(
            ref = "26:130",
            ruleCount = 2,
            theirs = "وَإِذَا بَطَشْت[c:9813[ُم ب]َطَشْتُمْ جَبَّار[p[ِي]نَ",
            ours = "وَإِذَا بَطَشْتُم بَطَشْتُمْ جَبَّارِينَ",
            expected = listOf('c' to "م بَ", 'p' to "ي"),
        ),
        Fixture(
            ref = "74:14",
            ruleCount = 2,
            theirs = "وَمَهَّ[d:14077[د]تُّ لَهُ[n[ۥ] تَمْهِيدًا",
            ours = "وَمَهَّدتُّ لَهُۥ تَمْهِيدًۭا",
            expected = listOf('d' to "د", 'n' to "ۥ"),
        ),
        Fixture(
            ref = "74:2",
            ruleCount = 1,
            theirs = "قُمْ فَأَ[f:14070[نذ]ِرْ",
            ours = "قُمْ فَأَنذِرْ",
            expected = listOf('f' to "نذِ"),
        ),
        Fixture(
            ref = "80:16",
            ruleCount = 1,
            theirs = "كِرَا[i:14307[مِۭ ب]َرَرَةٍ",
            ours = "كِرَامٍۭ بَرَرَةٍۢ",
            expected = listOf('i' to "مٍۭ بَ"),
        ),
        Fixture(
            ref = "80:30",
            ruleCount = 1,
            theirs = "وَحَد[o[َا]ٓئِقَ غُلْبًا",
            ours = "وَحَدَآئِقَ غُلْبًۭا",
            expected = listOf('o' to "آ"),
        ),
        Fixture(
            ref = "56:22",
            ruleCount = 1,
            theirs = "وَحُورٌ ع[p[ِي]نٌ",
            ours = "وَحُورٌ عِينٌۭ",
            expected = listOf('p' to "ي"),
        ),
        Fixture(
            ref = "85:18",
            ruleCount = 2,
            theirs = "فِرْعَوْنَ وَثَم[p[ُو][q[د]َ",
            ours = "فِرْعَوْنَ وَثَمُودَ",
            expected = listOf('p' to "و", 'q' to "دَ"),
        ),
        Fixture(
            ref = "20:28",
            ruleCount = 1,
            theirs = "يَفْقَهُو[s[اْ] قَوْلِى",
            ours = "يَفْقَهُوا۟ قَوْلِى",
            expected = listOf('s' to "ا۟"),
        ),
        Fixture(
            ref = "56:3",
            ruleCount = 1,
            theirs = "خَافِضَ[u:13202[ةٌ ر]َّافِعَةٌ",
            ours = "خَافِضَةٌۭ رَّافِعَةٌ",
            expected = listOf('u' to "ةٌۭ رَّ"),
        ),
        Fixture(
            ref = "104:8",
            ruleCount = 2,
            theirs = "إِ[g[نّ]َهَا عَلَيْه[w:14641[ِم م]ُّؤْصَدَةٌ",
            ours = "إِنَّهَا عَلَيْهِم مُّؤْصَدَةٌۭ",
            expected = listOf('g' to "نَّ", 'w' to "م مُّ"),
        ),
        Fixture(
            ref = "78:26",
            ruleCount = 2,
            theirs = "جَز[o[َا]ٓ[a:14252[ءً و]ِفَاقًا",
            ours = "جَزَآءًۭ وِفَاقًا",
            expected = listOf('o' to "آ", 'a' to "ءًۭ وِ"),
        ),
        Fixture(
            ref = "83:5",
            ruleCount = 1,
            theirs = "لِيَوْمٍ عَظ[p[ِي]مٍ",
            ours = "لِيَوْمٍ عَظِيمٍۢ",
            expected = listOf('p' to "ي"),
        ),
        Fixture(
            ref = "53:57",
            ruleCount = 1,
            theirs = "أَزِفَتِ [h:13029[ٱ]لْأَزِفَةُ",
            ours = "أَزِفَتِ ٱلْءَازِفَةُ",
            expected = listOf('h' to "ٱ"),
        ),
        Fixture(
            ref = "75:21",
            ruleCount = 1,
            theirs = "وَتَذَرُونَ [h:14134[ٱ]لْأَخِرَةَ",
            ours = "وَتَذَرُونَ ٱلْءَاخِرَةَ",
            expected = listOf('h' to "ٱ"),
        ),
        Fixture(
            ref = "87:5",
            ruleCount = 2,
            theirs = "فَجَعَلَهُ[n[ۥ] غُث[o[َا]ٓءً أَحْوَىٰ",
            ours = "فَجَعَلَهُۥ غُثَآءً أَحْوَىٰ",
            expected = listOf('n' to "ۥ", 'o' to "آ"),
        ),
        Fixture(
            ref = "75:40",
            ruleCount = 5,
            theirs = "أَلَيْسَ ذ[n[َٲ]لِكَ بِقَ[n[ـٰ]دِرٍ عَلَ[o[ىٰٓ] أ[a:12519[َن ي]ُحْــِۧىَ [h:12520[ٱ]لْمَوْتَىٰ",
            ours = "أَلَيْسَ ذَٰلِكَ بِقَٰدِرٍ عَلَىٰٓ أَن يُحْۦِىَ ٱلْمَوْتَىٰ",
            expected = listOf('n' to "ٰ", 'n' to "ٰ", 'o' to "ىٰٓ", 'a' to "ن يُ", 'h' to "ٱ"),
        ),
        Fixture(
            ref = "32:3",
            ruleCount = 13,
            theirs = "أَمْ يَقُولُونَ [h:6061[ٱ]فْتَرَ[ٮٰ]هُ\u200Cۚ بَلْ هُوَ [h:8923[ٱ]لْحَقُّ م[u:770[ِن ر]َّبِّكَ لِتُ[f:4582[نذ]ِرَ قَوْ[a:10181[مًا م]ّ[o[َآ] أَتَ[n[ـٰ]ه[w:10182[ُم م]ّ[a:10183[ِن ن]َّذِي[a:10184[رٍ م]ِّ[f:18[ن ق]َ[q:19[بْ]لِكَ لَعَلَّهُمْ يَهْتَد[p[ُو]نَ",
            ours = "أَمْ يَقُولُونَ ٱفْتَرَىٰهُ ۚ بَلْ هُوَ ٱلْحَقُّ مِن رَّبِّكَ لِتُنذِرَ قَوْمًۭا مَّآ أَتَىٰهُم مِّن نَّذِيرٍۢ مِّن قَبْلِكَ لَعَلَّهُمْ يَهْتَدُونَ",
            expected = listOf('h' to "ٱ", 'h' to "ٱ", 'u' to "ن رَّ", 'f' to "نذِ", 'a' to "مًۭا مَّ", 'o' to "آ", 'n' to "ٰ", 'w' to "م مِّ", 'a' to "ن نَّ", 'a' to "رٍۢ مِّ", 'f' to "ن قَ", 'q' to "بْ", 'p' to "و"),
        ),
        Fixture(
            ref = "8:59",
            ruleCount = 7,
            theirs = "وَلَا يَحْسَبَ[g[نّ]َ [h:2445[ٱ]لَّذِينَ كَفَرُو[s[اْ] سَبَق[o[ُوٓ[s[اْ]\u200Cۚ] إِ[g[نّ]َهُمْ لَا يُعْجِز[p[ُو]نَ",
            ours = "وَلَا يَحْسَبَنَّ ٱلَّذِينَ كَفَرُوا۟ سَبَقُوٓا۟ ۚ إِنَّهُمْ لَا يُعْجِزُونَ",
            expected = listOf('g' to "نَّ", 'h' to "ٱ", 's' to "ا۟", 'o' to "وٓ", 's' to "ا۟", 'g' to "نَّ", 'p' to "و"),
        ),
        Fixture(
            ref = "2:190",
            ruleCount = 11,
            theirs = "وَقَ[n[ـٰ]تِلُو[s[اْ] فِى سَبِيلِ [h:788[ٱ]للَّهِ [h:1020[ٱ]لَّذِينَ يُقَ[n[ـٰ]تِلُونَكُمْ وَلَا تَعْتَد[o[ُوٓ[s[اْ]\u200Cۚ] إِ[g[نّ]َ [h:80[ٱ]للَّهَ لَا يُحِبُّ [h:1021[ٱ]لْمُعْتَد[p[ِي]نَ",
            ours = "وَقَٰتِلُوا۟ فِى سَبِيلِ ٱللَّهِ ٱلَّذِينَ يُقَٰتِلُونَكُمْ وَلَا تَعْتَدُوٓا۟ ۚ إِنَّ ٱللَّهَ لَا يُحِبُّ ٱلْمُعْتَدِينَ",
            expected = listOf('n' to "ٰ", 's' to "ا۟", 'h' to "ٱ", 'h' to "ٱ", 'n' to "ٰ", 'o' to "وٓ", 's' to "ا۟", 'g' to "نَّ", 'h' to "ٱ", 'h' to "ٱ", 'p' to "ي"),
        ),
        Fixture(
            ref = "47:31",
            ruleCount = 11,
            theirs = "وَلَنَ[q:791[بْ]لُوَ[g[نّ]َكُمْ حَتَّىٰ نَعْلَمَ [h:12602[ٱ]لْمُجَ[n[ـٰ]هِدِينَ مِ[f:344[نك]ُمْ وَ[h:918[ٱ][l[ل]صَّ[n[ـٰ]بِرِينَ وَنَ[q:12603[بْ]لُو[o[َ[s[اْ]] أَخْبَارَكُمْ",
            ours = "وَلَنَبْلُوَنَّكُمْ حَتَّىٰ نَعْلَمَ ٱلْمُجَٰهِدِينَ مِنكُمْ وَٱلصَّٰبِرِينَ وَنَبْلُوَا۟ أَخْبَارَكُمْ",
            expected = listOf('q' to "بْ", 'g' to "نَّ", 'h' to "ٱ", 'n' to "ٰ", 'f' to "نكُ", 'h' to "ٱ", 'l' to "ل", 'n' to "ٰ", 'q' to "بْ", 's' to "ا۟"),
        ),
        Fixture(
            ref = "3:148",
            ruleCount = 8,
            theirs = "فَـَٔـاتَ[n[ـٰ]هُمُ [h:2306[ٱ]للَّهُ ثَوَابَ [h:2294[ٱ][l[ل]دُّنْيَا وَحُسْنَ ثَوَابِ [h:2307[ٱ]لْأَخِرَةِ\u200Cۗ وَ[h:72[ٱ]للَّهُ يُحِبُّ [h:1047[ٱ]لْمُحْسِن[p[ِي]نَ",
            ours = "فَـَٔاتَىٰهُمُ ٱللَّهُ ثَوَابَ ٱلدُّنْيَا وَحُسْنَ ثَوَابِ ٱلْءَاخِرَةِ ۗ وَٱللَّهُ يُحِبُّ ٱلْمُحْسِنِينَ",
            expected = listOf('n' to "ٰ", 'h' to "ٱ", 'h' to "ٱ", 'l' to "ل", 'h' to "ٱ", 'h' to "ٱ", 'h' to "ٱ", 'p' to "ي"),
        ),
    )
}
