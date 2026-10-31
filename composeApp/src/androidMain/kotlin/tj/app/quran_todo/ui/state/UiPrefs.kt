package tj.app.quran_todo.ui.state

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import tj.app.quran_todo.common.analytics.AppTelemetry
import tj.app.quran_todo.common.audio.Reciter
import tj.app.quran_todo.common.audio.Reciters
import tj.app.quran_todo.common.settings.UserSettingsStorage
import tj.app.quran_todo.common.theme.ThemeStorage

enum class ReaderLayout { LIST, MUSHAF }

/**
 * How much of a hidden ayah the "Recall" step leaves on screen (task F2a). Stored by name; the names also travel in the backup file
 * (`BackupSettingsRules.HINT_MODES` keeps the two lists equal).
 */
enum class HifzHintMode {
    /** The ayah is hidden entirely. */
    NONE,

    /** The first word stays sharp. */
    FIRST_WORD,

    /** Every word is hidden, with its first letter in its place. */
    FIRST_LETTERS,

    /** The 1st, 3rd, 5th... words stay sharp, the rest is hidden. */
    EVERY_OTHER,

    /** The ayah starts whole and the person hides it by quarters from the end ("Hide more"). */
    PROGRESSIVE,
}

/** Values of [UiPrefs.onboardingGoals]. */
object OnboardingGoal {
    const val READ = "read"
    const val LISTEN = "listen"
    const val MEMORIZE = "memorize"
}

/**
 * Reading position as seen by the new UI.
 *
 * [ayahInSurah] is the ayah number inside the surah. It is null when only a legacy position exists:
 * the old audio session wrote the GLOBAL ayah number (1..6236, [legacyGlobalAyah]) to
 * `UserSettingsStorage.getLastPlaybackAyahNumber`, and converting it to an in-surah number needs
 * the loaded Quran (`HomeViewModel.uiState.completeQuran`: `AyahEntity.number` is the global number,
 * `AyahEntity.numberInSurah` the in-surah one), which is not available here.
 */
data class LastReadPosition(
    val surah: Int,
    val ayahInSurah: Int?,
    val legacyGlobalAyah: Int?,
)

/**
 * Android-only UI preferences (docs/redesign/ANDROID_REDESIGN.md, 4.3). Backed by SharedPreferences;
 * every value is observable as a [StateFlow] and changed through its setter.
 * Registered as a Koin singleton.
 */
class UiPrefs(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /**
     * Theme follows the system day/night setting. Defaults to true only while no theme mode was ever
     * saved, so existing users keep the theme they chose.
     */
    private val _themeFollowsSystem = MutableStateFlow(
        if (prefs.contains(KEY_THEME_FOLLOWS_SYSTEM)) {
            prefs.getBoolean(KEY_THEME_FOLLOWS_SYSTEM, true)
        } else {
            ThemeStorage.getSavedThemeMode() == null
        },
    )
    val themeFollowsSystem: StateFlow<Boolean> = _themeFollowsSystem.asStateFlow()

    fun setThemeFollowsSystem(value: Boolean) {
        prefs.edit().putBoolean(KEY_THEME_FOLLOWS_SYSTEM, value).apply()
        _themeFollowsSystem.value = value
    }

    private val _hapticsEnabled = MutableStateFlow(prefs.getBoolean(KEY_HAPTICS, true))
    val hapticsEnabled: StateFlow<Boolean> = _hapticsEnabled.asStateFlow()

    fun setHapticsEnabled(value: Boolean) {
        prefs.edit().putBoolean(KEY_HAPTICS, value).apply()
        _hapticsEnabled.value = value
    }

    /** In-app "Less motion" switch; combined with the system animator scale by the app shell. */
    private val _reduceMotion = MutableStateFlow(prefs.getBoolean(KEY_REDUCE_MOTION, false))
    val reduceMotion: StateFlow<Boolean> = _reduceMotion.asStateFlow()

    fun setReduceMotion(value: Boolean) {
        prefs.edit().putBoolean(KEY_REDUCE_MOTION, value).apply()
        _reduceMotion.value = value
    }

    private val _readerLayout = MutableStateFlow(
        prefs.getString(KEY_READER_LAYOUT, null)
            ?.let { name -> ReaderLayout.entries.firstOrNull { it.name == name } }
            ?: ReaderLayout.LIST,
    )
    val readerLayout: StateFlow<ReaderLayout> = _readerLayout.asStateFlow()

    fun setReaderLayout(value: ReaderLayout) {
        prefs.edit().putString(KEY_READER_LAYOUT, value.name).apply()
        _readerLayout.value = value
    }

    private val _keepScreenOn = MutableStateFlow(prefs.getBoolean(KEY_KEEP_SCREEN_ON, true))
    val keepScreenOn: StateFlow<Boolean> = _keepScreenOn.asStateFlow()

    fun setKeepScreenOn(value: Boolean) {
        prefs.edit().putBoolean(KEY_KEEP_SCREEN_ON, value).apply()
        _keepScreenOn.value = value
    }

    /** "Highlight words while listening" (the "Aa" sheet of the reader): the word that sounds is marked in the text. On by default. */
    private val _highlightWords = MutableStateFlow(prefs.getBoolean(KEY_HIGHLIGHT_WORDS, true))
    val highlightWords: StateFlow<Boolean> = _highlightWords.asStateFlow()

    fun setHighlightWords(value: Boolean) {
        prefs.edit().putBoolean(KEY_HIGHLIGHT_WORDS, value).apply()
        _highlightWords.value = value
    }

    /**
     * "Tajweed" (the "Aa" sheet of the reader, task F3b): the rules of recitation are coloured in the text of the reader and of the
     * lesson. Off by default. The markup is fetched the first time it is turned on (`ui/tajweed/TajweedStore`); the switch stays on without
     * network and the colours come when the file does. It is in the backup file (`BackupSettings.tajweed`, optional).
     */
    private val _tajweed = MutableStateFlow(prefs.getBoolean(KEY_TAJWEED, false))
    val tajweed: StateFlow<Boolean> = _tajweed.asStateFlow()

    fun setTajweed(value: Boolean) {
        prefs.edit().putBoolean(KEY_TAJWEED, value).apply()
        _tajweed.value = value
    }

    /**
     * "Word meanings (English)" (the "Aa" sheet of the text reader, task F4b): a tap on an Arabic word shows its meaning. Off by default;
     * the meanings of a surah are fetched at the first tap in it. It is in the backup file (`BackupSettings.wordMeanings`, optional).
     */
    private val _wordMeanings = MutableStateFlow(prefs.getBoolean(KEY_WORD_MEANINGS, false))
    val wordMeanings: StateFlow<Boolean> = _wordMeanings.asStateFlow()

    fun setWordMeanings(value: Boolean) {
        prefs.edit().putBoolean(KEY_WORD_MEANINGS, value).apply()
        _wordMeanings.value = value
    }

    /**
     * The commentary the person chose last (`TafsirSource.key`, task F4b); null while none was chosen, and the screen then takes the one of
     * the language of the app. It is in the backup file (`BackupSettings.tafsir`, optional).
     */
    private val _tafsirSource = MutableStateFlow(prefs.getString(KEY_TAFSIR_SOURCE, null))
    val tafsirSource: StateFlow<String?> = _tafsirSource.asStateFlow()

    fun setTafsirSource(key: String?) {
        prefs.edit().apply { if (key == null) remove(KEY_TAFSIR_SOURCE) else putString(KEY_TAFSIR_SOURCE, key) }.apply()
        _tafsirSource.value = key
    }

    /** Up to [MAX_RECENT_SURAHS] surah numbers, the most recently opened first. */
    private val _recentSurahs = MutableStateFlow(
        decodeInts(prefs.getString(KEY_RECENT_SURAHS, null)).take(MAX_RECENT_SURAHS),
    )
    val recentSurahs: StateFlow<List<Int>> = _recentSurahs.asStateFlow()

    fun recordRecentSurah(surah: Int) {
        val next = _recentSurahs.value.withRecent(surah)
        if (next == _recentSurahs.value) return
        prefs.edit().putString(KEY_RECENT_SURAHS, encodeInts(next)).apply()
        _recentSurahs.value = next
    }

    private val _pinnedSurahs = MutableStateFlow<Set<Int>>(
        decodeInts(prefs.getString(KEY_PINNED_SURAHS, null)).toCollection(LinkedHashSet()),
    )
    val pinnedSurahs: StateFlow<Set<Int>> = _pinnedSurahs.asStateFlow()

    /** Pins the surah, or unpins it when it is already pinned. */
    fun togglePin(surah: Int) {
        val next = _pinnedSurahs.value.toggled(surah)
        prefs.edit().putString(KEY_PINNED_SURAHS, encodeInts(next)).apply()
        _pinnedSurahs.value = next
    }

    private val _lastReadPosition = MutableStateFlow(readLastReadPosition())

    /**
     * Where the person stopped reading, kept separately from the audio position. Falls back to the
     * legacy playback position when nothing was saved by the new reader yet (see [LastReadPosition]).
     */
    val lastReadPosition: StateFlow<LastReadPosition?> = _lastReadPosition.asStateFlow()

    /** Saves the reading position. The caller debounces (the reader uses 400 ms). */
    fun saveLastRead(surah: Int, ayahInSurah: Int) {
        prefs.edit()
            .putInt(KEY_LAST_READ_SURAH, surah)
            .putInt(KEY_LAST_READ_AYAH, ayahInSurah)
            .apply()
        _lastReadPosition.value = LastReadPosition(surah, ayahInSurah, legacyGlobalAyah = null)
    }

    /**
     * (surah, ayah number inside the surah) saved by the new reader, or null when there is none.
     * A legacy-only position is not returned here because its ayah is a global number; read
     * [lastReadPosition] when the fallback is wanted.
     */
    fun lastRead(): Pair<Int, Int>? {
        val position = _lastReadPosition.value ?: return null
        val ayah = position.ayahInSurah ?: return null
        return position.surah to ayah
    }

    private fun readLastReadPosition(): LastReadPosition? {
        if (prefs.contains(KEY_LAST_READ_SURAH) && prefs.contains(KEY_LAST_READ_AYAH)) {
            return LastReadPosition(
                surah = prefs.getInt(KEY_LAST_READ_SURAH, 1),
                ayahInSurah = prefs.getInt(KEY_LAST_READ_AYAH, 1),
                legacyGlobalAyah = null,
            )
        }
        val legacySurah = UserSettingsStorage.getLastPlaybackSurah() ?: return null
        return LastReadPosition(
            surah = legacySurah,
            ayahInSurah = null,
            legacyGlobalAyah = UserSettingsStorage.getLastPlaybackAyahNumber(),
        )
    }

    /** Values of [OnboardingGoal]. */
    private val _onboardingGoals = MutableStateFlow<Set<String>>(
        prefs.getString(KEY_ONBOARDING_GOALS, null)
            ?.split(',')
            ?.filter { it.isNotBlank() }
            ?.toSet()
            ?: emptySet(),
    )
    val onboardingGoals: StateFlow<Set<String>> = _onboardingGoals.asStateFlow()

    fun setOnboardingGoals(value: Set<String>) {
        prefs.edit().putString(KEY_ONBOARDING_GOALS, value.joinToString(",")).apply()
        _onboardingGoals.value = value
    }

    /**
     * Onboarding v2 (task C1) bookkeeping. These are written straight to the preferences file and read back through
     * [Companion.readOnboarding], because the splash screen and `MainActivity` decide whether the onboarding is next before the
     * Compose app (and so this class) is there.
     */
    fun setOnboardingCompleted() {
        prefs.edit().putBoolean(KEY_ONBOARDING_COMPLETED, true).apply()
    }

    /** The onboarding v2 was shown at least once: from now on this is not an existing user who only updated the app. */
    fun markOnboardingStarted() {
        if (!prefs.getBoolean(KEY_ONBOARDING_V2_STARTED, false)) prefs.edit().putBoolean(KEY_ONBOARDING_V2_STARTED, true).apply()
    }

    /** The one-time "What's new: the lesson of the day" sheet for people who updated from an older version was shown. */
    fun setWhatsNewShown() {
        prefs.edit().putBoolean(KEY_WHATS_NEW_SHOWN, true).apply()
    }

    private val _hifzHintMode = MutableStateFlow(
        prefs.getString(KEY_HIFZ_HINT_MODE, null)
            ?.let { name -> HifzHintMode.entries.firstOrNull { it.name == name } }
            ?: HifzHintMode.NONE,
    )
    val hifzHintMode: StateFlow<HifzHintMode> = _hifzHintMode.asStateFlow()

    fun setHifzHintMode(value: HifzHintMode) {
        prefs.edit().putString(KEY_HIFZ_HINT_MODE, value.name).apply()
        _hifzHintMode.value = value
    }

    /** "Listen" phase of the lesson: how many times every ayah of the new passage sounds (1..5, default 3). */
    private val _lessonAyahRepeats = MutableStateFlow(
        prefs.getInt(KEY_LESSON_AYAH_REPEATS, DEFAULT_LESSON_AYAH_REPEATS).coerceIn(LESSON_AYAH_REPEATS_RANGE),
    )
    val lessonAyahRepeats: StateFlow<Int> = _lessonAyahRepeats.asStateFlow()

    fun setLessonAyahRepeats(value: Int) {
        val next = value.coerceIn(LESSON_AYAH_REPEATS_RANGE)
        prefs.edit().putInt(KEY_LESSON_AYAH_REPEATS, next).apply()
        _lessonAyahRepeats.value = next
    }

    /** "Listen" phase of the lesson: how many times the whole new passage sounds after that (1..4, default 2). */
    private val _lessonPassageRepeats = MutableStateFlow(
        prefs.getInt(KEY_LESSON_PASSAGE_REPEATS, DEFAULT_LESSON_PASSAGE_REPEATS).coerceIn(LESSON_PASSAGE_REPEATS_RANGE),
    )
    val lessonPassageRepeats: StateFlow<Int> = _lessonPassageRepeats.asStateFlow()

    fun setLessonPassageRepeats(value: Int) {
        val next = value.coerceIn(LESSON_PASSAGE_REPEATS_RANGE)
        prefs.edit().putInt(KEY_LESSON_PASSAGE_REPEATS, next).apply()
        _lessonPassageRepeats.value = next
    }

    /**
     * "Listen" phase of the lesson, "I repeat after the reciter" (task F1b): every reading of the phase is followed by the echo
     * silence. Off by default.
     */
    private val _lessonEcho = MutableStateFlow(prefs.getBoolean(KEY_LESSON_ECHO, false))
    val lessonEcho: StateFlow<Boolean> = _lessonEcho.asStateFlow()

    fun setLessonEcho(value: Boolean) {
        prefs.edit().putBoolean(KEY_LESSON_ECHO, value).apply()
        _lessonEcho.value = value
    }

    /** "Listen to the old" (task F1b): the way the person last listened, with the echo silences or once. */
    private val _listenEcho = MutableStateFlow(prefs.getBoolean(KEY_LISTEN_ECHO, false))
    val listenEcho: StateFlow<Boolean> = _listenEcho.asStateFlow()

    fun setListenEcho(value: Boolean) {
        prefs.edit().putBoolean(KEY_LISTEN_ECHO, value).apply()
        _listenEcho.value = value
    }

    /** The reciter of the ayah audio. An unknown or missing saved id means the default reciter (Alafasy). */
    private val _reciter = MutableStateFlow(Reciters.byId(prefs.getString(KEY_RECITER, null)))
    val reciter: StateFlow<Reciter> = _reciter.asStateFlow()

    fun setReciter(value: Reciter) {
        prefs.edit().putString(KEY_RECITER, value.id).apply()
        _reciter.value = value
    }

    /**
     * "Anonymous statistics and crash reports" (Firebase Analytics and Crashlytics). On by default; the
     * choice is disclosed in onboarding and can be changed in the settings. The telemetry reads the stored
     * value at app start ([readAnalyticsEnabled]).
     */
    private val _analyticsEnabled = MutableStateFlow(prefs.getBoolean(KEY_ANALYTICS_ENABLED, true))
    val analyticsEnabled: StateFlow<Boolean> = _analyticsEnabled.asStateFlow()

    /** Stores the choice and applies it at once: off stops all collection and clears the SDK's local data. */
    fun setAnalyticsEnabled(value: Boolean) {
        prefs.edit().putBoolean(KEY_ANALYTICS_ENABLED, value).apply()
        _analyticsEnabled.value = value
        AppTelemetry.setEnabled(value)
    }

    /**
     * When the person last saved or sent a backup file ("Transfer and backup" in the settings), epoch milliseconds; null while
     * they never did.
     */
    private val _lastExportAt = MutableStateFlow(prefs.getLong(KEY_LAST_EXPORT_AT, 0L).takeIf { it > 0L })
    val lastExportAt: StateFlow<Long?> = _lastExportAt.asStateFlow()

    fun setLastExportAt(value: Long) {
        prefs.edit().putLong(KEY_LAST_EXPORT_AT, value).apply()
        _lastExportAt.value = value
    }

    /**
     * The daily lesson reminder (task B4). Off by default: the system permission is asked at the moment the person turns it on.
     * The alarm itself is planned by `common/reminder/ReminderSync` from these two values, which it reads straight from the
     * preferences file (see [Companion.readReminder]) because it also runs in receivers.
     */
    private val _reminderEnabled = MutableStateFlow(prefs.getBoolean(KEY_REMINDER_ENABLED, false))
    val reminderEnabled: StateFlow<Boolean> = _reminderEnabled.asStateFlow()

    fun setReminderEnabled(value: Boolean) {
        prefs.edit().putBoolean(KEY_REMINDER_ENABLED, value).apply()
        _reminderEnabled.value = value
    }

    /** When the reminder comes: minutes after midnight (20:00 is 1200), in the time zone of the phone. */
    private val _reminderMinuteOfDay = MutableStateFlow(
        prefs.getInt(KEY_REMINDER_MINUTE, DEFAULT_REMINDER_MINUTE_OF_DAY).coerceIn(REMINDER_MINUTE_RANGE),
    )
    val reminderMinuteOfDay: StateFlow<Int> = _reminderMinuteOfDay.asStateFlow()

    fun setReminderMinuteOfDay(value: Int) {
        val next = value.coerceIn(REMINDER_MINUTE_RANGE)
        prefs.edit().putInt(KEY_REMINDER_MINUTE, next).apply()
        _reminderMinuteOfDay.value = next
    }

    internal companion object {
        /**
         * The stored reminder settings for code that runs without the Compose app (the boot and alarm receivers). Always the
         * latest persisted values, whichever [UiPrefs] instance wrote them.
         */
        fun readReminder(context: Context): ReminderPrefs {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            return ReminderPrefs(
                enabled = prefs.getBoolean(KEY_REMINDER_ENABLED, false),
                minuteOfDay = prefs.getInt(KEY_REMINDER_MINUTE, DEFAULT_REMINDER_MINUTE_OF_DAY).coerceIn(REMINDER_MINUTE_RANGE),
            )
        }

        /** The onboarding v2 flags as stored, for code that runs before the Compose app (splash, `MainActivity`). */
        fun readOnboarding(context: Context): OnboardingFlags {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            return OnboardingFlags(
                completed = prefs.getBoolean(KEY_ONBOARDING_COMPLETED, false),
                v2Started = prefs.getBoolean(KEY_ONBOARDING_V2_STARTED, false),
                whatsNewShown = prefs.getBoolean(KEY_WHATS_NEW_SHOWN, false),
            )
        }

        /** Marks the onboarding as passed without [UiPrefs]: an existing user is recognised at the first start of this version. */
        fun writeOnboardingCompleted(context: Context) {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().putBoolean(KEY_ONBOARDING_COMPLETED, true).apply()
        }

        /** The stored analytics choice for code that runs before Koin starts (telemetry in Application.onCreate). */
        fun readAnalyticsEnabled(context: Context): Boolean =
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getBoolean(KEY_ANALYTICS_ENABLED, true)

        const val PREFS_NAME = "quran_hifz_ui_prefs"
        const val KEY_THEME_FOLLOWS_SYSTEM = "theme_follows_system"
        const val KEY_HAPTICS = "haptics_enabled"
        const val KEY_REDUCE_MOTION = "reduce_motion"
        const val KEY_READER_LAYOUT = "reader_layout"
        const val KEY_KEEP_SCREEN_ON = "keep_screen_on"
        const val KEY_HIGHLIGHT_WORDS = "highlight_words"
        const val KEY_TAJWEED = "tajweed"
        const val KEY_WORD_MEANINGS = "word_meanings"
        const val KEY_TAFSIR_SOURCE = "tafsir_source"
        const val KEY_RECENT_SURAHS = "recent_surahs"
        const val KEY_PINNED_SURAHS = "pinned_surahs"
        const val KEY_LAST_READ_SURAH = "last_read_surah"
        const val KEY_LAST_READ_AYAH = "last_read_ayah_in_surah"
        const val KEY_ONBOARDING_GOALS = "onboarding_goals"
        const val KEY_ONBOARDING_COMPLETED = "onboarding_completed"
        const val KEY_ONBOARDING_V2_STARTED = "onboarding_v2_started"
        const val KEY_WHATS_NEW_SHOWN = "whats_new_shown"
        const val KEY_HIFZ_HINT_MODE = "hifz_hint_mode"
        const val KEY_ANALYTICS_ENABLED = "analytics_enabled"
        const val KEY_RECITER = "reciter"
        const val KEY_LESSON_AYAH_REPEATS = "lesson_ayah_repeats"
        const val KEY_LESSON_PASSAGE_REPEATS = "lesson_passage_repeats"
        const val KEY_LESSON_ECHO = "lesson_echo"
        const val KEY_LISTEN_ECHO = "listen_echo"
        const val KEY_LAST_EXPORT_AT = "last_export_at"
        const val KEY_REMINDER_ENABLED = "reminder_enabled"
        const val KEY_REMINDER_MINUTE = "reminder_minute_of_day"
    }
}

/**
 * The onboarding v2 flags. [completed]: the person went through the onboarding (or was recognised as an existing user).
 * [v2Started]: the onboarding v2 screen was shown at least once on this install. [whatsNewShown]: the "What's new" sheet was shown.
 */
data class OnboardingFlags(val completed: Boolean, val v2Started: Boolean, val whatsNewShown: Boolean)

/** The reminder settings as stored: on or off, and the time as minutes after midnight. */
data class ReminderPrefs(val enabled: Boolean, val minuteOfDay: Int)

/** Allowed values of [UiPrefs.reminderMinuteOfDay] and its default, 20:00. */
val REMINDER_MINUTE_RANGE = 0..1439
const val DEFAULT_REMINDER_MINUTE_OF_DAY = 20 * 60

/** Allowed values of [UiPrefs.lessonAyahRepeats] and [UiPrefs.lessonPassageRepeats], and their defaults. */
val LESSON_AYAH_REPEATS_RANGE = 1..5
val LESSON_PASSAGE_REPEATS_RANGE = 1..4
const val DEFAULT_LESSON_AYAH_REPEATS = 3
const val DEFAULT_LESSON_PASSAGE_REPEATS = 2

internal const val MAX_RECENT_SURAHS = 8

/** Moves [surah] to the front, drops duplicates and keeps at most [limit] entries. */
internal fun List<Int>.withRecent(surah: Int, limit: Int = MAX_RECENT_SURAHS): List<Int> =
    (listOf(surah) + filterNot { it == surah }).take(limit)

/** Adds [surah] (appended, so pins keep the order they were made in) or removes it when present. */
internal fun Set<Int>.toggled(surah: Int): Set<Int> =
    if (surah in this) this - surah else LinkedHashSet(this).apply { add(surah) }

private fun encodeInts(values: Collection<Int>): String = values.joinToString(",")

private fun decodeInts(raw: String?): List<Int> =
    raw?.split(',')?.mapNotNull { it.trim().toIntOrNull() } ?: emptyList()
