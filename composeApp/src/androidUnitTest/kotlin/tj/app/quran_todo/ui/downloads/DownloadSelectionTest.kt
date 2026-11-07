package tj.app.quran_todo.ui.downloads

import tj.app.quran_todo.common.audio.Reciters
import tj.app.quran_todo.data.bundled.QuranFacts
import tj.app.quran_todo.hifz.model.HifzPlan
import tj.app.quran_todo.hifz.model.HifzScope
import tj.app.quran_todo.hifz.model.SurahHifzStatus
import tj.app.quran_todo.ui.common.JuzIndex
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DownloadSelectionTest {
    private fun plan(order: List<Int>) = HifzPlan(
        scope = HifzScope.CUSTOM,
        surahOrder = order,
        dailyNewAyahs = 5,
        targetEpochDay = null,
        startedEpochDay = 0,
        manzilDays = 7,
    )

    @Test
    fun aSurahIsOneSegmentOfAllItsAyahs() {
        assertEquals(listOf(Segment(112, 1, 4)), DownloadSelection.surah(112))
        assertEquals(listOf(Segment(2, 1, 286)), DownloadSelection.surah(2))
    }

    @Test
    fun aJuzIsTheWholeSurahsInsideItAndThePartsAtItsEnds() {
        // Juz 30 starts at the beginning of surah 78 and ends with surah 114.
        val amma = DownloadSelection.juz(30)
        assertEquals(78, amma.first().surah)
        assertEquals(114, amma.last().surah)
        assertEquals(37, amma.size)
        assertTrue(amma.all { it.first == 1 && it.last == QuranFacts.ayahCount(it.surah) })
        // Juz 2 is the second part of Al-Baqarah only.
        assertEquals(listOf(Segment(2, 142, 252)), DownloadSelection.juz(2))
        // Juz 4 spans 3:93 to 4:23.
        assertEquals(listOf(Segment(3, 93, 200), Segment(4, 1, 23)), DownloadSelection.juz(4))
    }

    @Test
    fun theThirtyJuzTogetherCoverEveryAyahOnce() {
        val all = (1..JuzIndex.COUNT).flatMap { DownloadSelection.juz(it) }
        assertEquals(QuranFacts.AYAH_COUNT, all.sumOf { it.count })
        val keys = DownloadSelection.keys(all, Reciters.Alafasy)
        assertEquals(QuranFacts.AYAH_COUNT, keys.toSet().size)
    }

    @Test
    fun theKeysAreTheKeysOfTheAudioCache() {
        assertEquals(listOf("ayah_112_1", "ayah_112_2", "ayah_112_3", "ayah_112_4"), DownloadSelection.keys(DownloadSelection.surah(112), Reciters.Alafasy))
        assertEquals(listOf("husary_1_1", "husary_1_2"), DownloadSelection.keys(listOf(Segment(1, 1, 2)), Reciters.Husary))
    }

    @Test
    fun surahsKeepTheirOrderAndDropRepeatsAndNonsense() {
        assertEquals(
            listOf(Segment(114, 1, 6), Segment(112, 1, 4)),
            DownloadSelection.surahs(listOf(114, 112, 114, 0, 200)),
        )
    }

    private fun request(kind: DownloadKind, number: Int?, segments: List<Segment>, reciter: String = "alafasy") = DownloadRequest(
        id = DownloadRequest.idOf(kind, number, reciter),
        kind = kind,
        title = "t",
        reciterId = reciter,
        segments = segments,
    )

    @Test
    fun theUniqueKeysOfOverlappingRequestsAreExactlyTheirUnion() {
        val juz30 = request(DownloadKind.JUZ, 30, DownloadSelection.juz(30))
        val surah112 = request(DownloadKind.SURAH, 112, DownloadSelection.surah(112))
        val planned = request(DownloadKind.PLAN, null, DownloadSelection.surahs(listOf(111, 112, 2)))

        val unique = DownloadSelection.uniqueKeys(listOf(juz30, surah112, planned))

        assertEquals(juz30.keys().toSet() + planned.keys().toSet(), unique.toSet())
        // Juz 30 is 564 ayahs; surah 2 adds its 286; 111 and 112 are inside the juz already.
        assertEquals(564 + 286, unique.size)
        // Nothing twice, and the order is that of the requests.
        assertEquals(unique.size, unique.toList().distinct().size)
        assertEquals(juz30.keys().first(), unique.first())
    }

    @Test
    fun theSameSurahForTwoRecitersIsTwoSetsOfFiles() {
        val alafasy = request(DownloadKind.SURAH, 112, DownloadSelection.surah(112), "alafasy")
        val husary = request(DownloadKind.SURAH, 112, DownloadSelection.surah(112), "husary")
        assertEquals(8, DownloadSelection.uniqueKeys(listOf(alafasy, husary)).size)
        assertTrue(alafasy.id != husary.id)
    }

    @Test
    fun theSurahsOfThePlanAreTheLearnedAndLearningOnesAndTheNextToLearn() {
        val order = listOf(114, 113, 112, 111, 110)
        // 114 learned, 113 being learned: the next one that is not learned is 113 itself.
        assertEquals(
            listOf(114, 113),
            DownloadSelection.planSurahs(plan(order), mapOf(114 to SurahHifzStatus.LEARNED, 113 to SurahHifzStatus.LEARNING)),
        )
        // 114 and 113 learned, nothing of 112 yet: 112 is offered as the next one.
        assertEquals(
            listOf(114, 113, 112),
            DownloadSelection.planSurahs(plan(order), mapOf(114 to SurahHifzStatus.LEARNED, 113 to SurahHifzStatus.LEARNED)),
        )
        // A new plan with nothing started: its first surah.
        assertEquals(listOf(114), DownloadSelection.planSurahs(plan(order), emptyMap()))
        // A surah that is learned but not in the queue is not in "My plan".
        assertEquals(listOf(114), DownloadSelection.planSurahs(plan(listOf(114)), mapOf(1 to SurahHifzStatus.LEARNED)))
        // Everything learned: just the learned ones.
        assertEquals(order, DownloadSelection.planSurahs(plan(order), order.associateWith { SurahHifzStatus.LEARNED }))
        // No plan, no section.
        assertEquals(emptyList(), DownloadSelection.planSurahs(null, mapOf(1 to SurahHifzStatus.LEARNED)))
    }
}
