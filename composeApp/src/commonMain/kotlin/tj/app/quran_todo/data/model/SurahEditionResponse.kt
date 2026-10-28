package tj.app.quran_todo.data.model

import kotlinx.serialization.Serializable

/** `GET /v1/surah/{n}/{edition}` of api.alquran.cloud: one surah in one edition. */
@Serializable
data class SurahEditionResponse(
    val code: Int = 0,
    val data: SurahEditionData,
)

@Serializable
data class SurahEditionData(
    val ayahs: List<SurahEditionAyah>,
)

@Serializable
data class SurahEditionAyah(
    /** Global ayah number (1..6236). */
    val number: Int,
    val numberInSurah: Int = 0,
    val text: String,
)

/** `GET /api/v4/quran/translations/{id}?chapter_number={n}` of quran.com: the verses of a chapter in order. */
@Serializable
data class QuranComTranslationsResponse(
    val translations: List<QuranComTranslationText>,
)

@Serializable
data class QuranComTranslationText(
    val text: String,
)
