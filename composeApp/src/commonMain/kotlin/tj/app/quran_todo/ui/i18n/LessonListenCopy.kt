package tj.app.quran_todo.ui.i18n

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import tj.app.quran_todo.common.i18n.AppLanguage
import tj.app.quran_todo.common.i18n.LocalAppLanguage

/**
 * Strings of the "Listen" and "Read" phases of the new passage added by the UX plan (U7, U8): where the listening is and the
 * "Translation" chip. Patterns carry `{a}`, `{n}`, `{r}`, `{m}`, `{p}` placeholders so each language keeps its own word order.
 *
 * The Tajik, Uzbek and Turkish wording should be checked by a native speaker.
 */
@Immutable
data class LessonListenCopy(
    val language: AppLanguage,
    /** "Ayah 2 of 6 · repeat 1 of 3": `{a}` the ayah of the passage, `{n}` their count, `{r}` the repeat, `{m}` the repeats. */
    val ayahProgressPattern: String,
    /** "Whole passage · 1 of 2": `{p}` the pass, `{m}` the passes. */
    val passageProgressPattern: String,
    val translationChip: String,
) {
    private fun String.fill(vararg values: Pair<String, Any>): String =
        values.fold(this) { text, (key, value) -> text.replace("{$key}", value.toString()) }

    fun ayahProgress(ayah: Int, ayahs: Int, repeat: Int, repeats: Int): String =
        ayahProgressPattern.fill("a" to ayah, "n" to ayahs, "r" to repeat, "m" to repeats)

    fun passageProgress(pass: Int, passes: Int): String = passageProgressPattern.fill("p" to pass, "m" to passes)
}

@Composable
fun rememberLessonListenCopy(language: AppLanguage = LocalAppLanguage.current): LessonListenCopy =
    remember(language) { lessonListenCopy(language) }

fun lessonListenCopy(language: AppLanguage): LessonListenCopy = when (language) {
    AppLanguage.RU -> LessonListenCopy(
        language = language,
        ayahProgressPattern = "Аят {a} из {n} · повтор {r} из {m}",
        passageProgressPattern = "Весь отрывок · {p} из {m}",
        translationChip = "Перевод",
    )
    AppLanguage.EN -> LessonListenCopy(
        language = language,
        ayahProgressPattern = "Ayah {a} of {n} · repeat {r} of {m}",
        passageProgressPattern = "Whole passage · {p} of {m}",
        translationChip = "Translation",
    )
    AppLanguage.TG -> LessonListenCopy(
        language = language,
        ayahProgressPattern = "Оят {a} аз {n} · такрор {r} аз {m}",
        passageProgressPattern = "Тамоми порча · {p} аз {m}",
        translationChip = "Тарҷума",
    )
    AppLanguage.UZ -> LessonListenCopy(
        language = language,
        ayahProgressPattern = "Oyat {a} / {n} · takror {r} / {m}",
        passageProgressPattern = "Butun parcha · {p} / {m}",
        translationChip = "Tarjima",
    )
    AppLanguage.TR -> LessonListenCopy(
        language = language,
        ayahProgressPattern = "Ayet {a} / {n} · tekrar {r} / {m}",
        passageProgressPattern = "Tüm bölüm · {p} / {m}",
        translationChip = "Çeviri",
    )
}
