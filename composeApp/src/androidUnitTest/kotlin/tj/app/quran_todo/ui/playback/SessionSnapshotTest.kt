package tj.app.quran_todo.ui.playback

import tj.app.quran_todo.common.audio.ReaderAudioAyah
import tj.app.quran_todo.common.audio.ReaderAudioState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** How the playback controller's state is shown to the media session (notification, lock screen). */
class SessionSnapshotTest {
    private val now = NowPlaying(78, (1..5).map { ReaderAudioAyah(globalNumber = 5_000 + it, numberInSurah = it) })

    @Test
    fun withoutAudioTheSessionIsIdleAndEmpty() {
        val snapshot = sessionSnapshotOf(null, ReaderAudioState())
        assertFalse(snapshot.hasMedia)
        assertFalse(snapshot.playWhenReady)
        assertEquals(0, snapshot.ayahCount)
    }

    @Test
    fun aPlayingAyahIsPlayWhenReadyAndAdvancing() {
        val snapshot = sessionSnapshotOf(
            now, ReaderAudioState(index = 2, isPlaying = true, positionMs = 800, durationMs = 4_000),
        )
        assertTrue(snapshot.hasMedia)
        assertEquals(5, snapshot.ayahCount)
        assertEquals(2, snapshot.index)
        assertTrue(snapshot.playWhenReady)
        assertFalse(snapshot.buffering)
        assertTrue(snapshot.advancing)
        assertEquals(800, snapshot.positionMs)
        assertEquals(4_000, snapshot.durationMs)
    }

    @Test
    fun fetchingTheAyahIsBufferingAndNotYetAdvancing() {
        val snapshot = sessionSnapshotOf(now, ReaderAudioState(index = 1, isPlaying = true, isPreparing = true))
        assertTrue(snapshot.playWhenReady)
        assertTrue(snapshot.buffering)
        assertFalse(snapshot.advancing)
    }

    @Test
    fun theSilentPauseBetweenAyahsKeepsPlayingButTheClockStands() {
        val snapshot = sessionSnapshotOf(
            now,
            ReaderAudioState(index = 1, isPlaying = true, pauseRemainingSeconds = 3, positionMs = 4_000, durationMs = 4_000),
        )
        assertTrue(snapshot.playWhenReady)
        assertFalse(snapshot.advancing)
    }

    @Test
    fun aPausedAyahIsNotPlayWhenReady() {
        val snapshot = sessionSnapshotOf(
            now, ReaderAudioState(index = 3, canResume = true, positionMs = 1_000, durationMs = 4_000),
        )
        assertTrue(snapshot.hasMedia)
        assertFalse(snapshot.playWhenReady)
        assertFalse(snapshot.advancing)
        assertEquals(1_000, snapshot.positionMs)
    }

    @Test
    fun aStoppedOrFailedSequenceIsShownAsPausedSoThePlayButtonRetries() {
        val failed = sessionSnapshotOf(now, ReaderAudioState(index = 2, hasError = true))
        assertTrue(failed.hasMedia)
        assertFalse(failed.playWhenReady)
        assertTrue(failed.hasError)
    }

    @Test
    fun theIndexIsKeptInsideThePlaylist() {
        assertEquals(4, sessionSnapshotOf(now, ReaderAudioState(index = 99)).index)
        assertEquals(0, sessionSnapshotOf(now, ReaderAudioState(index = -3)).index)
    }

    @Test
    fun theFivePerSecondPositionTicksDoNotChangeTheKeyThatRefreshesTheSession() {
        val first = sessionSnapshotOf(now, ReaderAudioState(index = 1, isPlaying = true, positionMs = 200, durationMs = 4_000))
        val later = sessionSnapshotOf(now, ReaderAudioState(index = 1, isPlaying = true, positionMs = 2_400, durationMs = 4_000))
        assertEquals(first.withoutPosition(), later.withoutPosition())
        val paused = sessionSnapshotOf(now, ReaderAudioState(index = 1, canResume = true, positionMs = 2_400, durationMs = 4_000))
        assertTrue(first.withoutPosition() != paused.withoutPosition())
    }
}
