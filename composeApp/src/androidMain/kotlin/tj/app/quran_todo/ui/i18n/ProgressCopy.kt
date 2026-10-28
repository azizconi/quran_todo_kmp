package tj.app.quran_todo.ui.i18n

import androidx.compose.runtime.Immutable
import tj.app.quran_todo.common.i18n.AppLanguage

/**
 * Strings of the progress screen in the five app languages (wording follows the glossary of
 * docs/redesign/ANDROID_REDESIGN.md, section 10). Texts with numbers or names are patterns with
 * `{p}`, `{a}`, `{b}`, `{n}`, `{d}`, `{m}` and `{name}` placeholders, filled by the functions below, so
 * each language keeps its own word order and plural forms.
 */
@Immutable
data class ProgressCopy(
    val language: AppLanguage,

    // Top bar and the ring
    val title: String,
    val back: String,
    /** Spoken by accessibility services for the big ring: `{p}` percent, `{a}` memorized, `{b}` all ayahs. */
    val ringDescriptionPattern: String,
    /** `{n}` is "3 surahs". */
    val learnedSurahsPattern: String,
    val learningSurahsPattern: String,
    /** Turkish writes the sign in front of the number ("%12"). */
    val percentFirst: Boolean,

    // Quran map
    val mapTitle: String,
    val mapHint: String,
    val statusNotStarted: String,
    val statusLearning: String,
    val statusLearned: String,
    val ayahsOfPattern: String,
    val open: String,

    // Tiles
    val tileStreak: String,
    val tileDue: String,
    val tileWeak: String,
    val tileLearning: String,

    // Activity
    val activityTitle: String,
    val activityEmpty: String,
    val activityNone: String,
    val today: String,
    val weekdays: List<String>,
    val months: List<String>,
    val datePattern: String,

    // Weak spots
    val weakTitle: String,
    val weakNoneTitle: String,
    val weakNoneBody: String,
    val weakRepeat: String,
    val weakRepeatDescriptionPattern: String,
    val weakMorePattern: String,
    val weakLess: String,

    // Nothing started yet
    val emptyTitle: String,
    val emptyBody: String,
    val emptyAction: String,
) {
    fun ringDescription(percent: Int, learned: Int, total: Int): String =
        ringDescriptionPattern.fill("p" to percent, "a" to learned, "b" to total)

    /**
     * "412 ayahs · 3 surahs memorized · 5 surahs in progress". The surah parts are left out while they
     * are zero: "0 surahs memorized" would only discourage.
     */
    fun summary(learnedAyahs: Int, learnedSurahs: Int, learningSurahs: Int): String = buildList {
        add(ayahCountLabel(learnedAyahs, language))
        if (learnedSurahs > 0) add(learnedSurahsPattern.fill("n" to surahCountLabel(learnedSurahs, language)))
        if (learningSurahs > 0) add(learningSurahsPattern.fill("n" to surahCountLabel(learningSurahs, language)))
    }.joinToString(" · ")

    fun ayahsOf(learned: Int, total: Int): String = ayahsOfPattern.fill("a" to learned, "b" to total)

    /** "Mo" .. "Su" for [dayIndex] 0 (Monday) .. 6 (Sunday). */
    fun weekday(dayIndex: Int): String = weekdays[dayIndex.mod(7)]

    /** "28 Oct" / "28 окт". [month] is 1..12. */
    fun date(day: Int, month: Int): String =
        datePattern.fill("d" to day, "m" to months[(month - 1).mod(12)])

    /** "5 ayahs" or "no marks" for a day without any. */
    fun activityValue(count: Int): String = if (count <= 0) activityNone else ayahCountLabel(count, language)

    fun weakRepeatDescription(name: String): String = weakRepeatDescriptionPattern.fill("name" to name)

    fun weakMore(n: Int): String = weakMorePattern.fill("n" to n)

    fun weakCount(n: Int): String = weakCountLabel(n, language)

    fun weakAyahs(ayahs: List<Int>): String = weakAyahsLabel(ayahs, language)
}

private fun String.fill(vararg values: Pair<String, Any>): String {
    var result = this
    values.forEach { (key, value) -> result = result.replace("{$key}", value.toString()) }
    return result
}

/** "3 weak"; Russian 1 слабый / 2 слабых. */
fun weakCountLabel(n: Int, language: AppLanguage): String = when (language) {
    AppLanguage.RU -> "$n ${if (n % 10 == 1 && n % 100 != 11) "слабый" else "слабых"}"
    AppLanguage.EN -> "$n weak"
    AppLanguage.TG -> "$n заиф"
    AppLanguage.UZ -> "$n ta zaif"
    AppLanguage.TR -> "$n zayıf"
}

/**
 * "ayahs 3, 7, 12" (or "ayah 5" for one). Shows at most [limit] numbers and ends with an ellipsis when
 * there are more. [ayahs] are numbers inside the surah, ascending.
 */
fun weakAyahsLabel(ayahs: List<Int>, language: AppLanguage, limit: Int = WeakAyahsShown): String {
    if (ayahs.isEmpty()) return ""
    val shown = ayahs.take(limit).joinToString(", ") + if (ayahs.size > limit) "…" else ""
    val single = ayahs.size == 1
    return when (language) {
        AppLanguage.RU -> (if (single) "аят " else "аяты ") + shown
        AppLanguage.EN -> (if (single) "ayah " else "ayahs ") + shown
        AppLanguage.TG -> (if (single) "оят " else "оятҳои ") + shown
        AppLanguage.UZ -> if (single) "$shown-oyat" else "oyatlar: $shown"
        AppLanguage.TR -> "ayet $shown"
    }
}

/** How many weak ayah numbers one row names before the ellipsis. */
const val WeakAyahsShown = 6

fun progressCopy(language: AppLanguage): ProgressCopy = when (language) {
    AppLanguage.RU -> ProgressCopy(
        language = language,
        title = "Прогресс",
        back = "Назад",
        ringDescriptionPattern = "{p}%: выучено {a} из {b} аятов",
        learnedSurahsPattern = "{n} выучено",
        learningSurahsPattern = "{n} в процессе",
        percentFirst = false,
        mapTitle = "Карта Корана",
        mapHint = "Нажмите на ячейку, чтобы увидеть суру",
        statusNotStarted = "Не начал",
        statusLearning = "Заучиваю",
        statusLearned = "Выучено",
        ayahsOfPattern = "{a} из {b} аятов",
        open = "Открыть",
        tileStreak = "Дней подряд",
        tileDue = "Повторить сегодня",
        tileWeak = "Слабых аятов",
        tileLearning = "Аятов в работе",
        activityTitle = "Активность за 14 дней",
        activityEmpty = "Отмечайте аяты при чтении — здесь вырастут столбики.",
        activityNone = "без отметок",
        today = "Сегодня",
        weekdays = listOf("Пн", "Вт", "Ср", "Чт", "Пт", "Сб", "Вс"),
        months = listOf("янв", "фев", "мар", "апр", "мая", "июн", "июл", "авг", "сен", "окт", "ноя", "дек"),
        datePattern = "{d} {m}",
        weakTitle = "Слабые места",
        weakNoneTitle = "Слабых аятов нет",
        weakNoneBody = "Всё, что вы повторяли, держится уверенно.",
        weakRepeat = "Повторить",
        weakRepeatDescriptionPattern = "Повторить слабые места: {name}",
        weakMorePattern = "Ещё {n}",
        weakLess = "Свернуть",
        emptyTitle = "Здесь появится ваш путь",
        emptyBody = "Отметьте суру как «Заучиваю» или отмечайте выученные аяты при чтении — и карта Корана оживёт.",
        emptyAction = "К сурам",
    )

    AppLanguage.EN -> ProgressCopy(
        language = language,
        title = "Progress",
        back = "Back",
        ringDescriptionPattern = "{p}%: {a} of {b} ayahs memorized",
        learnedSurahsPattern = "{n} memorized",
        learningSurahsPattern = "{n} in progress",
        percentFirst = false,
        mapTitle = "Quran map",
        mapHint = "Tap a cell to see the surah",
        statusNotStarted = "Not started",
        statusLearning = "Memorizing",
        statusLearned = "Memorized",
        ayahsOfPattern = "{a} of {b} ayahs",
        open = "Open",
        tileStreak = "Day streak",
        tileDue = "Review today",
        tileWeak = "Weak ayahs",
        tileLearning = "Ayahs in progress",
        activityTitle = "Last 14 days",
        activityEmpty = "Mark ayahs as you read and the bars will grow here.",
        activityNone = "no marks",
        today = "Today",
        weekdays = listOf("Mo", "Tu", "We", "Th", "Fr", "Sa", "Su"),
        months = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"),
        datePattern = "{m} {d}",
        weakTitle = "Weak spots",
        weakNoneTitle = "No weak ayahs",
        weakNoneBody = "Everything you reviewed is holding strong.",
        weakRepeat = "Review",
        weakRepeatDescriptionPattern = "Review weak spots: {name}",
        weakMorePattern = "{n} more",
        weakLess = "Show less",
        emptyTitle = "Your journey will appear here",
        emptyBody = "Mark a surah as “Memorizing”, or mark ayahs as you read, and the Quran map will come to life.",
        emptyAction = "To surahs",
    )

    AppLanguage.TG -> ProgressCopy(
        language = language,
        title = "Пешрафт",
        back = "Бозгашт",
        ringDescriptionPattern = "{p}%: {a} аз {b} оят аз бар шуд",
        learnedSurahsPattern = "{n} аз бар шуд",
        learningSurahsPattern = "{n} дар раванд",
        percentFirst = false,
        mapTitle = "Харитаи Қуръон",
        mapHint = "Ба ҳуҷайра зер кунед, то сура намоён шавад",
        statusNotStarted = "Оғоз нашудааст",
        statusLearning = "Аз бар мекунам",
        statusLearned = "Аз бар шуд",
        ayahsOfPattern = "{a} аз {b} оят",
        open = "Кушодан",
        tileStreak = "Рӯзи пайваста",
        tileDue = "Такрор имрӯз",
        tileWeak = "Оятҳои заиф",
        tileLearning = "Оятҳо дар кор",
        activityTitle = "Фаъолият дар 14 рӯз",
        activityEmpty = "Ҳангоми хондан оятҳоро қайд кунед — ин ҷо сутунҳо меафзоянд.",
        activityNone = "бе қайд",
        today = "Имрӯз",
        weekdays = listOf("Дш", "Сш", "Чш", "Пш", "Ҷм", "Шн", "Яш"),
        months = listOf("янв", "фев", "мар", "апр", "май", "июн", "июл", "авг", "сен", "окт", "ноя", "дек"),
        datePattern = "{d} {m}",
        weakTitle = "Ҷойҳои заиф",
        weakNoneTitle = "Оятҳои заиф нест",
        weakNoneBody = "Ҳар чизе ки такрор кардед, устувор аст.",
        weakRepeat = "Такрор",
        weakRepeatDescriptionPattern = "Такрори ҷойҳои заиф: {name}",
        weakMorePattern = "Боз {n}",
        weakLess = "Ҷамъ кардан",
        emptyTitle = "Роҳи шумо дар ин ҷо пайдо мешавад",
        emptyBody = "Сураро «Аз бар мекунам» ишора кунед ё ҳангоми хондан оятҳои аз бар кардаатонро қайд намоед — ва харитаи Қуръон зинда мешавад.",
        emptyAction = "Ба сураҳо",
    )

    AppLanguage.UZ -> ProgressCopy(
        language = language,
        title = "Natija",
        back = "Orqaga",
        ringDescriptionPattern = "{p}%: {b} ta oyatdan {a} tasi yodlangan",
        learnedSurahsPattern = "{n} yodlangan",
        learningSurahsPattern = "{n} jarayonda",
        percentFirst = false,
        mapTitle = "Qur'on xaritasi",
        mapHint = "Surani ko‘rish uchun katakchani bosing",
        statusNotStarted = "Boshlanmagan",
        statusLearning = "Yodlayapman",
        statusLearned = "Yodlangan",
        ayahsOfPattern = "{b} ta oyatdan {a} tasi",
        open = "Ochish",
        tileStreak = "Ketma-ket kun",
        tileDue = "Bugun takrorlash",
        tileWeak = "Zaif oyatlar",
        tileLearning = "Ishdagi oyatlar",
        activityTitle = "So‘nggi 14 kun",
        activityEmpty = "O‘qiyotganda oyatlarni belgilang — ustunlar shu yerda o‘sadi.",
        activityNone = "belgi yo‘q",
        today = "Bugun",
        weekdays = listOf("Du", "Se", "Ch", "Pa", "Ju", "Sh", "Ya"),
        months = listOf("yan", "fev", "mar", "apr", "may", "iyn", "iyl", "avg", "sen", "okt", "noy", "dek"),
        datePattern = "{d} {m}",
        weakTitle = "Zaif joylar",
        weakNoneTitle = "Zaif oyatlar yo‘q",
        weakNoneBody = "Takrorlaganlaringiz mustahkam turibdi.",
        weakRepeat = "Takrorlash",
        weakRepeatDescriptionPattern = "Zaif joylarni takrorlash: {name}",
        weakMorePattern = "Yana {n} ta",
        weakLess = "Yig‘ish",
        emptyTitle = "Yo‘lingiz shu yerda ko‘rinadi",
        emptyBody = "Surani «Yodlayapman» deb belgilang yoki o‘qiyotganda yodlagan oyatlaringizni belgilang — Qur'on xaritasi jonlanadi.",
        emptyAction = "Suralarga",
    )

    AppLanguage.TR -> ProgressCopy(
        language = language,
        title = "İlerleme",
        back = "Geri",
        ringDescriptionPattern = "%{p}: {b} ayetten {a} ayet ezberlendi",
        learnedSurahsPattern = "{n} ezberlendi",
        learningSurahsPattern = "{n} devam ediyor",
        percentFirst = true,
        mapTitle = "Kur'an haritası",
        mapHint = "Sureyi görmek için bir kareye dokunun",
        statusNotStarted = "Başlanmadı",
        statusLearning = "Ezberliyorum",
        statusLearned = "Ezberlendi",
        ayahsOfPattern = "{b} ayetten {a} ayet",
        open = "Aç",
        tileStreak = "Ardışık gün",
        tileDue = "Bugün tekrar et",
        tileWeak = "Zayıf ayet",
        tileLearning = "Çalışılan ayet",
        activityTitle = "Son 14 gün",
        activityEmpty = "Okurken ayetleri işaretleyin; çubuklar burada büyüsün.",
        activityNone = "işaret yok",
        today = "Bugün",
        weekdays = listOf("Pt", "Sa", "Ça", "Pe", "Cu", "Ct", "Pz"),
        months = listOf("Oca", "Şub", "Mar", "Nis", "May", "Haz", "Tem", "Ağu", "Eyl", "Eki", "Kas", "Ara"),
        datePattern = "{d} {m}",
        weakTitle = "Zayıf yerler",
        weakNoneTitle = "Zayıf ayet yok",
        weakNoneBody = "Tekrar ettiklerinizin hepsi sağlam duruyor.",
        weakRepeat = "Tekrar et",
        weakRepeatDescriptionPattern = "Zayıf yerleri tekrar et: {name}",
        weakMorePattern = "{n} tane daha",
        weakLess = "Daralt",
        emptyTitle = "Yolculuğunuz burada görünecek",
        emptyBody = "Bir sureyi “Ezberliyorum” olarak işaretleyin ya da okurken ezberlediğiniz ayetleri işaretleyin; Kur'an haritası canlanacak.",
        emptyAction = "Surelere",
    )
}
