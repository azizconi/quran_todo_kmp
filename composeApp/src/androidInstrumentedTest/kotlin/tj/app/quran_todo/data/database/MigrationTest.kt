package tj.app.quran_todo.data.database

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import tj.app.quran_todo.data.database.entity.quran.AyahEntity
import tj.app.quran_todo.data.database.entity.quran.EditionEntity
import tj.app.quran_todo.data.database.entity.quran.SurahEntity
import tj.app.quran_todo.data.database.entity.todo.AyahTodoStatus
import tj.app.quran_todo.data.database.entity.todo.SurahTodoStatus
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Migration tests on a device or an emulator (Room's MigrationTestHelper reads `schemas/.../<version>.json`
 * from the assets of this test APK; the Room Gradle plugin puts them there).
 *
 * Run: `./gradlew :composeApp:connectedDebugAndroidTest`
 * (only this class: `-Pandroid.testInstrumentationRunnerArguments.class=tj.app.quran_todo.data.database.MigrationTest`).
 *
 * Today the schema is at version 10, which is what every Play release up to 1.0.2 has, and [AllMigrations] is
 * empty. These tests therefore check that a database created from the exported version 10 schema is opened by
 * the app's own database builder without losing data. Task B1 adds `MIGRATION_10_11`: copy
 * [userDataSurvivesOpeningAVersion10DatabaseWithTheAppsBuilder] into a `migrate10To11` test that calls
 * `helper.runMigrationsAndValidate(11, AllMigrations.toList())` and checks the same rows afterwards.
 */
@RunWith(AndroidJUnit4::class)
class MigrationTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val dbFile = instrumentation.targetContext.getDatabasePath("migration-test.db")

    @get:Rule
    val helper = MigrationTestHelper(
        instrumentation = instrumentation,
        file = dbFile,
        driver = BundledSQLiteDriver(),
        databaseClass = QuranTodoDatabase::class,
    )

    @Before
    fun deleteOldFiles() = deleteDatabaseFiles()

    @After
    fun cleanUp() = deleteDatabaseFiles()

    private fun deleteDatabaseFiles() {
        listOf("", "-wal", "-shm", "-journal").forEach { suffix ->
            java.io.File(dbFile.path + suffix).delete()
        }
    }

    /** Rows of a person who has been using the app: statuses, a note, a review, a cached translation. */
    private fun SQLiteConnection.insertUserData() {
        execSQL("INSERT INTO surah_todo (surahNumber, status) VALUES (112, 'LEARNED'), (114, 'LEARNING')")
        execSQL("INSERT INTO ayah_todo (ayahNumber, surahNumber, status, updatedAt) VALUES (6222, 112, 'LEARNED', 1700000000000)")
        execSQL("INSERT INTO ayah_note (ayahNumber, surahNumber, note, updatedAt) VALUES (6222, 112, 'my note', 1700000000001)")
        execSQL("INSERT INTO ayah_review (ayahNumber, surahNumber, nextReviewAt, intervalIndex, lastReviewedAt) VALUES (6222, 112, 1700086400000, 2, 1700000000002)")
        execSQL("INSERT INTO focus_session (startedAt, durationMinutes) VALUES (1700000000003, 25)")
        execSQL("INSERT INTO ayah_translation (ayahNumber, surahNumber, languageCode, text) VALUES (6222, 112, 'en', 'Say, He is Allah, One')")
    }

    @Test
    fun theExportedVersion10SchemaCanBeCreated() {
        helper.createDatabase(10).close()
    }

    @Test
    fun opensVersion10WithTheMigrationsOfTheApp() {
        helper.createDatabase(10).use { it.insertUserData() }

        val migrated = helper.runMigrationsAndValidate(10, AllMigrations.toList())
        migrated.use { connection ->
            connection.prepare("SELECT COUNT(*) FROM surah_todo").use { statement ->
                assertTrue(statement.step())
                assertEquals(2L, statement.getLong(0))
            }
        }
    }

    @Test
    fun userDataSurvivesOpeningAVersion10DatabaseWithTheAppsBuilder() = runBlocking {
        helper.createDatabase(10).use { it.insertUserData() }

        // The same way both platforms build the database in the app.
        val database = getRoomDatabase(
            Room.databaseBuilder<QuranTodoDatabase>(
                context = instrumentation.targetContext,
                name = dbFile.absolutePath,
            ),
        )
        try {
            val surahs = database.getSurahTodoDao().getAllSurahTodo().first().associate { it.surahNumber to it.status }
            assertEquals(mapOf(112 to SurahTodoStatus.LEARNED, 114 to SurahTodoStatus.LEARNING), surahs)

            val ayahs = database.getAyahTodoDao().getAllOnce()
            assertEquals(1, ayahs.size)
            assertEquals(AyahTodoStatus.LEARNED, ayahs.single().status)

            assertEquals("my note", database.getAyahNoteDao().getByAyahNumber(6222)?.note)
            assertEquals(2, database.getAyahReviewDao().getByAyahNumber(6222)?.intervalIndex)
            assertEquals(
                "Say, He is Allah, One",
                database.getAyahTranslationDao().getBySurahAndLanguage(112, "en").single().text,
            )
        } finally {
            database.close()
        }
    }

    @Test
    fun replacingTheQuranTextKeepsTheUserData() = runBlocking {
        helper.createDatabase(10).use { it.insertUserData() }
        val database = getRoomDatabase(
            Room.databaseBuilder<QuranTodoDatabase>(
                context = instrumentation.targetContext,
                name = dbFile.absolutePath,
            ),
        )
        try {
            val edition = EditionEntity("quran-uthmani", "Uthmani", "text", "ar", "name", "quran")
            val surahs = listOf(SurahEntity(112, "s", "Al-Ikhlas", "Sincerity", "Meccan", edition.identifier))
            val ayahs = (1..4).map { i ->
                AyahEntity(6221 + i, i, 30, 240, 7, 604, 554, null, "text $i", 112)
            }
            val quran = database.getQuranDao()
            // Twice: the second time everything is deleted (ayahs go with their surah) and written again.
            quran.replaceQuran(edition, surahs, ayahs)
            quran.replaceQuran(edition, surahs, ayahs)

            assertEquals(4, quran.countAyahs())
            assertEquals(1, quran.countSurahs())
            assertEquals(2, database.getSurahTodoDao().getAllSurahTodo().first().size)
            assertEquals(1, database.getAyahTodoDao().getAllOnce().size)
            assertEquals("my note", database.getAyahNoteDao().getByAyahNumber(6222)?.note)
            assertEquals(1, database.getAyahReviewDao().getAll().size)
            assertEquals(1, database.getAyahTranslationDao().countByLanguage("en"))
        } finally {
            database.close()
        }
    }
}
