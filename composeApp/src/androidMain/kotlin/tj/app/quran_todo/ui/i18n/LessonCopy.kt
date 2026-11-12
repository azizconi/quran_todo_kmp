package tj.app.quran_todo.ui.i18n

import androidx.compose.runtime.Immutable
import tj.app.quran_todo.common.i18n.AppLanguage

/**
 * Strings of the lesson screen in the five app languages (wording follows the glossary of
 * docs/redesign/ANDROID_REDESIGN.md, section 10). The recall component has its own [HifzCopy]. Texts with numbers or
 * names are patterns with `{n}`, `{m}`, `{a}`, `{b}`, `{name}`, `{what}`, `{date}` and `{ayahs}` placeholders, filled by
 * the functions below, so each language keeps its own word order and plural forms.
 */
@Immutable
data class LessonCopy(
    val language: AppLanguage,

    // Header
    val title: String,
    val back: String,
    val stepNew: String,
    val stepRecent: String,
    val stepOld: String,
    val minutesLeftFmt: String,
    val catchingUp: String,
    val stepProgressFmt: String,
    val stepDoneFmt: String,

    // The new passage
    val phaseListen: String,
    val phaseRead: String,
    val phaseRecall: String,
    val listenPlay: String,
    val listenPause: String,
    val listenReplay: String,
    val listenHintFmt: String,
    val listenHintOnlyAyahsFmt: String,
    val readHint: String,
    val next: String,
    val listenSettings: String,
    val settingsTitle: String,
    val ayahRepeatsLabel: String,
    val passageRepeatsLabel: String,

    // A repetition
    val rangeOneFmt: String,
    val rangeManyFmt: String,
    val overdueFmt: String,
    val hardCountFmt: String,
    val listenFirst: String,
    val startRecall: String,

    // Leaving
    val exitTitle: String,
    val exitBody: String,
    val stay: String,
    val leave: String,

    // Empty states
    val noPlanTitle: String,
    val noPlanAction: String,
    val nothingTitle: String,
    val nothingBody: String,
    val planDoneTitle: String,
    val planDoneBody: String,
    val toHome: String,
    val saveFailed: String,

    // Summary
    val summaryTitle: String,
    val summaryCaption: String,
    val streakFmt: String,
    val forecastFinishFmt: String,
    val forecastBehindFmt: String,
    val planFinished: String,
    val tomorrowFmt: String,
    val tomorrowNothing: String,
    val tomorrowAnd: String,
    val tomorrowNewFmt: String,
    val repetitionOne: String,
    val repetitionFew: String,
    val repetitionMany: String,
) {
    fun minutesLeft(minutes: Int): String = minutesLeftFmt.fill("n" to minutes)

    fun stepProgress(done: Int, total: Int): String = stepProgressFmt.fill("a" to done, "b" to total)

    /** Spoken form of a step of the header: "Recent: 2 of 5". */
    fun stepDescription(name: String, done: Int, total: Int): String =
        stepDoneFmt.fill("name" to name, "a" to done, "b" to total)

    fun listenHint(ayahRepeats: Int, passageRepeats: Int): String =
        if (passageRepeats <= 0) {
            listenHintOnlyAyahsFmt.fill("n" to ayahRepeats)
        } else {
            listenHintFmt.fill("n" to ayahRepeats, "m" to passageRepeats)
        }

    /** "Ayah 3" or "Ayahs 6–10". */
    fun range(start: Int, end: Int): String =
        if (start == end) rangeOneFmt.fill("a" to start) else rangeManyFmt.fill("a" to start, "b" to end)

    fun overdue(days: Int): String = overdueFmt.fill("n" to days)

    fun hardCount(count: Int): String = hardCountFmt.fill("n" to count)

    fun ayahCount(count: Int): String = readerCopy(language).ayahCount(count)

    fun streak(days: Int): String = streakFmt.fill("n" to days)

    fun forecastFinish(date: String): String = forecastFinishFmt.fill("date" to date)

    fun forecastBehind(ayahs: Int): String = forecastBehindFmt.fill("ayahs" to ayahCount(ayahs))

    /** "Repetition", "3 repetitions" with the plural forms of the language. */
    fun repetitions(count: Int): String {
        val pattern = when (language) {
            AppLanguage.RU -> {
                val mod10 = count % 10
                val mod100 = count % 100
                when {
                    mod10 == 1 && mod100 != 11 -> repetitionOne
                    mod10 in 2..4 && mod100 !in 12..14 -> repetitionFew
                    else -> repetitionMany
                }
            }
            AppLanguage.EN -> if (count == 1) repetitionOne else repetitionMany
            else -> repetitionMany
        }
        return pattern.fill("n" to count)
    }

    /**
     * "Tomorrow: An-Naba 6–10 and 3 repetitions". [newRange] is "An-Naba 6–10" (null when no new passage is planned),
     * [dueCount] the repetitions of the ladder that are due then.
     */
    fun tomorrow(newRange: String?, dueCount: Int): String {
        val parts = buildList {
            newRange?.let { add(tomorrowNewFmt.fill("name" to it)) }
            if (dueCount > 0) add(repetitions(dueCount))
        }
        if (parts.isEmpty()) return tomorrowNothing
        return tomorrowFmt.fill("what" to parts.joinToString(" $tomorrowAnd "))
    }
}

private fun String.fill(vararg values: Pair<String, Any>): String {
    var result = this
    values.forEach { (key, value) -> result = result.replace("{$key}", value.toString()) }
    return result
}

fun lessonCopy(language: AppLanguage): LessonCopy = when (language) {
    AppLanguage.RU -> LessonCopy(
        language = language,
        title = "Урок дня", back = "Назад", stepNew = "Новое", stepRecent = "Недавнее", stepOld = "Старое",
        minutesLeftFmt = "Осталось ≈ {n} мин",
        catchingUp = "Догоняем: часть повторений перенесена на следующие дни",
        stepProgressFmt = "{a} из {b}", stepDoneFmt = "{name}: {a} из {b}",
        phaseListen = "Слушать", phaseRead = "Читать", phaseRecall = "Вспомнить",
        listenPlay = "Слушать", listenPause = "Пауза", listenReplay = "Сначала",
        listenHintFmt = "Каждый аят ×{n}, затем весь отрывок ×{m}",
        listenHintOnlyAyahsFmt = "Каждый аят ×{n}",
        readHint = "Нажмите на аят, чтобы послушать его", next = "Дальше",
        listenSettings = "Настройки прослушивания", settingsTitle = "Настройки урока",
        ayahRepeatsLabel = "Повторов каждого аята", passageRepeatsLabel = "Повторов всего отрывка",
        rangeOneFmt = "Аят {a}", rangeManyFmt = "Аяты {a}–{b}",
        overdueFmt = "Просрочено на {n} дн.", hardCountFmt = "Трудных аятов: {n}",
        listenFirst = "Послушать сначала", startRecall = "Вспомнить",
        exitTitle = "Выйти из урока?",
        exitBody = "Сделанное сохранено. Урок продолжится с первого невыполненного отрывка.",
        stay = "Остаться", leave = "Выйти",
        noPlanTitle = "Составьте план, и здесь появится урок", noPlanAction = "Составить план",
        nothingTitle = "На сегодня заданий нет", nothingBody = "Загляните завтра — урок появится сам.",
        planDoneTitle = "План выполнен",
        planDoneBody = "Все суры плана пройдены. Повторения вернутся по расписанию.",
        toHome = "На главную", saveFailed = "Не удалось сохранить результат",
        summaryTitle = "Урок выполнен", summaryCaption = "На сегодня всё. Хорошая работа!",
        streakFmt = "Серия: {n} дн.", forecastFinishFmt = "План закончите ≈ {date}",
        forecastBehindFmt = "Отстаёте от срока: {ayahs}", planFinished = "План выполнен",
        tomorrowFmt = "Завтра: {what}", tomorrowNothing = "До завтра", tomorrowAnd = "и",
        tomorrowNewFmt = "{name}",
        repetitionOne = "{n} повторение", repetitionFew = "{n} повторения", repetitionMany = "{n} повторений",
    )
    AppLanguage.EN -> LessonCopy(
        language = language,
        title = "Today's lesson", back = "Back", stepNew = "New", stepRecent = "Recent", stepOld = "Old",
        minutesLeftFmt = "≈ {n} min left",
        catchingUp = "Catching up: some reviews were moved to the next days",
        stepProgressFmt = "{a} of {b}", stepDoneFmt = "{name}: {a} of {b}",
        phaseListen = "Listen", phaseRead = "Read", phaseRecall = "Recall",
        listenPlay = "Listen", listenPause = "Pause", listenReplay = "Restart",
        listenHintFmt = "Every ayah ×{n}, then the whole passage ×{m}",
        listenHintOnlyAyahsFmt = "Every ayah ×{n}",
        readHint = "Tap an ayah to hear it", next = "Next",
        listenSettings = "Listening settings", settingsTitle = "Lesson settings",
        ayahRepeatsLabel = "Repeats of every ayah", passageRepeatsLabel = "Repeats of the whole passage",
        rangeOneFmt = "Ayah {a}", rangeManyFmt = "Ayahs {a}–{b}",
        overdueFmt = "{n} d overdue", hardCountFmt = "Hard ayahs: {n}",
        listenFirst = "Listen first", startRecall = "Recall",
        exitTitle = "Leave the lesson?",
        exitBody = "What you did is saved. The lesson goes on from the first passage that is not done.",
        stay = "Stay", leave = "Leave",
        noPlanTitle = "Make a plan and your lesson will appear here", noPlanAction = "Make a plan",
        nothingTitle = "Nothing to do today", nothingBody = "Come back tomorrow — the lesson will be here.",
        planDoneTitle = "Plan complete",
        planDoneBody = "Every surah of the plan is covered. Reviews will come back on schedule.",
        toHome = "To home", saveFailed = "Couldn't save the result",
        summaryTitle = "Lesson complete", summaryCaption = "That's all for today. Well done!",
        streakFmt = "Streak: {n} d", forecastFinishFmt = "You will finish the plan ≈ {date}",
        forecastBehindFmt = "Behind schedule: {ayahs}", planFinished = "Plan complete",
        tomorrowFmt = "Tomorrow: {what}", tomorrowNothing = "See you tomorrow", tomorrowAnd = "and",
        tomorrowNewFmt = "{name}",
        repetitionOne = "{n} review", repetitionFew = "{n} reviews", repetitionMany = "{n} reviews",
    )
    AppLanguage.TG -> LessonCopy(
        language = language,
        title = "Дарси рӯз", back = "Бозгашт", stepNew = "Нав", stepRecent = "Навакак", stepOld = "Кӯҳна",
        minutesLeftFmt = "Боқӣ ≈ {n} дақ",
        catchingUp = "Ҷуброн мекунем: як қисми такрорҳо ба рӯзҳои оянда гузошта шуд",
        stepProgressFmt = "{a} аз {b}", stepDoneFmt = "{name}: {a} аз {b}",
        phaseListen = "Гӯш кунед", phaseRead = "Бихонед", phaseRecall = "Ба ёд оред",
        listenPlay = "Гӯш кардан", listenPause = "Таваққуф", listenReplay = "Аз аввал",
        listenHintFmt = "Ҳар оят ×{n}, сипас тамоми порча ×{m}",
        listenHintOnlyAyahsFmt = "Ҳар оят ×{n}",
        readHint = "Барои шунидан ба оят зер кунед", next = "Минбаъд",
        listenSettings = "Танзимоти гӯш кардан", settingsTitle = "Танзимоти дарс",
        ayahRepeatsLabel = "Такрори ҳар оят", passageRepeatsLabel = "Такрори тамоми порча",
        rangeOneFmt = "Оят {a}", rangeManyFmt = "Оятҳои {a}–{b}",
        overdueFmt = "{n} рӯз дер шудааст", hardCountFmt = "Оятҳои душвор: {n}",
        listenFirst = "Аввал гӯш кардан", startRecall = "Ба ёд овардан",
        exitTitle = "Аз дарс мебароед?",
        exitBody = "Ҳар чи анҷом ёфт, захира шуд. Дарс аз аввалин порчаи анҷомнаёфта идома меёбад.",
        stay = "Мондан", leave = "Баромадан",
        noPlanTitle = "Нақша созед, ва дарс дар ин ҷо пайдо мешавад", noPlanAction = "Сохтани нақша",
        nothingTitle = "Имрӯз вазифа нест", nothingBody = "Фардо боз оед — дарс худаш пайдо мешавад.",
        planDoneTitle = "Нақша иҷро шуд",
        planDoneBody = "Ҳамаи сураҳои нақша гузашта шуданд. Такрорҳо аз рӯи ҷадвал бармегарданд.",
        toHome = "Ба саҳифаи асосӣ", saveFailed = "Натиҷаро захира кардан муяссар нашуд",
        summaryTitle = "Дарс анҷом ёфт", summaryCaption = "Барои имрӯз ҳамин қадар. Офарин!",
        streakFmt = "Силсила: {n} рӯз", forecastFinishFmt = "Нақшаро тақрибан {date} анҷом медиҳед",
        forecastBehindFmt = "Аз мӯҳлат қафо мондаед: {ayahs}", planFinished = "Нақша иҷро шуд",
        tomorrowFmt = "Фардо: {what}", tomorrowNothing = "То фардо", tomorrowAnd = "ва",
        tomorrowNewFmt = "{name}",
        repetitionOne = "{n} такрор", repetitionFew = "{n} такрор", repetitionMany = "{n} такрор",
    )
    AppLanguage.UZ -> LessonCopy(
        language = language,
        title = "Kun darsi", back = "Orqaga", stepNew = "Yangi", stepRecent = "Yaqinda", stepOld = "Eski",
        minutesLeftFmt = "Qoldi ≈ {n} daq",
        catchingUp = "Yetib olyapmiz: ba’zi takrorlar keyingi kunlarga ko‘chirildi",
        stepProgressFmt = "{a} / {b}", stepDoneFmt = "{name}: {a} / {b}",
        phaseListen = "Tinglang", phaseRead = "O‘qing", phaseRecall = "Eslab ayting",
        listenPlay = "Tinglash", listenPause = "Pauza", listenReplay = "Boshidan",
        listenHintFmt = "Har bir oyat ×{n}, keyin butun parcha ×{m}",
        listenHintOnlyAyahsFmt = "Har bir oyat ×{n}",
        readHint = "Eshitish uchun oyatni bosing", next = "Keyingi",
        listenSettings = "Tinglash sozlamalari", settingsTitle = "Dars sozlamalari",
        ayahRepeatsLabel = "Har bir oyat takrori", passageRepeatsLabel = "Butun parcha takrori",
        rangeOneFmt = "{a}-oyat", rangeManyFmt = "{a}–{b}-oyatlar",
        overdueFmt = "{n} kunga kechikkan", hardCountFmt = "Qiyin oyatlar: {n}",
        listenFirst = "Avval tinglash", startRecall = "Eslab aytish",
        exitTitle = "Darsdan chiqasizmi?",
        exitBody = "Bajarilgani saqlandi. Dars bajarilmagan birinchi parchadan davom etadi.",
        stay = "Qolish", leave = "Chiqish",
        noPlanTitle = "Reja tuzing, dars shu yerda paydo bo‘ladi", noPlanAction = "Reja tuzish",
        nothingTitle = "Bugun vazifa yo‘q", nothingBody = "Ertaga qaytib keling — dars o‘zi paydo bo‘ladi.",
        planDoneTitle = "Reja bajarildi",
        planDoneBody = "Rejadagi barcha suralar o‘tildi. Takrorlar jadval bo‘yicha qaytadi.",
        toHome = "Bosh sahifaga", saveFailed = "Natijani saqlab bo‘lmadi",
        summaryTitle = "Dars tugadi", summaryCaption = "Bugunga shu. Barakalla!",
        streakFmt = "Seriya: {n} kun", forecastFinishFmt = "Rejani taxminan {date} tugatasiz",
        forecastBehindFmt = "Muddatdan orqadasiz: {ayahs}", planFinished = "Reja bajarildi",
        tomorrowFmt = "Ertaga: {what}", tomorrowNothing = "Ertagacha", tomorrowAnd = "va",
        tomorrowNewFmt = "{name}",
        repetitionOne = "{n} ta takror", repetitionFew = "{n} ta takror", repetitionMany = "{n} ta takror",
    )
    AppLanguage.TR -> LessonCopy(
        language = language,
        title = "Günün dersi", back = "Geri", stepNew = "Yeni", stepRecent = "Yakın zamanda", stepOld = "Eski",
        minutesLeftFmt = "Kalan ≈ {n} dk",
        catchingUp = "Telafi ediyoruz: bazı tekrarlar sonraki günlere ertelendi",
        stepProgressFmt = "{a} / {b}", stepDoneFmt = "{name}: {a} / {b}",
        phaseListen = "Dinleyin", phaseRead = "Okuyun", phaseRecall = "Hatırlayın",
        listenPlay = "Dinle", listenPause = "Duraklat", listenReplay = "Baştan",
        listenHintFmt = "Her ayet ×{n}, sonra tüm bölüm ×{m}",
        listenHintOnlyAyahsFmt = "Her ayet ×{n}",
        readHint = "Dinlemek için ayete dokunun", next = "İleri",
        listenSettings = "Dinleme ayarları", settingsTitle = "Ders ayarları",
        ayahRepeatsLabel = "Her ayetin tekrarı", passageRepeatsLabel = "Tüm bölümün tekrarı",
        rangeOneFmt = "{a}. ayet", rangeManyFmt = "{a}–{b}. ayetler",
        overdueFmt = "{n} gün gecikti", hardCountFmt = "Zor ayetler: {n}",
        listenFirst = "Önce dinle", startRecall = "Hatırlayın",
        exitTitle = "Dersten çıkılsın mı?",
        exitBody = "Yaptıklarınız kaydedildi. Ders, tamamlanmamış ilk bölümden devam eder.",
        stay = "Kal", leave = "Çık",
        noPlanTitle = "Bir plan yapın, ders burada görünsün", noPlanAction = "Plan yap",
        nothingTitle = "Bugün yapılacak bir şey yok", nothingBody = "Yarın tekrar gelin — ders kendiliğinden çıkar.",
        planDoneTitle = "Plan tamamlandı",
        planDoneBody = "Plandaki tüm sureler geçildi. Tekrarlar programa göre geri gelir.",
        toHome = "Ana sayfaya", saveFailed = "Sonuç kaydedilemedi",
        summaryTitle = "Ders tamamlandı", summaryCaption = "Bugünlük bu kadar. Tebrikler!",
        streakFmt = "Seri: {n} gün", forecastFinishFmt = "Planı yaklaşık {date} bitireceksiniz",
        forecastBehindFmt = "Süreden geridesiniz: {ayahs}", planFinished = "Plan tamamlandı",
        tomorrowFmt = "Yarın: {what}", tomorrowNothing = "Yarına kadar", tomorrowAnd = "ve",
        tomorrowNewFmt = "{name}",
        repetitionOne = "{n} tekrar", repetitionFew = "{n} tekrar", repetitionMany = "{n} tekrar",
    )
}
