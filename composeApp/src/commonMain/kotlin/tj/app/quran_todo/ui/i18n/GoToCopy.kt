package tj.app.quran_todo.ui.i18n

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import tj.app.quran_todo.common.i18n.AppLanguage
import tj.app.quran_todo.common.i18n.LocalAppLanguage

/**
 * Strings of the "Go to" sheet (U21) and of the labelled top bar and the two-part appearance sheet of the reader (U23).
 * The labels under the icons are one short word each. The Tajik, Uzbek and Turkish wording should be checked by a native speaker.
 */
@Immutable
data class GoToCopy(
    // "Go to" sheet
    val title: String,
    val surah: String,
    val ayah: String,
    /** Pattern with `{n}`: the range the ayah field accepts ("1 to 286"). */
    val rangePattern: String,
    val go: String,
    val chooseSurah: String,
    val searchHint: String,
    val clearSearch: String,
    val nothingFound: String,
    // Labels under the icons of the top bars
    val labelView: String,
    val labelPages: String,
    val labelByHeart: String,
    val labelMore: String,
    val labelPlay: String,
    val labelPause: String,
    val labelText: String,
    // Appearance sheet
    val tabText: String,
    val tabAdvanced: String,
    val legendTitle: String,
    val legendHard: String,
    val legendSimilar: String,
    val legendNote: String,
) {
    fun range(n: Int): String = rangePattern.replace("{n}", n.toString())
}

fun goToCopy(language: AppLanguage): GoToCopy = when (language) {
    AppLanguage.RU -> GoToCopy(
        title = "Перейти", surah = "Сура", ayah = "Аят", rangePattern = "от 1 до {n}", go = "Перейти",
        chooseSurah = "Выбрать суру", searchHint = "Название или номер суры", clearSearch = "Очистить", nothingFound = "Ничего не найдено",
        labelView = "Вид", labelPages = "Страницы", labelByHeart = "Наизусть", labelMore = "Ещё",
        labelPlay = "Слушать", labelPause = "Пауза", labelText = "Текстом",
        tabText = "Текст", tabAdvanced = "Дополнительно",
        legendTitle = "Значки у аята", legendHard = "трудный аят", legendSimilar = "есть похожие аяты", legendNote = "есть заметка",
    )
    AppLanguage.EN -> GoToCopy(
        title = "Go to", surah = "Surah", ayah = "Ayah", rangePattern = "1 to {n}", go = "Go",
        chooseSurah = "Choose a surah", searchHint = "Surah name or number", clearSearch = "Clear", nothingFound = "Nothing found",
        labelView = "Text", labelPages = "Pages", labelByHeart = "By heart", labelMore = "More",
        labelPlay = "Play", labelPause = "Pause", labelText = "As text",
        tabText = "Text", tabAdvanced = "More",
        legendTitle = "Ayah marks", legendHard = "hard ayah", legendSimilar = "has similar ayahs", legendNote = "has a note",
    )
    AppLanguage.TG -> GoToCopy(
        title = "Гузаштан", surah = "Сура", ayah = "Оят", rangePattern = "аз 1 то {n}", go = "Гузаштан",
        chooseSurah = "Интихоби сура", searchHint = "Номи сура ё рақам", clearSearch = "Тоза кардан", nothingFound = "Ҳеҷ чиз ёфт нашуд",
        labelView = "Намуд", labelPages = "Саҳифаҳо", labelByHeart = "Аз ёд", labelMore = "Бештар",
        labelPlay = "Гӯш", labelPause = "Таваққуф", labelText = "Матн",
        tabText = "Матн", tabAdvanced = "Иловагӣ",
        legendTitle = "Аломатҳои оят", legendHard = "ояти душвор", legendSimilar = "оятҳои монанд ҳастанд", legendNote = "ёддошт ҳаст",
    )
    AppLanguage.UZ -> GoToCopy(
        title = "O‘tish", surah = "Sura", ayah = "Oyat", rangePattern = "1 dan {n} gacha", go = "O‘tish",
        chooseSurah = "Sura tanlash", searchHint = "Sura nomi yoki raqami", clearSearch = "Tozalash", nothingFound = "Hech narsa topilmadi",
        labelView = "Ko‘rinish", labelPages = "Sahifalar", labelByHeart = "Yoddan", labelMore = "Yana",
        labelPlay = "Tinglash", labelPause = "Pauza", labelText = "Matn",
        tabText = "Matn", tabAdvanced = "Qo‘shimcha",
        legendTitle = "Oyat belgilari", legendHard = "qiyin oyat", legendSimilar = "o‘xshash oyatlar bor", legendNote = "eslatma bor",
    )
    AppLanguage.TR -> GoToCopy(
        title = "Git", surah = "Sure", ayah = "Ayet", rangePattern = "1 – {n}", go = "Git",
        chooseSurah = "Sure seç", searchHint = "Sure adı veya numarası", clearSearch = "Temizle", nothingFound = "Sonuç yok",
        labelView = "Görünüm", labelPages = "Sayfalar", labelByHeart = "Ezber", labelMore = "Diğer",
        labelPlay = "Dinle", labelPause = "Duraklat", labelText = "Metin",
        tabText = "Metin", tabAdvanced = "Gelişmiş",
        legendTitle = "Ayet işaretleri", legendHard = "zor ayet", legendSimilar = "benzer ayetler var", legendNote = "not var",
    )
}

@Composable
fun rememberGoToCopy(language: AppLanguage = LocalAppLanguage.current): GoToCopy = remember(language) { goToCopy(language) }
