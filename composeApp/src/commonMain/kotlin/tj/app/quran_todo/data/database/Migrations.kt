package tj.app.quran_todo.data.database

import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

/**
 * Every schema change after version 10 goes here as an explicit [Migration]; the database is built with all of
 * them on both platforms (see [getRoomDatabase]). There is NO destructive fallback: a missing migration must
 * fail a test, never wipe the progress of a person.
 *
 * Version 10 is what users had before the passage model. It was established from the release bundles in git
 * history: the Android App Bundles of 1.0.0 (versionCode 1), the one of versionCode 5 and the one of 1.0.2
 * (versionCode 7) all contain `QuranTodoDatabase_Impl` with the identity hash of `schemas/.../10.json`;
 * `@Database(version = 10)` had not changed since 2026-01-10.
 *
 * Version 11 (task B1) adds the tables of the passage-based memorization model (`hifz_plan`, `hifz_passage`,
 * `passage_hard`, `review_log`, `hifz_meta`). It only creates tables and indices; the old tables (`surah_todo`,
 * `ayah_todo`, `ayah_review`, ...) are neither changed nor dropped, iOS still reads them. The rows of the old
 * tables are turned into passages later, once, by the legacy import (`hifz/legacy`), not by SQL.
 *
 * How to add the next one:
 * 1. bump `version` in [QuranTodoDatabase], build once so Room writes `schemas/.../<n>.json`, commit it;
 * 2. add `val MIGRATION_11_12 = object : Migration(11, 12) { override fun migrate(connection: SQLiteConnection) { ... } }`
 *    with the SQL copied word for word from the `createSql` fields of the new `<n>.json` (the placeholder
 *    TABLE_NAME in them is the name of the table), and list it in [AllMigrations];
 * 3. extend `androidInstrumentedTest/.../MigrationTest.kt` (it creates a database of the previous version from the
 *    exported schema, runs the migration through Room's validator and checks that user data survives).
 */
val MIGRATION_10_11: Migration = object : Migration(10, 11) {
    override fun migrate(connection: SQLiteConnection) {
        // Copied from `createSql` in schemas/.../11.json.
        connection.execSQL(
            "CREATE TABLE IF NOT EXISTS `hifz_plan` (`id` INTEGER NOT NULL, `scope` TEXT NOT NULL, " +
                "`surahOrder` TEXT NOT NULL, `dailyNewAyahs` INTEGER NOT NULL, `targetEpochDay` INTEGER, " +
                "`startedEpochDay` INTEGER NOT NULL, `manzilDays` INTEGER NOT NULL, PRIMARY KEY(`id`))",
        )
        connection.execSQL(
            "CREATE TABLE IF NOT EXISTS `hifz_passage` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`surah` INTEGER NOT NULL, `startAyah` INTEGER NOT NULL, `endAyah` INTEGER NOT NULL, " +
                "`learnedEpochDay` INTEGER NOT NULL, `stage` TEXT NOT NULL, `nextDueEpochDay` INTEGER, " +
                "`lastReviewedEpochDay` INTEGER, `lapses` INTEGER NOT NULL)",
        )
        connection.execSQL("CREATE INDEX IF NOT EXISTS `index_hifz_passage_surah` ON `hifz_passage` (`surah`)")
        connection.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_hifz_passage_stage_nextDueEpochDay` " +
                "ON `hifz_passage` (`stage`, `nextDueEpochDay`)",
        )
        connection.execSQL(
            "CREATE TABLE IF NOT EXISTS `passage_hard` (`passageId` INTEGER NOT NULL, " +
                "`ayahInSurah` INTEGER NOT NULL, `clearsAfter` INTEGER NOT NULL, " +
                "PRIMARY KEY(`passageId`, `ayahInSurah`), FOREIGN KEY(`passageId`) " +
                "REFERENCES `hifz_passage`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
        )
        connection.execSQL(
            "CREATE TABLE IF NOT EXISTS `review_log` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`passageId` INTEGER, `surah` INTEGER NOT NULL, `startAyah` INTEGER NOT NULL, " +
                "`endAyah` INTEGER NOT NULL, `kind` TEXT NOT NULL, `grade` TEXT NOT NULL, " +
                "`epochDay` INTEGER NOT NULL, `timestamp` INTEGER NOT NULL)",
        )
        connection.execSQL("CREATE INDEX IF NOT EXISTS `index_review_log_epochDay` ON `review_log` (`epochDay`)")
        connection.execSQL(
            "CREATE TABLE IF NOT EXISTS `hifz_meta` (`key` TEXT NOT NULL, `value` TEXT NOT NULL, PRIMARY KEY(`key`))",
        )
    }
}

val AllMigrations: Array<Migration> = arrayOf(MIGRATION_10_11)
