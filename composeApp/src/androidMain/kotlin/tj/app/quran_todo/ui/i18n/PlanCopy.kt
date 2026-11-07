package tj.app.quran_todo.ui.i18n

import androidx.compose.runtime.Immutable
import kotlinx.datetime.LocalDate
import tj.app.quran_todo.common.i18n.AppLanguage
import kotlin.math.roundToInt

/**
 * Strings of "My plan" in the five app languages (wording follows the glossary of
 * docs/redesign/ANDROID_REDESIGN.md, section 10). Texts with values are patterns with `{n}`, `{ayahs}`,
 * `{date}`, `{pages}` placeholders, filled by the functions below, so each language keeps its own word
 * order; the plural forms live in the functions.
 *
 * The Tajik, Uzbek and Turkish wording of the plan screen should be checked by a native speaker.
 */
@Immutable
data class PlanCopy(
    val language: AppLanguage,
    val title: String,
    val back: String,

    // What I learn
    val sectionWhat: String,
    val scopeJuz30: String,
    val scopeJuz29: String,
    val scopeFromStart: String,
    val scopeCustom: String,
    val describeJuz30: String,
    val describeJuz29: String,
    val describeFromStart: String,
    val describeCustom: String,
    val editOrder: String,
    val addSurah: String,
    val remove: String,
    val dragHandle: String,
    val moveUp: String,
    val moveDown: String,
    val showAllPattern: String,
    val showLess: String,
    val queueEmpty: String,
    val learnedMark: String,
    val currentMark: String,

    // Pace
    val sectionPace: String,
    val modePerDay: String,
    val modeByDate: String,
    val perDayLabel: String,
    val finishBy: String,
    val needPerDayPattern: String,
    val minutesPerDayPattern: String,
    val longLessonPattern: String,
    val tooFastPattern: String,

    // Old
    val sectionOld: String,
    val oldTitlePattern: String,
    val oldPagesPattern: String,
    val oldNone: String,
    val oldHintPattern: String,
    val oldSetDaysPattern: String,

    // Forecast
    val sectionForecast: String,
    val finishOnPattern: String,
    val finished: String,
    val forecastEmpty: String,
    val behindPattern: String,

    // Actions
    val save: String,
    val create: String,
    val savedToast: String,
    val saveFailed: String,
    val delete: String,
    val deleteTitle: String,
    val deleteBody: String,
    val deleteConfirm: String,
    val deletedToast: String,
    val cancel: String,
    val unsavedTitle: String,
    val unsavedBody: String,
    val discard: String,
    val keepEditing: String,

    // Add a surah
    val addTitle: String,
    val searchPlaceholder: String,
    val clearSearch: String,
    val added: String,
    val noResults: String,
    val done: String,

    // Date
    val pickDate: String,
    val ok: String,
    private val months: List<String>,
) {
    /** "Memorize 7 ayahs a day" needs: "7 ayahs a day". */
    fun needPerDay(n: Int): String = needPerDayPattern.fill("ayahs" to ayahCountLabel(n, language))

    fun minutesPerDay(n: Int): String = minutesPerDayPattern.fill("n" to minutesLabel(n))

    fun longLesson(n: Int): String = longLessonPattern.fill("n" to n)

    fun tooFast(n: Int): String = tooFastPattern.fill("ayahs" to ayahCountLabel(n, language))

    fun showAll(n: Int): String = showAllPattern.fill("n" to n)

    fun oldTitle(days: Int): String = oldTitlePattern.fill("n" to daysLabel(days))

    /** "≈ 1,5 pages a day". */
    fun oldPages(pages: Float): String = oldPagesPattern.fill("pages" to pagesLabel(pages))

    /**
     * The soft hint under the circle slider: "That is about 27 minutes a day. A circle of 20 days is about 10 minutes."
     * [minutes] is the circle as it stands, [days] and [targetMinutes] the suggested one.
     */
    fun oldHint(minutes: Int, days: Int, targetMinutes: Int): String = oldHintPattern.fill(
        "minutes" to aboutMinutesLabel(minutes),
        "days" to daysLabel(days),
        "target" to aboutMinutesLabel(targetMinutes),
    )

    /** What the chip "20 days" does, for TalkBack: "Set the circle to 20 days". */
    fun oldSetDays(days: Int): String = oldSetDaysPattern.fill("days" to daysLabel(days))

    /** A number of minutes after "about": the Russian genitive ("около 21 минуты", "около 22 минут"). */
    fun aboutMinutesLabel(n: Int): String = when (language) {
        AppLanguage.RU -> "$n ${if (n % 10 == 1 && n % 100 != 11) "минуты" else "минут"}"
        else -> minutesLabel(n)
    }

    fun finishOn(epochDay: Int, today: Int): String = finishOnPattern.fill("date" to dateLabel(epochDay, today))

    fun behind(ayahs: Int): String = behindPattern.fill("ayahs" to ayahCountLabel(ayahs, language))

    /** "Left 120 of 564 ayahs". */
    fun remaining(remaining: Int, total: Int): String = when (language) {
        AppLanguage.RU -> "Осталось $remaining из $total аятов"
        AppLanguage.EN -> "$remaining of $total ayahs left"
        AppLanguage.TG -> "Монд: $remaining аз $total оят"
        AppLanguage.UZ -> "Qoldi: $total ta oyatdan $remaining tasi"
        AppLanguage.TR -> "Kalan: $total ayetten $remaining"
    }

    /** "14 March", with the year when it is not this year's. Never uses the locale of the phone: the app language decides. */
    fun dateLabel(epochDay: Int, today: Int): String {
        val date = LocalDate.fromEpochDays(epochDay)
        val month = months[date.monthNumber - 1]
        val sameYear = date.year == LocalDate.fromEpochDays(today).year
        val dayAndMonth = if (language == AppLanguage.EN) "$month ${date.dayOfMonth}" else "${date.dayOfMonth} $month"
        return when {
            sameYear -> dayAndMonth
            language == AppLanguage.EN -> "$dayAndMonth, ${date.year}"
            else -> "$dayAndMonth ${date.year}"
        }
    }

    fun minutesLabel(n: Int): String = when (language) {
        AppLanguage.RU -> "$n ${russianPlural(n, "минуту", "минуты", "минут")}"
        AppLanguage.EN -> "$n ${if (n == 1) "minute" else "minutes"}"
        AppLanguage.TG -> "$n дақиқа"
        AppLanguage.UZ -> "$n daqiqa"
        AppLanguage.TR -> "$n dakika"
    }

    fun daysLabel(n: Int): String = when (language) {
        AppLanguage.RU -> "$n ${russianPlural(n, "день", "дня", "дней")}"
        AppLanguage.EN -> "$n ${if (n == 1) "day" else "days"}"
        AppLanguage.TG -> "$n рӯз"
        AppLanguage.UZ -> "$n kun"
        AppLanguage.TR -> "$n gün"
    }

    /** "1.5 pages", "2 pages": one decimal, the decimal sign of the language; never less than 0.1. */
    fun pagesLabel(pages: Float): String {
        val tenths = (pages * 10).roundToInt().coerceAtLeast(1)
        val whole = tenths / 10
        val fraction = tenths % 10
        val sign = if (language == AppLanguage.EN) "." else ","
        return if (fraction == 0) {
            when (language) {
                AppLanguage.RU -> "$whole ${russianPlural(whole, "страницу", "страницы", "страниц")}"
                AppLanguage.EN -> "$whole ${if (whole == 1) "page" else "pages"}"
                AppLanguage.TG -> "$whole саҳифа"
                AppLanguage.UZ -> "$whole sahifa"
                AppLanguage.TR -> "$whole sayfa"
            }
        } else {
            val number = "$whole$sign$fraction"
            when (language) {
                AppLanguage.RU -> "$number страницы"
                AppLanguage.EN -> "$number pages"
                AppLanguage.TG -> "$number саҳифа"
                AppLanguage.UZ -> "$number sahifa"
                AppLanguage.TR -> "$number sayfa"
            }
        }
    }

    /** "37 surahs · 564 ayahs": what a preset holds. */
    fun scopeInfo(surahs: Int, ayahs: Int): String = "${surahCountLabel(surahs, language)} · ${ayahCountLabel(ayahs, language)}"
}

private fun String.fill(vararg values: Pair<String, Any>): String {
    var result = this
    values.forEach { (key, value) -> result = result.replace("{$key}", value.toString()) }
    return result
}

fun planCopy(language: AppLanguage): PlanCopy = when (language) {
    AppLanguage.RU -> PlanCopy(
        language = language,
        title = "Мой план",
        back = "Назад",
        sectionWhat = "Что учу",
        scopeJuz30 = "Джуз Амма",
        scopeJuz29 = "Джуз Таборак",
        scopeFromStart = "С начала",
        scopeCustom = "Свои суры",
        describeJuz30 = "С конца: от коротких сур к длинным (114 → 78)",
        describeJuz29 = "От коротких сур к длинным (77 → 67)",
        describeFromStart = "По порядку мусхафа (1 → 114)",
        describeCustom = "Ваш список и порядок",
        editOrder = "Изменить порядок",
        addSurah = "Добавить суру",
        remove = "Убрать",
        dragHandle = "Перетащить",
        moveUp = "Выше",
        moveDown = "Ниже",
        showAllPattern = "Показать все ({n})",
        showLess = "Свернуть",
        queueEmpty = "Добавьте хотя бы одну суру",
        learnedMark = "выучена",
        currentMark = "сейчас",
        sectionPace = "Темп",
        modePerDay = "Аятов в день",
        modeByDate = "Закончить к дате",
        perDayLabel = "аятов в день",
        finishBy = "Закончить к",
        needPerDayPattern = "нужно {ayahs} в день",
        minutesPerDayPattern = "≈ {n} в день",
        longLessonPattern = "Это около {n} минут в день — урок будет длинным",
        tooFastPattern = "Слишком быстро: нужно {ayahs} в день, а в плане не больше 20.",
        sectionOld = "Круг старого",
        oldTitlePattern = "Повторять всё выученное за {n}",
        oldPagesPattern = "≈ {pages} в день",
        oldNone = "Когда появится выученное, здесь будет видно, сколько страниц в день вы повторяете.",
        oldHintPattern = "Это около {minutes} в день. Круг в {days} — около {target}.",
        oldSetDaysPattern = "Поставить круг в {days}",
        sectionForecast = "Прогноз",
        finishOnPattern = "Закончите ~{date}",
        finished = "Все суры плана выучены",
        forecastEmpty = "Добавьте суры, чтобы увидеть прогноз",
        behindPattern = "отстаёте на {ayahs}",
        save = "Сохранить",
        create = "Создать план",
        savedToast = "План сохранён",
        saveFailed = "Не удалось сохранить план",
        delete = "Удалить план",
        deleteTitle = "Удалить план?",
        deleteBody = "Выученное останется: пропадут только очередь и темп.",
        deleteConfirm = "Удалить",
        deletedToast = "План удалён",
        cancel = "Отмена",
        unsavedTitle = "Сохранить изменения?",
        unsavedBody = "Изменения плана ещё не сохранены.",
        discard = "Не сохранять",
        keepEditing = "Продолжить",
        addTitle = "Добавить суру",
        searchPlaceholder = "Сура или номер",
        clearSearch = "Очистить",
        added = "Добавлена",
        noResults = "Ничего не нашлось",
        done = "Готово",
        pickDate = "Выберите дату",
        ok = "Готово",
        months = listOf(
            "января", "февраля", "марта", "апреля", "мая", "июня",
            "июля", "августа", "сентября", "октября", "ноября", "декабря",
        ),
    )

    AppLanguage.EN -> PlanCopy(
        language = language,
        title = "My plan",
        back = "Back",
        sectionWhat = "What I learn",
        scopeJuz30 = "Juz Amma",
        scopeJuz29 = "Juz Tabarak",
        scopeFromStart = "From the start",
        scopeCustom = "My surahs",
        describeJuz30 = "From the end: short surahs first (114 → 78)",
        describeJuz29 = "Short surahs first (77 → 67)",
        describeFromStart = "In the order of the mushaf (1 → 114)",
        describeCustom = "Your list and your order",
        editOrder = "Change the order",
        addSurah = "Add a surah",
        remove = "Remove",
        dragHandle = "Drag",
        moveUp = "Move up",
        moveDown = "Move down",
        showAllPattern = "Show all ({n})",
        showLess = "Show less",
        queueEmpty = "Add at least one surah",
        learnedMark = "memorized",
        currentMark = "now",
        sectionPace = "Pace",
        modePerDay = "Ayahs a day",
        modeByDate = "Finish by a date",
        perDayLabel = "ayahs a day",
        finishBy = "Finish by",
        needPerDayPattern = "you need {ayahs} a day",
        minutesPerDayPattern = "≈ {n} a day",
        longLessonPattern = "That is about {n} minutes a day — the lesson will be long",
        tooFastPattern = "Too fast: that needs {ayahs} a day, and a plan allows at most 20.",
        sectionOld = "Review circle",
        oldTitlePattern = "Review everything memorized over {n}",
        oldPagesPattern = "≈ {pages} a day",
        oldNone = "Once something is memorized, this shows how many pages a day you review.",
        oldHintPattern = "That is about {minutes} a day. A circle of {days} is about {target}.",
        oldSetDaysPattern = "Set the circle to {days}",
        sectionForecast = "Forecast",
        finishOnPattern = "You will finish ~{date}",
        finished = "All the surahs of the plan are memorized",
        forecastEmpty = "Add surahs to see the forecast",
        behindPattern = "you are {ayahs} behind",
        save = "Save",
        create = "Create plan",
        savedToast = "Plan saved",
        saveFailed = "Couldn’t save the plan",
        delete = "Delete plan",
        deleteTitle = "Delete the plan?",
        deleteBody = "What you memorized stays: only the queue and the pace go.",
        deleteConfirm = "Delete",
        deletedToast = "Plan deleted",
        cancel = "Cancel",
        unsavedTitle = "Save the changes?",
        unsavedBody = "The changes to the plan are not saved yet.",
        discard = "Don’t save",
        keepEditing = "Keep editing",
        addTitle = "Add a surah",
        searchPlaceholder = "Surah or number",
        clearSearch = "Clear",
        added = "Added",
        noResults = "Nothing found",
        done = "Done",
        pickDate = "Choose a date",
        ok = "Done",
        months = listOf(
            "January", "February", "March", "April", "May", "June",
            "July", "August", "September", "October", "November", "December",
        ),
    )

    AppLanguage.TG -> PlanCopy(
        language = language,
        title = "Нақшаи ман",
        back = "Бозгашт",
        sectionWhat = "Чиро аз бар мекунам",
        scopeJuz30 = "Ҷузъи Амма",
        scopeJuz29 = "Ҷузъи Табораку",
        scopeFromStart = "Аз аввал",
        scopeCustom = "Сураҳои худам",
        describeJuz30 = "Аз охир: аз сураҳои кӯтоҳ ба дароз (114 → 78)",
        describeJuz29 = "Аз сураҳои кӯтоҳ ба дароз (77 → 67)",
        describeFromStart = "Бо тартиби мусҳаф (1 → 114)",
        describeCustom = "Рӯйхат ва тартиби шумо",
        editOrder = "Тағйири тартиб",
        addSurah = "Илова кардани сура",
        remove = "Хориҷ кардан",
        dragHandle = "Кашидан",
        moveUp = "Боло",
        moveDown = "Поён",
        showAllPattern = "Ҳамаро нишон додан ({n})",
        showLess = "Пинҳон кардан",
        queueEmpty = "Ақалан як сура илова кунед",
        learnedMark = "аз бар шудааст",
        currentMark = "ҳозир",
        sectionPace = "Суръат",
        modePerDay = "Оят дар як рӯз",
        modeByDate = "То сана",
        perDayLabel = "оят дар як рӯз",
        finishBy = "Анҷом то",
        needPerDayPattern = "лозим аст: {ayahs} дар як рӯз",
        minutesPerDayPattern = "≈ {n} дар як рӯз",
        longLessonPattern = "Ин тақрибан {n} дақиқа дар як рӯз аст — дарс дароз мешавад",
        tooFastPattern = "Хеле тез: {ayahs} дар як рӯз лозим аст, дар нақша на бештар аз 20 мешавад.",
        sectionOld = "Давраи кӯҳна",
        oldTitlePattern = "Ҳамаи аз бар шударо дар {n} такрор кардан",
        oldPagesPattern = "≈ {pages} дар як рӯз",
        oldNone = "Вақте чизе аз бар шавад, дар ин ҷо менамояд, ки чанд саҳифа дар як рӯз такрор мекунед.",
        oldHintPattern = "Ин тақрибан {minutes} дар як рӯз аст. Давра дар {days} — тақрибан {target}.",
        oldSetDaysPattern = "Давраро ба {days} танзим кунед",
        sectionForecast = "Пешгӯӣ",
        finishOnPattern = "Анҷом медиҳед ~{date}",
        finished = "Ҳамаи сураҳои нақша аз бар шудаанд",
        forecastEmpty = "Барои пешгӯӣ сураҳо илова кунед",
        behindPattern = "{ayahs} ақиб мондаед",
        save = "Сабт кардан",
        create = "Сохтани нақша",
        savedToast = "Нақша сабт шуд",
        saveFailed = "Нақшаро сабт кардан нашуд",
        delete = "Нест кардани нақша",
        deleteTitle = "Нақша нест карда шавад?",
        deleteBody = "Аз бар шуда мемонад: танҳо навбат ва суръат нест мешаванд.",
        deleteConfirm = "Нест кардан",
        deletedToast = "Нақша нест шуд",
        cancel = "Бекор",
        unsavedTitle = "Тағйиротро сабт кунем?",
        unsavedBody = "Тағйироти нақша ҳоло сабт нашудааст.",
        discard = "Сабт накардан",
        keepEditing = "Идома додан",
        addTitle = "Илова кардани сура",
        searchPlaceholder = "Сура ё рақам",
        clearSearch = "Пок кардан",
        added = "Илова шуд",
        noResults = "Чизе ёфт нашуд",
        done = "Тайёр",
        pickDate = "Санаро интихоб кунед",
        ok = "Тайёр",
        months = listOf(
            "январ", "феврал", "март", "апрел", "май", "июн",
            "июл", "август", "сентябр", "октябр", "ноябр", "декабр",
        ),
    )

    AppLanguage.UZ -> PlanCopy(
        language = language,
        title = "Mening rejam",
        back = "Orqaga",
        sectionWhat = "Nimani yodlayman",
        scopeJuz30 = "Amma juzi",
        scopeJuz29 = "Tabarak juzi",
        scopeFromStart = "Boshidan",
        scopeCustom = "O‘z suralarim",
        describeJuz30 = "Oxiridan: qisqa suralardan uzunlariga (114 → 78)",
        describeJuz29 = "Qisqa suralardan uzunlariga (77 → 67)",
        describeFromStart = "Mushaf tartibida (1 → 114)",
        describeCustom = "Sizning ro‘yxatingiz va tartibingiz",
        editOrder = "Tartibni o‘zgartirish",
        addSurah = "Sura qo‘shish",
        remove = "Olib tashlash",
        dragHandle = "Siljitish",
        moveUp = "Yuqoriga",
        moveDown = "Pastga",
        showAllPattern = "Hammasini ko‘rsatish ({n})",
        showLess = "Yig‘ish",
        queueEmpty = "Kamida bitta sura qo‘shing",
        learnedMark = "yodlangan",
        currentMark = "hozir",
        sectionPace = "Sur’at",
        modePerDay = "Kuniga oyat",
        modeByDate = "Sanagacha",
        perDayLabel = "oyat kuniga",
        finishBy = "Tugatish sanasi",
        needPerDayPattern = "kerak: kuniga {ayahs}",
        minutesPerDayPattern = "≈ kuniga {n}",
        longLessonPattern = "Bu kuniga taxminan {n} daqiqa — dars uzun bo‘ladi",
        tooFastPattern = "Juda tez: kuniga {ayahs} kerak, rejada esa 20 tadan ko‘p bo‘lmaydi.",
        sectionOld = "Eski yodlanganlar doirasi",
        oldTitlePattern = "Yodlanganlarning hammasini {n}da takrorlash",
        oldPagesPattern = "≈ kuniga {pages}",
        oldNone = "Yodlangan narsa paydo bo‘lgach, bu yerda kuniga necha sahifa takrorlashingiz ko‘rinadi.",
        oldHintPattern = "Bu kuniga taxminan {minutes}. {days}lik doira — taxminan {target}.",
        oldSetDaysPattern = "Doirani {days}ga qo‘yish",
        sectionForecast = "Bashorat",
        finishOnPattern = "Tugatasiz ~{date}",
        finished = "Rejadagi barcha suralar yodlangan",
        forecastEmpty = "Bashorat uchun sura qo‘shing",
        behindPattern = "{ayahs} orqadasiz",
        save = "Saqlash",
        create = "Reja tuzish",
        savedToast = "Reja saqlandi",
        saveFailed = "Rejani saqlab bo‘lmadi",
        delete = "Rejani o‘chirish",
        deleteTitle = "Reja o‘chirilsinmi?",
        deleteBody = "Yodlanganlar qoladi: faqat navbat va sur’at o‘chadi.",
        deleteConfirm = "O‘chirish",
        deletedToast = "Reja o‘chirildi",
        cancel = "Bekor qilish",
        unsavedTitle = "O‘zgarishlar saqlansinmi?",
        unsavedBody = "Reja o‘zgarishlari hali saqlanmagan.",
        discard = "Saqlamaslik",
        keepEditing = "Davom etish",
        addTitle = "Sura qo‘shish",
        searchPlaceholder = "Sura yoki raqam",
        clearSearch = "Tozalash",
        added = "Qo‘shildi",
        noResults = "Hech narsa topilmadi",
        done = "Tayyor",
        pickDate = "Sanani tanlang",
        ok = "Tayyor",
        months = listOf(
            "yanvar", "fevral", "mart", "aprel", "may", "iyun",
            "iyul", "avgust", "sentabr", "oktabr", "noyabr", "dekabr",
        ),
    )

    AppLanguage.TR -> PlanCopy(
        language = language,
        title = "Planım",
        back = "Geri",
        sectionWhat = "Ne ezberliyorum",
        scopeJuz30 = "Amme cüzü",
        scopeJuz29 = "Tebâreke cüzü",
        scopeFromStart = "Baştan",
        scopeCustom = "Kendi surelerim",
        describeJuz30 = "Sondan: kısa surelerden uzunlara (114 → 78)",
        describeJuz29 = "Kısa surelerden uzunlara (77 → 67)",
        describeFromStart = "Mushaf sırasıyla (1 → 114)",
        describeCustom = "Sizin listeniz ve sıranız",
        editOrder = "Sırayı değiştir",
        addSurah = "Sure ekle",
        remove = "Kaldır",
        dragHandle = "Sürükle",
        moveUp = "Yukarı taşı",
        moveDown = "Aşağı taşı",
        showAllPattern = "Tümünü göster ({n})",
        showLess = "Daralt",
        queueEmpty = "En az bir sure ekleyin",
        learnedMark = "ezberlendi",
        currentMark = "şimdi",
        sectionPace = "Tempo",
        modePerDay = "Günde ayet",
        modeByDate = "Tarihe kadar",
        perDayLabel = "günde ayet",
        finishBy = "Bitiş tarihi",
        needPerDayPattern = "gerekli: günde {ayahs}",
        minutesPerDayPattern = "≈ günde {n}",
        longLessonPattern = "Bu günde yaklaşık {n} dakika — ders uzun olacak",
        tooFastPattern = "Çok hızlı: günde {ayahs} gerekiyor, ancak planda en fazla 20 olabilir.",
        sectionOld = "Eskileri tekrar döngüsü",
        oldTitlePattern = "Ezberlenenlerin hepsini {n} içinde tekrar et",
        oldPagesPattern = "≈ günde {pages}",
        oldNone = "Ezberlediğiniz bir şey olunca burada günde kaç sayfa tekrar ettiğiniz görünür.",
        oldHintPattern = "Bu günde yaklaşık {minutes}. {days}lük döngü yaklaşık {target} sürer.",
        oldSetDaysPattern = "Döngüyü {days} yap",
        sectionForecast = "Tahmin",
        finishOnPattern = "Bitirirsiniz ~{date}",
        finished = "Plandaki tüm sureler ezberlendi",
        forecastEmpty = "Tahmin için sure ekleyin",
        behindPattern = "{ayahs} geridesiniz",
        save = "Kaydet",
        create = "Plan oluştur",
        savedToast = "Plan kaydedildi",
        saveFailed = "Plan kaydedilemedi",
        delete = "Planı sil",
        deleteTitle = "Plan silinsin mi?",
        deleteBody = "Ezberlediklerin kalır: yalnızca sıra ve tempo silinir.",
        deleteConfirm = "Sil",
        deletedToast = "Plan silindi",
        cancel = "İptal",
        unsavedTitle = "Değişiklikler kaydedilsin mi?",
        unsavedBody = "Plandaki değişiklikler henüz kaydedilmedi.",
        discard = "Kaydetme",
        keepEditing = "Düzenlemeye devam",
        addTitle = "Sure ekle",
        searchPlaceholder = "Sure veya numara",
        clearSearch = "Temizle",
        added = "Eklendi",
        noResults = "Sonuç bulunamadı",
        done = "Tamam",
        pickDate = "Tarih seçin",
        ok = "Tamam",
        months = listOf(
            "Ocak", "Şubat", "Mart", "Nisan", "Mayıs", "Haziran",
            "Temmuz", "Ağustos", "Eylül", "Ekim", "Kasım", "Aralık",
        ),
    )
}
