package tj.app.quran_todo.ui.i18n

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import tj.app.quran_todo.common.i18n.AppLanguage
import tj.app.quran_todo.common.i18n.LocalAppLanguage

/**
 * Strings of the row of actions under a tapped ayah of the text reader (UX_PLAN U19) and of the one-time question about the
 * translation (U22). The Tajik, Uzbek and Turkish wording should be checked by a native speaker.
 */
@Immutable
data class AyahActionsCopy(
    val listen: String,
    val repeat: String,
    val more: String,
    val offerQuestion: String,
    val yes: String,
    val no: String,
)

@Composable
fun rememberAyahActionsCopy(language: AppLanguage = LocalAppLanguage.current): AyahActionsCopy =
    remember(language) { ayahActionsCopy(language) }

fun ayahActionsCopy(language: AppLanguage): AyahActionsCopy = when (language) {
    AppLanguage.RU -> AyahActionsCopy(
        listen = "Слушать отсюда", repeat = "Повторять", more = "Ещё",
        offerQuestion = "Показать перевод под аятами?", yes = "Да", no = "Нет",
    )
    AppLanguage.EN -> AyahActionsCopy(
        listen = "Listen from here", repeat = "Repeat", more = "More",
        offerQuestion = "Show the translation under the ayahs?", yes = "Yes", no = "No",
    )
    AppLanguage.TG -> AyahActionsCopy(
        listen = "Гӯш кардан аз ин ҷо", repeat = "Такрор", more = "Бештар",
        offerQuestion = "Тарҷумаро дар зери оятҳо нишон диҳем?", yes = "Ҳа", no = "Не",
    )
    AppLanguage.UZ -> AyahActionsCopy(
        listen = "Shu yerdan tinglash", repeat = "Takrorlash", more = "Yana",
        offerQuestion = "Oyatlar ostida tarjima ko'rsatilsinmi?", yes = "Ha", no = "Yo'q",
    )
    AppLanguage.TR -> AyahActionsCopy(
        listen = "Buradan dinle", repeat = "Tekrarla", more = "Daha fazla",
        offerQuestion = "Ayetlerin altında çeviri gösterilsin mi?", yes = "Evet", no = "Hayır",
    )
}
