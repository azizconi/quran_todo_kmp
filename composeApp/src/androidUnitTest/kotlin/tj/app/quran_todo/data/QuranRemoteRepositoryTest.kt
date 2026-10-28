package tj.app.quran_todo.data

import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import tj.app.quran_todo.common.utils.Resource
import tj.app.quran_todo.data.bundled.BundledQuranSource
import tj.app.quran_todo.data.bundled.QuranTsvParser
import tj.app.quran_todo.data.database.entity.quran.SurahWithAyahs
import tj.app.quran_todo.data.repository.QuranRemoteRepositoryImpl
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class QuranRemoteRepositoryTest {
    private val bom: Char = 0xFEFF.toChar()
    private val bundled = QuranTsvParser.parse(BundledFiles.quranTsv())

    private fun repository(dao: FakeQuranDao, source: BundledQuranSource = BundledFiles.source()) =
        QuranRemoteRepositoryImpl(dao = dao, source = source)

    private fun List<Resource<List<SurahWithAyahs>>>.successData(): List<SurahWithAyahs> {
        assertIs<Resource.Loading<*>>(first())
        val last = last()
        assertIs<Resource.Success<List<SurahWithAyahs>>>(last)
        return last.data
    }

    private fun FakeQuranDao.fillWithBundled() {
        editions = listOf(bundled.edition)
        surahs = bundled.surahs
        ayahs = bundled.ayahs
    }

    @Test
    fun anEmptyDatabaseGetsTheBundledTextInOneTransactionWithoutAnyNetwork() = runBlocking {
        val dao = FakeQuranDao()
        val results = repository(dao).getCompleteQuran(withLocalAction = true).toList()

        val quran = results.successData()
        assertEquals(114, quran.size)
        assertEquals(6236, quran.sumOf { it.ayahs.size })
        assertEquals(1, dao.replaceCalls, "one replaceQuran call = one transaction")
        assertEquals(6236, dao.ayahs.size)
        assertTrue(dao.ayahs.none { it.text.contains(bom) })
    }

    @Test
    fun aCompleteDatabaseIsLeftAlone() = runBlocking {
        val dao = FakeQuranDao().apply { fillWithBundled() }
        val results = repository(dao).getCompleteQuran(withLocalAction = true).toList()

        assertEquals(114, results.successData().size)
        assertEquals(0, dao.replaceCalls)
    }

    @Test
    fun anIncompleteDatabaseIsRewritten() = runBlocking {
        // What an interrupted download used to leave behind: surahs without (all) their ayahs.
        val dao = FakeQuranDao().apply {
            editions = listOf(bundled.edition)
            surahs = bundled.surahs
            ayahs = bundled.ayahs.take(4000)
        }
        val results = repository(dao).getCompleteQuran(withLocalAction = true).toList()

        assertEquals(6236, results.successData().sumOf { it.ayahs.size })
        assertEquals(1, dao.replaceCalls)
    }

    @Test
    fun surahsWithoutAnyAyahAreRewritten() = runBlocking {
        val dao = FakeQuranDao().apply {
            editions = listOf(bundled.edition)
            surahs = bundled.surahs
        }
        repository(dao).getCompleteQuran(withLocalAction = true).toList().successData()
        assertEquals(1, dao.replaceCalls)
        assertEquals(6236, dao.ayahs.size)
    }

    @Test
    fun aByteOrderMarkInTheFirstAyahIsReplacedByTheBundledText() = runBlocking {
        val dao = FakeQuranDao().apply {
            fillWithBundled()
            ayahs = ayahs.map { if (it.number == 1) it.copy(text = bom + it.text) else it }
        }
        assertTrue(dao.getFirstAyahText()!!.startsWith(bom))

        repository(dao).getCompleteQuran(withLocalAction = true).toList().successData()

        assertEquals(1, dao.replaceCalls)
        assertFalse(dao.getFirstAyahText()!!.startsWith(bom))
        assertTrue(dao.getFirstAyahText()!!.startsWith("بِسْمِ"))
    }

    @Test
    fun theRetryButtonWritesTheBundledTextAgain() = runBlocking {
        val dao = FakeQuranDao().apply { fillWithBundled() }
        repository(dao).getCompleteQuran(withLocalAction = false).toList().successData()
        assertEquals(1, dao.replaceCalls)
    }

    @Test
    fun aDamagedBundledFileIsAnErrorAndTheDatabaseIsNotTouched() = runBlocking {
        val dao = FakeQuranDao()
        val damaged = BundledFiles.source { text -> text.lines().filterNot { it.startsWith("A\t6236\t") }.joinToString("\n") }
        val results = repository(dao, damaged).getCompleteQuran(withLocalAction = true).toList()

        assertIs<Resource.Loading<*>>(results.first())
        assertIs<Resource.Error<*>>(results.last())
        assertEquals(0, dao.replaceCalls)
        assertTrue(dao.ayahs.isEmpty() && dao.surahs.isEmpty())
    }

    @Test
    fun aFailingWriteIsAnErrorNotACrash() = runBlocking {
        val dao = FakeQuranDao().apply { failReplaceWith = IllegalStateException("disk full") }
        val results = repository(dao).getCompleteQuran(withLocalAction = true).toList()

        val last = results.last()
        assertIs<Resource.Error<*>>(last)
        assertTrue("disk full" in last.errorMessage)
    }

    @Test
    fun twoCollectorsWriteTheTextOnlyOnce() = runBlocking {
        val dao = FakeQuranDao()
        val repository = repository(dao)
        val a = repository.getCompleteQuran(withLocalAction = true).toList()
        val b = repository.getCompleteQuran(withLocalAction = true).toList()
        a.successData()
        b.successData()
        assertEquals(1, dao.replaceCalls)
    }
}
