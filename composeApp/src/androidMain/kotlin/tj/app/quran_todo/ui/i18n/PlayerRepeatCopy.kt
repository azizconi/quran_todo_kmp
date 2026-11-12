package tj.app.quran_todo.ui.i18n

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import tj.app.quran_todo.common.i18n.AppLanguage
import tj.app.quran_todo.common.i18n.LocalAppLanguage

/**
 * Strings of the expanded player that gathered the audio choices from the settings (task U20): the reciter row, the one "Repeat"
 * block with its three modes, and "next ayah automatically". In this UI the audio loop is always called "Repeat" (RU: «Повтор»).
 */
@Immutable
data class PlayerRepeatCopy(
    val reciterRow: String,
    val repeatTitle: String,
    val modeNone: String,
    val modeAyah: String,
    val modePassage: String,
    val ayahHint: String,
    val passageHint: String,
    val autoAdvanceTitle: String,
    val autoAdvanceHint: String,
    /** The word before the active repeat in the collapsed status line: "Repeat". */
    val statusWord: String,
    /** "ayah" and "passage" as they read after [statusWord]. */
    val statusAyah: String,
    val statusPassage: String,
) {
    /** "Повтор: аят 3×" */
    fun ayahStatus(count: String) = "$statusWord: $statusAyah $count"

    /** "Повтор: отрывок 2–4 · 3×" */
    fun passageStatus(range: String, count: String) = "$statusWord: $statusPassage $range · $count"
}

@Composable
fun rememberPlayerRepeatCopy(language: AppLanguage = LocalAppLanguage.current): PlayerRepeatCopy =
    remember(language) { playerRepeatCopy(language) }

fun playerRepeatCopy(language: AppLanguage): PlayerRepeatCopy = when (language) {
    AppLanguage.RU -> PlayerRepeatCopy(
        reciterRow = "Чтец",
        repeatTitle = "Повтор",
        modeNone = "Нет", modeAyah = "Аят", modePassage = "Отрывок",
        ayahHint = "Каждый аят звучит столько раз, сколько выбрано.",
        passageHint = "Звучит выбранный отрывок, снова и снова.",
        autoAdvanceTitle = "Следующий аят автоматически",
        autoAdvanceHint = "После повторов звук идёт дальше сам.",
        statusWord = "Повтор", statusAyah = "аят", statusPassage = "отрывок",
    )
    AppLanguage.EN -> PlayerRepeatCopy(
        reciterRow = "Reciter",
        repeatTitle = "Repeat",
        modeNone = "Off", modeAyah = "Ayah", modePassage = "Passage",
        ayahHint = "Every ayah plays as many times as chosen.",
        passageHint = "The chosen passage plays again and again.",
        autoAdvanceTitle = "Next ayah automatically",
        autoAdvanceHint = "After the repeats, audio moves on by itself.",
        statusWord = "Repeat", statusAyah = "ayah", statusPassage = "passage",
    )
    AppLanguage.TG -> PlayerRepeatCopy(
        reciterRow = "Қорӣ",
        repeatTitle = "Такрор",
        modeNone = "Не", modeAyah = "Оят", modePassage = "Порча",
        ayahHint = "Ҳар оят ҳамон қадар бор садо медиҳад, ки интихоб шудааст.",
        passageHint = "Порчаи интихобшуда бори дигар ва дигар садо медиҳад.",
        autoAdvanceTitle = "Ояти баъдӣ худкор",
        autoAdvanceHint = "Пас аз такрорҳо садо худаш идома меёбад.",
        statusWord = "Такрор", statusAyah = "оят", statusPassage = "порча",
    )
    AppLanguage.UZ -> PlayerRepeatCopy(
        reciterRow = "Qori",
        repeatTitle = "Takror",
        modeNone = "Yo‘q", modeAyah = "Oyat", modePassage = "Parcha",
        ayahHint = "Har bir oyat tanlangan marta yangraydi.",
        passageHint = "Tanlangan parcha qayta-qayta yangraydi.",
        autoAdvanceTitle = "Keyingi oyat avtomatik",
        autoAdvanceHint = "Takrorlardan so‘ng ovoz o‘zi davom etadi.",
        statusWord = "Takror", statusAyah = "oyat", statusPassage = "parcha",
    )
    AppLanguage.TR -> PlayerRepeatCopy(
        reciterRow = "Kâri",
        repeatTitle = "Tekrar",
        modeNone = "Yok", modeAyah = "Ayet", modePassage = "Bölüm",
        ayahHint = "Her ayet seçilen sayıda çalınır.",
        passageHint = "Seçilen bölüm tekrar tekrar çalınır.",
        autoAdvanceTitle = "Sonraki ayet otomatik",
        autoAdvanceHint = "Tekrarlardan sonra ses kendiliğinden devam eder.",
        statusWord = "Tekrar", statusAyah = "ayet", statusPassage = "bölüm",
    )
}
