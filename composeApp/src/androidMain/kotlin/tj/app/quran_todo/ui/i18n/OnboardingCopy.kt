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
 * Font names and descriptions, the theme and palette labels come from [SettingsCopy], the preset names, the date and the plan
 * warnings from [PlanCopy], the analytics switch from [TrustCopy] and the notification preview from [ReminderCopy].
 *
 * The Tajik, Uzbek and Turkish texts are to be checked by native speakers.
 */
@Immutable
data class OnboardingCopy(
    val language: AppLanguage,

    // Chrome
    val next: String,
    val skip: String,
    val later: String,
    /** Template with `{n}` and `{total}`; read by screen readers on the progress bar. */
    val stepOf: String,
    val slideOf: String,

    // 1. Language
    val welcomeSubtitle: String,
    val chooseLanguage: String,
    val chooseLanguageHint: String,

    // 2-4. The story
    val storyReadTitle: String,
    val storyReadBody: String,
    val storyListenTitle: String,
    val storyListenBody: String,
    val storyMemorizeTitle: String,
    val storyMemorizeBody: String,
    /** Al-Fatihah, only when the loaded surah names are not there yet. */
    val fatihaName: String,

    // 5. Goal
    val goalTitle: String,
    val goalSubtitle: String,
    val goalMemorizeTitle: String,
    val goalMemorizeBody: String,
    val goalReadTitle: String,
    val goalReadBody: String,

    // 6. What is known
    val knownTitle: String,
    val knownSubtitle: String,
    val knownOther: String,
    val knownNone: String,
    val knownCountFmt: String,
    val removeFmt: String,

    // 7. What to learn
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

    // 8. Pace
    val paceTitle: String,
    val paceSubtitle: String,
    val paceByDate: String,
    val forecastFmt: String,

    // 9. Reminder
    val reminderTitle: String,
    val reminderSubtitle: String,
    val reminderMorning: String,
    val reminderEvening: String,
    val reminderOther: String,
    val reminderOn: String,
    val reminderNotNow: String,
    val reminderDenied: String,

    // 10. How to read
    val comfortTitle: String,
    val comfortSubtitle: String,

    // 11. Ready
    val readyTitle: String,
    val readySubtitle: String,
    val readyLessonFmt: String,
    val readyKnownFmt: String,
    val readyForecastFmt: String,
    val readyReminderFmt: String,
    val readyStart: String,
    val readyReadTitle: String,
    val readyReadBody: String,
    val readyReadOpen: String,
    val finishFailed: String,
) {
    fun stepLabel(step: Int, total: Int): String = stepOf.fill("n" to step, "total" to total)

    fun slideLabel(slide: Int, total: Int): String = slideOf.fill("n" to slide, "total" to total)

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

    fun readyReminder(time: String): String = readyReminderFmt.fill("time" to time)
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
        next = "Next", skip = "Skip", later = "Later",
        stepOf = "Step {n} of {total}",
        slideOf = "Slide {n} of {total}",
        welcomeSubtitle = "Memorize the Quran every day — a little at a time, and never forget it",
        chooseLanguage = "Choose your language",
        chooseLanguageHint = "You can change it later in Settings.",
        storyReadTitle = "Read beautifully",
        storyReadBody = "The clean text of the mushaf, a translation and a large font — the way you like it.",
        storyListenTitle = "Listen and repeat",
        storyListenBody = "Pick a reciter and a passage: every ayah sounds as many times as you need.",
        storyMemorizeTitle = "Memorize and don’t forget",
        storyMemorizeBody = "A ready lesson every day: new, recent and old. Reviews come on the right day, so what you learned stays.",
        fatihaName = "Al-Fatihah",
        goalTitle = "What do you want to do?",
        goalSubtitle = "Pick the main thing — the rest is always there.",
        goalMemorizeTitle = "Memorize the Quran",
        goalMemorizeBody = "A plan, a lesson every day and review, so nothing is forgotten.",
        goalReadTitle = "Read and listen",
        goalReadBody = "Beautiful text, reciters and a way back to where you stopped.",
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
        paceTitle = "How many ayahs a day?",
        paceSubtitle = "A gentle start is best. You can change the pace at any time.",
        paceByDate = "I want it by a date",
        forecastFmt = "≈ {span} · ~{date}",
        reminderTitle = "Remind you about the lesson?",
        reminderSubtitle = "It won’t come if today’s lesson is already done.",
        reminderMorning = "Morning", reminderEvening = "Evening", reminderOther = "Other…",
        reminderOn = "Remind me", reminderNotNow = "Not now",
        reminderDenied = "You can turn it on later in Settings",
        comfortTitle = "How do you like to read?",
        comfortSubtitle = "Font, size and theme — the text below follows at once.",
        readyTitle = "Your plan is ready",
        readySubtitle = "Here is where we start.",
        readyLessonFmt = "Lesson 1: {range} · {min}",
        readyKnownFmt = "You know: {count} — we will review them",
        readyForecastFmt = "{name}: you will finish ~{date}",
        readyReminderFmt = "We will remind you at {time}",
        readyStart = "Start the first lesson",
        readyReadTitle = "Start with Al-Fatihah",
        readyReadBody = "Read and listen — the app remembers where you stopped. You can turn on memorizing later in My plan.",
        readyReadOpen = "Open Al-Fatihah",
        finishFailed = "Couldn’t save. Please try again.",
    )

    AppLanguage.RU -> OnboardingCopy(
        language = language,
        next = "Далее", skip = "Пропустить", later = "Позже",
        stepOf = "Шаг {n} из {total}",
        slideOf = "Слайд {n} из {total}",
        welcomeSubtitle = "Заучивайте Коран каждый день — понемногу и без забывания",
        chooseLanguage = "Выберите язык",
        chooseLanguageHint = "Позже его можно изменить в настройках.",
        storyReadTitle = "Читайте красиво",
        storyReadBody = "Чистый текст мусхафа, перевод и крупный шрифт — так, как удобно вам.",
        storyListenTitle = "Слушайте и повторяйте",
        storyListenBody = "Выберите чтеца и отрывок: каждый аят прозвучит столько раз, сколько нужно.",
        storyMemorizeTitle = "Заучивайте и не забывайте",
        storyMemorizeBody = "Каждый день — готовый урок: новое, недавнее и старое. Повторения приходят в нужный день, и выученное остаётся.",
        fatihaName = "Аль-Фатиха",
        goalTitle = "Что вы хотите делать?",
        goalSubtitle = "Выберите главное — остальное всегда под рукой.",
        goalMemorizeTitle = "Заучивать Коран",
        goalMemorizeBody = "План, урок на каждый день и повторение, чтобы ничего не забывалось.",
        goalReadTitle = "Читать и слушать",
        goalReadBody = "Красивый текст, чтецы и возврат туда, где вы остановились.",
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
        paceTitle = "Сколько аятов в день?",
        paceSubtitle = "Лучше начать спокойно. Темп можно менять в любой момент.",
        paceByDate = "Хочу к дате",
        forecastFmt = "≈ {span} · ~{date}",
        reminderTitle = "Напоминать об уроке?",
        reminderSubtitle = "Напоминание не придёт, если урок уже сделан.",
        reminderMorning = "Утром", reminderEvening = "Вечером", reminderOther = "Другое…",
        reminderOn = "Напоминать", reminderNotNow = "Не сейчас",
        reminderDenied = "Можно включить позже в настройках",
        comfortTitle = "Как удобнее читать?",
        comfortSubtitle = "Шрифт, размер и тема — текст ниже меняется сразу.",
        readyTitle = "План готов",
        readySubtitle = "Вот с чего мы начнём.",
        readyLessonFmt = "Урок 1: {range} · {min}",
        readyKnownFmt = "Знаете: {count} — будем повторять",
        readyForecastFmt = "{name}: закончите ~{date}",
        readyReminderFmt = "Напомним в {time}",
        readyStart = "Начать первый урок",
        readyReadTitle = "Начните с Аль-Фатихи",
        readyReadBody = "Читайте и слушайте — приложение запомнит, где вы остановились. Заучивание можно включить позже в «Моём плане».",
        readyReadOpen = "Открыть Аль-Фатиху",
        finishFailed = "Не получилось сохранить. Попробуйте ещё раз.",
    )

    AppLanguage.TG -> OnboardingCopy(
        language = language,
        next = "Идома", skip = "Гузаштан", later = "Баъдтар",
        stepOf = "Қадами {n} аз {total}",
        slideOf = "Слайди {n} аз {total}",
        welcomeSubtitle = "Қуръонро ҳар рӯз аз бар кунед — андак-андак ва бе фаромӯшӣ",
        chooseLanguage = "Забонро интихоб кунед",
        chooseLanguageHint = "Баъдтар онро дар танзимот иваз кардан мумкин аст.",
        storyReadTitle = "Зебо бихонед",
        storyReadBody = "Матни софи мусҳаф, тарҷума ва ҳарфи калон — чунон ки ба шумо қулай аст.",
        storyListenTitle = "Гӯш кунед ва такрор кунед",
        storyListenBody = "Қорӣ ва порчаро интихоб кунед: ҳар оят ба қадри лозим садо медиҳад.",
        storyMemorizeTitle = "Аз бар кунед ва фаромӯш накунед",
        storyMemorizeBody = "Ҳар рӯз дарси тайёр: нав, навакак ва кӯҳна. Такрорҳо дар рӯзи лозим меоянд ва омӯхтаатон мемонад.",
        fatihaName = "Ал-Фотиҳа",
        goalTitle = "Шумо чӣ кор кардан мехоҳед?",
        goalSubtitle = "Муҳимашро интихоб кунед — боқӣ ҳамеша дар дастрас аст.",
        goalMemorizeTitle = "Қуръонро аз бар кардан",
        goalMemorizeBody = "Нақша, дарси ҳар рӯз ва такрор, то чизе фаромӯш нашавад.",
        goalReadTitle = "Хондан ва гӯш кардан",
        goalReadBody = "Матни зебо, қорӣҳо ва бозгашт ба ҷое, ки хонишро қатъ карда будед.",
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
        paceTitle = "Дар як рӯз чанд оят?",
        paceSubtitle = "Оромона оғоз кардан беҳтар аст. Суръатро ҳар вақт иваз кардан мумкин аст.",
        paceByDate = "То сана мехоҳам",
        forecastFmt = "≈ {span} · ~{date}",
        reminderTitle = "Дар бораи дарс ёдрас кунем?",
        reminderSubtitle = "Агар дарс аллакай анҷом шуда бошад, ёдраскунӣ намеояд.",
        reminderMorning = "Субҳ", reminderEvening = "Бегоҳ", reminderOther = "Дигар…",
        reminderOn = "Ёдрас кун", reminderNotNow = "Ҳозир не",
        reminderDenied = "Баъдтар онро дар танзимот фаъол кардан мумкин аст",
        comfortTitle = "Чӣ тавр хондан қулай аст?",
        comfortSubtitle = "Шрифт, андоза ва мавзӯъ — матни поён фавран иваз мешавад.",
        readyTitle = "Нақша тайёр аст",
        readySubtitle = "Мо аз ин ҷо оғоз мекунем.",
        readyLessonFmt = "Дарси 1: {range} · {min}",
        readyKnownFmt = "Медонед: {count} — такрор мекунем",
        readyForecastFmt = "{name}: анҷом медиҳед ~{date}",
        readyReminderFmt = "Ёдраскунӣ: {time}",
        readyStart = "Оғози дарси якум",
        readyReadTitle = "Аз сураи Фотиҳа оғоз кунед",
        readyReadBody = "Бихонед ва гӯш кунед — барнома ҷойеро, ки истодед, ба ёд мегирад. Аз бар кардани Қуръонро баъдтар дар «Нақшаи ман» фаъол кардан мумкин аст.",
        readyReadOpen = "Кушодани сураи Фотиҳа",
        finishFailed = "Сабт нашуд. Боз кӯшиш кунед.",
    )

    AppLanguage.UZ -> OnboardingCopy(
        language = language,
        next = "Davom etish", skip = "O‘tkazish", later = "Keyinroq",
        stepOf = "{n}-qadam, jami {total}",
        slideOf = "{n}-slayd, jami {total}",
        welcomeSubtitle = "Qur’onni har kuni yodlang — oz-ozdan va unutmasdan",
        chooseLanguage = "Tilni tanlang",
        chooseLanguageHint = "Keyinroq sozlamalarda o‘zgartirishingiz mumkin.",
        storyReadTitle = "Chiroyli o‘qing",
        storyReadBody = "Mushafning toza matni, tarjima va katta shrift — o‘zingizga qulay holda.",
        storyListenTitle = "Tinglang va takrorlang",
        storyListenBody = "Qori va parchani tanlang: har bir oyat kerakli marta yangraydi.",
        storyMemorizeTitle = "Yodlang va unutmang",
        storyMemorizeBody = "Har kuni tayyor dars: yangi, yaqinda o‘rganilgan va eski. Takrorlar kerakli kunda keladi va o‘rganganingiz esda qoladi.",
        fatihaName = "Fotiha",
        goalTitle = "Nima qilmoqchisiz?",
        goalSubtitle = "Asosiysini tanlang — qolgani doim qo‘l ostida.",
        goalMemorizeTitle = "Qur’onni yodlash",
        goalMemorizeBody = "Reja, har kungi dars va takrorlash — hech narsa unutilmasligi uchun.",
        goalReadTitle = "O‘qish va tinglash",
        goalReadBody = "Chiroyli matn, qorilar va to‘xtagan joyingizga qaytish.",
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
        paceTitle = "Kuniga nechta oyat?",
        paceSubtitle = "Sekin boshlash yaxshiroq. Tezlikni istalgan payt o‘zgartirsa bo‘ladi.",
        paceByDate = "Sanagacha xohlayman",
        forecastFmt = "≈ {span} · ~{date}",
        reminderTitle = "Dars haqida eslatib turaylikmi?",
        reminderSubtitle = "Dars bajarilgan bo‘lsa, eslatma kelmaydi.",
        reminderMorning = "Ertalab", reminderEvening = "Kechqurun", reminderOther = "Boshqa…",
        reminderOn = "Eslatib tur", reminderNotNow = "Hozir emas",
        reminderDenied = "Keyinroq sozlamalarda yoqish mumkin",
        comfortTitle = "Qanday o‘qish qulay?",
        comfortSubtitle = "Shrift, o‘lcham va mavzu — quyidagi matn darhol o‘zgaradi.",
        readyTitle = "Reja tayyor",
        readySubtitle = "Mana, shundan boshlaymiz.",
        readyLessonFmt = "1-dars: {range} · {min}",
        readyKnownFmt = "Bilasiz: {count} — takrorlab turamiz",
        readyForecastFmt = "{name}: ~{date} tugatasiz",
        readyReminderFmt = "Eslatma: {time}",
        readyStart = "Birinchi darsni boshlash",
        readyReadTitle = "Fotihadan boshlang",
        readyReadBody = "O‘qing va tinglang — ilova to‘xtagan joyingizni eslab qoladi. Yodlashni keyinroq «Mening rejam»da yoqish mumkin.",
        readyReadOpen = "Fotihani ochish",
        finishFailed = "Saqlab bo‘lmadi. Yana urinib ko‘ring.",
    )

    AppLanguage.TR -> OnboardingCopy(
        language = language,
        next = "Devam", skip = "Atla", later = "Sonra",
        stepOf = "Adım {n} / {total}",
        slideOf = "Slayt {n} / {total}",
        welcomeSubtitle = "Kur’an’ı her gün ezberleyin — azar azar ve unutmadan",
        chooseLanguage = "Dilinizi seçin",
        chooseLanguageHint = "Daha sonra Ayarlar'dan değiştirebilirsiniz.",
        storyReadTitle = "Güzel okuyun",
        storyReadBody = "Mushafın sade metni, meal ve büyük yazı tipi — size uygun şekilde.",
        storyListenTitle = "Dinleyin ve tekrarlayın",
        storyListenBody = "Bir kâri ve bir bölüm seçin: her ayet istediğiniz kadar tekrar edilir.",
        storyMemorizeTitle = "Ezberleyin ve unutmayın",
        storyMemorizeBody = "Her gün hazır bir ders: yeni, yakın zamanda ve eski. Tekrarlar tam zamanında gelir, öğrendikleriniz kalır.",
        fatihaName = "Fâtiha",
        goalTitle = "Ne yapmak istiyorsunuz?",
        goalSubtitle = "Asıl olanı seçin — gerisi hep elinizin altında.",
        goalMemorizeTitle = "Kur’an ezberlemek",
        goalMemorizeBody = "Plan, her gün bir ders ve hiçbir şeyin unutulmaması için tekrar.",
        goalReadTitle = "Okumak ve dinlemek",
        goalReadBody = "Güzel bir metin, kâriler ve kaldığınız yere dönüş.",
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
        paceTitle = "Günde kaç ayet?",
        paceSubtitle = "Sakin başlamak en iyisi. Hızı istediğiniz zaman değiştirebilirsiniz.",
        paceByDate = "Bir tarihe kadar istiyorum",
        forecastFmt = "≈ {span} · ~{date}",
        reminderTitle = "Ders için hatırlatalım mı?",
        reminderSubtitle = "Bugünün dersi yapıldıysa hatırlatma gelmez.",
        reminderMorning = "Sabah", reminderEvening = "Akşam", reminderOther = "Diğer…",
        reminderOn = "Hatırlat", reminderNotNow = "Şimdi değil",
        reminderDenied = "Daha sonra Ayarlar'dan açabilirsiniz",
        comfortTitle = "Nasıl okumak istersiniz?",
        comfortSubtitle = "Yazı tipi, boyut ve tema — aşağıdaki metin hemen değişir.",
        readyTitle = "Planınız hazır",
        readySubtitle = "İşte buradan başlıyoruz.",
        readyLessonFmt = "1. ders: {range} · {min}",
        readyKnownFmt = "Bildikleriniz: {count} — tekrar edeceğiz",
        readyForecastFmt = "{name}: ~{date} bitirirsiniz",
        readyReminderFmt = "Hatırlatma: {time}",
        readyStart = "İlk dersi başlat",
        readyReadTitle = "Fâtiha’dan başlayın",
        readyReadBody = "Okuyun ve dinleyin — uygulama kaldığınız yeri hatırlar. Ezberi daha sonra «Planım»dan açabilirsiniz.",
        readyReadOpen = "Fâtiha’yı aç",
        finishFailed = "Kaydedilemedi. Lütfen tekrar deneyin.",
    )
}
