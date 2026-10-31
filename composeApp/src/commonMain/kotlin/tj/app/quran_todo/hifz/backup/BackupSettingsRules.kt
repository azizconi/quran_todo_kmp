package tj.app.quran_todo.hifz.backup

import tj.app.quran_todo.common.audio.Reciters
import tj.app.quran_todo.common.i18n.AppLanguage
import tj.app.quran_todo.common.i18n.code
import tj.app.quran_todo.common.theme.ReadingFontStyle
import tj.app.quran_todo.common.theme.ThemeMode
import tj.app.quran_todo.common.theme.ThemePalette

/**
 * What a value of [BackupSettings] may be. The settings screens clamp their values to the same ranges; a value outside them
 * (a hand-edited file, a newer app) is dropped, so the current setting of the phone stays.
 */
object BackupSettingsRules {
    val LANGUAGES: Set<String> = AppLanguage.entries.mapTo(LinkedHashSet()) { it.code }
    val THEME_MODES: Set<String> = ThemeMode.entries.mapTo(LinkedHashSet()) { it.name }
    val PALETTES: Set<String> = ThemePalette.entries.mapTo(LinkedHashSet()) { it.name }
    val READING_FONTS: Set<String> = ReadingFontStyle.entries.mapTo(LinkedHashSet()) { it.storageValue }
    val RECITERS: Set<String> = Reciters.all.mapTo(LinkedHashSet()) { it.id }

    /** The names of `ui.state.ReaderLayout` (android) and `HifzHintMode`; the unit tests keep them equal to the enums. */
    val READER_LAYOUTS: Set<String> = setOf("LIST", "MUSHAF")
    val HINT_MODES: Set<String> = setOf("NONE", "FIRST_WORD", "FIRST_LETTERS", "EVERY_OTHER", "PROGRESSIVE")

    val READING_FONT_SIZE = 22..40
    val DAILY_GOAL = 1..50
    val PLAYBACK_SPEEDS: Set<Float> = setOf(0.75f, 1f, 1.25f, 1.5f, 2f)
    val REPEAT_COUNTS: Set<Int> = setOf(1, 3, 5, -1)
    val LESSON_REPEATS = 1..20

    /** Minutes after midnight. */
    val REMINDER_MINUTE_OF_DAY = 0..1439
}

/** Returns the settings with every value that is not valid removed (null = "leave the phone's setting as it is"). */
fun BackupSettings.sanitized(): BackupSettings = BackupSettings(
    language = language?.takeIf { it in BackupSettingsRules.LANGUAGES },
    themeMode = themeMode?.takeIf { it in BackupSettingsRules.THEME_MODES },
    themeFollowsSystem = themeFollowsSystem,
    palette = palette?.takeIf { it in BackupSettingsRules.PALETTES },
    readingFont = readingFont?.takeIf { it in BackupSettingsRules.READING_FONTS },
    readingFontSize = readingFontSize?.takeIf { it in BackupSettingsRules.READING_FONT_SIZE },
    dailyGoal = dailyGoal?.takeIf { it in BackupSettingsRules.DAILY_GOAL },
    showTranslation = showTranslation,
    highlightPlayingAyah = highlightPlayingAyah,
    autoplayOnOpen = autoplayOnOpen,
    autoAdvanceAyahs = autoAdvanceAyahs,
    playbackSpeed = playbackSpeed?.takeIf { it in BackupSettingsRules.PLAYBACK_SPEEDS },
    repeatCount = repeatCount?.takeIf { it in BackupSettingsRules.REPEAT_COUNTS },
    readerLayout = readerLayout?.takeIf { it in BackupSettingsRules.READER_LAYOUTS },
    keepScreenOn = keepScreenOn,
    reciter = reciter?.takeIf { it in BackupSettingsRules.RECITERS },
    lessonAyahRepeats = lessonAyahRepeats?.takeIf { it in BackupSettingsRules.LESSON_REPEATS },
    lessonPassageRepeats = lessonPassageRepeats?.takeIf { it in BackupSettingsRules.LESSON_REPEATS },
    hifzHint = hifzHint?.takeIf { it in BackupSettingsRules.HINT_MODES },
    hapticsEnabled = hapticsEnabled,
    reduceMotion = reduceMotion,
    reminderEnabled = reminderEnabled,
    reminderMinuteOfDay = reminderMinuteOfDay?.takeIf { it in BackupSettingsRules.REMINDER_MINUTE_OF_DAY },
)
