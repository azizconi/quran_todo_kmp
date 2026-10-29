package tj.app.quran_todo.ui.i18n

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import tj.app.quran_todo.common.i18n.AppLanguage
import tj.app.quran_todo.common.i18n.LocalAppLanguage

/**
 * Strings of the first-launch onboarding. Font names and descriptions, the theme and palette labels
 * come from [SettingsCopy] (which wraps the shared `NativeSettingsCopy`), so they are not repeated here.
 */
data class OnboardingCopy(
    val welcomeSubtitle: String,
    val chooseLanguage: String,
    val chooseLanguageHint: String,
    val next: String,
    val start: String,
    val skip: String,
    /** Template with `{n}` and `{total}`; read by screen readers on the step indicator. */
    val stepOf: String,
    val readingTitle: String,
    val readingSubtitle: String,
    val lookTitle: String,
    val lookSubtitle: String,
    val heroOverline: String,
    val heroSurah: String,
    val heroProgress: String,
    val heroRead: String,
    val heroListen: String,
    val goalsTitle: String,
    val goalsSubtitle: String,
    val goalRead: String,
    val goalListen: String,
    val goalMemorize: String,
    val dailyGoalTitle: String,
    val dailyGoalHint: String,
) {
    fun stepLabel(step: Int, total: Int): String =
        stepOf.replace("{n}", step.toString()).replace("{total}", total.toString())
}

@Composable
fun rememberOnboardingCopy(language: AppLanguage = LocalAppLanguage.current): OnboardingCopy =
    remember(language) { onboardingCopy(language) }

fun onboardingCopy(language: AppLanguage): OnboardingCopy = when (language) {
    AppLanguage.EN -> OnboardingCopy(
        welcomeSubtitle = "Read. Listen. Memorize.",
        chooseLanguage = "Choose your language",
        chooseLanguageHint = "You can change it later in Settings.",
        next = "Next", start = "Get started", skip = "Skip",
        stepOf = "Step {n} of {total}",
        readingTitle = "How do you like to read?",
        readingSubtitle = "Pick a font and a size. The text below follows you at once.",
        lookTitle = "Make it yours",
        lookSubtitle = "Light, dark or like your phone — and a color you enjoy.",
        heroOverline = "Continue", heroSurah = "Al-Baqarah", heroProgress = "Ayah 255 of 286",
        heroRead = "Read", heroListen = "Listen",
        goalsTitle = "What matters to you?",
        goalsSubtitle = "Choose any — or skip it. Everything can be changed later.",
        goalRead = "Read", goalListen = "Listen", goalMemorize = "Memorize",
        dailyGoalTitle = "Ayahs per day",
        dailyGoalHint = "A gentle pace works best. You can change it in Settings.",
    )

    AppLanguage.RU -> OnboardingCopy(
        welcomeSubtitle = "Читайте. Слушайте. Заучивайте.",
        chooseLanguage = "Выберите язык",
        chooseLanguageHint = "Позже его можно изменить в настройках.",
        next = "Далее", start = "Начать", skip = "Пропустить",
        stepOf = "Шаг {n} из {total}",
        readingTitle = "Как вам удобнее читать?",
        readingSubtitle = "Выберите шрифт и размер — текст ниже меняется сразу.",
        lookTitle = "Сделайте приложение своим",
        lookSubtitle = "Светлая, тёмная или как в системе — и цвет, который вам по душе.",
        heroOverline = "Продолжить", heroSurah = "Аль-Бакара", heroProgress = "Аят 255 из 286",
        heroRead = "Читать", heroListen = "Слушать",
        goalsTitle = "Что для вас важно?",
        goalsSubtitle = "Выберите любое — или пропустите. Всё можно изменить позже.",
        goalRead = "Читать", goalListen = "Слушать", goalMemorize = "Заучивать",
        dailyGoalTitle = "Аятов в день",
        dailyGoalHint = "Лучше начинать спокойно. Это можно изменить в настройках.",
    )

    AppLanguage.TG -> OnboardingCopy(
        welcomeSubtitle = "Хонед. Гӯш кунед. Аз бар кунед.",
        chooseLanguage = "Забонро интихоб кунед",
        chooseLanguageHint = "Баъдтар онро дар танзимот иваз кардан мумкин аст.",
        next = "Идома", start = "Оғоз", skip = "Гузаштан",
        stepOf = "Қадами {n} аз {total}",
        readingTitle = "Чӣ тавр хондан қулай аст?",
        readingSubtitle = "Шрифт ва андозаро интихоб кунед — матни поён фавран иваз мешавад.",
        lookTitle = "Барномаро аз худ кунед",
        lookSubtitle = "Равшан, торик ё мувофиқи система — ва рангест, ки ба шумо маъқул аст.",
        heroOverline = "Идома", heroSurah = "Ал-Бақара", heroProgress = "Оят 255 аз 286",
        heroRead = "Хондан", heroListen = "Гӯш кардан",
        goalsTitle = "Барои шумо чӣ муҳим аст?",
        goalsSubtitle = "Ҳар кадомашро интихоб кунед — ё гузаред. Ҳамаро баъдтар иваз кардан мумкин аст.",
        goalRead = "Хондан", goalListen = "Гӯш кардан", goalMemorize = "Аз бар кардан",
        dailyGoalTitle = "Оят дар як рӯз",
        dailyGoalHint = "Оромона оғоз кардан беҳтар аст. Инро дар танзимот иваз кардан мумкин аст.",
    )

    AppLanguage.UZ -> OnboardingCopy(
        welcomeSubtitle = "O‘qing. Tinglang. Yodlang.",
        chooseLanguage = "Tilni tanlang",
        chooseLanguageHint = "Keyinroq sozlamalarda o‘zgartirishingiz mumkin.",
        next = "Davom etish", start = "Boshlash", skip = "O‘tkazish",
        stepOf = "{n}-qadam, jami {total}",
        readingTitle = "Qanday o‘qish sizga qulay?",
        readingSubtitle = "Shrift va o‘lchamni tanlang — quyidagi matn darhol o‘zgaradi.",
        lookTitle = "Ilovani o‘zingizga moslang",
        lookSubtitle = "Yorug‘, qorong‘i yoki tizimdagidek — va o‘zingizga yoqqan rang.",
        heroOverline = "Davom etish", heroSurah = "Baqara", heroProgress = "255-oyat, jami 286",
        heroRead = "O‘qish", heroListen = "Tinglash",
        goalsTitle = "Siz uchun nima muhim?",
        goalsSubtitle = "Xohlaganingizni tanlang — yoki o‘tkazib yuboring. Hammasini keyin o‘zgartirsa bo‘ladi.",
        goalRead = "O‘qish", goalListen = "Tinglash", goalMemorize = "Yodlash",
        dailyGoalTitle = "Kuniga oyat",
        dailyGoalHint = "Sekin boshlash yaxshiroq. Buni sozlamalarda o‘zgartirishingiz mumkin.",
    )

    AppLanguage.TR -> OnboardingCopy(
        welcomeSubtitle = "Okuyun. Dinleyin. Ezberleyin.",
        chooseLanguage = "Dilinizi seçin",
        chooseLanguageHint = "Daha sonra Ayarlar'dan değiştirebilirsiniz.",
        next = "Devam", start = "Başla", skip = "Atla",
        stepOf = "Adım {n} / {total}",
        readingTitle = "Nasıl okumak istersiniz?",
        readingSubtitle = "Bir yazı tipi ve boyut seçin — aşağıdaki metin hemen değişir.",
        lookTitle = "Uygulamayı size göre ayarlayın",
        lookSubtitle = "Açık, koyu ya da sistemle uyumlu — ve hoşunuza giden bir renk.",
        heroOverline = "Devam et", heroSurah = "Bakara", heroProgress = "Ayet 255 / 286",
        heroRead = "Oku", heroListen = "Dinle",
        goalsTitle = "Sizin için ne önemli?",
        goalsSubtitle = "Dilediğinizi seçin — ya da atlayın. Hepsi sonradan değiştirilebilir.",
        goalRead = "Oku", goalListen = "Dinle", goalMemorize = "Ezberle",
        dailyGoalTitle = "Günde ayet",
        dailyGoalHint = "Sakin başlamak en iyisi. Bunu Ayarlar'dan değiştirebilirsiniz.",
    )
}
