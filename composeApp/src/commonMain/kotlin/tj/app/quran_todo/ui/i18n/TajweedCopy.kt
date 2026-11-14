package tj.app.quran_todo.ui.i18n

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import tj.app.quran_todo.common.i18n.AppLanguage
import tj.app.quran_todo.common.i18n.LocalAppLanguage
import tj.app.quran_todo.common.tajweed.TajweedGroup
import tj.app.quran_todo.common.tajweed.TajweedRule

/** The name of a rule as the language writes the Arabic term, and one line on how it is read. */
@Immutable
data class TajweedRuleCopy(val name: String, val how: String)

/**
 * Strings of the tajweed colours (task F3b) in the five app languages: the switch and the download line of the "Aa" sheet, and the
 * legend "What the colours mean". The names of the rules are the Arabic terms in the way each language writes them (ru "идгам", "ихфа",
 * "икляб", "калькаля", "мадд"; tr "idğam", "ihfa", "iklab", "kalkale", "med"), not translations.
 */
@Immutable
data class TajweedCopy(
    val language: AppLanguage,

    // The "Aa" sheet
    val switchTitle: String,
    val switchHint: String,
    val legendRow: String,
    val downloading: String,
    val needInternet: String,
    val retry: String,

    // The legend
    val legendTitle: String,
    val legendIntro: String,
    val groups: Map<TajweedGroup, String>,
    val rules: Map<TajweedRule, TajweedRuleCopy>,
) {
    fun group(group: TajweedGroup): String = groups.getValue(group)

    fun rule(rule: TajweedRule): TajweedRuleCopy = rules.getValue(rule)
}

@Composable
fun rememberTajweedCopy(language: AppLanguage = LocalAppLanguage.current): TajweedCopy =
    remember(language) { tajweedCopy(language) }

private fun groupsOf(
    silent: String,
    madd: String,
    nasal: String,
    iqlab: String,
    qalqalah: String,
    merge: String,
): Map<TajweedGroup, String> = mapOf(
    TajweedGroup.Silent to silent,
    TajweedGroup.Madd to madd,
    TajweedGroup.Nasal to nasal,
    TajweedGroup.Iqlab to iqlab,
    TajweedGroup.Qalqalah to qalqalah,
    TajweedGroup.Merge to merge,
)

/** The rules in the order of the legend; every rule has a name and a line. */
private fun rulesOf(vararg entries: Pair<String, String>): Map<TajweedRule, TajweedRuleCopy> {
    require(entries.size == TajweedRule.entries.size) { "${entries.size} rules" }
    return TajweedRule.entries.zip(entries.toList()).associate { (rule, entry) -> rule to TajweedRuleCopy(entry.first, entry.second) }
}

fun tajweedCopy(language: AppLanguage): TajweedCopy = when (language) {
    AppLanguage.RU -> TajweedCopy(
        language = language,
        switchTitle = "Таджвид",
        switchHint = "Правила чтения — цветом",
        legendRow = "Что значат цвета",
        downloading = "Загружаю разметку таджвида · 2,5 МБ",
        needInternet = "Нужен интернет один раз",
        retry = "Ещё раз",
        legendTitle = "Что значат цвета",
        legendIntro = "Цвет буквы показывает правило чтения. Сам текст не меняется.",
        groups = groupsOf(
            silent = "Не читается",
            madd = "Мадд — протяжение",
            nasal = "Гунна и ихфа",
            iqlab = "Икляб",
            qalqalah = "Калькаля",
            merge = "Идгам — слияние",
        ),
        rules = rulesOf(
            "Хамзат аль-васль" to "Не произносится, если читать слитно с предыдущим словом.",
            "Лям шамсия" to "«Ль» не произносится, следующая буква читается удвоенной.",
            "Непроизносимая буква" to "Буква написана, но при чтении пропускается.",
            "Мадд табии" to "Гласный тянется 2 счёта.",
            "Мадд джаиз" to "Допустимое протяжение: 2, 4 или 6 счётов.",
            "Мадд ваджиб" to "Обязательное протяжение: 4–5 счётов.",
            "Мадд лязим" to "Необходимое протяжение: 6 счётов.",
            "Гунна" to "Носовой звук на нуне и миме с шаддой, 2 счёта.",
            "Идгам с гунной" to "Нун сакина или танвин сливается с буквами ي ن م و с носовым звуком.",
            "Ихфа" to "Нун сакина или танвин скрывается перед 15 буквами, с носовым звуком.",
            "Ихфа шафави" to "Мим сакина перед «ба» произносится скрыто, губы едва размыкаются.",
            "Икляб" to "Нун сакина или танвин перед «ба» становится «мим», с носовым звуком.",
            "Калькаля" to "Лёгкий отскок звука на буквах ق ط ب ج د с сукуном.",
            "Идгам без гунны" to "Нун сакина или танвин сливается с «ль» или «ра» без носового звука.",
            "Идгам мутаджанисайн" to "Две буквы с одним местом произнесения сливаются в одну.",
            "Идгам мутакарибайн" to "Две буквы с близким местом произнесения сливаются в одну.",
            "Идгам шафави" to "Мим сакина сливается со следующим «мим».",
        ),
    )
    AppLanguage.EN -> TajweedCopy(
        language = language,
        switchTitle = "Tajweed",
        switchHint = "Rules of recitation, in colour",
        legendRow = "What the colours mean",
        downloading = "Downloading tajweed markup · 2.5 MB",
        needInternet = "An internet connection is needed once",
        retry = "Retry",
        legendTitle = "What the colours mean",
        legendIntro = "The colour of a letter shows its rule of recitation. The text itself does not change.",
        groups = groupsOf(
            silent = "Not pronounced",
            madd = "Madd: prolongation",
            nasal = "Ghunna and ikhfa",
            iqlab = "Iqlab",
            qalqalah = "Qalqalah",
            merge = "Idgham: merging",
        ),
        rules = rulesOf(
            "Hamzat al-wasl" to "Not pronounced when it is joined to the word before.",
            "Lam shamsiyah" to "The lam is silent and the next letter is doubled.",
            "Silent letter" to "Written, but skipped in reading.",
            "Madd tabii" to "The vowel is stretched for 2 counts.",
            "Madd jaiz" to "Permissible madd: 2, 4 or 6 counts.",
            "Madd wajib" to "Obligatory madd: 4–5 counts.",
            "Madd lazim" to "Necessary madd: 6 counts.",
            "Ghunna" to "A nasal sound on noon and meem with shaddah, 2 counts.",
            "Idgham with ghunna" to "Noon sakinah or tanween merges into ي ن م و with a nasal sound.",
            "Ikhfa" to "Noon sakinah or tanween is hidden before 15 letters, with a nasal sound.",
            "Ikhfa shafawi" to "Meem sakinah before ba is hidden; the lips barely open.",
            "Iqlab" to "Noon sakinah or tanween before ba turns into meem, with a nasal sound.",
            "Qalqalah" to "A light echo on ق ط ب ج د with a sukun.",
            "Idgham without ghunna" to "Noon sakinah or tanween merges into lam or ra without a nasal sound.",
            "Idgham mutajanisayn" to "Two letters from the same place of articulation merge.",
            "Idgham mutaqaribayn" to "Two letters from close places of articulation merge.",
            "Idgham shafawi" to "Meem sakinah merges into the meem after it.",
        ),
    )
    AppLanguage.TG -> TajweedCopy(
        language = language,
        switchTitle = "Таҷвид",
        switchHint = "Қоидаҳои қироат бо ранг",
        legendRow = "Рангҳо чӣ маъно доранд",
        downloading = "Нишонагузории таҷвид зеркашӣ мешавад · 2,5 МБ",
        needInternet = "Як бор интернет лозим аст",
        retry = "Бори дигар",
        legendTitle = "Рангҳо чӣ маъно доранд",
        legendIntro = "Ранги ҳарф қоидаи қироатро нишон медиҳад. Худи матн тағйир намеёбад.",
        groups = groupsOf(
            silent = "Хонда намешавад",
            madd = "Мадд — кашидан",
            nasal = "Ғунна ва ихфо",
            iqlab = "Иқлоб",
            qalqalah = "Қалқала",
            merge = "Идғом — пайваст шудан",
        ),
        rules = rulesOf(
            "Ҳамзаи васл" to "Ҳангоми бо калимаи пешина пайваста хондан талаффуз намешавад.",
            "Ломи шамсӣ" to "«Л» хонда намешавад, ҳарфи баъдӣ дукарата талаффуз мешавад.",
            "Ҳарфи хонданашаванда" to "Ҳарф навишта шудааст, вале хонда намешавад.",
            "Мадди табиӣ" to "Садонок 2 ҳарака кашида мешавад.",
            "Мадди ҷоиз" to "Кашиши ҷоиз: 2, 4 ё 6 ҳарака.",
            "Мадди воҷиб" to "Кашиши воҷиб: 4–5 ҳарака.",
            "Мадди лозим" to "Кашиши лозим: 6 ҳарака.",
            "Ғунна" to "Овози бинӣ дар нун ва мими ташдиддор, 2 ҳарака.",
            "Идғом бо ғунна" to "Нуни соқин ё танвин ба ҳарфҳои ي ن م و бо овози бинӣ пайваст мешавад.",
            "Ихфо" to "Нуни соқин ё танвин пеш аз 15 ҳарф бо овози бинӣ пинҳон хонда мешавад.",
            "Ихфои шафавӣ" to "Мими соқин пеш аз «бо» пинҳон хонда мешавад, лабҳо базӯр кушода мешаванд.",
            "Иқлоб" to "Нуни соқин ё танвин пеш аз «бо» ба «мим» табдил меёбад, бо овози бинӣ.",
            "Қалқала" to "Акси садои сабук дар ҳарфҳои ق ط ب ج د бо сукун.",
            "Идғом бе ғунна" to "Нуни соқин ё танвин ба «лом» ё «ро» бе овози бинӣ пайваст мешавад.",
            "Идғоми мутаҷонисайн" to "Ду ҳарфи ҳаммахраҷ ба ҳам пайваст мешаванд.",
            "Идғоми мутақорибайн" to "Ду ҳарфи наздикмахраҷ ба ҳам пайваст мешаванд.",
            "Идғоми шафавӣ" to "Мими соқин ба мими баъдӣ пайваст мешавад.",
        ),
    )
    AppLanguage.UZ -> TajweedCopy(
        language = language,
        switchTitle = "Tajvid",
        switchHint = "Qiroat qoidalari rang bilan",
        legendRow = "Ranglar nimani anglatadi",
        downloading = "Tajvid belgilari yuklanmoqda · 2,5 MB",
        needInternet = "Bir marta internet kerak",
        retry = "Qayta urinish",
        legendTitle = "Ranglar nimani anglatadi",
        legendIntro = "Harfning rangi o‘qish qoidasini ko‘rsatadi. Matnning o‘zi o‘zgarmaydi.",
        groups = groupsOf(
            silent = "O‘qilmaydi",
            madd = "Madd — cho‘zish",
            nasal = "G‘unna va ixfo",
            iqlab = "Iqlob",
            qalqalah = "Qalqala",
            merge = "Idg‘om — qo‘shib o‘qish",
        ),
        rules = rulesOf(
            "Hamzatul vasl" to "Oldingi so‘z bilan ulab o‘qilganda talaffuz qilinmaydi.",
            "Lomi shamsiya" to "«Lom» o‘qilmaydi, undan keyingi harf ikki marta (tashdid bilan) o‘qiladi.",
            "O‘qilmaydigan harf" to "Harf yozilgan, lekin o‘qilmaydi.",
            "Madd tabiiy" to "Unli tovush 2 harakat cho‘ziladi.",
            "Madd joiz" to "Joiz cho‘zish: 2, 4 yoki 6 harakat.",
            "Madd vojib" to "Vojib cho‘zish: 4–5 harakat.",
            "Madd lozim" to "Lozim cho‘zish: 6 harakat.",
            "G‘unna" to "Tashdidli nun va mimda burun tovushi, 2 harakat.",
            "G‘unnali idg‘om" to "Sokin nun yoki tanvin ي ن م و harflariga burun tovushi bilan qo‘shiladi.",
            "Ixfo" to "Sokin nun yoki tanvin 15 ta harf oldida burun tovushi bilan yashirib o‘qiladi.",
            "Ixfoi shafaviy" to "Sokin mim «ba» oldida yashirib o‘qiladi, lablar zo‘rg‘a ochiladi.",
            "Iqlob" to "«Ba» oldida sokin nun yoki tanvin burun tovushi bilan «mim»ga aylanadi.",
            "Qalqala" to "Sukunli ق ط ب ج د harflarida yengil aks-sado.",
            "G‘unnasiz idg‘om" to "Sokin nun yoki tanvin burun tovushisiz «lom» yoki «ro»ga qo‘shiladi.",
            "Idg‘omi mutajonisayn" to "Chiqish joyi bir xil ikki harf qo‘shilib o‘qiladi.",
            "Idg‘omi mutaqoribayn" to "Chiqish joyi yaqin ikki harf qo‘shilib o‘qiladi.",
            "Idg‘omi shafaviy" to "Sokin mim o‘zidan keyingi mimga qo‘shiladi.",
        ),
    )
    AppLanguage.TR -> TajweedCopy(
        language = language,
        switchTitle = "Tecvid",
        switchHint = "Okuma kuralları renkli",
        legendRow = "Renkler ne anlama geliyor",
        downloading = "Tecvid işaretleri indiriliyor · 2,5 MB",
        needInternet = "Bir kez internet gerekli",
        retry = "Tekrar dene",
        legendTitle = "Renkler ne anlama geliyor",
        legendIntro = "Harfin rengi okuma kuralını gösterir. Metnin kendisi değişmez.",
        groups = groupsOf(
            silent = "Okunmayanlar",
            madd = "Med: uzatma",
            nasal = "Gunne ve ihfa",
            iqlab = "İklab",
            qalqalah = "Kalkale",
            merge = "İdğam: katma",
        ),
        rules = rulesOf(
            "Hemzetü’l-vasl" to "Önceki kelimeyle birleştirilerek okunurken telaffuz edilmez.",
            "Lam-ı şemsiyye" to "Lam okunmaz, sonraki harf şeddeli okunur.",
            "Okunmayan harf" to "Yazılır ama okunurken atlanır.",
            "Tabii med" to "Ünlü ses 2 elif miktarı uzatılır.",
            "Câiz med" to "Câiz uzatma: 2, 4 veya 6 elif miktarı.",
            "Vâcib med" to "Vâcib uzatma: 4–5 elif miktarı.",
            "Lâzım med" to "Lâzım uzatma: 6 elif miktarı.",
            "Gunne" to "Şeddeli nun ve mimde genizden ses, 2 elif miktarı.",
            "Gunneli idğam" to "Sakin nun veya tenvin, ي ن م و harflerine genizden sesle katılır.",
            "İhfa" to "Sakin nun veya tenvin, 15 harften önce genizden sesle gizlenir.",
            "İhfa-i şefevî" to "Sakin mim, be harfinden önce gizlenir; dudaklar zar zor aralanır.",
            "İklab" to "Sakin nun veya tenvin, be harfinden önce genizden sesle mime dönüşür.",
            "Kalkale" to "Sükûnlu ق ط ب ج د harflerinde hafif yankı.",
            "Gunnesiz idğam" to "Sakin nun veya tenvin, lam veya ra harfine genizden ses olmadan katılır.",
            "İdğam-ı mütecâniseyn" to "Mahreci aynı olan iki harf birbirine katılır.",
            "İdğam-ı mütekāribeyn" to "Mahreci yakın iki harf birbirine katılır.",
            "İdğam-ı şefevî" to "Sakin mim, kendinden sonraki mime katılır.",
        ),
    )
}
