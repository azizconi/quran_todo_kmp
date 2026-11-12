package tj.app.quran_todo.ui.i18n

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import tj.app.quran_todo.common.i18n.AppLanguage
import tj.app.quran_todo.common.i18n.LocalAppLanguage

/** Strings of the inline hints (one quiet line on a screen) in the five app languages. */
@Immutable
data class HintsCopy(
    /** Content description of the "×" on a hint. */
    val dismiss: String,
    /** Reader, under the surah header. */
    val readerHold: String,
    /** Lesson, under the "I remember / Hard" buttons. */
    val lessonGrade: String,
)

@Composable
fun rememberHintsCopy(language: AppLanguage = LocalAppLanguage.current): HintsCopy = remember(language) { hintsCopy(language) }

fun hintsCopy(language: AppLanguage): HintsCopy = when (language) {
    AppLanguage.RU -> HintsCopy(
        dismiss = "Скрыть подсказку",
        readerHold = "Удерживайте аят — заметка, повтор и другие действия",
        lessonGrade = "От ответа зависит, когда отрывок вернётся",
    )
    AppLanguage.EN -> HintsCopy(
        dismiss = "Hide this tip",
        readerHold = "Hold an ayah for notes, repeat and more",
        lessonGrade = "Your answer decides when the passage comes back",
    )
    AppLanguage.TG -> HintsCopy(
        dismiss = "Пинҳон кардани маслиҳат",
        readerHold = "Оятро нигоҳ доред — ёддошт, такрор ва амалҳои дигар",
        lessonGrade = "Аз ҷавоби шумо вобаста аст, ки порча кай бармегардад",
    )
    AppLanguage.UZ -> HintsCopy(
        dismiss = "Maslahatni yashirish",
        readerHold = "Oyatni bosib turing — eslatma, takror va boshqa amallar",
        lessonGrade = "Javobingizga qarab parcha qachon qaytishi belgilanadi",
    )
    AppLanguage.TR -> HintsCopy(
        dismiss = "İpucunu gizle",
        readerHold = "Not, tekrar ve diğer işlemler için ayete basılı tutun",
        lessonGrade = "Cevabınıza göre bölümün ne zaman geri geleceği belirlenir",
    )
}
