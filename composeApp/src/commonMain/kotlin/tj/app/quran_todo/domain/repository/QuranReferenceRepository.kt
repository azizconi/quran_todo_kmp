package tj.app.quran_todo.domain.repository

import kotlinx.coroutines.flow.StateFlow
import tj.app.quran_todo.common.i18n.AppLanguage
import tj.app.quran_todo.data.translation.TranslationEdition
import tj.app.quran_todo.domain.model.ChapterNameModel

/** How the download of a whole translation ended. */
enum class WholeDownloadResult {
    DONE,

    /** The request did not get an answer (offline, timeout): worth trying again when the network is back. */
    NO_NETWORK,

    /** The source answered, but not with a whole Quran (HTTP error, wrong shape). */
    FAILED,
}

interface QuranReferenceRepository {
    suspend fun getChapterNames(language: AppLanguage): Map<Int, ChapterNameModel>

    /**
     * Translations of one surah by global ayah number; an empty map when they could not be loaded.
     * A surah (and language) that failed is not requested again for 30 seconds unless [force] is true
     * (an explicit "Retry").
     */
    suspend fun getAyahTranslations(
        surahNumber: Int,
        language: AppLanguage,
        expectedCount: Int,
        force: Boolean = false,
    ): Map<Int, String>

    /** Changes whenever a whole translation was downloaded or deleted (the screens that list them read again). */
    val translationChanges: StateFlow<Int>

    /** The keys ([TranslationEdition.key]) of the editions that are stored whole: all 6236 ayahs. */
    suspend fun downloadedTranslations(): Set<String>

    /** Downloads [edition] whole (one request) and stores it; the stored surahs of it are replaced. */
    suspend fun downloadWholeTranslation(edition: TranslationEdition): WholeDownloadResult

    /**
     * Asks QuranEnc for the current versions of its editions (`GET /api/v1/translations/list`), at most once in 7 days, quietly: a
     * failure changes nothing and shows nothing. A stored edition older than the answer is "update available".
     */
    suspend fun checkTranslationUpdates()

    /** Removes everything stored of [edition]. */
    suspend fun deleteTranslation(edition: TranslationEdition)

    /** The stored text of [edition] by global ayah number, in the order of the Quran; empty when nothing is stored. */
    suspend fun getStoredTranslation(edition: TranslationEdition): Map<Int, String>
}
