package tj.app.quran_todo.data

import tj.app.quran_todo.data.bundled.BundledQuranSource
import tj.app.quran_todo.data.database.dao.AyahTranslationDao
import tj.app.quran_todo.data.database.dao.ChapterNameCacheDao
import tj.app.quran_todo.data.database.dao.ChapterNameDao
import tj.app.quran_todo.data.database.dao.QuranDao
import tj.app.quran_todo.data.database.entity.quran.AyahEntity
import tj.app.quran_todo.data.database.entity.quran.ChapterNameCacheEntity
import tj.app.quran_todo.data.database.entity.quran.ChapterNameEntity
import tj.app.quran_todo.data.database.entity.quran.EditionEntity
import tj.app.quran_todo.data.database.entity.quran.SurahEntity
import tj.app.quran_todo.data.database.entity.quran.SurahWithAyahs
import tj.app.quran_todo.data.database.entity.translation.AyahTranslationEntity
import java.io.File

/** Where the bundled files live in the source tree; local unit tests run with the module directory as the working one. */
object BundledFiles {
    private val root: File = generateSequence(File("").absoluteFile) { it.parentFile }
        .map { dir -> File(dir, "src/commonMain/composeResources/files/quran") }
        .firstOrNull { it.isDirectory }
        ?: generateSequence(File("").absoluteFile) { it.parentFile }
            .map { dir -> File(dir, "composeApp/src/commonMain/composeResources/files/quran") }
            .first { it.isDirectory }

    fun file(name: String): File = File(root, name)

    fun quranTsv(): String = file("quran-uthmani.tsv").readText(Charsets.UTF_8)

    /** A [BundledQuranSource] that reads the real bundled file from disk (the app reads it through Res). */
    fun source(transform: (String) -> String = { it }): BundledQuranSource =
        BundledQuranSource(readBytes = { path ->
            check(path == BundledQuranSource.PATH) { "unexpected path $path" }
            transform(quranTsv()).toByteArray(Charsets.UTF_8)
        })
}

/** In-memory [QuranDao]; [replaceQuran] counts its calls and can be made to fail. */
class FakeQuranDao : QuranDao {
    var editions: List<EditionEntity> = emptyList()
    var surahs: List<SurahEntity> = emptyList()
    var ayahs: List<AyahEntity> = emptyList()
    var replaceCalls = 0
    var failReplaceWith: Throwable? = null

    override suspend fun insertEdition(edition: EditionEntity) {
        editions = editions.filter { it.identifier != edition.identifier } + edition
    }

    override suspend fun insertSurahs(surahs: List<SurahEntity>) {
        this.surahs = this.surahs.filter { s -> surahs.none { it.number == s.number } } + surahs
    }

    override suspend fun insertAyahs(ayahs: List<AyahEntity>) {
        this.ayahs = this.ayahs.filter { a -> ayahs.none { it.number == a.number } } + ayahs
    }

    override suspend fun getSurahsWithAyahs(editionId: String): List<SurahWithAyahs> =
        surahs.filter { it.editionId == editionId }
            .sortedBy { it.number }
            .map { surah -> SurahWithAyahs(surah, ayahs.filter { it.surahNumber == surah.number }.sortedBy { it.number }) }

    override suspend fun getEditionIds(): List<String> = editions.map { it.identifier }

    override suspend fun clearAyahs() {
        ayahs = emptyList()
    }

    override suspend fun clearSurahs() {
        surahs = emptyList()
    }

    override suspend fun clearEditions() {
        editions = emptyList()
    }

    override suspend fun countAyahs(): Int = ayahs.size

    override suspend fun countSurahs(): Int = surahs.size

    override suspend fun getFirstAyahText(): String? = ayahs.firstOrNull { it.number == 1 }?.text

    override suspend fun replaceQuran(edition: EditionEntity, surahs: List<SurahEntity>, ayahs: List<AyahEntity>) {
        replaceCalls++
        failReplaceWith?.let { throw it }
        // All or nothing, like the real transaction.
        this.editions = listOf(edition)
        this.surahs = surahs
        this.ayahs = ayahs
    }
}

class FakeAyahTranslationDao : AyahTranslationDao {
    val rows = mutableListOf<AyahTranslationEntity>()
    var insertCalls = 0

    override suspend fun getBySurahAndLanguage(surahNumber: Int, languageCode: String): List<AyahTranslationEntity> =
        rows.filter { it.surahNumber == surahNumber && it.languageCode == languageCode }

    override suspend fun countByLanguage(languageCode: String): Int = rows.count { it.languageCode == languageCode }

    override suspend fun insertAll(items: List<AyahTranslationEntity>) {
        insertCalls++
        items.forEach { item ->
            rows.removeAll { it.languageCode == item.languageCode && it.ayahNumber == item.ayahNumber }
            rows += item
        }
    }
}

class FakeChapterNameDao : ChapterNameDao {
    val rows = mutableListOf<ChapterNameEntity>()

    override suspend fun getByLanguage(languageCode: String): List<ChapterNameEntity> =
        rows.filter { it.languageCode == languageCode }

    override suspend fun insertAll(items: List<ChapterNameEntity>) {
        rows += items
    }
}

class FakeChapterNameCacheDao : ChapterNameCacheDao {
    val rows = mutableMapOf<String, ChapterNameCacheEntity>()

    override suspend fun get(languageCode: String): ChapterNameCacheEntity? = rows[languageCode]

    override suspend fun upsert(entity: ChapterNameCacheEntity) {
        rows[entity.languageCode] = entity
    }
}
