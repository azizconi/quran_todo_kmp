package tj.app.quran_todo.ui.components.hints

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import tj.app.quran_todo.common.i18n.AppLanguage
import tj.app.quran_todo.ui.i18n.hintsCopy

/** The "used" marks of the inline hints. */
class HintFlagsTest {
    @Test
    fun nothingIsUsedAtFirst() {
        val flags = HintFlags(InMemoryHintStore())
        assertFalse(flags.isUsed(Hints.ReaderHold))
        assertFalse(flags.isUsed(Hints.LessonGrade))
    }

    @Test
    fun aMarkIsPerHintAndSurvivesARestart() {
        val store = InMemoryHintStore()
        HintFlags(store).markUsed(Hints.ReaderHold)
        val after = HintFlags(store)
        assertTrue(after.isUsed(Hints.ReaderHold))
        assertFalse(after.isUsed(Hints.LessonGrade))
    }

    @Test
    fun markingTwiceIsHarmless() {
        val store = InMemoryHintStore()
        val flags = HintFlags(store)
        flags.markUsed(Hints.ReaderHold)
        flags.markUsed(Hints.ReaderHold)
        assertEquals(setOf(Hints.ReaderHold), store.read())
    }

    @Test
    fun resetBringsAllHintsBack() {
        val store = InMemoryHintStore()
        val flags = HintFlags(store)
        flags.markUsed(Hints.ReaderHold)
        flags.markUsed(Hints.LessonGrade)
        flags.resetAll()
        assertFalse(flags.isUsed(Hints.ReaderHold))
        assertFalse(flags.isUsed(Hints.LessonGrade))
        assertTrue(store.read().isEmpty())
        assertFalse(HintFlags(store).isUsed(Hints.LessonGrade))
    }

    @Test
    fun everyLanguageHasTheHintTexts() {
        AppLanguage.entries.forEach { language ->
            val copy = hintsCopy(language)
            listOf(copy.dismiss, copy.readerHold, copy.lessonGrade).forEach { assertTrue(it.isNotBlank(), language.name) }
        }
    }
}
