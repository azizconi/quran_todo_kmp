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

    // ---- The echo mode (task F1b): the reciter reads, then silence for the person, then on ----

    /** What plays, in order: `A<n>` is the reciter reading ayah n, `.` is the silence that follows a reading in the echo mode. */
    private fun plan(
        from: Int,
        ayahCount: Int,
        practice: ReaderPracticeSettings,
        prefs: SurahReaderPreferences = preferences,
        limit: Int = 200,
    ): List<String> {
        val events = mutableListOf<String>()
        var step: ReaderPlaybackStep? = freshStep(from - 1, prefs, practice.normalized(ayahCount))
        while (step != null && events.size < limit) {
            events += "A${step.index + 1}"
            if (practice.echo) events += "."
            step = nextReaderPlaybackStep(step.index, step.repeatsRemaining, ayahCount, prefs, practice, step.echoRound)
        }
        return events
    }

    @Test
    fun oneAyahOnceInTheEchoModeIsTheReadingThenItsSilence() {
        val echo = ReaderPracticeSettings(echo = true, echoRepeats = 1)
        assertEquals(listOf("A1", "."), plan(from = 1, ayahCount = 1, practice = echo))
    }

    @Test
    fun theEchoRoundsRepeatReadingAndSilenceBeforeTheNextAyah() {
        val echo = ReaderPracticeSettings(echo = true, echoRepeats = 2)
        assertEquals(
            listOf("A1", ".", "A1", ".", "A2", ".", "A2", ".", "A3", ".", "A3", "."),
            plan(from = 1, ayahCount = 3, practice = echo),
        )
        assertEquals(listOf("A2", ".", "A3", "."), plan(from = 2, ayahCount = 3, practice = echo.copy(echoRepeats = 1)))
    }

    @Test
    fun aPassageOfThreeAyahsWithTwoRoundsRepeatedThreeTimes() {
        val echo = ReaderPracticeSettings(
            rangeEnabled = true, startAyah = 5, endAyah = 7, rangeRepeatCount = 3, echo = true, echoRepeats = 2,
        )
        val pass = listOf("A5", ".", "A5", ".", "A6", ".", "A6", ".", "A7", ".", "A7", ".")
        assertEquals(pass + pass + pass, plan(from = 5, ayahCount = 286, practice = echo))
    }

    @Test
    fun anEchoPassageStopsAfterTheSilenceOfItsLastAyahAndNeverRunsOver() {
        val echo = ReaderPracticeSettings(rangeEnabled = true, startAyah = 2, endAyah = 3, rangeRepeatCount = 1, echo = true)
        assertEquals(listOf("A2", ".", "A3", "."), plan(from = 2, ayahCount = 10, practice = echo))
    }

    @Test
    fun theRoundsReplaceTheRepeatOfEachAyahAndAnEndlessRepeatStaysEndless() {
        val echo = ReaderPracticeSettings(echo = true, echoRepeats = 1)
        // "Each ayah five times" of the preferences does not multiply with the rounds.
        assertEquals(listOf("A1", ".", "A2", "."), plan(from = 1, ayahCount = 2, practice = echo, prefs = preferences.copy(repeatCount = 5)))
        // An endless repeat is still endless: reading, silence, reading, silence ...
        val endless = plan(from = 1, ayahCount = 2, practice = echo, prefs = preferences.copy(repeatCount = -1), limit = 10)
        assertEquals(listOf("A1", ".", "A1", ".", "A1", ".", "A1", ".", "A1", "."), endless)
    }

    @Test
    fun withoutAutoAdvanceTheEchoEndsAfterTheRoundsOfTheChosenAyah() {
        val echo = ReaderPracticeSettings(echo = true, echoRepeats = 2)
        assertEquals(
            listOf("A2", ".", "A2", "."),
            plan(from = 2, ayahCount = 5, practice = echo, prefs = preferences.copy(autoAdvanceAyahs = false)),
        )
    }

    @Test
    fun withTheEchoOffTheStepsCarryNoRoundAndNothingChanged() {
        assertEquals(ReaderPlaybackStep(4, 2, echoRound = 0), nextReaderPlaybackStep(4, 3, 7, preferences.copy(repeatCount = 3), ReaderPracticeSettings()))
        assertEquals(ReaderPlaybackStep(4, 3), freshStep(4, preferences.copy(repeatCount = 3), ReaderPracticeSettings()))
        assertEquals(ReaderPlaybackStep(4, 3, 1), freshStep(4, preferences, range.copy(echo = true)))
    }

    @Test
    fun theSilenceIsOneAndATwentiethTimesTheAyahAndNeverUnderTwoSeconds() {
        assertEquals(2_000L, EchoPlan.silenceMs(durationMs = 1_000))
        assertEquals(2_000L, EchoPlan.silenceMs(durationMs = 0))
        assertEquals(2_000L, EchoPlan.silenceMs(durationMs = 1_666))
        assertEquals(2_400L, EchoPlan.silenceMs(durationMs = 2_000))
        assertEquals(12_000L, EchoPlan.silenceMs(durationMs = 10_000))
        // At 0.75x the ayah takes a third longer, and so does the silence; at 2x half as long.
        assertEquals(16_000L, EchoPlan.silenceMs(durationMs = 10_000, speed = 0.75f))
        assertEquals(6_000L, EchoPlan.silenceMs(durationMs = 10_000, speed = 2f))
        assertEquals(2, EchoPlan.seconds(1_001))
        assertEquals(3, EchoPlan.seconds(2_001))
    }

    @Test
    fun settingsSavedBeforeTheEchoExistedStillLoadWithTheEchoOff() {
        val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
        val old = """{"112":{"rangeEnabled":true,"startAyah":2,"endAyah":3,"rangeRepeatCount":3,"pauseSeconds":5}}"""
        val loaded = json.decodeFromString<Map<Int, ReaderPracticeSettings>>(old).getValue(112)
        assertEquals(ReaderPracticeSettings(true, 2, 3, 3, 5), loaded)
        assertEquals(false, loaded.echo)
        assertEquals(1, loaded.echoRepeats)
        // And the echo survives a round trip.
        val echo = ReaderPracticeSettings(echo = true, echoRepeats = 3)
        val again = json.decodeFromString<ReaderPracticeSettings>(json.encodeToString(ReaderPracticeSettings.serializer(), echo))
        assertEquals(echo, again)
    }

    @Test
    fun theEchoSettingsAreNormalizedAndKeepTheOldOnesIntact() {
        assertEquals(1, ReaderPracticeSettings(echo = true, echoRepeats = 9).normalized(7).echoRepeats)
        assertEquals(3, ReaderPracticeSettings(echo = true, echoRepeats = 3).normalized(7).echoRepeats)
        assertEquals(false, ReaderPracticeSettings().echo)
    }
}
