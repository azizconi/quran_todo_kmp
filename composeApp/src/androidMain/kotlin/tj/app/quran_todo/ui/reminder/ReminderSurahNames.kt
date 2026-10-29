package tj.app.quran_todo.ui.reminder

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeoutOrNull
import tj.app.quran_todo.common.i18n.AppLanguage
import tj.app.quran_todo.common.i18n.code
import tj.app.quran_todo.common.utils.parseSurahList
import tj.app.quran_todo.data.database.dao.ChapterNameDao
import tj.app.quran_todo.domain.use_case.GetChapterNamesUseCase
import tj.app.quran_todo.ui.common.SurahNames

/**
 * Surah names for the notification, by the one naming rule of the Android UI ([SurahNames.displayName]). The notification is built
 * without the screens, so the names are read here: the bundled Russian names, and the transliteration of the language from the
 * chapter-name repository (bundled for Tajik and Uzbek, a cached or downloaded list for the others). A slow or failing download
 * does not hold the notification: after [NAMES_TIMEOUT_MILLIS] the list cached on the phone is used, and a surah without any name
 * is shown as "№ 78".
 */
internal class ReminderSurahNames(
    private val chapterNames: GetChapterNamesUseCase,
    private val chapterNameDao: ChapterNameDao,
) {
    /** The naming function of [language]; reads the names once and answers from memory. */
    suspend fun forLanguage(language: AppLanguage): (Int) -> String {
        val russian: Map<Int, String> = if (language == AppLanguage.RU) {
            attempt { parseSurahList().associate { it.surahNumber to it.name } } ?: emptyMap()
        } else {
            emptyMap()
        }
        val latin: Map<Int, String> =
            attempt { withTimeoutOrNull(NAMES_TIMEOUT_MILLIS) { chapterNames(language).mapValues { it.value.transliteration } } }
                ?: attempt { chapterNameDao.getByLanguage(language.code).associate { it.surahNumber to it.transliteration } }
                ?: emptyMap()
        return { surah ->
            SurahNames.displayName(
                language = language,
                russianName = russian[surah],
                transliteration = latin[surah],
                fallback = "№ $surah",
            )
        }
    }

    /** The result of [block], or null when it failed; a cancellation is passed on. */
    private suspend fun <T> attempt(block: suspend () -> T): T? =
        try {
            block()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            null
        }

    private companion object {
        const val NAMES_TIMEOUT_MILLIS = 3_000L
    }
}
