package tj.app.quran_todo.ui.i18n

import tj.app.quran_todo.common.audio.ReciterGroup
import tj.app.quran_todo.common.audio.Reciters
import tj.app.quran_todo.common.i18n.AppLanguage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class AudioCopyTest {
    @Test
    fun everyLanguageDescribesEveryReciter() {
        for (language in AppLanguage.entries) {
            val copy = audioCopy(language)
            val styles = Reciters.all.map { copy.styleOf(it) }
            assertEquals(styles.size, styles.toSet().size, "styles of ${language.name} must differ")
            assertNotEquals("", copy.sampleFailed)
        }
    }

    @Test
    fun everyLanguageTitlesTheThreeGroupsAndExplainsTheTeachingOne() {
        for (language in AppLanguage.entries) {
            val copy = audioCopy(language)
            val titles = ReciterGroup.entries.map { copy.groupTitle(it) }
            assertEquals(3, titles.toSet().size, "group titles of ${language.name} must differ")
            assertEquals(false, titles.any { it.isBlank() })
            assertNotEquals("", copy.groupTeachingHint)
        }
        assertEquals("Для заучивания", audioCopy(AppLanguage.RU).groupTitle(ReciterGroup.Teaching))
        assertEquals("Медленно, с паузами для повтора", audioCopy(AppLanguage.RU).groupTeachingHint)
    }
}
