package tj.app.quran_todo.data.database

import androidx.room.migration.Migration

/**
 * Every schema change after version 10 goes here as an explicit [Migration]; the database is built with all of
 * them on both platforms (see [getRoomDatabase]). There is NO destructive fallback: a missing migration must
 * fail a test, never wipe the progress of a person.
 *
 * Version 10 is what users have today. It was established from the release bundles in git history: the
 * Android App Bundles of 1.0.0 (versionCode 1), the one of versionCode 5 and the one of 1.0.2 (versionCode 7,
 * the current Play release) all contain `QuranTodoDatabase_Impl` with the identity hash of
 * `schemas/.../10.json`, i.e. exactly this schema; `@Database(version = 10)` has not changed since
 * 2026-01-10. So there is nothing to migrate yet and the list is empty. Task B1 adds `MIGRATION_10_11` here
 * (only `CREATE TABLE` for the hifz tables) and `schemas/.../11.json`.
 *
 * How to add one:
 * 1. bump `version` in [QuranTodoDatabase], build once so Room writes `schemas/.../<n>.json`, commit it;
 * 2. add `val MIGRATION_10_11 = object : Migration(10, 11) { override fun migrate(connection: SQLiteConnection) { ... } }`
 *    and list it in [AllMigrations];
 * 3. extend `androidInstrumentedTest/.../MigrationTest.kt` (it already creates a version 10 database from the
 *    exported schema and checks that user data survives).
 */
val AllMigrations: Array<Migration> = emptyArray()
