package tj.app.quran_todo.ui.state

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import tj.app.quran_todo.common.analytics.AppTelemetry
import tj.app.quran_todo.common.settings.UserSettingsStorage
import tj.app.quran_todo.common.theme.ThemeStorage

enum class ReaderLayout { LIST, MUSHAF }

enum class HifzHintMode { NONE, FIRST_WORD }

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

    internal companion object {
        /** The stored analytics choice for code that runs before Koin starts (telemetry in Application.onCreate). */
        fun readAnalyticsEnabled(context: Context): Boolean =
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getBoolean(KEY_ANALYTICS_ENABLED, true)

        const val PREFS_NAME = "quran_hifz_ui_prefs"
        const val KEY_THEME_FOLLOWS_SYSTEM = "theme_follows_system"
        const val KEY_HAPTICS = "haptics_enabled"
        const val KEY_REDUCE_MOTION = "reduce_motion"
        const val KEY_READER_LAYOUT = "reader_layout"
        const val KEY_KEEP_SCREEN_ON = "keep_screen_on"
        const val KEY_RECENT_SURAHS = "recent_surahs"
        const val KEY_PINNED_SURAHS = "pinned_surahs"
        const val KEY_LAST_READ_SURAH = "last_read_surah"
        const val KEY_LAST_READ_AYAH = "last_read_ayah_in_surah"
        const val KEY_ONBOARDING_GOALS = "onboarding_goals"
        const val KEY_HIFZ_HINT_MODE = "hifz_hint_mode"
        const val KEY_ANALYTICS_ENABLED = "analytics_enabled"
    }
}

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
