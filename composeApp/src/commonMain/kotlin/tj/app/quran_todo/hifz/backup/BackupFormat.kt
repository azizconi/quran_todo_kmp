package tj.app.quran_todo.hifz.backup

import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.Serializable

/**
 * The backup file of the memorization data and the settings (task B5, "Transfer and backup"): one JSON document that the
 * person saves to a file or sends to themselves and imports on this or another phone. The pure functions that make and check
 * it are in [BackupCodec]; the database side is `HifzRepository.exportBackupData` / `replaceWithBackup`.
 *
 * Example (shortened):
 * ```
 * {"format":"quran-hifz-backup","formatVersion":1,"appVersion":"1.0.2 (7)","exportedAt":1773312000000,"dbVersion":11,
 *  "hifz":{"plan":{"scope":"JUZ_30","surahOrder":[114,113],"dailyNewAyahs":5,"startedEpochDay":20500,"manzilDays":7},
 *          "passages":[{"id":1,"surah":114,"startAyah":1,"endAyah":6,"learnedEpochDay":20500,"stage":"R3",
 *                       "nextDueEpochDay":20503,"lastReviewedEpochDay":20500,"lapses":0}],
 *          "hard":[{"passageId":1,"ayah":4,"clearsAfter":2}],
 *          "reviewLog":[{"id":1,"passageId":1,"surah":114,"startAyah":1,"endAyah":6,"kind":"NEW","grade":"GOOD",
 *                        "epochDay":20500,"timestamp":1772000000000}],
 *          "meta":{"legacy_import":"done"}},
 *  "notes":[{"surah":2,"ayah":255,"text":"...","updatedAt":1772000000000}],
 *  "settings":{"language":"ru","themeMode":"DARK","palette":"FOREST","readingFont":"madinah_mushaf","readingFontSize":28}}
 * ```
 *
 * Rules of the format:
 * - Days are epoch days (days since 1970-01-01 in the time zone of the phone), instants are epoch milliseconds, ayah numbers are
 *   numbers in the surah (never global ones), enums are written by name. The old progress tables (`surah_todo`, `ayah_todo`,
 *   `ayah_review`) are not in the file: the importer rebuilds `surah_todo` and `ayah_todo` from the passages (the mirror).
 * - The reader ignores fields it does not know, so a later app can add fields without a new [VERSION]. A change that an old
 *   reader cannot ignore raises [VERSION]; an old app then refuses the file with [BackupError.NewerVersion].
 * - Settings never fail an import: a value that is not valid is dropped (see [BackupSettings.sanitized]). Analytics consent is
 *   deliberately not part of the file.
 */
object BackupFormat {
    /** The value of the field `format`: what tells our file from any other JSON. */
    const val NAME = "quran-hifz-backup"

    /** The value of the field `formatVersion` this app writes and understands. */
    const val VERSION = 1

    /** The version of the Room schema the file was made on (information only). */
    const val DB_VERSION = 11

    const val MIME_TYPE = "application/json"

    /** A bigger file is not a backup of ours (a year of use is about 3 MB); protects the memory. */
    const val MAX_FILE_BYTES: Long = 32L * 1024 * 1024

    /** `quran-hifz-2026-03-12.json`: the day is the calendar day of [exportedAt] in [zone]. */
    fun fileName(exportedAt: Long, zone: TimeZone = TimeZone.currentSystemDefault()): String {
        val date = Instant.fromEpochMilliseconds(exportedAt).toLocalDateTime(zone).date
        return "quran-hifz-${date.year.toString().padStart(4, '0')}-" +
            "${date.monthNumber.toString().padStart(2, '0')}-${date.dayOfMonth.toString().padStart(2, '0')}.json"
    }
}

/** The whole file. The fields have no defaults on purpose: a file without them is damaged, not "empty". */
@Serializable
data class BackupFile(
    val format: String,
    val formatVersion: Int,
    val appVersion: String,
    /** When the file was made, epoch milliseconds. */
    val exportedAt: Long,
    val dbVersion: Int,
    val hifz: BackupHifz,
    val notes: List<BackupNote>,
    val settings: BackupSettings = BackupSettings(),
)

/** The tables of the memorization model. */
@Serializable
data class BackupHifz(
    /** The active plan; absent when there is none. */
    val plan: BackupPlan? = null,
    val passages: List<BackupPassage>,
    /** The difficult ayahs of the passages (`passage_hard`). */
    val hard: List<BackupHard>,
    val reviewLog: List<BackupLogEntry>,
    /** `hifz_meta`: service values. */
    val meta: Map<String, String>,
)

@Serializable
data class BackupPlan(
    /** `HifzScope` by name. */
    val scope: String,
    val surahOrder: List<Int>,
    val dailyNewAyahs: Int,
    val targetEpochDay: Int? = null,
    val startedEpochDay: Int,
    val manzilDays: Int,
)

@Serializable
data class BackupPassage(
    val id: Long,
    val surah: Int,
    val startAyah: Int,
    val endAyah: Int,
    val learnedEpochDay: Int,
    /** `PassageStage` by name. */
    val stage: String,
    val nextDueEpochDay: Int? = null,
    val lastReviewedEpochDay: Int? = null,
    val lapses: Int,
)

@Serializable
data class BackupHard(
    val passageId: Long,
    /** The ayah in the surah. */
    val ayah: Int,
    val clearsAfter: Int,
)

@Serializable
data class BackupLogEntry(
    val id: Long,
    /** The passage the record is about; null for a check of several passages or when the passage is gone. */
    val passageId: Long? = null,
    val surah: Int,
    val startAyah: Int,
    val endAyah: Int,
    /** `ReviewKind` by name. */
    val kind: String,
    /** `ReviewGrade` by name. */
    val grade: String,
    val epochDay: Int,
    val timestamp: Long,
)

/** A note on an ayah. Both numbers are the ones a person knows: the surah and the ayah in it. */
@Serializable
data class BackupNote(
    val surah: Int,
    val ayah: Int,
    val text: String,
    val updatedAt: Long,
)

/**
 * What the person set up. Every field is optional: an absent one leaves the current value on the phone as it is, so an old
 * file with fewer fields and a file of another version both import fine. Values are plain strings and numbers (enums by
 * name); [sanitized] drops the ones this app cannot use.
 */
@Serializable
data class BackupSettings(
    /** `AppLanguage` code: ru, en, tg, uz, tr. */
    val language: String? = null,
    /** `ThemeMode` by name, the explicit choice of the person. */
    val themeMode: String? = null,
    /** "System" in the theme control: the theme follows the day/night mode of the phone. */
    val themeFollowsSystem: Boolean? = null,
    /** `ThemePalette` by name. */
    val palette: String? = null,
    /** `ReadingFontStyle.storageValue`. */
    val readingFont: String? = null,
    val readingFontSize: Int? = null,
    /** The old "ayahs per day" goal of the home screen. */
    val dailyGoal: Int? = null,
    val showTranslation: Boolean? = null,
    val highlightPlayingAyah: Boolean? = null,
    val autoplayOnOpen: Boolean? = null,
    val autoAdvanceAyahs: Boolean? = null,
    val playbackSpeed: Float? = null,
    /** -1 is "forever". */
    val repeatCount: Int? = null,
    /** `ReaderLayout` by name: LIST or MUSHAF. */
    val readerLayout: String? = null,
    val keepScreenOn: Boolean? = null,
    /** `Reciter.id`. */
    val reciter: String? = null,
    /** How many times each ayah sounds in the "Listen" step of the lesson. */
    val lessonAyahRepeats: Int? = null,
    /** How many times the whole passage sounds in the "Listen" step of the lesson. */
    val lessonPassageRepeats: Int? = null,
    /** `HifzHintMode` by name: NONE, FIRST_WORD, FIRST_LETTERS, EVERY_OTHER or PROGRESSIVE; an unknown name is dropped on import. */
    val hifzHint: String? = null,
    val hapticsEnabled: Boolean? = null,
    val reduceMotion: Boolean? = null,
    /** The daily lesson reminder (Android): on or off. */
    val reminderEnabled: Boolean? = null,
    /** When the reminder comes: minutes after midnight, 0..1439 (20:00 is 1200). */
    val reminderMinuteOfDay: Int? = null,
)

/** The database part of a backup: what `HifzRepository` reads and writes. */
data class BackupData(
    val hifz: BackupHifz,
    val notes: List<BackupNote>,
)
