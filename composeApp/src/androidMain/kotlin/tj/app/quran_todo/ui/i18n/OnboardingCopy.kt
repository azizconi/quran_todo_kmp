package tj.app.quran_todo.ui.i18n

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import tj.app.quran_todo.common.i18n.AppLanguage
import tj.app.quran_todo.common.i18n.LocalAppLanguage
import tj.app.quran_todo.ui.onboarding.PaceSpan
import tj.app.quran_todo.ui.onboarding.SpanUnit

/**
 * Strings of the onboarding v2 (docs/redesign/tasks/PC1-onboarding-v2.md) in the five app languages. Texts with values are
 * patterns with `{count}`, `{name}`, `{ayahs}`, `{range}`, `{min}`, `{span}`, `{date}` and `{time}` placeholders, so each language
 * keeps its own word order; the plural forms of the numbers live in the functions (and in `ayahCountLabel` / `surahCountLabel`).
 * The preset names, the date and the plan warnings come from [PlanCopy], the analytics switch from [TrustCopy].
 *
 * The Tajik, Uzbek and Turkish texts are to be checked by native speakers.
 */
@Immutable
data class OnboardingCopy(
    val language: AppLanguage,

    // Chrome
    val next: String,
    /** The main button of the welcome screen. */
    val start: String,
    val later: String,
    /** Template with `{n}` and `{total}`; read by screen readers on the progress bar. */
    val stepOf: String,

    // 1. Language
    val welcomeSubtitle: String,
    val chooseLanguage: String,
    val chooseLanguageHint: String,

    // 2. What is known
    val knownTitle: String,
    val knownSubtitle: String,
    val knownOther: String,
    val knownNone: String,
    val knownCountFmt: String,
    val removeFmt: String,

    // 3. What to learn
    val presetTitle: String,
    val presetSubtitle: String,
    val presetJuzAmma: String,
    val presetJuzTabarak: String,
    val presetFromStart: String,
    val presetChoose: String,
    val presetStartFmt: String,
    val allKnownJuzAmma: String,
    val allKnownJuzTabarak: String,
    val allKnownFromStart: String,
    val allKnownCustom: String,
    val customEmpty: String,
    val customOrderHint: String,
    /** The quiet link under the preset cards: leave the onboarding with the reader. */
    val presetReadOnly: String,
    val forecastFmt: String,

    // 4. Ready
    val readyTitle: String,
    val readySubtitle: String,
    val readyLessonFmt: String,
    val readyKnownFmt: String,
    val readyForecastFmt: String,
    val readyStart: String,
    /** The label of the pace row: "Ayahs a day". */
    val readyPerDay: String,
    val readyFewer: String,
    val readyMore: String,
    /** Template with `{min}`, `{max}` and `{unit}`; the lesson length is a range, not a promise. */
    val readyMinutesFmt: String,
    val finishFailed: String,
) {
    fun stepLabel(step: Int, total: Int): String = stepOf.fill("n" to step, "total" to total)

    /** "Marked: 5 surahs". */
    fun knownCount(count: Int): String = knownCountFmt.fill("count" to surahCountLabel(count, language))

    fun remove(name: String): String = removeFmt.fill("name" to name)

    /** "We start with Al-Falaq · 5 ayahs". */
    fun presetStart(surahName: String, ayahs: Int): String =
        presetStartFmt.fill("name" to surahName, "ayahs" to ayahCountLabel(ayahs, language))

    /** "3 weeks", "4 months": with the plural forms of the language. */
    internal fun span(span: PaceSpan): String {
        val n = span.count
        return when (language) {
            AppLanguage.RU -> when (span.unit) {
                SpanUnit.Day -> "$n ${russianPlural(n, "день", "дня", "дней")}"
                SpanUnit.Week -> "$n ${russianPlural(n, "неделя", "недели", "недель")}"
                SpanUnit.Month -> "$n ${russianPlural(n, "месяц", "месяца", "месяцев")}"
                SpanUnit.Year -> "$n ${russianPlural(n, "год", "года", "лет")}"
            }
            AppLanguage.EN -> when (span.unit) {
                SpanUnit.Day -> "$n ${if (n == 1) "day" else "days"}"
                SpanUnit.Week -> "$n ${if (n == 1) "week" else "weeks"}"
                SpanUnit.Month -> "$n ${if (n == 1) "month" else "months"}"
                SpanUnit.Year -> "$n ${if (n == 1) "year" else "years"}"
            }
            AppLanguage.TG -> when (span.unit) {
                SpanUnit.Day -> "$n рӯз"
                SpanUnit.Week -> "$n ҳафта"
                SpanUnit.Month -> "$n моҳ"
                SpanUnit.Year -> "$n сол"
            }
            AppLanguage.UZ -> when (span.unit) {
                SpanUnit.Day -> "$n kun"
                SpanUnit.Week -> "$n hafta"
                SpanUnit.Month -> "$n oy"
                SpanUnit.Year -> "$n yil"
            }
            AppLanguage.TR -> when (span.unit) {
                SpanUnit.Day -> "$n gün"
                SpanUnit.Week -> "$n hafta"
                SpanUnit.Month -> "$n ay"
                SpanUnit.Year -> "$n yıl"
            }
        }
    }

    /** "≈ 4 months · ~14 March". */
    internal fun forecast(span: PaceSpan, date: String): String = forecastFmt.fill("span" to span(span), "date" to date)

    /** "Lesson 1: An-Nas 1–6 · ~6 min"; without the estimate (it is still being counted) just the passage. */
    fun readyLesson(range: String, minutes: Int?): String {
        val text = readyLessonFmt.fill("range" to range, "min" to (minutes?.let { homeCopy(language).lessonMinutes(it) } ?: ""))
        return if (minutes == null) text.substringBefore(" · ").trimEnd() else text
    }

    /** "You know: 5 surahs — we will review them". */
    fun readyKnown(count: Int): String = readyKnownFmt.fill("count" to surahCountLabel(count, language))

    /** "Juz Amma: you will finish ~14 March". */
    fun readyForecast(name: String, date: String): String = readyForecastFmt.fill("name" to name, "date" to date)

    /** "5–10 minutes a day" for an estimate of [minutes]: from the estimate to twice that. */
    fun readyMinutes(minutes: Int): String {
        val low = minutes.coerceAtLeast(1)
        val high = low * 2
        val unit = when (language) {
            AppLanguage.RU -> russianPlural(high, "минуту", "минуты", "минут")
            AppLanguage.EN -> "minutes"
            AppLanguage.TG -> "дақиқа"
            AppLanguage.UZ -> "daqiqa"
            AppLanguage.TR -> "dakika"
        }
        return readyMinutesFmt.fill("min" to low, "max" to high, "unit" to unit)
    }
}

@Composable
fun rememberOnboardingCopy(language: AppLanguage = LocalAppLanguage.current): OnboardingCopy =
    remember(language) { onboardingCopy(language) }

private fun String.fill(vararg values: Pair<String, Any>): String {
    var result = this
    values.forEach { (key, value) -> result = result.replace("{$key}", value.toString()) }
    return result
}

fun onboardingCopy(language: AppLanguage): OnboardingCopy = when (language) {
    AppLanguage.EN -> OnboardingCopy(
        language = language,
        next = "Next", later = "Later",
        start = "Start",
        stepOf = "Step {n} of {total}",
        welcomeSubtitle = "Memorize the Quran every day — a little at a time, and never forget it",
        chooseLanguage = "Choose your language",
        chooseLanguageHint = "You can change it later in Settings.",
        knownTitle = "What do you already know?",
        knownSubtitle = "Mark the surahs you know by heart. We will review them instead of teaching them again.",
        knownOther = "Other surahs",
        knownNone = "Nothing yet",
        knownCountFmt = "Marked: {count}",
        removeFmt = "Remove: {name}",
        presetTitle = "What shall we learn?",
        presetSubtitle = "Let’s start with something doable. You can change the list in My plan.",
        presetJuzAmma = "Juz Amma (from the end)",
        presetJuzTabarak = "Juz Tabarak",
        presetFromStart = "From the start of the Quran",
        presetChoose = "Choose surahs",
        presetStartFmt = "We start with {name} · {ayahs}",
        allKnownJuzAmma = "You already know all of Juz Amma",
        allKnownJuzTabarak = "You already know all of Juz Tabarak",
        allKnownFromStart = "You already know the whole Quran",
        allKnownCustom = "You already know all the chosen surahs",
        customEmpty = "Choose surahs in the order you will learn them",
        customOrderHint = "In the order you chose them",
        presetReadOnly = "Just read and listen for now",
        forecastFmt = "≈ {span} · ~{date}",
        readyTitle = "Your plan is ready",
        readySubtitle = "Here is where we start.",
        readyLessonFmt = "Lesson 1: {range} · {min}",
        readyKnownFmt = "You know: {count} — we will review them",
        readyForecastFmt = "{name}: you will finish ~{date}",
        readyStart = "Start the first lesson",
        readyPerDay = "Ayahs a day", readyFewer = "Fewer ayahs a day", readyMore = "More ayahs a day",
        readyMinutesFmt = "{min}–{max} {unit} a day",
        finishFailed = "Couldn’t save. Please try again.",
    )

    AppLanguage.RU -> OnboardingCopy(
        language = language,
        next = "Далее", later = "Позже",
        start = "Начать",
        stepOf = "Шаг {n} из {total}",
        welcomeSubtitle = "Заучивайте Коран каждый день — понемногу и без забывания",
        chooseLanguage = "Выберите язык",
        chooseLanguageHint = "Позже его можно изменить в настройках.",
        knownTitle = "Что уже знаете?",
        knownSubtitle = "Отметьте суры, которые знаете наизусть. Мы будем их повторять, а не учить заново.",
        knownOther = "Другие суры",
        knownNone = "Пока ничего",
        knownCountFmt = "Отмечено: {count}",
        removeFmt = "Убрать: {name}",
        presetTitle = "Что будем учить?",
        presetSubtitle = "Начнём с посильного. Список можно изменить в «Моём плане».",
        presetJuzAmma = "Джуз Амма (с конца)",
        presetJuzTabarak = "Джуз Таборак",
        presetFromStart = "С начала Корана",
        presetChoose = "Выбрать суры",
        presetStartFmt = "Начнём с {name} · {ayahs}",
        allKnownJuzAmma = "Вы уже знаете весь Джуз Амма",
        allKnownJuzTabarak = "Вы уже знаете весь Джуз Таборак",
        allKnownFromStart = "Вы уже знаете весь Коран",
        allKnownCustom = "Вы уже знаете все выбранные суры",
        customEmpty = "Выберите суры в том порядке, в каком будете их учить",
        customOrderHint = "Порядок — как вы выбрали",
        presetReadOnly = "Пока только читать и слушать",
        forecastFmt = "≈ {span} · ~{date}",
        readyTitle = "План готов",
        readySubtitle = "Вот с чего мы начнём.",
        readyLessonFmt = "Урок 1: {range} · {min}",
        readyKnownFmt = "Знаете: {count} — будем повторять",
        readyForecastFmt = "{name}: закончите ~{date}",
        readyStart = "Начать первый урок",
        readyPerDay = "Аятов в день", readyFewer = "Меньше аятов в день", readyMore = "Больше аятов в день",
        readyMinutesFmt = "{min}–{max} {unit} в день",
        finishFailed = "Не получилось сохранить. Попробуйте ещё раз.",
    )

    AppLanguage.TG -> OnboardingCopy(
        language = language,
        next = "Идома", later = "Баъдтар",
        start = "Оғоз кардан",
        stepOf = "Қадами {n} аз {total}",
        welcomeSubtitle = "Қуръонро ҳар рӯз аз бар кунед — андак-андак ва бе фаромӯшӣ",
        chooseLanguage = "Забонро интихоб кунед",
        chooseLanguageHint = "Баъдтар онро дар танзимот иваз кардан мумкин аст.",
        knownTitle = "Аллакай чиро медонед?",
        knownSubtitle = "Сураҳоеро, ки аз бар медонед, қайд кунед. Мо онҳоро такрор мекунем, на аз нав меомӯзем.",
        knownOther = "Сураҳои дигар",
        knownNone = "Ҳоло ҳеҷ чиз",
        knownCountFmt = "Қайд шуд: {count}",
        removeFmt = "Дур кардан: {name}",
        presetTitle = "Чиро меомӯзем?",
        presetSubtitle = "Аз осонтар оғоз мекунем. Рӯйхатро дар «Нақшаи ман» иваз кардан мумкин аст.",
        presetJuzAmma = "Ҷузъи Амма (аз охир)",
        presetJuzTabarak = "Ҷузъи Табораку",
        presetFromStart = "Аз аввали Қуръон",
        presetChoose = "Интихоби сураҳо",
        presetStartFmt = "Аз {name} оғоз мекунем · {ayahs}",
        allKnownJuzAmma = "Шумо тамоми Ҷузъи Аммаро аллакай медонед",
        allKnownJuzTabarak = "Шумо тамоми Ҷузъи Табораку аллакай медонед",
        allKnownFromStart = "Шумо тамоми Қуръонро аллакай медонед",
        allKnownCustom = "Шумо ҳамаи сураҳои интихобшударо аллакай медонед",
        customEmpty = "Сураҳоро ба тартибе интихоб кунед, ки онҳоро меомӯзед",
        customOrderHint = "Тартиб — чунон ки интихоб кардед",
        presetReadOnly = "Ҳоло танҳо хондан ва гӯш кардан",
        forecastFmt = "≈ {span} · ~{date}",
        readyTitle = "Нақша тайёр аст",
        readySubtitle = "Мо аз ин ҷо оғоз мекунем.",
        readyLessonFmt = "Дарси 1: {range} · {min}",
        readyKnownFmt = "Медонед: {count} — такрор мекунем",
        readyForecastFmt = "{name}: анҷом медиҳед ~{date}",
        readyStart = "Оғози дарси якум",
        readyPerDay = "Оят дар як рӯз", readyFewer = "Оятҳои камтар дар як рӯз", readyMore = "Оятҳои бештар дар як рӯз",
        readyMinutesFmt = "{min}–{max} {unit} дар як рӯз",
        finishFailed = "Сабт нашуд. Боз кӯшиш кунед.",
    )

    AppLanguage.UZ -> OnboardingCopy(
        language = language,
        next = "Davom etish", later = "Keyinroq",
        start = "Boshlash",
        stepOf = "{n}-qadam, jami {total}",
        welcomeSubtitle = "Qur’onni har kuni yodlang — oz-ozdan va unutmasdan",
        chooseLanguage = "Tilni tanlang",
        chooseLanguageHint = "Keyinroq sozlamalarda o‘zgartirishingiz mumkin.",
        knownTitle = "Nimani allaqachon bilasiz?",
        knownSubtitle = "Yoddan biladigan suralaringizni belgilang. Ularni qaytadan o‘rganmaymiz, takrorlaymiz.",
        knownOther = "Boshqa suralar",
        knownNone = "Hozircha hech narsa",
        knownCountFmt = "Belgilandi: {count}",
        removeFmt = "Olib tashlash: {name}",
        presetTitle = "Nimani o‘rganamiz?",
        presetSubtitle = "Osonidan boshlaymiz. Ro‘yxatni «Mening rejam»da o‘zgartirish mumkin.",
        presetJuzAmma = "Amma juzi (oxiridan)",
        presetJuzTabarak = "Tabarak juzi",
        presetFromStart = "Qur’onning boshidan",
        presetChoose = "Suralarni tanlash",
        presetStartFmt = "Boshlaymiz: {name} · {ayahs}",
        allKnownJuzAmma = "Siz butun Amma juzini allaqachon bilasiz",
        allKnownJuzTabarak = "Siz butun Tabarak juzini allaqachon bilasiz",
        allKnownFromStart = "Siz butun Qur’onni allaqachon bilasiz",
        allKnownCustom = "Siz tanlangan barcha suralarni allaqachon bilasiz",
        customEmpty = "Suralarni o‘rganish tartibida tanlang",
        customOrderHint = "Tartib — siz tanlagandek",
        presetReadOnly = "Hozircha faqat o‘qish va tinglash",
        forecastFmt = "≈ {span} · ~{date}",
        readyTitle = "Reja tayyor",
        readySubtitle = "Mana, shundan boshlaymiz.",
        readyLessonFmt = "1-dars: {range} · {min}",
        readyKnownFmt = "Bilasiz: {count} — takrorlab turamiz",
        readyForecastFmt = "{name}: ~{date} tugatasiz",
        readyStart = "Birinchi darsni boshlash",
        readyPerDay = "Kuniga oyat", readyFewer = "Kuniga kamroq oyat", readyMore = "Kuniga ko‘proq oyat",
        readyMinutesFmt = "Kuniga {min}–{max} {unit}",
        finishFailed = "Saqlab bo‘lmadi. Yana urinib ko‘ring.",
    )

    AppLanguage.TR -> OnboardingCopy(
        language = language,
        next = "Devam", later = "Sonra",
        start = "Başla",
        stepOf = "Adım {n} / {total}",
        welcomeSubtitle = "Kur’an’ı her gün ezberleyin — azar azar ve unutmadan",
        chooseLanguage = "Dilinizi seçin",
        chooseLanguageHint = "Daha sonra Ayarlar'dan değiştirebilirsiniz.",
        knownTitle = "Zaten neleri biliyorsunuz?",
        knownSubtitle = "Ezbere bildiğiniz sureleri işaretleyin. Onları yeniden öğrenmeyeceğiz, tekrar edeceğiz.",
        knownOther = "Diğer sureler",
        knownNone = "Şimdilik hiçbiri",
        knownCountFmt = "İşaretlenen: {count}",
        removeFmt = "Kaldır: {name}",
        presetTitle = "Ne öğrenelim?",
        presetSubtitle = "Kolayından başlayalım. Listeyi «Planım»dan değiştirebilirsiniz.",
        presetJuzAmma = "Amme cüzü (sondan)",
        presetJuzTabarak = "Tebâreke cüzü",
        presetFromStart = "Kur’an’ın başından",
        presetChoose = "Sure seç",
        presetStartFmt = "Başlangıç: {name} · {ayahs}",
        allKnownJuzAmma = "Amme cüzünün tamamını zaten biliyorsunuz",
        allKnownJuzTabarak = "Tebâreke cüzünün tamamını zaten biliyorsunuz",
        allKnownFromStart = "Kur’an’ın tamamını zaten biliyorsunuz",
        allKnownCustom = "Seçtiğiniz tüm sureleri zaten biliyorsunuz",
        customEmpty = "Sureleri öğreneceğiniz sırayla seçin",
        customOrderHint = "Sıra — seçtiğiniz gibi",
        presetReadOnly = "Şimdilik sadece oku ve dinle",
        forecastFmt = "≈ {span} · ~{date}",
        readyTitle = "Planınız hazır",
        readySubtitle = "İşte buradan başlıyoruz.",
        readyLessonFmt = "1. ders: {range} · {min}",
        readyKnownFmt = "Bildikleriniz: {count} — tekrar edeceğiz",
        readyForecastFmt = "{name}: ~{date} bitirirsiniz",
        readyStart = "İlk dersi başlat",
        readyPerDay = "Günde ayet", readyFewer = "Günde daha az ayet", readyMore = "Günde daha çok ayet",
        readyMinutesFmt = "Günde {min}–{max} {unit}",
        finishFailed = "Kaydedilemedi. Lütfen tekrar deneyin.",
    )
}
