package tj.app.quran_todo.ui.components.spotlight

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The "seen" marks of the tours. */
class SpotlightFlagsTest {
    @Test
    fun nothingIsSeenAtFirst() {
        val flags = SpotlightFlags(InMemorySpotlightStore())
        SpotlightTours.all.forEach { assertFalse(flags.isSeen(it), it.id) }
    }

    @Test
    fun aMarkIsPerTour() {
        val flags = SpotlightFlags(InMemorySpotlightStore())
        flags.markSeen(SpotlightTours.Home)
        assertTrue(flags.isSeen(SpotlightTours.Home))
        assertFalse(flags.isSeen(SpotlightTours.Reader))
        assertFalse(flags.isSeen(SpotlightTours.Player))
    }

    @Test
    fun marksSurviveARestartOfTheApp() {
        val store = InMemorySpotlightStore()
        SpotlightFlags(store).markSeen(SpotlightTours.Reader)
        val after = SpotlightFlags(store)
        assertTrue(after.isSeen(SpotlightTours.Reader))
        assertFalse(after.isSeen(SpotlightTours.Home))
    }

    @Test
    fun markingTwiceIsHarmless() {
        val store = InMemorySpotlightStore()
        val flags = SpotlightFlags(store)
        flags.markSeen(SpotlightTours.Home)
        flags.markSeen(SpotlightTours.Home)
        assertEquals(setOf("home"), store.read())
    }

    @Test
    fun resetForgetsEverything() {
        val store = InMemorySpotlightStore()
        val flags = SpotlightFlags(store)
        SpotlightTours.all.forEach(flags::markSeen)
        assertEquals(SpotlightTours.all.map { it.id }.toSet(), store.read())

        flags.resetAll()
        SpotlightTours.all.forEach { assertFalse(flags.isSeen(it), it.id) }
        assertTrue(store.read().isEmpty())
        assertTrue(SpotlightFlags(store).let { fresh -> SpotlightTours.all.none(fresh::isSeen) })
    }

    @Test
    fun aToursCanBeMarkedAgainAfterAReset() {
        val flags = SpotlightFlags(InMemorySpotlightStore())
        flags.markSeen(SpotlightTours.Home)
        flags.resetAll()
        flags.markSeen(SpotlightTours.Home)
        assertTrue(flags.isSeen(SpotlightTours.Home))
    }

    @Test
    fun theFlagsFileIsNotTheOneOfTheOtherPreferences() {
        assertEquals("quran_hifz_spotlights", SharedPreferencesSpotlightStore.FileName)
    }
}
