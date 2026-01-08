package tj.app.quran_todo.common.i18n

import androidx.compose.runtime.staticCompositionLocalOf

enum class AppLanguage(val displayName: String) {
    EN("English"),
    RU("Русский"),
    UZ("O'zbek"),
    TG("Тоҷикӣ"),
    TR("Türkçe"),
}

val AppLanguage.code: String
    get() = when (this) {
        AppLanguage.EN -> "en"
        AppLanguage.RU -> "ru"
        AppLanguage.UZ -> "uz"
        AppLanguage.TG -> "tg"
        AppLanguage.TR -> "tr"
    }

fun languageFromCode(code: String?): AppLanguage {
    val normalized = code?.lowercase() ?: return AppLanguage.EN
    return when {
        normalized.startsWith("ru") -> AppLanguage.RU
        normalized.startsWith("tg") -> AppLanguage.TG
        normalized.startsWith("uz") -> AppLanguage.UZ
        normalized.startsWith("tr") -> AppLanguage.TR
        else -> AppLanguage.EN
    }
}

fun editionForLanguage(language: AppLanguage): String = when (language) {
    AppLanguage.EN -> "en.sahih"
    AppLanguage.RU -> "ru.kuliev"
    AppLanguage.UZ -> "uz.sodik"
    AppLanguage.TG -> "tg.ayati"
    AppLanguage.TR -> "tr.yazir"
}

fun localizeRevelationPlace(value: String, strings: AppStrings): String {
    val normalized = value.lowercase()
    return when {
        normalized.contains("meccan") || normalized.contains("makka") || normalized.contains("мекка") ->
            strings.meccaLabel
        normalized.contains("medinan") || normalized.contains("madina") || normalized.contains("медина") ->
            strings.medinaLabel
        else -> value
    }
}

data class AppStrings(
    val appName: String,
    val homeTitle: String,
    val homeSubtitle: String,
    val statsTitle: String,
    val settingsTitle: String,
    val languageLabel: String,
    val themeLabel: String,
    val themeLight: String,
    val themeDark: String,
    val statsSurahs: String,
    val statsAyahs: String,
    val totalLabel: String,
    val learnedLabel: String,
    val learningLabel: String,
    val noStateLabel: String,
    val filterAll: String,
    val filterLearned: String,
    val filterLearning: String,
    val selectionLabel: String,
    val actionsLabel: String,
    val resetSelection: String,
    val focusTitle: String,
    val focusSubtitle: String,
    val startFocus: String,
    val noLearningYet: String,
    val statusLabel: String,
    val clearLabel: String,
    val surahLabel: String,
    val ayahsLabel: String,
    val revelationOrderLabel: String,
    val meccaLabel: String,
    val medinaLabel: String,
    val insightsTitle: String,
    val goalTitle: String,
    val goalSubtitle: String,
    val progressTitle: String,
    val progressSubtitle: String,
)

fun stringsFor(language: AppLanguage): AppStrings = when (language) {
    AppLanguage.EN -> AppStrings(
        appName = "Quran Todo",
        homeTitle = "Progress",
        homeSubtitle = "Build daily momentum with small steps",
        statsTitle = "Statistics",
        settingsTitle = "Settings",
        languageLabel = "Language",
        themeLabel = "Theme",
        themeLight = "Light",
        themeDark = "Dark",
        statsSurahs = "Surahs",
        statsAyahs = "Ayahs",
        totalLabel = "Total",
        learnedLabel = "Learned",
        learningLabel = "Learning",
        noStateLabel = "No status",
        filterAll = "All",
        filterLearned = "Learned",
        filterLearning = "Learning",
        selectionLabel = "Selected",
        actionsLabel = "Actions",
        resetSelection = "Clear selection",
        focusTitle = "Focus session",
        focusSubtitle = "Continue where you left off",
        startFocus = "Start session",
        noLearningYet = "No learning surahs yet",
        statusLabel = "Status",
        clearLabel = "Clear",
        surahLabel = "Surah",
        ayahsLabel = "Ayahs",
        revelationOrderLabel = "Revelation order",
        meccaLabel = "Mecca",
        medinaLabel = "Medina",
        insightsTitle = "Insights",
        goalTitle = "Daily goal",
        goalSubtitle = "Suggested ayahs per day",
        progressTitle = "Overall progress",
        progressSubtitle = "Based on learned ayahs",
    )
    AppLanguage.RU -> AppStrings(
        appName = "Quran Todo",
        homeTitle = "Прогресс",
        homeSubtitle = "Небольшие шаги каждый день",
        statsTitle = "Статистика",
        settingsTitle = "Настройки",
        languageLabel = "Язык",
        themeLabel = "Тема",
        themeLight = "Светлая",
        themeDark = "Темная",
        statsSurahs = "Суры",
        statsAyahs = "Аяты",
        totalLabel = "Всего",
        learnedLabel = "Выучил",
        learningLabel = "Заучиваю",
        noStateLabel = "Без статуса",
        filterAll = "Все",
        filterLearned = "Выучил",
        filterLearning = "Заучиваю",
        selectionLabel = "Выбрано",
        actionsLabel = "Действия",
        resetSelection = "Сбросить выбранные",
        focusTitle = "Фокус-сессия",
        focusSubtitle = "Продолжить с места остановки",
        startFocus = "Начать",
        noLearningYet = "Пока нет сур в изучении",
        statusLabel = "Статус",
        clearLabel = "Сбросить",
        surahLabel = "Сура",
        ayahsLabel = "Аятов",
        revelationOrderLabel = "Ниспослано",
        meccaLabel = "Мекка",
        medinaLabel = "Медина",
        insightsTitle = "Инсайты",
        goalTitle = "Дневная цель",
        goalSubtitle = "Рекомендуемое число аятов",
        progressTitle = "Общий прогресс",
        progressSubtitle = "По выученным аятам",
    )
    AppLanguage.UZ -> AppStrings(
        appName = "Quran Todo",
        homeTitle = "Progress",
        homeSubtitle = "Har kuni kichik qadamlar",
        statsTitle = "Statistika",
        settingsTitle = "Sozlamalar",
        languageLabel = "Til",
        themeLabel = "Mavzu",
        themeLight = "Yorug'",
        themeDark = "Qorong'i",
        statsSurahs = "Suralar",
        statsAyahs = "Oyatlar",
        totalLabel = "Jami",
        learnedLabel = "Yodlangan",
        learningLabel = "Yodlayapman",
        noStateLabel = "Holatsiz",
        filterAll = "Barchasi",
        filterLearned = "Yodlangan",
        filterLearning = "Yodlayapman",
        selectionLabel = "Tanlangan",
        actionsLabel = "Amallar",
        resetSelection = "Tanlovni tozalash",
        focusTitle = "Fokus sessiya",
        focusSubtitle = "Toxtagan joydan davom etish",
        startFocus = "Boshlash",
        noLearningYet = "Hozircha organilayotgan sura yoq",
        statusLabel = "Holat",
        clearLabel = "Tozalash",
        surahLabel = "Sura",
        ayahsLabel = "Oyatlar",
        revelationOrderLabel = "Nozil bolish tartibi",
        meccaLabel = "Makka",
        medinaLabel = "Madina",
        insightsTitle = "Insights",
        goalTitle = "Kundalik maqsad",
        goalSubtitle = "Kunlik tavsiya etilgan oyatlar",
        progressTitle = "Umumiy progress",
        progressSubtitle = "Yodlangan oyatlar boyicha",
    )
    AppLanguage.TG -> AppStrings(
        appName = "Quran Todo",
        homeTitle = "Пешрафт",
        homeSubtitle = "Қадамҳои хурд ҳар рӯз",
        statsTitle = "Омор",
        settingsTitle = "Танзимот",
        languageLabel = "Забон",
        themeLabel = "Мавзӯъ",
        themeLight = "Рӯшан",
        themeDark = "Торик",
        statsSurahs = "Сураҳо",
        statsAyahs = "Оятҳо",
        totalLabel = "Ҳамагӣ",
        learnedLabel = "Ёд карда",
        learningLabel = "Ёд мекунам",
        noStateLabel = "Бе ҳолат",
        filterAll = "Ҳама",
        filterLearned = "Ёд карда",
        filterLearning = "Ёд мекунам",
        selectionLabel = "Интихоб",
        actionsLabel = "Амалҳо",
        resetSelection = "Тоза кардан",
        focusTitle = "Сессияи фокус",
        focusSubtitle = "Аз ҷойи пешина идома диҳед",
        startFocus = "Оғоз",
        noLearningYet = "Ҳоло сураи дар омӯзиш нест",
        statusLabel = "Ҳолат",
        clearLabel = "Тоза кардан",
        surahLabel = "Сура",
        ayahsLabel = "Оятҳо",
        revelationOrderLabel = "Нузул",
        meccaLabel = "Макка",
        medinaLabel = "Мадина",
        insightsTitle = "Назарҳо",
        goalTitle = "Ҳадафи рӯзона",
        goalSubtitle = "Оятҳои тавсияшуда дар рӯз",
        progressTitle = "Пешрафти умумӣ",
        progressSubtitle = "Аз рӯи оятҳои ёдшуда",
    )
    AppLanguage.TR -> AppStrings(
        appName = "Quran Todo",
        homeTitle = "Ilerleme",
        homeSubtitle = "Her gun kucuk adimlar",
        statsTitle = "Istatistik",
        settingsTitle = "Ayarlar",
        languageLabel = "Dil",
        themeLabel = "Tema",
        themeLight = "Açık",
        themeDark = "Koyu",
        statsSurahs = "Sureler",
        statsAyahs = "Ayetler",
        totalLabel = "Toplam",
        learnedLabel = "Ezberlendi",
        learningLabel = "Ezberleniyor",
        noStateLabel = "Durum yok",
        filterAll = "Hepsi",
        filterLearned = "Ezberlendi",
        filterLearning = "Ezberleniyor",
        selectionLabel = "Secili",
        actionsLabel = "Islemler",
        resetSelection = "Secimi temizle",
        focusTitle = "Odak oturumu",
        focusSubtitle = "Kaldigin yerden devam et",
        startFocus = "Baslat",
        noLearningYet = "Henuz ezberlenen sure yok",
        statusLabel = "Durum",
        clearLabel = "Temizle",
        surahLabel = "Sure",
        ayahsLabel = "Ayetler",
        revelationOrderLabel = "Nuzul sirasi",
        meccaLabel = "Mekke",
        medinaLabel = "Medine",
        insightsTitle = "Ongoruler",
        goalTitle = "Gunluk hedef",
        goalSubtitle = "Gunluk onerilen ayet sayisi",
        progressTitle = "Genel ilerleme",
        progressSubtitle = "Ezberlenen ayetlere gore",
    )
}

val LocalAppStrings = staticCompositionLocalOf { stringsFor(AppLanguage.EN) }
val LocalAppLanguage = staticCompositionLocalOf { AppLanguage.EN }
val LocalAppLanguageSetter = staticCompositionLocalOf<(AppLanguage) -> Unit> { {} }
