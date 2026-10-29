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
 * Version 10 is what every Play release up to 1.0.2 has; version 11 (task B1) adds the tables of the passage
 * model and [MIGRATION_10_11] only creates them. These tests check that the migration passes Room's validation
 * against `11.json` (columns, keys, indices, foreign keys), that the rows of a person who used version 10 survive,
 * and that the new tables are usable afterwards - also when the database is opened by the app's own builder.
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
    fun theExportedVersion11SchemaCanBeCreated() {
        helper.createDatabase(11).close()
    }

    private fun SQLiteConnection.count(table: String): Long =
        prepare("SELECT COUNT(*) FROM $table").use { statement ->
            assertTrue(statement.step())
            statement.getLong(0)
        }

    @Test
    fun migrate10To11() {
        helper.createDatabase(10).use { it.insertUserData() }

        // Room compares the migrated database with schemas/.../11.json: a wrong column, key or index fails here.
        val migrated = helper.runMigrationsAndValidate(11, AllMigrations.toList())
        migrated.use { connection ->
            // The rows of version 10 are all there.
            assertEquals(2L, connection.count("surah_todo"))
            assertEquals(1L, connection.count("ayah_todo"))
            assertEquals(1L, connection.count("ayah_note"))
            assertEquals(1L, connection.count("ayah_review"))
            assertEquals(1L, connection.count("focus_session"))
            assertEquals(1L, connection.count("ayah_translation"))
            connection.prepare("SELECT status FROM surah_todo WHERE surahNumber = 112").use { statement ->
                assertTrue(statement.step())
                assertEquals("LEARNED", statement.getText(0))
            }
            connection.prepare("SELECT intervalIndex, nextReviewAt FROM ayah_review WHERE ayahNumber = 6222").use { statement ->
                assertTrue(statement.step())
                assertEquals(2L, statement.getLong(0))
                assertEquals(1700086400000L, statement.getLong(1))
            }

            // The new tables exist, are empty and accept rows (a passage, a difficult ayah of it, a log record, a plan, a mark).
            for (table in listOf("hifz_plan", "hifz_passage", "passage_hard", "review_log", "hifz_meta")) {
                assertEquals(0L, connection.count(table), table)
            }
            connection.execSQL("PRAGMA foreign_keys = ON")
            connection.execSQL(
                "INSERT INTO hifz_passage (surah, startAyah, endAyah, learnedEpochDay, stage, nextDueEpochDay, lastReviewedEpochDay, lapses) " +
                    "VALUES (112, 1, 4, 20000, 'R1', 20001, NULL, 0)",
            )
            connection.execSQL("INSERT INTO passage_hard (passageId, ayahInSurah, clearsAfter) VALUES (1, 2, 2)")
            connection.execSQL(
                "INSERT INTO review_log (passageId, surah, startAyah, endAyah, kind, grade, epochDay, timestamp) " +
                    "VALUES (1, 112, 1, 4, 'NEW', 'GOOD', 20000, 1700000000000)",
            )
            connection.execSQL(
                "INSERT INTO hifz_plan (id, scope, surahOrder, dailyNewAyahs, targetEpochDay, startedEpochDay, manzilDays) " +
                    "VALUES (1, 'CUSTOM', '114,113', 5, NULL, 20000, 7)",
            )
            connection.execSQL("INSERT INTO hifz_meta (`key`, value) VALUES ('legacy_import', 'done')")
            // Deleting a passage takes its difficult ayahs along (foreign key), but the history stays (no foreign key on purpose).
            connection.execSQL("DELETE FROM hifz_passage WHERE id = 1")
            assertEquals(0L, connection.count("passage_hard"))
            assertEquals(1L, connection.count("review_log"))
        }
    }

    @Test
    fun migrate10To11OfAnEmptyDatabase() {
        helper.createDatabase(10).close()
        helper.runMigrationsAndValidate(11, AllMigrations.toList()).use { connection ->
            assertEquals(0L, connection.count("hifz_passage"))
            assertEquals(0L, connection.count("surah_todo"))
        }
    }

    @Test
    fun opensVersion10WithTheMigrationsOfTheApp() {
        helper.createDatabase(10).use { it.insertUserData() }

        val migrated = helper.runMigrationsAndValidate(11, AllMigrations.toList())
        migrated.use { connection -> assertEquals(2L, connection.count("surah_todo")) }
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

            // The builder of the app migrated the database to 11: the hifz tables are there and empty.
            val hifz = database.getHifzDao()
            assertTrue(hifz.getPassages().isEmpty())
            assertEquals(null, hifz.getPlan())
            assertEquals(null, hifz.getMeta("legacy_import"))
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
