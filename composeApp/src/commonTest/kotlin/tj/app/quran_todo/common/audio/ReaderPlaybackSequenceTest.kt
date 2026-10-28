package tj.app.quran_todo.common.audio

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import tj.app.quran_todo.common.settings.ReaderPracticeSettings
import tj.app.quran_todo.common.settings.SurahReaderPreferences

class ReaderPlaybackSequenceTest {
    private val preferences = SurahReaderPreferences()
    private val range = ReaderPracticeSettings(rangeEnabled = true, startAyah = 5, endAyah = 10, rangeRepeatCount = 3)

    @Test
    fun repeatsTheEntirePassageThreeTimesAndStopsBeforeTheNextAyah() {
        var current: ReaderPlaybackStep? = ReaderPlaybackStep(4, 3)
        val played = mutableListOf<Int>()
        while (current != null && played.size < 100) {
            played += current.index + 1
            current = nextReaderPlaybackStep(current.index, current.repeatsRemaining, 286, preferences, range)
        }
        assertEquals(List(3) { (5..10).toList() }.flatten(), played)
        assertNull(current)
    }

    @Test
    fun anEndlessPassageWrapsEvenWhenAutoAdvanceIsOff() {
        assertEquals(
            ReaderPlaybackStep(4, -1),
            nextReaderPlaybackStep(9, -1, 286, preferences.copy(autoAdvanceAyahs = false), range),
        )
    }

    @Test
    fun aSingleAyahIsAlsoAValidFinitePassage() {
        val one = range.copy(startAyah = 7, endAyah = 7)
        assertEquals(ReaderPlaybackStep(6, 2), nextReaderPlaybackStep(6, 3, 286, preferences, one))
        assertNull(nextReaderPlaybackStep(6, 1, 286, preferences, one))
    }

    @Test
    fun rangeCountsDoNotRepeatEachAyahIndividually() {
        assertEquals(ReaderPlaybackStep(5, 3), nextReaderPlaybackStep(4, 3, 286, preferences.copy(repeatCount = 5), range))
    }

    @Test
    fun withoutARangeExistingAyahRepeatsAndAutoAdvanceStillWork() {
        val noRange = ReaderPracticeSettings()
        val settings = preferences.copy(repeatCount = 3)
        assertEquals(ReaderPlaybackStep(4, 2), nextReaderPlaybackStep(4, 3, 7, settings, noRange))
        assertEquals(ReaderPlaybackStep(5, 3), nextReaderPlaybackStep(4, 1, 7, settings, noRange))
        assertNull(nextReaderPlaybackStep(6, 1, 7, settings, noRange))
    }

    @Test
    fun autoAdvanceOffStopsAfterTheSelectedAyah() {
        assertNull(nextReaderPlaybackStep(4, 1, 286, preferences.copy(autoAdvanceAyahs = false), ReaderPracticeSettings()))
    }

    @Test
    fun anEmptySurahNeverProducesAPlaybackStep() {
        assertNull(nextReaderPlaybackStep(0, -1, 0, preferences, range))
    }

    @Test
    fun storedRangesAndUnsupportedOptionsAreSafelyNormalized() {
        assertEquals(
            ReaderPracticeSettings(true, 7, 7, 5, 0),
            ReaderPracticeSettings(true, 200, 1, 200, -5).normalized(7),
        )
    }

    @Test
    fun aSelectionOutsideTheRangeReturnsToItsStart() {
        assertEquals(ReaderPlaybackStep(4, 3), nextReaderPlaybackStep(100, 1, 286, preferences, range))
    }
}
