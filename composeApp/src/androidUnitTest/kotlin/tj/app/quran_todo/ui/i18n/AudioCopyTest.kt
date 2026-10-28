package tj.app.quran_todo.ui.i18n

import tj.app.quran_todo.common.audio.Reciters
import tj.app.quran_todo.common.i18n.AppLanguage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class AudioCopyTest {
    private val mb = 1024L * 1024L

    @Test
    fun theCacheSizeIsShownInTheUnitsOfTheLanguage() {
        val ru = audioCopy(AppLanguage.RU)
        assertEquals("0 КБ", ru.sizeLabel(0))
        assertEquals("120 КБ", ru.sizeLabel(120 * 1024L))
        assertEquals("230 МБ", ru.sizeLabel(230 * mb))
        assertEquals("1 МБ", ru.sizeLabel(mb))
        assertEquals("1,4 ГБ", ru.sizeLabel(1_450 * mb))
        assertEquals("230 MB", audioCopy(AppLanguage.EN).sizeLabel(230 * mb))
        assertEquals("1.0 GB", audioCopy(AppLanguage.EN).sizeLabel(1_024 * mb))
    }

    @Test
    fun everyLanguageDescribesEveryReciter() {
        for (language in AppLanguage.entries) {
            val copy = audioCopy(language)
            val styles = Reciters.all.map { copy.styleOf(it) }
            assertEquals(styles.size, styles.toSet().size, "styles of ${language.name} must differ")
            assertNotEquals("", copy.audioOnDevice)
            assertNotEquals("", copy.audioClear)
            assertNotEquals("", copy.audioCleared)
            assertNotEquals("", copy.sampleFailed)
        }
    }
}
