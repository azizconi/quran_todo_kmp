package tj.app.quran_todo.presentation.surah

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.mp.KoinPlatformTools
import tj.app.quran_todo.common.audio.AudioCache
import tj.app.quran_todo.common.audio.AudioPlayer
import tj.app.quran_todo.common.audio.ayahAudioCacheKey
import tj.app.quran_todo.common.i18n.AppLanguage
import tj.app.quran_todo.common.i18n.LanguageStorage
import tj.app.quran_todo.common.settings.SurahReaderPreferences
import tj.app.quran_todo.common.settings.UserSettingsStorage
import tj.app.quran_todo.common.theme.ThemeMode
import tj.app.quran_todo.common.theme.ThemePalette
import tj.app.quran_todo.common.theme.ThemeStorage
import tj.app.quran_todo.common.theme.nativeReaderThemeColors
import tj.app.quran_todo.common.utils.Resource
import tj.app.quran_todo.data.database.entity.todo.SurahTodoStatus
import tj.app.quran_todo.domain.use_case.GetAyahTranslationsUseCase
import tj.app.quran_todo.domain.use_case.GetCompleteQuranUseCase
import tj.app.quran_todo.domain.use_case.TodoGetSurahListUseCase
import androidx.compose.ui.graphics.Color

/**
 * A deliberately small bridge between the shared KMP data layer and the native
 * SwiftUI reader. UI state stays in SwiftUI; Quran data, statuses, preferences
 * and offline audio caching stay shared with Android.
 */
class NativeSurahReaderBridge internal constructor(
    private val getCompleteQuran: GetCompleteQuranUseCase,
    private val getSurahTodos: TodoGetSurahListUseCase,
    private val getTranslations: GetAyahTranslationsUseCase,
    private val surahViewModel: SurahViewModel,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val audioCache = AudioCache()
    private val audioPlayer = AudioPlayer()
    private var audioPlaybackJob: Job? = null
    private var audioPlaybackRequestId = 0L
    private var isAudioPreparing = false
    private var activePlaybackSpeed = 1f

    fun load(
        surahNumber: Int,
        isSystemDark: Boolean,
        onLoaded: (NativeSurahReaderSnapshot) -> Unit,
        onError: (String) -> Unit,
    ) {
        val language = LanguageStorage.getSavedLanguage() ?: LanguageStorage.getDeviceLanguage()
        val readerCopy = nativeReaderCopy(language)
        scope.launch {
            runCatching {
                val result = getCompleteQuran(withLocalAction = true)
                    .first { it is Resource.Success }
                @Suppress("UNCHECKED_CAST")
                val surahs = (result as Resource.Success<List<tj.app.quran_todo.data.database.entity.quran.SurahWithAyahs>>).data
                val surah = surahs.firstOrNull { it.surah.number == surahNumber }
                    ?: error(readerCopy.loadErrorTitle)
                val preferences = SurahReaderPreferences.load()
                val ayahs = surah.ayahs.sortedBy { it.numberInSurah }
                val surahStatus = getSurahTodos()
                    .first()
                    .firstOrNull { it.surahNumber == surahNumber }
                    ?.status
                val initialAyahGlobalNumber = UserSettingsStorage
                    .takeIf { it.getLastPlaybackSurah() == surahNumber }
                    ?.getLastPlaybackAyahNumber()
                    ?: ayahs.firstOrNull()?.number
                    ?: 0
                val readerTheme = nativeReaderTheme(isSystemDark)
                val translations = if (preferences.showTranslation) {
                    getTranslations(surahNumber, language, ayahs.size)
                } else {
                    emptyMap()
                }
                NativeSurahReaderSnapshot(
                    number = surah.surah.number,
                    arabicName = surah.surah.name,
                    title = if (language == AppLanguage.EN) {
                        surah.surah.englishName
                    } else {
                        surah.surah.name
                    },
                    subtitle = if (language == AppLanguage.EN) {
                        surah.surah.englishNameTranslation
                    } else {
                        ""
                    },
                    revelationType = localizedReaderRevelationType(
                        value = surah.surah.revelationType,
                        copy = readerCopy,
                    ),
                    surahStatus = surahStatus?.name?.lowercase().orEmpty(),
                    initialAyahGlobalNumber = initialAyahGlobalNumber,
                    preferences = preferences.toSnapshot(),
                    strings = readerCopy,
                    theme = readerTheme,
                    readingFontSize = UserSettingsStorage.getReadingFontSize()
                        ?.coerceIn(24, 34)
                        ?: 28,
                    readingFontStyle = (ThemeStorage.getSavedReadingFontStyle()
                        ?: tj.app.quran_todo.common.theme.ReadingFontStyle.MADINAH_MUSHAF).storageValue,
                    ayahs = ayahs.map { ayah ->
                        NativeSurahReaderAyah(
                            globalNumber = ayah.number,
                            numberInSurah = ayah.numberInSurah,
                            arabicText = ayah.text,
                            translation = translations[ayah.number].orEmpty(),
                        )
                    },
                )
            }.onSuccess { snapshot ->
                withContext(Dispatchers.Main) { onLoaded(snapshot) }
            }.onFailure { throwable ->
                withContext(Dispatchers.Main) {
                    onError(throwable.message ?: readerCopy.loadErrorTitle)
                }
            }
        }
    }

    fun setSurahStatus(
        surahNumber: Int,
        status: String,
        onCompleted: () -> Unit,
        onError: (String) -> Unit,
    ) {
        val normalizedStatus = when (status.lowercase()) {
            "learned" -> SurahTodoStatus.LEARNED
            "learning" -> SurahTodoStatus.LEARNING
            else -> null
        }
        surahViewModel.setSurahStatus(
            surahNumber = surahNumber,
            status = normalizedStatus,
            onCompleted = { scope.launch(Dispatchers.Main) { onCompleted() } },
            onError = { message -> scope.launch(Dispatchers.Main) { onError(message) } },
        )
    }

    fun savePreferences(
        showTranslation: Boolean,
        autoplayOnOpen: Boolean,
        autoAdvanceAyahs: Boolean,
        playbackSpeed: Float,
        repeatCount: Int,
        highlightPlayingAyah: Boolean,
    ) {
        SurahReaderPreferences(
            showTranslation = showTranslation,
            autoplayOnOpen = autoplayOnOpen,
            autoAdvanceAyahs = autoAdvanceAyahs,
            playbackSpeed = playbackSpeed,
            repeatCount = repeatCount,
            highlightPlayingAyah = highlightPlayingAyah,
        ).save()
    }

    fun saveCurrentAyah(surahNumber: Int, ayahGlobalNumber: Int) {
        UserSettingsStorage.saveLastPlaybackPosition(surahNumber, ayahGlobalNumber)
    }

    fun playAyah(
        surahNumber: Int,
        ayahGlobalNumber: Int,
        ayahNumberInSurah: Int,
        playbackSpeed: Float,
        onStarted: () -> Unit,
        onCompleted: () -> Unit,
    ) {
        audioPlaybackJob?.cancel()
        audioPlayer.stop()
        val requestId = ++audioPlaybackRequestId
        activePlaybackSpeed = playbackSpeed
        isAudioPreparing = true
        audioPlaybackJob = scope.launch {
            val remoteUrl = "https://cdn.islamic.network/quran/audio/128/ar.alafasy/$ayahGlobalNumber.mp3"
            val cacheKey = ayahAudioCacheKey(surahNumber, ayahNumberInSurah)
            val source = audioCache.getOrFetch(remoteUrl, cacheKey) ?: remoteUrl
            withContext(Dispatchers.Main) {
                if (requestId != audioPlaybackRequestId) return@withContext
                isAudioPreparing = false
                audioPlayer.setPlaybackSpeed(activePlaybackSpeed)
                audioPlayer.play(source) {
                    scope.launch(Dispatchers.Main) {
                        if (requestId == audioPlaybackRequestId) {
                            onCompleted()
                        }
                    }
                }
                onStarted()
            }
        }
    }

    fun pauseAudio() {
        if (isAudioPreparing) {
            cancelPendingAudioPlayback()
        } else {
            audioPlayer.pause()
        }
    }

    fun resumeAudio(playbackSpeed: Float) {
        activePlaybackSpeed = playbackSpeed
        audioPlayer.setPlaybackSpeed(activePlaybackSpeed)
        audioPlayer.resume()
    }

    fun setAudioPlaybackSpeed(playbackSpeed: Float) {
        activePlaybackSpeed = playbackSpeed
        audioPlayer.setPlaybackSpeed(activePlaybackSpeed)
    }

    fun stopAudio() = cancelPendingAudioPlayback()

    fun audioPositionMs(): Long = audioPlayer.getPositionMs()

    fun audioDurationMs(): Long = audioPlayer.getDurationMs()

    fun close() {
        cancelPendingAudioPlayback()
        scope.coroutineContext.cancel()
    }

    private fun cancelPendingAudioPlayback() {
        audioPlaybackRequestId += 1
        isAudioPreparing = false
        audioPlaybackJob?.cancel()
        audioPlaybackJob = null
        audioPlayer.stop()
    }

    private fun SurahReaderPreferences.toSnapshot() = NativeSurahReaderPreferencesSnapshot(
        showTranslation = showTranslation,
        autoplayOnOpen = autoplayOnOpen,
        autoAdvanceAyahs = autoAdvanceAyahs,
        playbackSpeed = playbackSpeed,
        repeatCount = repeatCount,
        highlightPlayingAyah = highlightPlayingAyah,
    )
}

private fun nativeReaderTheme(isSystemDark: Boolean): NativeSurahReaderTheme {
    val mode = ThemeStorage.getSavedThemeMode() ?: if (isSystemDark) {
        ThemeMode.DARK
    } else {
        ThemeMode.LIGHT
    }
    val palette = ThemeStorage.getSavedThemePalette() ?: ThemePalette.FOREST
    val colors = nativeReaderThemeColors(mode, palette)
    return NativeSurahReaderTheme(
        isDark = mode == ThemeMode.DARK,
        backgroundArgb = colors.readingBackground.toArgbLong(),
        surfaceArgb = colors.readingSurface.toArgbLong(),
        surfaceAltArgb = colors.readingMutedSurface.toArgbLong(),
        borderArgb = colors.border.toArgbLong(),
        primaryArgb = colors.primary.toArgbLong(),
        primaryWeakArgb = colors.primaryWeak.toArgbLong(),
        onPrimaryArgb = if (mode == ThemeMode.DARK) {
            Color(0xFF101513).toArgbLong()
        } else {
            Color.White.toArgbLong()
        },
        onSurfaceArgb = colors.onSurface.toArgbLong(),
        textSecondaryArgb = colors.textSecondary.toArgbLong(),
        successArgb = colors.success.toArgbLong(),
        infoArgb = colors.info.toArgbLong(),
    )
}

private fun Color.toArgbLong(): Long {
    fun channel(value: Float): Long = (value.coerceIn(0f, 1f) * 255f + 0.5f).toLong()
    return (channel(alpha) shl 24) or
        (channel(red) shl 16) or
        (channel(green) shl 8) or
        channel(blue)
}

private fun localizedReaderRevelationType(
    value: String,
    copy: NativeSurahReaderCopy,
): String = when {
    value.contains("meccan", ignoreCase = true) || value.contains("mecca", ignoreCase = true) -> copy.mecca
    value.contains("medinan", ignoreCase = true) || value.contains("medina", ignoreCase = true) -> copy.medina
    else -> value
}

private fun nativeReaderCopy(language: AppLanguage): NativeSurahReaderCopy = when (language) {
    AppLanguage.EN -> NativeSurahReaderCopy(
        loadingSurah = "Loading surah…",
        loadErrorTitle = "Unable to open surah",
        settings = "Settings",
        settingsAccessibilityLabel = "Reading and audio settings",
        ayahsLabel = "ayahs",
        mecca = "Mecca",
        medina = "Medina",
        statusSurah = "Surah status",
        notStarted = "Not started",
        learning = "Learning",
        learned = "Learned",
        statusUpdateErrorTitle = "Status wasn't changed",
        confirmationLabel = "OK",
        tryAgain = "Try again.",
        statusHint = "Opens status options",
        selectAyahHint = "Double tap to make this the current ayah",
        reciter = "Alafasy",
        ayahLabel = "Ayah",
        ofLabel = "of",
        readingSection = "Reading",
        showTranslation = "Show translation",
        highlightPlayingAyah = "Highlight playing ayah",
        audioSection = "Audio",
        autoplayOnOpen = "Autoplay when opening",
        autoAdvanceAyahs = "Go to the next ayah automatically",
        speed = "Speed",
        repeatAyah = "Repeat ayah",
        repeatOnce = "Once",
        repeatThreeTimes = "3 times",
        repeatFiveTimes = "5 times",
        repeatForever = "Forever",
        readingAndAudioTitle = "Reading and audio",
        done = "Done",
    )

    AppLanguage.RU -> NativeSurahReaderCopy(
        loadingSurah = "Загружаем суру…",
        loadErrorTitle = "Не удалось открыть суру",
        settings = "Настройки",
        settingsAccessibilityLabel = "Настройки чтения и аудио",
        ayahsLabel = "аятов",
        mecca = "Мекканская",
        medina = "Мединская",
        statusSurah = "Статус суры",
        notStarted = "Не начал",
        learning = "Заучиваю",
        learned = "Выучил",
        statusUpdateErrorTitle = "Статус не изменён",
        confirmationLabel = "Хорошо",
        tryAgain = "Попробуйте ещё раз.",
        statusHint = "Открывает выбор статуса",
        selectAyahHint = "Дважды нажмите, чтобы сделать этот аят текущим",
        reciter = "Аль-Афаси",
        ayahLabel = "аят",
        ofLabel = "из",
        readingSection = "Чтение",
        showTranslation = "Показывать перевод",
        highlightPlayingAyah = "Подсвечивать проигрываемый аят",
        audioSection = "Аудио",
        autoplayOnOpen = "Автозапуск при открытии",
        autoAdvanceAyahs = "Переходить к следующему аяту",
        speed = "Скорость",
        repeatAyah = "Повтор аята",
        repeatOnce = "1 раз",
        repeatThreeTimes = "3 раза",
        repeatFiveTimes = "5 раз",
        repeatForever = "Бесконечно",
        readingAndAudioTitle = "Чтение и аудио",
        done = "Готово",
    )

    AppLanguage.UZ -> NativeSurahReaderCopy(
        loadingSurah = "Sura yuklanmoqda…",
        loadErrorTitle = "Surani ochib bo‘lmadi",
        settings = "Sozlamalar",
        settingsAccessibilityLabel = "O‘qish va audio sozlamalari",
        ayahsLabel = "oyat",
        mecca = "Makka",
        medina = "Madina",
        statusSurah = "Sura holati",
        notStarted = "Boshlanmagan",
        learning = "Yodlanmoqda",
        learned = "Yodlangan",
        statusUpdateErrorTitle = "Holat o‘zgarmadi",
        confirmationLabel = "Xo‘p",
        tryAgain = "Qayta urinib ko‘ring.",
        statusHint = "Holatlarni ochadi",
        selectAyahHint = "Bu oyatni joriy qilish uchun ikki marta bosing",
        reciter = "Alafasy",
        ayahLabel = "oyat",
        ofLabel = "/",
        readingSection = "O‘qish",
        showTranslation = "Tarjimani ko‘rsatish",
        highlightPlayingAyah = "Ijro etilayotgan oyatni ajratish",
        audioSection = "Audio",
        autoplayOnOpen = "Ochilganda avtomatik ijro",
        autoAdvanceAyahs = "Keyingi oyatga avtomatik o‘tish",
        speed = "Tezlik",
        repeatAyah = "Oyat takrori",
        repeatOnce = "1 marta",
        repeatThreeTimes = "3 marta",
        repeatFiveTimes = "5 marta",
        repeatForever = "Cheksiz",
        readingAndAudioTitle = "O‘qish va audio",
        done = "Tayyor",
    )

    AppLanguage.TG -> NativeSurahReaderCopy(
        loadingSurah = "Сура бор карда мешавад…",
        loadErrorTitle = "Кушодани сура муяссар нашуд",
        settings = "Танзимот",
        settingsAccessibilityLabel = "Танзимоти қироат ва аудио",
        ayahsLabel = "оят",
        mecca = "Макка",
        medina = "Мадина",
        statusSurah = "Ҳолати сура",
        notStarted = "Оғоз нашудааст",
        learning = "Ҳифз мешавад",
        learned = "Ҳифз шуд",
        statusUpdateErrorTitle = "Ҳолат тағйир наёфт",
        confirmationLabel = "Хуб",
        tryAgain = "Бори дигар кӯшиш кунед.",
        statusHint = "Интихоби ҳолатро мекушояд",
        selectAyahHint = "Барои интихоби ин оят ду бор пахш кунед",
        reciter = "Алафаси",
        ayahLabel = "оят",
        ofLabel = "аз",
        readingSection = "Қироат",
        showTranslation = "Намоиши тарҷума",
        highlightPlayingAyah = "Равшан кардани ояти садодиҳанда",
        audioSection = "Аудио",
        autoplayOnOpen = "Ҳангоми кушодан худкор оғоз шавад",
        autoAdvanceAyahs = "Ба ояти навбатӣ худкор гузарад",
        speed = "Суръат",
        repeatAyah = "Такрори оят",
        repeatOnce = "1 бор",
        repeatThreeTimes = "3 бор",
        repeatFiveTimes = "5 бор",
        repeatForever = "Беохир",
        readingAndAudioTitle = "Қироат ва аудио",
        done = "Омода",
    )

    AppLanguage.TR -> NativeSurahReaderCopy(
        loadingSurah = "Sure yükleniyor…",
        loadErrorTitle = "Sure açılamadı",
        settings = "Ayarlar",
        settingsAccessibilityLabel = "Okuma ve ses ayarları",
        ayahsLabel = "ayet",
        mecca = "Mekke",
        medina = "Medine",
        statusSurah = "Sure durumu",
        notStarted = "Başlanmadı",
        learning = "Ezberleniyor",
        learned = "Ezberlendi",
        statusUpdateErrorTitle = "Durum değiştirilemedi",
        confirmationLabel = "Tamam",
        tryAgain = "Tekrar deneyin.",
        statusHint = "Durum seçeneklerini açar",
        selectAyahHint = "Bu ayeti seçmek için iki kez dokunun",
        reciter = "Alafasy",
        ayahLabel = "ayet",
        ofLabel = "/",
        readingSection = "Okuma",
        showTranslation = "Meali göster",
        highlightPlayingAyah = "Çalınan ayeti vurgula",
        audioSection = "Ses",
        autoplayOnOpen = "Açılışta otomatik oynat",
        autoAdvanceAyahs = "Sonraki ayete otomatik geç",
        speed = "Hız",
        repeatAyah = "Ayeti tekrarla",
        repeatOnce = "1 kez",
        repeatThreeTimes = "3 kez",
        repeatFiveTimes = "5 kez",
        repeatForever = "Sonsuz",
        readingAndAudioTitle = "Okuma ve ses",
        done = "Bitti",
    )
}

object NativeSurahReaderBridgeFactory {
    fun create(): NativeSurahReaderBridge {
        val koin = KoinPlatformTools.defaultContext().get()
        return NativeSurahReaderBridge(
            getCompleteQuran = koin.get(),
            getSurahTodos = koin.get(),
            getTranslations = koin.get(),
            surahViewModel = koin.get(),
        )
    }
}

class NativeSurahReaderSnapshot(
    val number: Int,
    val arabicName: String,
    val title: String,
    val subtitle: String,
    val revelationType: String,
    val surahStatus: String,
    /** Zero means no previous ayah has been stored for this surah. */
    val initialAyahGlobalNumber: Int,
    val preferences: NativeSurahReaderPreferencesSnapshot,
    val strings: NativeSurahReaderCopy,
    val theme: NativeSurahReaderTheme,
    val readingFontSize: Int,
    val readingFontStyle: String,
    val ayahs: List<NativeSurahReaderAyah>,
)

class NativeSurahReaderCopy(
    val loadingSurah: String,
    val loadErrorTitle: String,
    val settings: String,
    val settingsAccessibilityLabel: String,
    val ayahsLabel: String,
    val mecca: String,
    val medina: String,
    val statusSurah: String,
    val notStarted: String,
    val learning: String,
    val learned: String,
    val statusUpdateErrorTitle: String,
    val confirmationLabel: String,
    val tryAgain: String,
    val statusHint: String,
    val selectAyahHint: String,
    val reciter: String,
    val ayahLabel: String,
    val ofLabel: String,
    val readingSection: String,
    val showTranslation: String,
    val highlightPlayingAyah: String,
    val audioSection: String,
    val autoplayOnOpen: String,
    val autoAdvanceAyahs: String,
    val speed: String,
    val repeatAyah: String,
    val repeatOnce: String,
    val repeatThreeTimes: String,
    val repeatFiveTimes: String,
    val repeatForever: String,
    val readingAndAudioTitle: String,
    val done: String,
)

class NativeSurahReaderTheme(
    val isDark: Boolean,
    val backgroundArgb: Long,
    val surfaceArgb: Long,
    val surfaceAltArgb: Long,
    val borderArgb: Long,
    val primaryArgb: Long,
    val primaryWeakArgb: Long,
    val onPrimaryArgb: Long,
    val onSurfaceArgb: Long,
    val textSecondaryArgb: Long,
    val successArgb: Long,
    val infoArgb: Long,
)

class NativeSurahReaderAyah(
    val globalNumber: Int,
    val numberInSurah: Int,
    val arabicText: String,
    val translation: String,
)

class NativeSurahReaderPreferencesSnapshot(
    val showTranslation: Boolean,
    val autoplayOnOpen: Boolean,
    val autoAdvanceAyahs: Boolean,
    val playbackSpeed: Float,
    val repeatCount: Int,
    val highlightPlayingAyah: Boolean,
)
