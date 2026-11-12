package tj.app.quran_todo.ui.i18n

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import tj.app.quran_todo.common.i18n.AppLanguage
import tj.app.quran_todo.common.i18n.LocalAppLanguage

/**
 * Strings of the drill of the difficult ayahs (task F2a) in the five app languages: the screen, its entries (the tile of "Progress",
 * the end of the lesson) and the end. Texts with numbers are patterns with `{a}`, `{b}` and `{n}` placeholders.
 */
@Immutable
data class DrillCopy(
    val language: AppLanguage,

    // The screen
    val title: String,
    val progressFmt: String,
    val previousAyah: String,
    val question: String,
    val ayahFmt: String,
    val exit: String,
    val saveFailed: String,

    // The end
    val doneTitle: String,
    val summaryFmt: String,
    val moreFmt: String,
    val done: String,
    val emptyTitle: String,
    val emptyBody: String,

    // The entries
    val trainFmt: String,
    val tileTrain: String,
) {
    private fun String.fill(vararg values: Pair<String, Any>): String =
        values.fold(this) { text, (key, value) -> text.replace("{$key}", value.toString()) }

    /** "2 of 10". */
    fun progress(position: Int, total: Int): String = progressFmt.fill("a" to position, "b" to total)

    /** "Ayah 5". */
    fun ayah(number: Int): String = ayahFmt.fill("n" to number)

    /** "I remember: 7 · Still hard: 3". */
    fun summary(remembered: Int, stillHard: Int): String = summaryFmt.fill("a" to remembered, "b" to stillHard)

    /** "4 more next time". */
    fun more(count: Int): String = moreFmt.fill("n" to count)

    /** "Practice hard ayahs · 5": the button of the end of the lesson. */
    fun train(count: Int): String = trainFmt.fill("n" to count)
}

@Composable
fun rememberDrillCopy(language: AppLanguage = LocalAppLanguage.current): DrillCopy = remember(language) { drillCopy(language) }

fun drillCopy(language: AppLanguage): DrillCopy = when (language) {
    AppLanguage.RU -> DrillCopy(
        language = language,
        title = "Тренировка трудных", progressFmt = "{a} из {b}", previousAyah = "Предыдущий аят",
        question = "Вспомните аят, который идёт следом", ayahFmt = "Аят {n}", exit = "Закрыть тренировку",
        saveFailed = "Не удалось сохранить результат",
        doneTitle = "Тренировка завершена", summaryFmt = "Помню: {a} · Ещё трудные: {b}",
        moreFmt = "Ещё {n} в следующий раз", done = "Готово",
        emptyTitle = "Трудных аятов нет", emptyBody = "Аяты, которые вы отметите трудными, появятся здесь.",
        trainFmt = "Тренировать трудные · {n}", tileTrain = "Тренировать",
    )
    AppLanguage.EN -> DrillCopy(
        language = language,
        title = "Practice hard ayahs", progressFmt = "{a} of {b}", previousAyah = "Previous ayah",
        question = "Recall the ayah that follows", ayahFmt = "Ayah {n}", exit = "Close the practice",
        saveFailed = "Could not save the result",
        doneTitle = "Practice finished", summaryFmt = "I remember: {a} · Still hard: {b}",
        moreFmt = "{n} more next time", done = "Done",
        emptyTitle = "No hard ayahs", emptyBody = "Ayahs you mark as hard will appear here.",
        trainFmt = "Practice hard ayahs · {n}", tileTrain = "Practice",
    )
    AppLanguage.TG -> DrillCopy(
        language = language,
        title = "Машқи оятҳои душвор", progressFmt = "{a} аз {b}", previousAyah = "Ояти пешина",
        question = "Оятеро, ки баъд аз он меояд, ба ёд оред", ayahFmt = "Оят {n}", exit = "Баромадан аз машқ",
        saveFailed = "Натиҷа сабт нашуд",
        doneTitle = "Машқ анҷом ёфт", summaryFmt = "Дар ёд дорам: {a} · Ҳанӯз душвор: {b}",
        moreFmt = "Боз {n} дар навбати оянда", done = "Тайёр",
        emptyTitle = "Оятҳои душвор нест", emptyBody = "Оятҳое, ки душвор қайд мекунед, дар ин ҷо пайдо мешаванд.",
        trainFmt = "Машқи оятҳои душвор · {n}", tileTrain = "Машқ",
    )
    AppLanguage.UZ -> DrillCopy(
        language = language,
        title = "Qiyin oyatlar mashqi", progressFmt = "{a} / {b}", previousAyah = "Oldingi oyat",
        question = "Keyingi oyatni eslang", ayahFmt = "{n}-oyat", exit = "Mashqdan chiqish",
        saveFailed = "Natijani saqlab bo‘lmadi",
        doneTitle = "Mashq tugadi", summaryFmt = "Esimda: {a} · Hali qiyin: {b}",
        moreFmt = "Yana {n} tasi keyingi safar", done = "Tayyor",
        emptyTitle = "Qiyin oyatlar yo‘q", emptyBody = "Qiyin deb belgilagan oyatlaringiz shu yerda ko‘rinadi.",
        trainFmt = "Qiyin oyatlar mashqi · {n}", tileTrain = "Mashq",
    )
    AppLanguage.TR -> DrillCopy(
        language = language,
        title = "Zor ayetleri çalış", progressFmt = "{a} / {b}", previousAyah = "Önceki ayet",
        question = "Ardından gelen ayeti hatırlayın", ayahFmt = "Ayet {n}", exit = "Çalışmayı kapat",
        saveFailed = "Sonuç kaydedilemedi",
        doneTitle = "Çalışma bitti", summaryFmt = "Hatırlıyorum: {a} · Hâlâ zor: {b}",
        moreFmt = "Gelecek sefer {n} ayet daha", done = "Bitti",
        emptyTitle = "Zor ayet yok", emptyBody = "Zor diye işaretlediğiniz ayetler burada görünür.",
        trainFmt = "Zor ayetleri çalış · {n}", tileTrain = "Çalış",
    )
}
