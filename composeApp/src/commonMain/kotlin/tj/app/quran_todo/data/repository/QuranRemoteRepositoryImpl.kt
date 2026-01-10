package tj.app.quran_todo.data.repository

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import tj.app.quran_todo.common.utils.Constants
import tj.app.quran_todo.common.utils.Resource
import tj.app.quran_todo.data.database.dao.QuranDao
import tj.app.quran_todo.data.database.entity.quran.AyahEntity
import tj.app.quran_todo.data.database.entity.quran.EditionEntity
import tj.app.quran_todo.data.database.entity.quran.Sajda
import tj.app.quran_todo.data.database.entity.quran.SurahEntity
import tj.app.quran_todo.data.database.entity.quran.SurahWithAyahs
import tj.app.quran_todo.data.model.QuranCompleteResponse
import tj.app.quran_todo.domain.repository.QuranRemoteRepository

class QuranRemoteRepositoryImpl(
    private val client: HttpClient,
    private val dao: QuranDao,
) : QuranRemoteRepository {
    override fun getCompleteQuran(withLocalAction: Boolean): Flow<Resource<List<SurahWithAyahs>>> =
        flow {
            // делаем запрос и сразу эмитим тело
            emit(Resource.Loading())  // Resource<Nothing> подойдёт из-за ковариантности

            suspend fun sendRequest() {
                val response: QuranCompleteResponse =
                    client.get(Constants.QURAN_API_BASE_URL + "quran/quran-uthmani").body()

                val data = response.data
                val edition = data.edition.run {
                    EditionEntity(
                        identifier = identifier,
                        englishName = englishName,
                        format = format,
                        language = language,
                        name = name,
                        type = type
                    )
                }
                val surahs = data.surahs.map { s ->
                    SurahEntity(
                        number = s.number,
                        name = s.name,
                        englishName = s.englishName,
                        englishNameTranslation = s.englishNameTranslation,
                        revelationType = s.revelationType,
                        editionId = edition.identifier
                    )
                }
                val ayahs = data.surahs.flatMap { s ->
                    s.ayahs.map { a ->
                        AyahEntity(
                            number = a.number,
                            numberInSurah = a.numberInSurah,
                            juz = a.juz,
                            hizbQuarter = a.hizbQuarter,
                            manzil = a.manzil,
                            page = a.page,
                            ruku = a.ruku,
                            sajda = a.sajda.let {
                                when (it) {
                                    is JsonObject -> {
                                        val obj = it.jsonObject
                                        val id = obj["id"]?.jsonPrimitive?.intOrNull
                                            ?: return@let null
                                        val recommended =
                                            obj["recommended"]?.jsonPrimitive?.booleanOrNull == true
                                        val obligatory =
                                            obj["obligatory"]?.jsonPrimitive?.booleanOrNull == true
                                        Sajda(
                                            id = id,
                                            recommended = recommended,
                                            obligatory = obligatory
                                        )
                                    }

                                    is JsonPrimitive -> {
                                        val bool = (((it.booleanOrNull
                                            ?: it.content.toBooleanStrictOrNull()) == true))
                                        null
                                    }

                                    else -> null
                                }
                            },
                            text = a.text,
                            surahNumber = s.number
                        )
                    }
                }

                dao.insertEdition(edition)
                dao.insertSurahs(surahs)
                dao.insertAyahs(ayahs)

                emit(Resource.Success(dao.getSurahsWithAyahs(edition.identifier)))

            }

            if (withLocalAction) {
                try {
                    val editionIds = dao.getEditionIds()
                    val editionId = editionIds.firstOrNull()
                    if (editionId == null) {
                        sendRequest()
                    } else {
                        val surahWithAyahs = dao.getSurahsWithAyahs(editionId)
                        if (surahWithAyahs.isEmpty()) {
                            sendRequest()
                        } else {
                            emit(Resource.Success(surahWithAyahs))
                        }
                    }
                } catch (e: Exception) {
                    emit(Resource.Error("Unexpected error: ${e.message}"))
                }
            } else {
                sendRequest()
            }

        }


}
