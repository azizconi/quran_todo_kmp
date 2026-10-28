package tj.app.quran_todo.data.repository

import io.ktor.util.date.getTimeMillis
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import tj.app.quran_todo.common.analytics.AppTelemetry
import tj.app.quran_todo.common.utils.Resource
import tj.app.quran_todo.data.bundled.BundledQuranSource
import tj.app.quran_todo.data.bundled.QuranFacts
import tj.app.quran_todo.data.database.dao.QuranDao
import tj.app.quran_todo.data.database.entity.quran.SurahWithAyahs
import tj.app.quran_todo.data.remote.DataDebug
import tj.app.quran_todo.domain.repository.QuranRemoteRepository

/**
 * The Quran text comes with the app (see [BundledQuranSource]): it is written to the database once, in one
 * transaction, and read back from there. No network is involved, so the very first start works offline.
 *
 * The bundled text is (re)written when the database has no Quran, has an incomplete one (not 114 surahs and
 * 6236 ayahs) or still holds the old download whose first ayah starts with a byte order mark (U+FEFF).
 */
class QuranRemoteRepositoryImpl(
    private val dao: QuranDao,
    private val source: BundledQuranSource = BundledQuranSource(),
    private val debug: DataDebug = DataDebug.Off,
) : QuranRemoteRepository {
    private val installMutex = Mutex()

    /**
     * [withLocalAction] true: use what is in the database when it is complete. false: the person asked to
     * reload (the "Retry" buttons), so the bundled text is written again.
     */
    override fun getCompleteQuran(withLocalAction: Boolean): Flow<Resource<List<SurahWithAyahs>>> =
        flow {
            emit(Resource.Loading())
            try {
                ensureInstalled(force = !withLocalAction)
                emit(Resource.Success(dao.getSurahsWithAyahs(BundledQuranSource.EDITION_ID)))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AppTelemetry.logError(
                    throwable = e,
                    context = "quran_remote_load_failed",
                    params = mapOf("with_local_action" to withLocalAction.toString())
                )
                emit(Resource.Error("Unexpected error: ${e.message}"))
            }
        }

    private suspend fun ensureInstalled(force: Boolean) {
        installMutex.withLock {
            // Checked under the lock: a second collector waits here and then finds the text already written.
            val reason = if (force) "forced" else missingReason()
            if (reason == null) return
            val started = getTimeMillis()
            val (quran, timing) = source.loadTimed()
            val parsed = getTimeMillis()
            dao.replaceQuran(quran.edition, quran.surahs, quran.ayahs)
            val finished = getTimeMillis()
            debug.log {
                "Quran text written to the database ($reason): read ${timing.readMs} ms, parse ${timing.parseMs} ms " +
                    "(on Dispatchers.Default), write ${finished - parsed} ms in one transaction, " +
                    "total ${finished - started} ms, ${quran.surahs.size} surahs, ${quran.ayahs.size} ayahs"
            }
        }
    }

    /** Why the bundled text has to be written, or null when the database already has the complete Quran. */
    private suspend fun missingReason(): String? {
        if (BundledQuranSource.EDITION_ID !in dao.getEditionIds()) return "no edition"
        val surahs = dao.countSurahs()
        if (surahs != QuranFacts.SURAH_COUNT) return "$surahs surahs"
        val ayahs = dao.countAyahs()
        if (ayahs != QuranFacts.AYAH_COUNT) return "$ayahs ayahs"
        val firstAyah = dao.getFirstAyahText()
        if (firstAyah == null) return "no first ayah"
        if (firstAyah.startsWith(BOM)) return "BOM in 1:1"
        return null
    }

    private companion object {
        val BOM: Char = 0xFEFF.toChar() // byte order mark
    }
}
