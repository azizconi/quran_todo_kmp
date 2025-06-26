package tj.app.quran_todo.common.utils

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import tj.app.quran_todo.common.json.surahJson
import tj.app.quran_todo.domain.model.SurahList
import tj.app.quran_todo.domain.model.SurahModel

private val json = Json { ignoreUnknownKeys = true }


suspend fun parseSurahList(): List<SurahModel> = withContext(Dispatchers.IO) {
    try {
        val result = json.decodeFromString<SurahList>(surahJson)
        result.surahs
    } catch (e: Exception) {
        println("Ошибка парсинга: ${e.message}")
        emptyList()
    }
}