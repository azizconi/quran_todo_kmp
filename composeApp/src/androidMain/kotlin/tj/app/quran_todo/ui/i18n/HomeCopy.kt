package tj.app.quran_todo.ui.i18n

import androidx.compose.runtime.Immutable
import tj.app.quran_todo.common.i18n.AppLanguage

/**
 * Strings of the home screen in the five app languages (wording follows the glossary of
 * docs/redesign/ANDROID_REDESIGN.md, section 10). Texts with numbers or names are patterns with
 * `{a}`, `{b}`, `{n}`, `{s}` and `{name}` placeholders, filled by the functions below, so each
 * language keeps its own word order and plural forms.
 */
@Immutable
data class HomeCopy(
    val language: AppLanguage,

    // Top bar
    val greeting: String,
    val title: String,
    val settings: String,
    val progress: String,
    val progressPattern: String,

    // Hero
    val continueLabel: String,
    val startWithFatiha: String,
    val read: String,
    val listen: String,
    val pause: String,
    val ayahOfPattern: String,

    // Today's lesson card
    val lessonTitle: String,
    /** `{range}` is "Surah 1–5". */
    val lessonNewPattern: String,
    /** `{passages}` is "3 passages". */
    val lessonRecentPattern: String,
    /** Some of the recent passages are done: `{a}` of `{b}`. */
    val lessonRecentProgressPattern: String,
    val lessonOldPattern: String,
    val lessonMinutesPattern: String,
    val lessonMinutesLeftPattern: String,
    val lessonStart: String,
    val lessonContinue: String,
    val lessonDone: String,
    /** `{what}` is the new passage and/or the repetitions of tomorrow. */
    val lessonTomorrowPattern: String,
    val lessonTomorrowDuePattern: String,
    val lessonTomorrowNothing: String,
    val lessonCatchingUp: String,
    val lessonInviteTitle: String,
    val lessonInviteBody: String,
    val lessonMakePlan: String,
    val lessonPlanFinished: String,
    val lessonPlanFinishedBody: String,
    val lessonNewPlan: String,
    val myPlan: String,
    /** The label of the "..." button of the card. */
    val lessonMore: String,

    // Recent
    val recent: String,
    val pinned: String,

    // Search and list
    val searchPlaceholder: String,
    val clearSearch: String,
    val filterAll: String,
    val filterLearning: String,
    val filterLearned: String,
    val smartOpen: String,
    val smartTitlePattern: String,
    val noResultsTitle: String,
    val noResultsBody: String,
    val emptyLearningTitle: String,
    val emptyLearningBody: String,
    val emptyLearnedTitle: String,
    val emptyLearnedBody: String,
    val open: String,

    // Loading failed
    val loadErrorTitle: String,
    val loadErrorBody: String,
    val retry: String,

    // Surah sheet
    val sheetStatus: String,
    val statusNotStarted: String,
    val statusLearning: String,
    val statusLearned: String,
    /** The three choices of the surah sheet: "Not started" is [statusNotStarted]. */
    val sheetLearning: String,
    val sheetKnown: String,
    /** Spoken state of a surah row being memorized: `{n}` is the percent already memorized. Turkish puts the sign first. */
    val learningPercentPattern: String,
    val pin: String,
    val unpin: String,
    val listenSurah: String,
    /** The surah the plan is learning now: a small label in the list. */
    val inPlan: String,
    val forgetBody: String,
    val forgetConfirm: String,
    val cancel: String,

    // Mini player
    val play: String,
    val stop: String,
    val openPlayer: String,
    val audioError: String,
    val playerSubPattern: String,

    // Toasts
    val learnedToastPattern: String,
    val learningToastPattern: String,
    val statusUpdateFailed: String,
) {
    /** "Progress: 3%": the spoken label of the ring in the top bar. */
    fun progressDescription(percent: Int): String = progressPattern.fill("n" to percent)

    fun ayahOf(ayah: Int, total: Int): String = ayahOfPattern.fill("a" to ayah, "b" to total)

    fun smartTitle(name: String, ayah: Int): String = smartTitlePattern.fill("name" to name, "n" to ayah)

    /** "Ayah 12 · 1.25×": the line under the surah name in the mini player. */
    fun playerSub(ayah: Int, speed: Float): String = playerSubPattern.fill("a" to ayah, "s" to speedLabel(speed))

    fun learnedToast(name: String): String = learnedToastPattern.fill("name" to name)

    fun learningToast(name: String): String = learningToastPattern.fill("name" to name)

    fun lessonNew(range: String): String = lessonNewPattern.fill("range" to range)

    /** "Recent: 3 passages", or "Recent: 1 of 3" once some of them are done. */
    fun lessonRecent(count: Int, doneCount: Int): String =
        if (doneCount in 1 until count) {
            lessonRecentProgressPattern.fill("a" to doneCount, "b" to count)
        } else {
            lessonRecentPattern.fill("passages" to passageCountLabel(count, language))
        }

    fun lessonOld(range: String): String = lessonOldPattern.fill("range" to range)

    fun lessonMinutes(n: Int): String = lessonMinutesPattern.fill("n" to n)

    fun lessonMinutesLeft(n: Int): String = lessonMinutesLeftPattern.fill("n" to n)

    /** "Tomorrow: Surah 6–10 · 2 to review". */
    fun lessonTomorrow(newRange: String?, dueCount: Int): String {
        val parts = listOfNotNull(
            newRange,
            lessonTomorrowDuePattern.fill("n" to dueCount).takeIf { dueCount > 0 },
        )
        return if (parts.isEmpty()) lessonTomorrowNothing else lessonTomorrowPattern.fill("what" to parts.joinToString(" · "))
    }

    /**
     * "Memorized 23 of 40 ayahs", with " · 2 hard" when some ayahs are marked difficult. When some of the [covered] ayahs are not
     * [learned] yet, it reads "Covered 23 of 40 ayahs · memorized 17".
     */
    fun coverageLine(covered: Int, total: Int, hard: Int, learned: Int = covered): String {
        val main = if (learned >= covered) when (language) {
            AppLanguage.RU -> "Выучено $covered из $total аятов"
            AppLanguage.EN -> "Memorized $covered of $total ayahs"
            AppLanguage.TG -> "$covered аз $total оят аз бар шудааст"
            AppLanguage.UZ -> "$total ta oyatdan $covered tasi yodlangan"
            AppLanguage.TR -> "$total ayetten $covered ayet ezberlendi"
        } else when (language) {
            AppLanguage.RU -> "Пройдено $covered из $total аятов · выучено $learned"
            AppLanguage.EN -> "Covered $covered of $total ayahs · memorized $learned"
            AppLanguage.TG -> "Гузашта: $covered аз $total оят · аз бар шуда: $learned"
            AppLanguage.UZ -> "O‘tilgan: $covered / $total oyat · yodlangan: $learned"
            AppLanguage.TR -> "Geçilen: $covered / $total ayet · ezberlenen: $learned"
        }
        if (hard <= 0) return main
        val hardText = when (language) {
            AppLanguage.RU -> "$hard ${if (hard % 10 == 1 && hard % 100 != 11) "трудный" else "трудных"}"
            AppLanguage.EN -> "$hard hard"
            AppLanguage.TG -> "$hard душвор"
            AppLanguage.UZ -> "$hard ta qiyin"
            AppLanguage.TR -> "$hard zor"
        }
        return "$main · $hardText"
    }

    /** The question before memorized ayahs leave the repetitions. */
    fun forgetTitle(n: Int): String = when (language) {
        AppLanguage.RU -> "Убрать $n ${russianPlural(n, "выученный аят", "выученных аята", "выученных аятов")} из повторений?"
        AppLanguage.EN -> "Remove $n memorized ${if (n == 1) "ayah" else "ayahs"} from your reviews?"
        AppLanguage.TG -> "$n оят аз такрорҳо хориҷ карда шавад?"
        AppLanguage.UZ -> "$n ta yodlangan oyat takrorlardan olib tashlansinmi?"
        AppLanguage.TR -> "$n ezberlenmiş ayet tekrarlardan kaldırılsın mı?"
    }

    fun ayahCount(n: Int): String = ayahCountLabel(n, language)

    fun surahCount(n: Int): String = surahCountLabel(n, language)

    fun ayahRange(start: Int, end: Int): String = ayahRangeLabel(start, end, language)

    /** "1×", "1.25×", "0.75×": the shortest exact form of the playback speed. */
    private fun speedLabel(speed: Float): String {
        val text = speed.toString().trimEnd('0').trimEnd('.')
        return "$text×"
    }
}

private fun String.fill(vararg values: Pair<String, Any>): String {
    var result = this
    values.forEach { (key, value) -> result = result.replace("{$key}", value.toString()) }
    return result
}

/** "1 ayah", "2 ayahs"; Russian 1 аят / 2 аята / 5 аятов; Tajik, Uzbek and Turkish do not inflect after a number. */
fun ayahCountLabel(n: Int, language: AppLanguage): String = when (language) {
    AppLanguage.RU -> "$n ${russianPlural(n, "аят", "аята", "аятов")}"
    AppLanguage.EN -> "$n ${if (n == 1) "ayah" else "ayahs"}"
    AppLanguage.TG -> "$n оят"
    AppLanguage.UZ -> "$n oyat"
    AppLanguage.TR -> "$n ayet"
}

/** The same for surahs: 1 сура / 2 суры / 5 сур. */
fun surahCountLabel(n: Int, language: AppLanguage): String = when (language) {
    AppLanguage.RU -> "$n ${russianPlural(n, "сура", "суры", "сур")}"
    AppLanguage.EN -> "$n ${if (n == 1) "surah" else "surahs"}"
    AppLanguage.TG -> "$n сура"
    AppLanguage.UZ -> "$n sura"
    AppLanguage.TR -> "$n sure"
}

/** "1 passage", "3 passages"; Russian 1 отрывок / 2 отрывка / 5 отрывков. */
fun passageCountLabel(n: Int, language: AppLanguage): String = when (language) {
    AppLanguage.RU -> "$n ${russianPlural(n, "отрывок", "отрывка", "отрывков")}"
    AppLanguage.EN -> "$n ${if (n == 1) "passage" else "passages"}"
    AppLanguage.TG -> "$n қисм"
    AppLanguage.UZ -> "$n ta parcha"
    AppLanguage.TR -> "$n bölüm"
}

/** "Surah 1–5" for a range, "Surah 5" for one ayah: the line of a passage in the lesson card and the plan. */
fun passageRangeLabel(surahName: String, start: Int, end: Int): String =
    if (start >= end) "$surahName $start" else "$surahName $start–$end"

/** "Ayah 5" for a single ayah, "Ayahs 1–5" for a range. */
fun ayahRangeLabel(start: Int, end: Int, language: AppLanguage): String {
    val single = start >= end
    return when (language) {
        AppLanguage.RU -> if (single) "Аят $start" else "Аяты $start–$end"
        AppLanguage.EN -> if (single) "Ayah $start" else "Ayahs $start–$end"
        AppLanguage.TG -> if (single) "Оят $start" else "Оятҳои $start–$end"
        AppLanguage.UZ -> if (single) "$start-oyat" else "$start–$end-oyatlar"
        AppLanguage.TR -> if (single) "Ayet $start" else "Ayet $start–$end"
    }
}

internal fun russianPlural(n: Int, one: String, few: String, many: String): String {
    val abs = kotlin.math.abs(n)
    return when {
        abs % 100 in 11..14 -> many
        abs % 10 == 1 -> one
        abs % 10 in 2..4 -> few
        else -> many
    }
}

fun homeCopy(language: AppLanguage): HomeCopy = when (language) {
    AppLanguage.RU -> HomeCopy(
        language = language,
        greeting = "Ассаляму алейкум",
        title = "Коран",
        settings = "Настройки",
        progress = "Прогресс",
        progressPattern = "Прогресс: {n}%",
        continueLabel = "Продолжить",
        startWithFatiha = "Начните с Аль-Фатихи",
        read = "Читать",
        listen = "Слушать",
        pause = "Пауза",
        ayahOfPattern = "Аят {a} из {b}",
        lessonTitle = "Урок дня",
        lessonNewPattern = "Новое: {range}",
        lessonRecentPattern = "Недавнее: {passages}",
        lessonRecentProgressPattern = "Недавнее: {a} из {b}",
        lessonOldPattern = "Старое: {range}",
        lessonMinutesPattern = "~{n} мин",
        lessonMinutesLeftPattern = "ещё ~{n} мин",
        lessonStart = "Начать урок",
        lessonContinue = "Продолжить урок",
        lessonDone = "Урок выполнен",
        lessonTomorrowPattern = "Завтра: {what}",
        lessonTomorrowDuePattern = "повторить: {n}",
        lessonTomorrowNothing = "Завтра: повторений нет",
        lessonCatchingUp = "Догоняем: часть урока перенесена на следующие дни",
        lessonInviteTitle = "Составьте план",
        lessonInviteBody = "Каждый день будет готовый урок: новое, недавнее и старое.",
        lessonMakePlan = "Составить план",
        lessonPlanFinished = "План выполнен",
        lessonPlanFinishedBody = "Все суры плана пройдены. Составьте следующий.",
        lessonNewPlan = "Новый план",
        myPlan = "Мой план",
        lessonMore = "Ещё",
        recent = "Недавние",
        pinned = "Закреплено",
        searchPlaceholder = "Сура, номер или 2:255",
        clearSearch = "Очистить",
        filterAll = "Все",
        filterLearning = "Учу",
        filterLearned = "Выучено",
        smartOpen = "Открыть",
        smartTitlePattern = "{name} · аят {n}",
        noResultsTitle = "Ничего не нашлось",
        noResultsBody = "Попробуйте название суры, её номер или аят в виде 2:255.",
        emptyLearningTitle = "Пока ничего не заучивается",
        emptyLearningBody = "Нажмите и удерживайте суру, чтобы отметить её как «Учу».",
        emptyLearnedTitle = "Выученных сур пока нет",
        emptyLearnedBody = "Когда выучите суру, отметьте её — она появится здесь.",
        open = "Открыть",
        loadErrorTitle = "Не удалось загрузить Коран",
        loadErrorBody = "Проверьте интернет. При первом запуске он нужен, чтобы скачать текст.",
        retry = "Повторить",
        sheetStatus = "Статус суры",
        statusNotStarted = "Не начал",
        statusLearning = "Заучиваю",
        statusLearned = "Выучено",
        sheetLearning = "Учу",
        sheetKnown = "Знаю наизусть",
        learningPercentPattern = "Заучиваю, выучено {n}%",
        pin = "Закрепить",
        unpin = "Открепить",
        listenSurah = "Слушать суру",
        inPlan = "в плане",
        forgetBody = "Расписание повторений этих аятов будет удалено.",
        forgetConfirm = "Убрать",
        cancel = "Отмена",
        play = "Играть",
        stop = "Остановить",
        openPlayer = "Открыть плеер",
        audioError = "Нет связи · Повторить",
        playerSubPattern = "Аят {a} · {s}",
        learnedToastPattern = "{name} — выучено",
        learningToastPattern = "{name} — в плане",
        statusUpdateFailed = "Не удалось изменить статус суры",
    )

    AppLanguage.EN -> HomeCopy(
        language = language,
        greeting = "Assalamu alaikum",
        title = "Quran",
        settings = "Settings",
        progress = "Progress",
        progressPattern = "Progress: {n}%",
        continueLabel = "Continue",
        startWithFatiha = "Start with Al-Fatihah",
        read = "Read",
        listen = "Listen",
        pause = "Pause",
        ayahOfPattern = "Ayah {a} of {b}",
        lessonTitle = "Today’s lesson",
        lessonNewPattern = "New: {range}",
        lessonRecentPattern = "Recent: {passages}",
        lessonRecentProgressPattern = "Recent: {a} of {b}",
        lessonOldPattern = "Old: {range}",
        lessonMinutesPattern = "~{n} min",
        lessonMinutesLeftPattern = "~{n} min left",
        lessonStart = "Start lesson",
        lessonContinue = "Continue lesson",
        lessonDone = "Lesson done",
        lessonTomorrowPattern = "Tomorrow: {what}",
        lessonTomorrowDuePattern = "{n} to review",
        lessonTomorrowNothing = "Tomorrow: nothing to review",
        lessonCatchingUp = "Catching up: part of the lesson moved to the coming days",
        lessonInviteTitle = "Make a plan",
        lessonInviteBody = "Every day you get a ready lesson: new, recent and old.",
        lessonMakePlan = "Create a plan",
        lessonPlanFinished = "Plan complete",
        lessonPlanFinishedBody = "You have been through all the surahs of the plan. Make the next one.",
        lessonNewPlan = "New plan",
        myPlan = "My plan",
        lessonMore = "More",
        recent = "Recent",
        pinned = "Pinned",
        searchPlaceholder = "Surah, number or 2:255",
        clearSearch = "Clear",
        filterAll = "All",
        filterLearning = "Memorizing",
        filterLearned = "Memorized",
        smartOpen = "Open",
        smartTitlePattern = "{name} · ayah {n}",
        noResultsTitle = "Nothing found",
        noResultsBody = "Try a surah name, its number, or an ayah like 2:255.",
        emptyLearningTitle = "Nothing memorizing yet",
        emptyLearningBody = "Press and hold a surah to mark it as “Memorizing”.",
        emptyLearnedTitle = "No memorized surahs yet",
        emptyLearnedBody = "Mark a surah as “Memorized” once you know it by heart.",
        open = "Open",
        loadErrorTitle = "Can’t load the Quran",
        loadErrorBody = "Check your connection. The first launch needs it to download the text.",
        retry = "Try again",
        sheetStatus = "Surah status",
        statusNotStarted = "Not started",
        statusLearning = "Memorizing",
        statusLearned = "Memorized",
        sheetLearning = "Memorizing",
        sheetKnown = "Know by heart",
        learningPercentPattern = "Memorizing, {n}% memorized",
        pin = "Pin",
        unpin = "Unpin",
        listenSurah = "Listen to surah",
        inPlan = "in plan",
        forgetBody = "The review schedule of these ayahs will be deleted.",
        forgetConfirm = "Remove",
        cancel = "Cancel",
        play = "Play",
        stop = "Stop",
        openPlayer = "Open player",
        audioError = "No connection · Retry",
        playerSubPattern = "Ayah {a} · {s}",
        learnedToastPattern = "{name} — memorized",
        learningToastPattern = "{name} — in your plan",
        statusUpdateFailed = "Couldn’t change the surah status",
    )

    AppLanguage.TG -> HomeCopy(
        language = language,
        greeting = "Ассалому алайкум",
        title = "Қуръон",
        settings = "Танзимот",
        progress = "Пешрафт",
        progressPattern = "Пешрафт: {n}%",
        continueLabel = "Идома",
        startWithFatiha = "Аз сураи Фотиҳа оғоз кунед",
        read = "Хондан",
        listen = "Гӯш кардан",
        pause = "Таваққуф",
        ayahOfPattern = "Оят {a} аз {b}",
        lessonTitle = "Дарси рӯз",
        lessonNewPattern = "Нав: {range}",
        lessonRecentPattern = "Навакак: {passages}",
        lessonRecentProgressPattern = "Навакак: {a} аз {b}",
        lessonOldPattern = "Кӯҳна: {range}",
        lessonMinutesPattern = "~{n} дақ.",
        lessonMinutesLeftPattern = "боз ~{n} дақ.",
        lessonStart = "Оғози дарс",
        lessonContinue = "Идомаи дарс",
        lessonDone = "Дарс иҷро шуд",
        lessonTomorrowPattern = "Фардо: {what}",
        lessonTomorrowDuePattern = "такрор: {n}",
        lessonTomorrowNothing = "Фардо: такрор нест",
        lessonCatchingUp = "Ҷуброн мекунем: як қисми дарс ба рӯзҳои оянда гузашт",
        lessonInviteTitle = "Нақша созед",
        lessonInviteBody = "Ҳар рӯз дарси тайёр хоҳад буд: нав, навакак ва кӯҳна.",
        lessonMakePlan = "Сохтани нақша",
        lessonPlanFinished = "Нақша иҷро шуд",
        lessonPlanFinishedBody = "Ҳамаи сураҳои нақша гузашта шуданд. Нақшаи навбатиро созед.",
        lessonNewPlan = "Нақшаи нав",
        myPlan = "Нақшаи ман",
        lessonMore = "Бештар",
        recent = "Охирин",
        pinned = "Пин шудааст",
        searchPlaceholder = "Сура, рақам ё 2:255",
        clearSearch = "Пок кардан",
        filterAll = "Ҳама",
        // The segment is a third of the screen: the full "Аз бар мекунам" would be cut off.
        filterLearning = "Дар ҳифз",
        filterLearned = "Аз бар шуд",
        smartOpen = "Кушодан",
        smartTitlePattern = "{name} · оят {n}",
        noResultsTitle = "Чизе ёфт нашуд",
        noResultsBody = "Номи сура, рақами он ё оятро чун 2:255 нависед.",
        emptyLearningTitle = "Ҳоло чизе аз бар намешавад",
        emptyLearningBody = "Сураро дароз пахш кунед, то «Аз бар мекунам» ишора шавад.",
        emptyLearnedTitle = "Ҳоло сураи аз бар шуда нест",
        emptyLearnedBody = "Вақте сураро аз бар кардед, онро ишора кунед — дар ин ҷо пайдо мешавад.",
        open = "Кушодан",
        loadErrorTitle = "Қуръон бор нашуд",
        loadErrorBody = "Пайвастро санҷед. Дар оғоз барои боргирии матн интернет лозим аст.",
        retry = "Такрор",
        sheetStatus = "Ҳолати сура",
        statusNotStarted = "Оғоз нашудааст",
        statusLearning = "Аз бар мекунам",
        statusLearned = "Аз бар шуд",
        sheetLearning = "Аз бар мекунам",
        sheetKnown = "Аз бар медонам",
        learningPercentPattern = "Аз бар мекунам, {n}% аз бар шуд",
        pin = "Пин кардан",
        unpin = "Пинро бардоштан",
        listenSurah = "Гӯш кардани сура",
        inPlan = "дар нақша",
        forgetBody = "Ҷадвали такрори ин оятҳо нест карда мешавад.",
        forgetConfirm = "Хориҷ кардан",
        cancel = "Бекор",
        play = "Пахш",
        stop = "Қатъ",
        openPlayer = "Кушодани плеер",
        audioError = "Пайваст нест · Такрор",
        playerSubPattern = "Оят {a} · {s}",
        learnedToastPattern = "{name} — аз бар шуд",
        learningToastPattern = "{name} — дар нақша",
        statusUpdateFailed = "Ҳолати сура тағйир наёфт",
    )

    AppLanguage.UZ -> HomeCopy(
        language = language,
        greeting = "Assalomu alaykum",
        title = "Qur'on",
        settings = "Sozlamalar",
        progress = "Natija",
        progressPattern = "Natija: {n}%",
        continueLabel = "Davom etish",
        startWithFatiha = "Fotiha surasidan boshlang",
        read = "O‘qish",
        listen = "Tinglash",
        pause = "Pauza",
        ayahOfPattern = "Oyat {a} / {b}",
        lessonTitle = "Kunlik dars",
        lessonNewPattern = "Yangi: {range}",
        lessonRecentPattern = "Yaqinda: {passages}",
        lessonRecentProgressPattern = "Yaqinda: {b} tadan {a} tasi",
        lessonOldPattern = "Eski: {range}",
        lessonMinutesPattern = "~{n} daq",
        lessonMinutesLeftPattern = "yana ~{n} daq",
        lessonStart = "Darsni boshlash",
        lessonContinue = "Darsni davom ettirish",
        lessonDone = "Dars bajarildi",
        lessonTomorrowPattern = "Ertaga: {what}",
        lessonTomorrowDuePattern = "takrorlash: {n}",
        lessonTomorrowNothing = "Ertaga: takrorlash yo‘q",
        lessonCatchingUp = "Yetib olyapmiz: darsning bir qismi keyingi kunlarga qoldi",
        lessonInviteTitle = "Reja tuzing",
        lessonInviteBody = "Har kuni tayyor dars bo‘ladi: yangi, yaqinda va eski.",
        lessonMakePlan = "Reja tuzish",
        lessonPlanFinished = "Reja bajarildi",
        lessonPlanFinishedBody = "Rejadagi barcha suralar o‘tildi. Keyingisini tuzing.",
        lessonNewPlan = "Yangi reja",
        myPlan = "Mening rejam",
        lessonMore = "Yana",
        recent = "So‘nggilar",
        pinned = "Mahkamlangan",
        searchPlaceholder = "Sura, raqam yoki 2:255",
        clearSearch = "Tozalash",
        filterAll = "Barchasi",
        filterLearning = "Yodlayapman",
        filterLearned = "Yodlangan",
        smartOpen = "Ochish",
        smartTitlePattern = "{name} · {n}-oyat",
        noResultsTitle = "Hech narsa topilmadi",
        noResultsBody = "Sura nomi, raqami yoki 2:255 kabi oyatni kiriting.",
        emptyLearningTitle = "Hozircha yodlanayotgan sura yo‘q",
        emptyLearningBody = "Surani bosib turing va «Yodlayapman» deb belgilang.",
        emptyLearnedTitle = "Yodlangan suralar hali yo‘q",
        emptyLearnedBody = "Surani yodlab bo‘lgach, uni belgilang — u shu yerda paydo bo‘ladi.",
        open = "Ochish",
        loadErrorTitle = "Qur'on yuklanmadi",
        loadErrorBody = "Internetni tekshiring. Birinchi ishga tushirishda matnni yuklab olish uchun u kerak.",
        retry = "Qayta urinish",
        sheetStatus = "Sura holati",
        statusNotStarted = "Boshlanmagan",
        statusLearning = "Yodlayapman",
        statusLearned = "Yodlangan",
        sheetLearning = "Yodlayapman",
        sheetKnown = "Yoddan bilaman",
        learningPercentPattern = "Yodlayapman, {n}% yodlangan",
        pin = "Mahkamlash",
        unpin = "Mahkamlashni olib tashlash",
        listenSurah = "Surani tinglash",
        inPlan = "rejada",
        forgetBody = "Bu oyatlarning takrorlash jadvali o‘chiriladi.",
        forgetConfirm = "Olib tashlash",
        cancel = "Bekor qilish",
        play = "Ijro etish",
        stop = "To‘xtatish",
        openPlayer = "Pleyerni ochish",
        audioError = "Aloqa yo‘q · Qayta urinish",
        playerSubPattern = "{a}-oyat · {s}",
        learnedToastPattern = "{name} — yodlangan",
        learningToastPattern = "{name} — rejada",
        statusUpdateFailed = "Sura holatini o‘zgartirib bo‘lmadi",
    )

    AppLanguage.TR -> HomeCopy(
        language = language,
        greeting = "Selamün aleyküm",
        title = "Kur'an",
        settings = "Ayarlar",
        progress = "İlerleme",
        progressPattern = "İlerleme: {n}%",
        continueLabel = "Devam et",
        startWithFatiha = "Fâtiha Suresi ile başlayın",
        read = "Oku",
        listen = "Dinle",
        pause = "Duraklat",
        ayahOfPattern = "Ayet {a} / {b}",
        lessonTitle = "Günün dersi",
        lessonNewPattern = "Yeni: {range}",
        lessonRecentPattern = "Yakın zamanda: {passages}",
        lessonRecentProgressPattern = "Yakın zamanda: {a} / {b}",
        lessonOldPattern = "Eski: {range}",
        lessonMinutesPattern = "~{n} dk",
        lessonMinutesLeftPattern = "~{n} dk kaldı",
        lessonStart = "Derse başla",
        lessonContinue = "Derse devam et",
        lessonDone = "Ders tamamlandı",
        lessonTomorrowPattern = "Yarın: {what}",
        lessonTomorrowDuePattern = "tekrar: {n}",
        lessonTomorrowNothing = "Yarın: tekrar yok",
        lessonCatchingUp = "Yetişiyoruz: dersin bir kısmı sonraki günlere kaldı",
        lessonInviteTitle = "Bir plan yapın",
        lessonInviteBody = "Her gün hazır bir ders olur: yeni, yakın zamanda ve eski.",
        lessonMakePlan = "Plan yap",
        lessonPlanFinished = "Plan tamamlandı",
        lessonPlanFinishedBody = "Plandaki tüm sureler geçildi. Sıradaki planı yapın.",
        lessonNewPlan = "Yeni plan",
        myPlan = "Planım",
        lessonMore = "Daha fazla",
        recent = "Son açılanlar",
        pinned = "Sabitlendi",
        searchPlaceholder = "Sure, numara veya 2:255",
        clearSearch = "Temizle",
        filterAll = "Tümü",
        filterLearning = "Ezberliyorum",
        filterLearned = "Ezberlendi",
        smartOpen = "Aç",
        smartTitlePattern = "{name} · ayet {n}",
        noResultsTitle = "Sonuç bulunamadı",
        noResultsBody = "Sure adını, numarasını veya 2:255 gibi bir ayeti yazın.",
        emptyLearningTitle = "Henüz ezberlenen sure yok",
        emptyLearningBody = "Bir sureye basılı tutup “Ezberliyorum” olarak işaretleyin.",
        emptyLearnedTitle = "Henüz ezberlenmiş sure yok",
        emptyLearnedBody = "Bir sureyi ezberleyince işaretleyin — burada görünür.",
        open = "Aç",
        loadErrorTitle = "Kur'an yüklenemedi",
        loadErrorBody = "Bağlantınızı kontrol edin. İlk açılışta metni indirmek için internet gerekir.",
        retry = "Tekrar dene",
        sheetStatus = "Sure durumu",
        statusNotStarted = "Başlanmadı",
        statusLearning = "Ezberliyorum",
        statusLearned = "Ezberlendi",
        sheetLearning = "Ezberliyorum",
        sheetKnown = "Ezbere biliyorum",
        learningPercentPattern = "Ezberliyorum, %{n} ezberlendi",
        pin = "Sabitle",
        unpin = "Sabitlemeyi kaldır",
        listenSurah = "Sureyi dinle",
        inPlan = "planda",
        forgetBody = "Bu ayetlerin tekrar programı silinecek.",
        forgetConfirm = "Kaldır",
        cancel = "İptal",
        play = "Oynat",
        stop = "Durdur",
        openPlayer = "Oynatıcıyı aç",
        audioError = "Bağlantı yok · Tekrar dene",
        playerSubPattern = "Ayet {a} · {s}",
        learnedToastPattern = "{name} — ezberlendi",
        learningToastPattern = "{name} — planda",
        statusUpdateFailed = "Sure durumu değiştirilemedi",
    )
}
