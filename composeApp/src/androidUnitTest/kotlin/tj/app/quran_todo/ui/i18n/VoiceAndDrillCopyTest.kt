package tj.app.quran_todo.ui.i18n

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import tj.app.quran_todo.common.i18n.AppLanguage

/** The strings of "My reading" and of the drill of the difficult ayahs (task F2a): all five languages, filled, without stars. */
class VoiceAndDrillCopyTest {
    private val stars = listOf('★', '☆', '✦', '✧', '✩', '✪', '⭐', '۞', '✶', '✷')

    private fun strings(copy: Any): Map<String, String> =
        copy.javaClass.declaredFields
            .filter { it.type == String::class.java }
            .onEach { it.isAccessible = true }
            .associate { it.name to it.get(copy) as String }

    @Test
    fun everyVoiceStringOfEveryLanguageIsWrittenAndHasNoStar() {
        AppLanguage.entries.forEach { language ->
            val all = strings(voiceCopy(language))
            assertTrue(all.size >= 20, "${language.name}: ${all.size} strings")
            all.forEach { (name, value) ->
                assertTrue(value.isNotBlank(), "${language.name}.$name is blank")
                assertTrue(stars.none { it in value }, "${language.name}.$name has a star: $value")
            }
        }
    }

    @Test
    fun everyDrillStringOfEveryLanguageIsWrittenAndHasNoStar() {
        AppLanguage.entries.forEach { language ->
            val all = strings(drillCopy(language))
            assertTrue(all.size >= 15, "${language.name}: ${all.size} strings")
            all.forEach { (name, value) ->
                assertTrue(value.isNotBlank(), "${language.name}.$name is blank")
                assertTrue(stars.none { it in value }, "${language.name}.$name has a star: $value")
            }
        }
    }

    @Test
    fun theOtherLanguagesAreTranslated() {
        listOf(::voiceCopy, ::drillCopy).forEach { build ->
            val english = strings(build(AppLanguage.EN))
            listOf(AppLanguage.RU, AppLanguage.TG, AppLanguage.UZ, AppLanguage.TR).forEach { language ->
                val other = strings(build(language))
                val same = english.filter { (name, value) -> other[name] == value }
                assertTrue(same.size < 3, "$language shares too much with English: ${same.keys}")
            }
        }
    }

    @Test
    fun theMicrophoneRefusalSaysWhatTheBriefSays() {
        assertEquals("Без микрофона записать не получится", voiceCopy(AppLanguage.RU).deniedTitle)
        assertEquals("Открыть настройки", voiceCopy(AppLanguage.RU).openSettings)
        assertEquals("Записать своё чтение", voiceCopy(AppLanguage.RU).recordAction)
        assertEquals("Послушать чтеца", voiceCopy(AppLanguage.RU).listenReciter)
        assertEquals("Моё чтение", voiceCopy(AppLanguage.RU).myReading)
    }

    @Test
    fun thePatternsFillTheirNumbers() {
        AppLanguage.entries.forEach { language ->
            val voice = voiceCopy(language)
            val drill = drillCopy(language)
            val filled = listOf(
                voice.myReadingCount(3), drill.progress(2, 10), drill.ayah(5), drill.summary(7, 3), drill.more(4), drill.train(5),
            )
            filled.forEach { text ->
                assertFalse("{" in text || "}" in text, "${language.name}: $text")
                assertTrue(text.any { it.isDigit() }, "${language.name}: $text has no number")
            }
        }
        val ru = drillCopy(AppLanguage.RU)
        assertEquals("2 из 10", ru.progress(2, 10))
        assertEquals("Помню: 7 · Ещё трудные: 3", ru.summary(7, 3))
        assertEquals("Ещё 4 в следующий раз", ru.more(4))
        assertEquals("Тренировать трудные · 5", ru.train(5))
        assertEquals("Моё чтение · 3", voiceCopy(AppLanguage.RU).myReadingCount(3))
    }

    @Test
    fun theDownloadsRowOfTheRecordingsIsInEveryLanguage() {
        AppLanguage.entries.forEach { language ->
            val copy = downloadsCopy(language)
            listOf(
                copy.storageRecordings, copy.storageRecordingsHint, copy.storageDeleteRecordings, copy.recordingsDeleteTitle,
                copy.recordingsDeleteConfirm, copy.recordingsDeletedToast,
            ).forEach { assertTrue(it.isNotBlank(), "$language has a blank recordings string") }
            assertTrue("{size}" in copy.recordingsDeleteBody, "recordingsDeleteBody/$language")
            val body = copy.recordingsDeleteBodyLabel(12L * 1024 * 1024)
            assertFalse("{" in body, "$language: $body")
            assertTrue("12" in body, "$language: $body")
        }
        assertEquals("Мои записи", downloadsCopy(AppLanguage.RU).storageRecordings)
        assertEquals("Удалить все записи", downloadsCopy(AppLanguage.RU).storageDeleteRecordings)
    }
}
