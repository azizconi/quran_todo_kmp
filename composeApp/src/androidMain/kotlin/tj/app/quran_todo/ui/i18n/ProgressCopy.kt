package tj.app.quran_todo.ui.i18n

import androidx.compose.runtime.Immutable
import tj.app.quran_todo.common.i18n.AppLanguage
import tj.app.quran_todo.hifz.model.HifzScope
import tj.app.quran_todo.ui.progress.PlanLineUi
import tj.app.quran_todo.ui.progress.PlanStatus
import tj.app.quran_todo.ui.progress.TodayUi

/**
 * Strings of the progress screen in the five app languages (wording follows the glossary of
 * docs/redesign/ANDROID_REDESIGN.md, section 10). Texts with numbers or names are patterns with
 * `{p}`, `{a}`, `{b}`, `{n}`, `{d}`, `{m}`, `{scope}`, `{date}`, `{ayahs}` and `{name}` placeholders, filled by the functions
 * below, so each language keeps its own word order and plural forms. The names of the plan presets, the dates and the plural
 * forms of "ayahs", "surahs" and "days" come from the other copies ([planCopy], [ayahCountLabel], [surahCountLabel]).
 *
 * The Tajik, Uzbek and Turkish wording of the new D2 strings (plan line, tiles, juz, report) should be checked by a native
 * speaker.
 */
@Immutable
data class ProgressCopy(
    val language: AppLanguage,

    // Top bar and the ring
    val title: String,
    val back: String,
    /** Spoken by accessibility services for the big ring: `{p}` percent, `{a}` memorized, `{b}` all ayahs. */
    val ringDescriptionPattern: String,
    /** "I know by heart: 3 surahs": `{n}` is "3 surahs". */
    val knownPattern: String,
    val learningPattern: String,
    /** Turkish writes the sign in front of the number ("%12"). */
    val percentFirst: Boolean,

    // Plan
    val planTitle: String,
    val planNoneTitle: String,
    val planNoneBody: String,
    val planCreate: String,
    val planFinishPattern: String,
    val planFinishedPattern: String,
    val planBehindPattern: String,
    val planAhead: String,
    val planOnTrack: String,
    val planNeedPacePattern: String,

    /** The ring's line when it is measured by the plan: `{scope}` plan name, `{a}` covered, `{b}` plan ayahs, `{p}` percent. */
    val planRingPattern: String,
    /** The line for the whole Quran under it: `{a}` covered, `{b}` all ayahs, `{l}` learned. */
    val wholeQuranPattern: String,

    // Tiles
    val tileStreak: String,
    val tileToday: String,
    val lessonDone: String,
    /** "2 of 5 left"; the numbers are tied to their words with no-break spaces, so a narrow tile wraps before "2 of 5", not inside it. */
    val lessonLeftPattern: String,
    val lessonNone: String,
    val tileRetention: String,
    val tileHard: String,

    // Activity
    val activityTitle: String,
    val activityEmpty: String,
    val activityNew: String,
    val activityReviewed: String,
    val activityNone: String,
    val today: String,
    val months: List<String>,
    val datePattern: String,

    // Quran map
    val mapTitle: String,
    val mapExpand: String,
    val mapCollapse: String,
    val mapHint: String,
    val statusNotStarted: String,
    val statusLearning: String,
    val statusLearned: String,
    val ayahsOfPattern: String,
    val open: String,

    // Juz
    val juzTitle: String,
    val juzHint: String,
    val juzPattern: String,

    // Hard spots
    val hardTitle: String,
    val hardNoneTitle: String,
    val hardNoneBody: String,
    val hardMorePattern: String,
    val hardLess: String,
    val hardAyahDescriptionPattern: String,

    // The report for the teacher: the entry points and the sheet
    val reportAction: String,
    val reportCardBody: String,
    /** One short line of the row that opens the report. */
    val reportRowBody: String,
    val reportShare: String,
    val sheetBody: String,
    val periodWeek: String,
    val periodMonth: String,
    val withImage: String,
    val withImageHint: String,
    val shareFailed: String,
    val chooserTitle: String,

    // Nothing learned yet
    val emptyTitle: String,
    val emptyBody: String,
    val emptyAction: String,
) {
    /** Spoken form of the big ring; [scope] is the name of the plan when the ring is measured by it. */
    fun ringDescription(percent: Int, covered: Int, total: Int, learned: Int = covered, scope: String? = null): String =
        (scope?.let { "$it: " } ?: "") + ringDescriptionPattern.fill("p" to percent, "a" to covered, "b" to total, "l" to learned)

    /** "Juz Amma: 10 of 564 · 2 %": the ring measured by the plan. */
    fun planRingLine(scope: HifzScope, covered: Int, total: Int, percent: Int): String =
        planRingPattern.fill("scope" to scopeName(scope), "a" to covered, "b" to total, "p" to percent)

    /** "Whole Quran: 17 of 6236 · memorized 13". */
    fun wholeQuranLine(covered: Int, total: Int, learned: Int): String =
        wholeQuranPattern.fill("a" to covered, "b" to total, "l" to learned)

    /** The line under the ring: "Gone through: 17 ayahs · memorized: 13" (the ring and its percentage count the first number). */
    fun heroAyahs(covered: Int, learned: Int): String = when (language) {
        AppLanguage.RU -> "Пройдено: ${ayahCountLabel(covered, language)} · выучено: $learned"
        AppLanguage.EN -> "Covered: ${ayahCountLabel(covered, language)} · memorized: $learned"
        AppLanguage.TG -> "Гузашта: ${ayahCountLabel(covered, language)} · аз бар шуда: $learned"
        AppLanguage.UZ -> "O‘tilgan: ${ayahCountLabel(covered, language)} · yodlangan: $learned"
        AppLanguage.TR -> "Geçilen: ${ayahCountLabel(covered, language)} · ezberlenen: $learned"
    }

    /** "412 of 6236 ayahs" (also "3 of 40 ayahs" in a map bubble). */
    fun ayahsOf(learned: Int, total: Int): String = ayahsOfPattern.fill("a" to learned, "b" to total)

    /**
     * "I know by heart: 3 surahs · Learning: 5 surahs". A part is left out while it is zero ("0 surahs" would only discourage);
     * null when both are.
     */
    fun surahsLine(learnedSurahs: Int, learningSurahs: Int): String? = buildList {
        if (learnedSurahs > 0) add(knownPattern.fill("n" to surahCountLabel(learnedSurahs, language)))
        if (learningSurahs > 0) add(learningPattern.fill("n" to surahCountLabel(learningSurahs, language)))
    }.takeIf { it.isNotEmpty() }?.joinToString(" · ")

    /** "42%" ("%42" in Turkish). */
    fun percentText(percent: Int): String = if (percentFirst) "%$percent" else "$percent%"

    /** The value of the retention tile: the percentage, or a dash while there is nothing to measure. */
    fun retentionValue(percent: Int?): String = if (percent == null) "—" else percentText(percent)

    /** Spoken form of the retention tile. */
    fun retentionDescription(percent: Int?, repetitions: Int): String =
        if (percent == null) {
            "$tileRetention: —"
        } else {
            "$tileRetention: ${percentText(percent)} · ${lessonCopy(language).repetitions(repetitions)}"
        }

    /** The value of the "today" tile: "Lesson done", "2 of 5 left" or "No lesson". */
    fun todayText(today: TodayUi): String = when (today) {
        TodayUi.None -> lessonNone
        TodayUi.Done -> lessonDone
        is TodayUi.Left -> lessonLeftPattern.fill("a" to today.left, "b" to today.total)
    }

    /** "28 Oct" / "28 окт". [month] is 1..12. */
    fun date(day: Int, month: Int): String =
        datePattern.fill("d" to day, "m" to months[(month - 1).mod(12)])

    /** "5 new · 12 reviewed" for a day, "no activity" for an empty one. */
    fun activityValue(learned: Int, reviewed: Int): String = when {
        learned <= 0 && reviewed <= 0 -> activityNone
        learned <= 0 -> "$activityReviewed ${reviewed}"
        reviewed <= 0 -> "$activityNew ${learned}"
        else -> "$activityNew ${learned} · $activityReviewed ${reviewed}"
    }

    /** The trailing text of the activity header: "New 14 · Reviewed 63", or nothing for an empty month. */
    fun activitySummary(learned: Int, reviewed: Int): String? =
        if (learned <= 0 && reviewed <= 0) null else activityValue(learned, reviewed)

    fun juzName(juz: Int): String = juzPattern.fill("n" to juz)

    /** Spoken form of a juz row: "Juz 30, 37%". */
    fun juzDescription(juz: Int, percent: Int): String = "${juzName(juz)}, ${percentText(percent)}"

    fun hardMore(n: Int): String = hardMorePattern.fill("n" to n)

    fun hardCount(n: Int): String = hardCountLabel(n, language)

    fun hardAyahDescription(name: String, ayah: Int): String =
        hardAyahDescriptionPattern.fill("name" to name, "n" to ayah)

    /** The name of the plan preset: "Juz Amma", "My surahs"... */
    fun scopeName(scope: HifzScope): String = planCopy(language).let { plan ->
        when (scope) {
            HifzScope.JUZ_30 -> plan.scopeJuz30
            HifzScope.JUZ_29 -> plan.scopeJuz29
            HifzScope.FROM_START -> plan.scopeFromStart
            HifzScope.CUSTOM -> plan.scopeCustom
        }
    }

    /**
     * The headline of the plan card: "Juz Amma: you will finish ~16 February"; just the name of the plan when no finish day is
     * known; null without a plan. [today] decides whether the date gets a year.
     */
    fun planHeadline(line: PlanLineUi, today: Int): String? {
        val scope = line.scope?.let(::scopeName) ?: return null
        val finish = line.finishEpochDay
        return when {
            line.status == PlanStatus.NoPlan -> null
            line.status == PlanStatus.Finished -> planFinishedPattern.fill("scope" to scope)
            finish == null -> scope
            else -> planFinishPattern.fill("scope" to scope, "date" to planCopy(language).dateLabel(finish, today))
        }
    }

    /** The second line of the plan card: behind, ahead, on schedule or too slow for the date; null when there is nothing to add. */
    fun planStatusLine(line: PlanLineUi): String? = when (line.status) {
        PlanStatus.Behind -> planBehindPattern.fill("ayahs" to ayahCountLabel(line.ayahs, language))
        PlanStatus.NeedPace -> planNeedPacePattern.fill("ayahs" to ayahCountLabel(line.ayahs, language))
        PlanStatus.Ahead -> planAhead
        PlanStatus.OnTrack -> planOnTrack
        PlanStatus.NoPlan, PlanStatus.Finished, PlanStatus.Finish -> null
    }
}

private fun String.fill(vararg values: Pair<String, Any>): String {
    var result = this
    values.forEach { (key, value) -> result = result.replace("{$key}", value.toString()) }
    return result
}

/** "3 hard"; Russian 1 трудный / 2 трудных. */
fun hardCountLabel(n: Int, language: AppLanguage): String = when (language) {
    AppLanguage.RU -> "$n ${if (n % 10 == 1 && n % 100 != 11) "трудный" else "трудных"}"
    AppLanguage.EN -> "$n hard"
    AppLanguage.TG -> "$n душвор"
    AppLanguage.UZ -> "$n ta qiyin"
    AppLanguage.TR -> "$n zor"
}

fun progressCopy(language: AppLanguage): ProgressCopy = when (language) {
    AppLanguage.RU -> ProgressCopy(
        language = language,
        title = "Прогресс",
        back = "Назад",
        ringDescriptionPattern = "{p}%: пройдено {a} из {b} аятов, выучено {l}",
        knownPattern = "Знаю наизусть: {n}",
        learningPattern = "Учу: {n}",
        percentFirst = false,
        planTitle = "План",
        planNoneTitle = "Плана пока нет",
        planNoneBody = "Составьте план — здесь появится прогноз окончания",
        planCreate = "Составить план",
        planFinishPattern = "{scope}: закончите ~{date}",
        planFinishedPattern = "{scope}: все суры пройдены",
        planBehindPattern = "Отстаёте на {ayahs}",
        planAhead = "Идёте с опережением",
        planOnTrack = "Идёте по графику",
        planNeedPacePattern = "Чтобы успеть к сроку, нужно {ayahs} в день",
        planRingPattern = "{scope}: {a} из {b} · {p} %",
        wholeQuranPattern = "Весь Коран: {a} из {b} · выучено {l}",
        tileStreak = "Дней подряд",
        tileToday = "Сегодня",
        lessonDone = "Урок выполнен",
        lessonLeftPattern = "Осталось {a}\u00A0из\u00A0{b}",
        lessonNone = "Урока нет",
        tileRetention = "Удержание за 30 дней",
        tileHard = "Трудных аятов сейчас",
        activityTitle = "Неделя занятий",
        activityEmpty = "Выучите отрывок или повторите — здесь вырастут столбики.",
        activityNew = "Новое",
        activityReviewed = "Повторено",
        activityNone = "без занятий",
        today = "Сегодня",
        months = listOf("янв", "фев", "мар", "апр", "мая", "июн", "июл", "авг", "сен", "окт", "ноя", "дек"),
        datePattern = "{d} {m}",
        mapTitle = "Карта Корана",
        mapExpand = "Показать карту",
        mapCollapse = "Скрыть карту",
        mapHint = "Нажмите на ячейку, чтобы увидеть суру",
        statusNotStarted = "Не начал",
        statusLearning = "Заучиваю",
        statusLearned = "Выучено",
        ayahsOfPattern = "{a} из {b} аятов",
        open = "Открыть",
        juzTitle = "Джузы",
        juzHint = "Нажмите на джуз — ридер откроется на его начале",
        juzPattern = "Джуз {n}",
        hardTitle = "Трудные места",
        hardNoneTitle = "Трудных аятов нет",
        hardNoneBody = "Отметки «Трудно» появятся здесь после повторений.",
        hardMorePattern = "Ещё {n}",
        hardLess = "Свернуть",
        hardAyahDescriptionPattern = "{name}, аят {n}",
        reportAction = "Отчёт для устаза",
        reportCardBody = "Что выучено и повторено за неделю или месяц, трудные аяты и серия — картинкой и текстом для Telegram или WhatsApp.",
        reportRowBody = "За неделю или месяц: картинкой, PDF или текстом",
        reportShare = "Поделиться отчётом",
        sheetBody = "За какой период?",
        periodWeek = "За неделю",
        periodMonth = "За месяц",
        withImage = "С картинкой",
        withImageHint = "Картинка-карточка и текст вместе",
        shareFailed = "Не удалось подготовить отчёт",
        chooserTitle = "Отправить отчёт",
        emptyTitle = "Здесь появится ваш прогресс",
        emptyBody = "Выучите первый отрывок в уроке или отметьте суру «Знаю наизусть» — и карта Корана оживёт.",
        emptyAction = "Составить план",
    )

    AppLanguage.EN -> ProgressCopy(
        language = language,
        title = "Progress",
        back = "Back",
        ringDescriptionPattern = "{p}%: {a} of {b} ayahs covered, {l} memorized",
        knownPattern = "Know by heart: {n}",
        learningPattern = "Learning: {n}",
        percentFirst = false,
        planTitle = "Plan",
        planNoneTitle = "No plan yet",
        planNoneBody = "Make a plan and the finish forecast will show up here",
        planCreate = "Make a plan",
        planFinishPattern = "{scope}: you will finish ~{date}",
        planFinishedPattern = "{scope}: all surahs covered",
        planBehindPattern = "You are {ayahs} behind",
        planAhead = "You are ahead of schedule",
        planOnTrack = "You are on schedule",
        planNeedPacePattern = "To make the date you need {ayahs} a day",
        planRingPattern = "{scope}: {a} of {b} · {p}%",
        wholeQuranPattern = "Whole Quran: {a} of {b} · memorized {l}",
        tileStreak = "Day streak",
        tileToday = "Today",
        lessonDone = "Lesson done",
        lessonLeftPattern = "{a}\u00A0of\u00A0{b} left",
        lessonNone = "No lesson",
        tileRetention = "Retention, 30 days",
        tileHard = "Hard ayahs now",
        activityTitle = "This week",
        activityEmpty = "Learn a passage or review one and the bars will grow here.",
        activityNew = "New",
        activityReviewed = "Reviewed",
        activityNone = "no activity",
        today = "Today",
        months = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"),
        datePattern = "{m} {d}",
        mapTitle = "Quran map",
        mapExpand = "Show the map",
        mapCollapse = "Hide the map",
        mapHint = "Tap a cell to see the surah",
        statusNotStarted = "Not started",
        statusLearning = "Memorizing",
        statusLearned = "Memorized",
        ayahsOfPattern = "{a} of {b} ayahs",
        open = "Open",
        juzTitle = "Juz",
        juzHint = "Tap a juz to open the reader at its start",
        juzPattern = "Juz {n}",
        hardTitle = "Hard spots",
        hardNoneTitle = "No hard ayahs",
        hardNoneBody = "Ayahs marked “Hard” will show up here after you review.",
        hardMorePattern = "{n} more",
        hardLess = "Show less",
        hardAyahDescriptionPattern = "{name}, ayah {n}",
        reportAction = "Report for your teacher",
        reportCardBody = "What you learned and reviewed over a week or a month, hard ayahs and your streak: a picture and a text for Telegram or WhatsApp.",
        reportRowBody = "A week or a month, as a picture, PDF or text",
        reportShare = "Share the report",
        sheetBody = "Which period?",
        periodWeek = "Past week",
        periodMonth = "Past month",
        withImage = "With a picture",
        withImageHint = "A card image together with the text",
        shareFailed = "Couldn’t prepare the report",
        chooserTitle = "Send the report",
        emptyTitle = "Your progress will appear here",
        emptyBody = "Learn your first passage in the lesson, or mark a surah as “Know by heart”, and the Quran map will come to life.",
        emptyAction = "Make a plan",
    )

    AppLanguage.TG -> ProgressCopy(
        language = language,
        title = "Пешрафт",
        back = "Бозгашт",
        ringDescriptionPattern = "{p}%: {a} аз {b} оят гузашт, {l} аз бар шуд",
        knownPattern = "Аз бар медонам: {n}",
        learningPattern = "Аз бар мекунам: {n}",
        percentFirst = false,
        planTitle = "Нақша",
        planNoneTitle = "Нақша ҳоло нест",
        planNoneBody = "Нақша тартиб диҳед — ин ҷо пешбинии анҷом пайдо мешавад",
        planCreate = "Тартиб додани нақша",
        planFinishPattern = "{scope}: анҷом медиҳед ~{date}",
        planFinishedPattern = "{scope}: ҳамаи сураҳо гузашта шуданд",
        planBehindPattern = "{ayahs} ақиб мондаед",
        planAhead = "Аз ҷадвал пеш мераведед",
        planOnTrack = "Мувофиқи ҷадвал пеш мераведед",
        planNeedPacePattern = "Барои ба мӯҳлат расидан дар як рӯз {ayahs} лозим аст",
        planRingPattern = "{scope}: {a} аз {b} · {p}%",
        wholeQuranPattern = "Тамоми Қуръон: {a} аз {b} · аз бар шуд {l}",
        tileStreak = "Рӯзи пайваста",
        tileToday = "Имрӯз",
        lessonDone = "Дарс иҷро шуд",
        lessonLeftPattern = "Монд: {a}\u00A0аз\u00A0{b}",
        lessonNone = "Дарс нест",
        tileRetention = "Нигоҳдорӣ дар 30 рӯз",
        tileHard = "Оятҳои душвор ҳоло",
        activityTitle = "Ҳафтаи машғулият",
        activityEmpty = "Порчаеро аз бар кунед ё такрор намоед — ин ҷо сутунҳо меафзоянд.",
        activityNew = "Нав",
        activityReviewed = "Такрор шуд",
        activityNone = "бе машғулият",
        today = "Имрӯз",
        months = listOf("янв", "фев", "мар", "апр", "май", "июн", "июл", "авг", "сен", "окт", "ноя", "дек"),
        datePattern = "{d} {m}",
        mapTitle = "Харитаи Қуръон",
        mapExpand = "Нишон додани харита",
        mapCollapse = "Пинҳон кардани харита",
        mapHint = "Ба ҳуҷайра зер кунед, то сура намоён шавад",
        statusNotStarted = "Оғоз нашудааст",
        statusLearning = "Аз бар мекунам",
        statusLearned = "Аз бар шуд",
        ayahsOfPattern = "{a} аз {b} оят",
        open = "Кушодан",
        juzTitle = "Ҷузъҳо",
        juzHint = "Ба ҷузъ зер кунед — хонанда аз аввали он кушода мешавад",
        juzPattern = "Ҷузъи {n}",
        hardTitle = "Ҷойҳои душвор",
        hardNoneTitle = "Оятҳои душвор нест",
        hardNoneBody = "Қайдҳои «Душвор» баъди такрор дар ин ҷо пайдо мешаванд.",
        hardMorePattern = "Боз {n}",
        hardLess = "Ҷамъ кардан",
        hardAyahDescriptionPattern = "{name}, оят {n}",
        reportAction = "Ҳисобот барои устод",
        reportCardBody = "Чӣ аз бар шуд ва такрор шуд дар як ҳафта ё як моҳ, оятҳои душвор ва силсила — ҳамчун тасвир ва матн барои Telegram ё WhatsApp.",
        reportRowBody = "Як ҳафта ё як моҳ: тасвир, PDF ё матн",
        reportShare = "Фиристодани ҳисобот",
        sheetBody = "Барои кадом давра?",
        periodWeek = "Барои ҳафта",
        periodMonth = "Барои моҳ",
        withImage = "Бо тасвир",
        withImageHint = "Тасвири корт бо матн якҷоя",
        shareFailed = "Ҳисоботро тайёр кардан нашуд",
        chooserTitle = "Фиристодани ҳисобот",
        emptyTitle = "Пешрафти шумо дар ин ҷо пайдо мешавад",
        emptyBody = "Нахустин порчаро дар дарс аз бар кунед ё сураро «Аз бар медонам» ишора намоед — ва харитаи Қуръон зинда мешавад.",
        emptyAction = "Тартиб додани нақша",
    )

    AppLanguage.UZ -> ProgressCopy(
        language = language,
        title = "Natija",
        back = "Orqaga",
        ringDescriptionPattern = "{p}%: {b} ta oyatdan {a} tasi o‘tildi, {l} tasi yodlandi",
        knownPattern = "Yoddan bilaman: {n}",
        learningPattern = "Yodlayapman: {n}",
        percentFirst = false,
        planTitle = "Reja",
        planNoneTitle = "Reja hali yo‘q",
        planNoneBody = "Reja tuzing — shu yerda tugash prognozi ko‘rinadi",
        planCreate = "Reja tuzish",
        planFinishPattern = "{scope}: tugatasiz ~{date}",
        planFinishedPattern = "{scope}: barcha suralar o‘tildi",
        planBehindPattern = "{ayahs} orqadasiz",
        planAhead = "Jadvaldan oldindasiz",
        planOnTrack = "Jadval bo‘yicha ketyapsiz",
        planNeedPacePattern = "Muddatga ulgurish uchun kuniga {ayahs} kerak",
        planRingPattern = "{scope}: {a} / {b} · {p}%",
        wholeQuranPattern = "Butun Qur‘on: {a} / {b} · yodlangan {l}",
        tileStreak = "Ketma-ket kun",
        tileToday = "Bugun",
        lessonDone = "Dars bajarildi",
        lessonLeftPattern = "Qoldi: {b}\u00A0tadan\u00A0{a}\u00A0tasi",
        lessonNone = "Dars yo‘q",
        tileRetention = "30 kunda eslab qolish",
        tileHard = "Hozirgi qiyin oyatlar",
        activityTitle = "Shu hafta",
        activityEmpty = "Parcha yodlang yoki takrorlang — ustunlar shu yerda o‘sadi.",
        activityNew = "Yangi",
        activityReviewed = "Takrorlandi",
        activityNone = "mashg‘ulot yo‘q",
        today = "Bugun",
        months = listOf("yan", "fev", "mar", "apr", "may", "iyn", "iyl", "avg", "sen", "okt", "noy", "dek"),
        datePattern = "{d} {m}",
        mapTitle = "Qur'on xaritasi",
        mapExpand = "Xaritani ko‘rsatish",
        mapCollapse = "Xaritani yashirish",
        mapHint = "Surani ko‘rish uchun katakchani bosing",
        statusNotStarted = "Boshlanmagan",
        statusLearning = "Yodlayapman",
        statusLearned = "Yodlangan",
        ayahsOfPattern = "{b} ta oyatdan {a} tasi",
        open = "Ochish",
        juzTitle = "Juzlar",
        juzHint = "Juzni bosing — o‘quvchi uning boshidan ochiladi",
        juzPattern = "{n}-juz",
        hardTitle = "Qiyin joylar",
        hardNoneTitle = "Qiyin oyatlar yo‘q",
        hardNoneBody = "«Qiyin» belgilari takrorlashdan keyin shu yerda paydo bo‘ladi.",
        hardMorePattern = "Yana {n} ta",
        hardLess = "Yig‘ish",
        hardAyahDescriptionPattern = "{name}, {n}-oyat",
        reportAction = "Ustoz uchun hisobot",
        reportCardBody = "Hafta yoki oy davomida nima yodlangani va takrorlangani, qiyin oyatlar va ketma-ketlik — Telegram yoki WhatsApp uchun rasm va matn.",
        reportRowBody = "Hafta yoki oy: rasm, PDF yoki matn",
        reportShare = "Hisobotni ulashish",
        sheetBody = "Qaysi davr uchun?",
        periodWeek = "Hafta uchun",
        periodMonth = "Oy uchun",
        withImage = "Rasm bilan",
        withImageHint = "Kartochka rasmi matn bilan birga",
        shareFailed = "Hisobotni tayyorlab bo‘lmadi",
        chooserTitle = "Hisobotni yuborish",
        emptyTitle = "Natijangiz shu yerda ko‘rinadi",
        emptyBody = "Darsda birinchi parchani yodlang yoki surani «Yoddan bilaman» deb belgilang — Qur'on xaritasi jonlanadi.",
        emptyAction = "Reja tuzish",
    )

    AppLanguage.TR -> ProgressCopy(
        language = language,
        title = "İlerleme",
        back = "Geri",
        ringDescriptionPattern = "%{p}: {b} ayetten {a} ayet geçildi, {l} ayet ezberlendi",
        knownPattern = "Ezbere bildiğim: {n}",
        learningPattern = "Ezberliyorum: {n}",
        percentFirst = true,
        planTitle = "Plan",
        planNoneTitle = "Henüz plan yok",
        planNoneBody = "Bir plan yapın; bitiş tahmini burada görünsün",
        planCreate = "Plan yap",
        planFinishPattern = "{scope}: bitirirsiniz ~{date}",
        planFinishedPattern = "{scope}: tüm sureler geçildi",
        planBehindPattern = "{ayahs} geridesiniz",
        planAhead = "Programın önündesiniz",
        planOnTrack = "Programa uygun gidiyorsunuz",
        planNeedPacePattern = "Süreye yetişmek için günde {ayahs} gerekir",
        planRingPattern = "{scope}: {a} / {b} · %{p}",
        wholeQuranPattern = "Bütün Kur'an: {a} / {b} · ezberlenen {l}",
        tileStreak = "Ardışık gün",
        tileToday = "Bugün",
        lessonDone = "Ders tamamlandı",
        lessonLeftPattern = "Kalan {a}\u00A0/\u00A0{b}",
        lessonNone = "Ders yok",
        tileRetention = "30 günde akılda kalan",
        tileHard = "Şu an zor ayetler",
        activityTitle = "Bu hafta",
        activityEmpty = "Bir bölüm ezberleyin ya da tekrar edin; çubuklar burada büyüsün.",
        activityNew = "Yeni",
        activityReviewed = "Tekrar",
        activityNone = "çalışma yok",
        today = "Bugün",
        months = listOf("Oca", "Şub", "Mar", "Nis", "May", "Haz", "Tem", "Ağu", "Eyl", "Eki", "Kas", "Ara"),
        datePattern = "{d} {m}",
        mapTitle = "Kur'an haritası",
        mapExpand = "Haritayı göster",
        mapCollapse = "Haritayı gizle",
        mapHint = "Sureyi görmek için bir kareye dokunun",
        statusNotStarted = "Başlanmadı",
        statusLearning = "Ezberliyorum",
        statusLearned = "Ezberlendi",
        ayahsOfPattern = "{b} ayetten {a} ayet",
        open = "Aç",
        juzTitle = "Cüzler",
        juzHint = "Bir cüze dokunun; okuyucu başlangıcında açılır",
        juzPattern = "{n}. cüz",
        hardTitle = "Zor yerler",
        hardNoneTitle = "Zor ayet yok",
        hardNoneBody = "“Zor” işaretleri tekrardan sonra burada görünür.",
        hardMorePattern = "{n} tane daha",
        hardLess = "Daralt",
        hardAyahDescriptionPattern = "{name}, {n}. ayet",
        reportAction = "Hoca için rapor",
        reportCardBody = "Bir hafta ya da ayda ezberlenenler ve tekrar edilenler, zor ayetler ve seri: Telegram veya WhatsApp için görsel ve metin.",
        reportRowBody = "Bir hafta ya da ay: görsel, PDF veya metin",
        reportShare = "Raporu paylaş",
        sheetBody = "Hangi dönem?",
        periodWeek = "Haftalık",
        periodMonth = "Aylık",
        withImage = "Görselle birlikte",
        withImageHint = "Kart görseli ve metin birlikte",
        shareFailed = "Rapor hazırlanamadı",
        chooserTitle = "Raporu gönder",
        emptyTitle = "İlerlemeniz burada görünecek",
        emptyBody = "Derste ilk bölümü ezberleyin ya da bir sureyi “Ezbere biliyorum” olarak işaretleyin; Kur'an haritası canlanacak.",
        emptyAction = "Plan yap",
    )
}

// -- The report text -------------------------------------------------------------------------------

/**
 * The words of the report for the teacher (the text that goes to Telegram or WhatsApp and the card picture). The wording is
 * neutral ("expected finish", "behind schedule"): it is read by the teacher, not by the student. Patterns use `{a}`, `{b}`,
 * `{list}`, `{n}`, `{date}` and `{ayahs}`.
 *
 * The Tajik, Uzbek and Turkish wording should be checked by a native speaker.
 */
@Immutable
data class ReportCopy(
    val language: AppLanguage,
    val title: String,
    val totalPattern: String,
    val newLabel: String,
    val none: String,
    val reviewedLabel: String,
    val surahsPattern: String,
    val moreSurahsPattern: String,
    val retentionLabel: String,
    val hardLabel: String,
    val activeDaysLabel: String,
    val streakLabel: String,
    val planLabel: String,
    val finishPattern: String,
    val finishedText: String,
    val behindPattern: String,
    val aheadText: String,
    val onTrackText: String,
    val needPacePattern: String,
) {
    fun total(covered: Int, total: Int, learned: Int = covered): String = totalPattern.fill("a" to covered, "b" to total, "l" to learned)

    fun surahs(list: String): String = surahsPattern.fill("list" to list)

    fun moreSurahs(n: Int): String = moreSurahsPattern.fill("n" to n)

    fun finish(date: String): String = finishPattern.fill("date" to date)

    fun behind(ayahs: Int): String = behindPattern.fill("ayahs" to ayahCountLabel(ayahs, language))

    fun needPace(ayahs: Int): String = needPacePattern.fill("ayahs" to ayahCountLabel(ayahs, language))
}

fun reportCopy(language: AppLanguage): ReportCopy = when (language) {
    AppLanguage.RU -> ReportCopy(
        language = language,
        title = "Отчёт по хифзу",
        totalPattern = "Пройдено: {a} из {b} аятов · выучено: {l}",
        newLabel = "Пройдено новое",
        none = "нет",
        reviewedLabel = "Повторено",
        surahsPattern = "Суры: {list}",
        moreSurahsPattern = "и ещё {n}",
        retentionLabel = "Вспомнено без ошибок",
        hardLabel = "Трудные аяты",
        activeDaysLabel = "Дней с занятиями",
        streakLabel = "Серия",
        planLabel = "План",
        finishPattern = "Прогноз окончания: ~{date}",
        finishedText = "все суры плана пройдены",
        behindPattern = "Отставание от срока: {ayahs}",
        aheadText = "Опережает срок",
        onTrackText = "Идёт по графику",
        needPacePattern = "Для срока нужно {ayahs} в день",
    )

    AppLanguage.EN -> ReportCopy(
        language = language,
        title = "Hifz report",
        totalPattern = "Covered: {a} of {b} ayahs · memorized: {l}",
        newLabel = "Newly covered",
        none = "none",
        reviewedLabel = "Reviewed",
        surahsPattern = "Surahs: {list}",
        moreSurahsPattern = "and {n} more",
        retentionLabel = "Recalled without mistakes",
        hardLabel = "Hard ayahs",
        activeDaysLabel = "Days with practice",
        streakLabel = "Streak",
        planLabel = "Plan",
        finishPattern = "Expected finish: ~{date}",
        finishedText = "all surahs of the plan are covered",
        behindPattern = "Behind schedule: {ayahs}",
        aheadText = "Ahead of schedule",
        onTrackText = "On schedule",
        needPacePattern = "Needs {ayahs} a day to meet the date",
    )

    AppLanguage.TG -> ReportCopy(
        language = language,
        title = "Ҳисобот оид ба ҳифз",
        totalPattern = "Гузашта: {a} аз {b} оят · аз бар шуда: {l}",
        newLabel = "Нав гузашта",
        none = "нест",
        reviewedLabel = "Такрор шуд",
        surahsPattern = "Сураҳо: {list}",
        moreSurahsPattern = "ва боз {n}",
        retentionLabel = "Бе хато ба ёд омад",
        hardLabel = "Оятҳои душвор",
        activeDaysLabel = "Рӯзҳои машғулият",
        streakLabel = "Силсила",
        planLabel = "Нақша",
        finishPattern = "Пешбинии анҷом: ~{date}",
        finishedText = "ҳамаи сураҳои нақша гузашта шуданд",
        behindPattern = "Аз мӯҳлат ақиб: {ayahs}",
        aheadText = "Аз мӯҳлат пеш",
        onTrackText = "Мувофиқи ҷадвал",
        needPacePattern = "Барои мӯҳлат дар як рӯз {ayahs} лозим",
    )

    AppLanguage.UZ -> ReportCopy(
        language = language,
        title = "Hifz hisoboti",
        totalPattern = "O‘tilgan: {b} ta oyatdan {a} tasi · yodlangan: {l}",
        newLabel = "Yangi o‘tilgan",
        none = "yo‘q",
        reviewedLabel = "Takrorlangan",
        surahsPattern = "Suralar: {list}",
        moreSurahsPattern = "va yana {n} ta",
        retentionLabel = "Xatosiz esga tushgan",
        hardLabel = "Qiyin oyatlar",
        activeDaysLabel = "Mashg‘ulot kunlari",
        streakLabel = "Ketma-ketlik",
        planLabel = "Reja",
        finishPattern = "Taxminiy tugash: ~{date}",
        finishedText = "rejadagi barcha suralar o‘tildi",
        behindPattern = "Muddatdan orqada: {ayahs}",
        aheadText = "Muddatdan oldinda",
        onTrackText = "Jadval bo‘yicha",
        needPacePattern = "Muddat uchun kuniga {ayahs} kerak",
    )

    AppLanguage.TR -> ReportCopy(
        language = language,
        title = "Ezber raporu",
        totalPattern = "Geçilen: {b} ayetten {a} ayet · ezberlenen: {l}",
        newLabel = "Yeni geçilen",
        none = "yok",
        reviewedLabel = "Tekrar edilen",
        surahsPattern = "Sureler: {list}",
        moreSurahsPattern = "ve {n} tane daha",
        retentionLabel = "Hatasız hatırlanan",
        hardLabel = "Zor ayetler",
        activeDaysLabel = "Çalışılan gün",
        streakLabel = "Seri",
        planLabel = "Plan",
        finishPattern = "Tahmini bitiş: ~{date}",
        finishedText = "plandaki tüm sureler geçildi",
        behindPattern = "Süreden geride: {ayahs}",
        aheadText = "Süreden önde",
        onTrackText = "Programa uygun",
        needPacePattern = "Süre için günde {ayahs} gerekir",
    )
}
