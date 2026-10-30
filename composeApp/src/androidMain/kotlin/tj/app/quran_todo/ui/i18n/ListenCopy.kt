package tj.app.quran_todo.ui.i18n

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import tj.app.quran_todo.common.i18n.AppLanguage
import tj.app.quran_todo.common.i18n.LocalAppLanguage

/**
 * Strings of the echo mode ("I repeat after the reciter") and of "Listen to the old" (task F1b) in the five app languages.
 * Texts with numbers are patterns with `{n}`, `{a}`, `{b}` and `{passages}` placeholders filled by the functions below, so each
 * language keeps its own word order. "Your turn" and "s" are those of the reader ([ReaderCopy]).
 */
@Immutable
data class ListenCopy(
    val language: AppLanguage,

    // The echo mode
    val echoChip: String,
    val echoTitle: String,
    val echoHint: String,
    val echoSwitch: String,
    val echoSwitchHint: String,
    val yourTurn: String,
    val secondsShort: String,

    // Listen to the old
    val oldButtonPattern: String,
    val oldPlayingPattern: String,
    val oldTitle: String,
    val oldSubtitlePattern: String,
    val modeOnce: String,
    val modeEcho: String,
    val passageOfPattern: String,
    val previousPassage: String,
    val nextPassage: String,
    val play: String,
    val pause: String,
    val done: String,
    val markHeard: String,
    val heardCountPattern: String,
    val markedPattern: String,
    val listenedBadge: String,
    val recalledBadge: String,
    val startAgain: String,
    val notRecallNote: String,
    val dockQueuePattern: String,
    val sheetEmpty: String,
    val nowPlayingLabel: String,

    // Progress
    val progressListenedPattern: String,
    val progressListenedMonthPattern: String,
) {
    private fun String.fill(vararg values: Pair<String, Any>): String =
        values.fold(this) { text, (key, value) -> text.replace("{$key}", value.toString()) }

    /** "Listen to the old · ~10 min": the button of the lesson card. */
    fun oldButton(minutes: Int): String = oldButtonPattern.fill("n" to minutes)

    /** "Listening to the old · 2 of 5": the same button while the queue plays. */
    fun oldPlaying(position: Int, total: Int): String = oldPlayingPattern.fill("a" to position, "b" to total)

    /** "Today's circle: 5 passages · ~10 min". */
    fun oldSubtitle(count: Int, minutes: Int): String =
        oldSubtitlePattern.fill("passages" to passageCountLabel(count, language), "n" to minutes)

    /** "Passage 2 of 5". */
    fun passageOf(position: Int, total: Int): String = passageOfPattern.fill("a" to position, "b" to total)

    /** "Heard to the end: 3 passages". */
    fun heardCount(count: Int): String = heardCountPattern.fill("passages" to passageCountLabel(count, language))

    /** "Marked: listened to 3 passages". */
    fun marked(count: Int): String = markedPattern.fill("passages" to passageCountLabel(count, language))

    /** "Old 2/5": the part of the dock's status line while the queue plays. */
    fun dockQueue(position: Int, total: Int): String = dockQueuePattern.fill("a" to position, "b" to total)

    /** "Listened: 3 passages", the line of today in the activity of the progress screen. */
    fun progressListened(count: Int): String = progressListenedPattern.fill("passages" to passageCountLabel(count, language))

    /** "Listened in 30 days: 7 passages". */
    /** "~3 min": the length of a passage in the list of the sheet. */
    fun minutes(minutes: Int): String = homeCopy(language).lessonMinutes(minutes)

    fun progressListenedMonth(count: Int): String =
        progressListenedMonthPattern.fill("passages" to passageCountLabel(count, language))
}

@Composable
fun rememberListenCopy(language: AppLanguage = LocalAppLanguage.current): ListenCopy =
    remember(language) { listenCopy(language) }

fun listenCopy(language: AppLanguage): ListenCopy {
    val reader = readerCopy(language)
    return when (language) {
        AppLanguage.RU -> ListenCopy(
            language = language,
            echoChip = "Эхо",
            echoTitle = "Эхо",
            echoHint = "Чтец читает аят, затем идёт тишина чуть длиннее аята: повторите вслух. Цифра — сколько раз на каждый аят.",
            echoSwitch = "Повторяю за чтецом",
            echoSwitchHint = "После каждого чтения — тишина: повторите вслух.",
            yourTurn = reader.yourTurn,
            secondsShort = reader.secondsShort,
            oldButtonPattern = "Слушать старое · ~{n} мин",
            oldPlayingPattern = "Слушаю старое · {a} из {b}",
            oldTitle = "Слушать старое",
            oldSubtitlePattern = "Круг на сегодня: {passages} · ~{n} мин",
            modeOnce = "Один раз",
            modeEcho = "С эхом",
            passageOfPattern = "Отрывок {a} из {b}",
            previousPassage = "Предыдущий отрывок",
            nextPassage = "Следующий отрывок",
            play = reader.play,
            pause = reader.pause,
            done = "Готово",
            markHeard = "Отметить: повторил на слух",
            heardCountPattern = "Дослушано до конца: {passages}",
            markedPattern = "Отмечено: прослушано {passages}",
            listenedBadge = "Прослушано ✓",
            recalledBadge = "Вспомнено ✓",
            startAgain = "Слушать снова",
            notRecallNote = "Это помощь, а не проверка: оценку «Помню / Трудно» даёт только «Вспомни», круг не сдвигается.",
            dockQueuePattern = "Старое {a}/{b}",
            sheetEmpty = "В круге на сегодня нет отрывков.",
            nowPlayingLabel = "Играет сейчас",
            progressListenedPattern = "Прослушано: {passages}",
            progressListenedMonthPattern = "Прослушано за 30 дней: {passages}",
        )
        AppLanguage.EN -> ListenCopy(
            language = language,
            echoChip = "Echo",
            echoTitle = "Echo",
            echoHint = "The reciter reads an ayah, then comes a silence a little longer than the ayah: repeat it aloud. The number is how many times per ayah.",
            echoSwitch = "I repeat after the reciter",
            echoSwitchHint = "After every reading there is a silence: repeat it aloud.",
            yourTurn = reader.yourTurn,
            secondsShort = reader.secondsShort,
            oldButtonPattern = "Listen to the old · ~{n} min",
            oldPlayingPattern = "Listening to the old · {a} of {b}",
            oldTitle = "Listen to the old",
            oldSubtitlePattern = "Today's circle: {passages} · ~{n} min",
            modeOnce = "Once",
            modeEcho = "With echo",
            passageOfPattern = "Passage {a} of {b}",
            previousPassage = "Previous passage",
            nextPassage = "Next passage",
            play = reader.play,
            pause = reader.pause,
            done = "Done",
            markHeard = "Mark: I repeated it by ear",
            heardCountPattern = "Heard to the end: {passages}",
            markedPattern = "Marked: listened to {passages}",
            listenedBadge = "Listened ✓",
            recalledBadge = "Recalled ✓",
            startAgain = "Listen again",
            notRecallNote = "This is a help, not a check: only \"Recall\" gives \"I remember / Hard\", and the circle does not move.",
            dockQueuePattern = "Old {a}/{b}",
            sheetEmpty = "There are no passages in today's circle.",
            nowPlayingLabel = "Playing now",
            progressListenedPattern = "Listened: {passages}",
            progressListenedMonthPattern = "Listened in 30 days: {passages}",
        )
        AppLanguage.TG -> ListenCopy(
            language = language,
            echoChip = "Акси садо",
            echoTitle = "Акси садо",
            echoHint = "Қорӣ оятро мехонад, баъд сукути аз оят каме дарозтар меояд: бо овоз такрор кунед. Рақам — чанд маротиба барои ҳар оят.",
            echoSwitch = "Аз паси қорӣ такрор мекунам",
            echoSwitchHint = "Пас аз ҳар хондан сукут меояд: бо овоз такрор кунед.",
            yourTurn = reader.yourTurn,
            secondsShort = reader.secondsShort,
            oldButtonPattern = "Шунидани қадим · ~{n} дақ.",
            oldPlayingPattern = "Қадимро мешунавам · {a} аз {b}",
            oldTitle = "Шунидани қадим",
            oldSubtitlePattern = "Давраи имрӯза: {passages} · ~{n} дақ.",
            modeOnce = "Як бор",
            modeEcho = "Бо акси садо",
            passageOfPattern = "Қисми {a} аз {b}",
            previousPassage = "Қисми пешина",
            nextPassage = "Қисми оянда",
            play = reader.play,
            pause = reader.pause,
            done = "Тайёр",
            markHeard = "Қайд кунед: бо гӯш такрор кардам",
            heardCountPattern = "То охир шунида шуд: {passages}",
            markedPattern = "Қайд шуд: шунида шуд {passages}",
            listenedBadge = "Шунида шуд ✓",
            recalledBadge = "Ба ёд оварда шуд ✓",
            startAgain = "Боз шунавед",
            notRecallNote = "Ин кӯмак аст, на санҷиш: баҳои «Дар ёд дорам / Душвор»-ро танҳо «Ба ёд ор» медиҳад, давра ҷойгузин намешавад.",
            dockQueuePattern = "Қадим {a}/{b}",
            sheetEmpty = "Дар давраи имрӯза қисм нест.",
            nowPlayingLabel = "Ҳозир садо медиҳад",
            progressListenedPattern = "Шунида шуд: {passages}",
            progressListenedMonthPattern = "Дар 30 рӯз шунида шуд: {passages}",
        )
        AppLanguage.UZ -> ListenCopy(
            language = language,
            echoChip = "Aks-sado",
            echoTitle = "Aks-sado",
            echoHint = "Qori oyatni o‘qiydi, so‘ng oyatdan biroz uzunroq sukunat keladi: ovoz chiqarib takrorlang. Raqam — har oyat necha marta.",
            echoSwitch = "Qoridan keyin takrorlayman",
            echoSwitchHint = "Har o‘qishdan keyin sukunat: ovoz chiqarib takrorlang.",
            yourTurn = reader.yourTurn,
            secondsShort = reader.secondsShort,
            oldButtonPattern = "Eskini tinglash · ~{n} daq",
            oldPlayingPattern = "Eskini tinglayapman · {a}/{b}",
            oldTitle = "Eskini tinglash",
            oldSubtitlePattern = "Bugungi doira: {passages} · ~{n} daq",
            modeOnce = "Bir marta",
            modeEcho = "Aks-sado bilan",
            passageOfPattern = "{b} parchadan {a}-parcha",
            previousPassage = "Oldingi parcha",
            nextPassage = "Keyingi parcha",
            play = reader.play,
            pause = reader.pause,
            done = "Tayyor",
            markHeard = "Belgilash: quloq bilan takrorladim",
            heardCountPattern = "Oxirigacha eshitildi: {passages}",
            markedPattern = "Belgilandi: eshitildi {passages}",
            listenedBadge = "Eshitildi ✓",
            recalledBadge = "Eslab aytildi ✓",
            startAgain = "Yana tinglash",
            notRecallNote = "Bu yordam, tekshiruv emas: «Eslayman / Qiyin» bahosini faqat «Eslab ayt» beradi, doira siljimaydi.",
            dockQueuePattern = "Eski {a}/{b}",
            sheetEmpty = "Bugungi doirada parcha yo‘q.",
            nowPlayingLabel = "Hozir eshitilmoqda",
            progressListenedPattern = "Eshitildi: {passages}",
            progressListenedMonthPattern = "30 kunda eshitildi: {passages}",
        )
        AppLanguage.TR -> ListenCopy(
            language = language,
            echoChip = "Yankı",
            echoTitle = "Yankı",
            echoHint = "Okuyucu ayeti okur, ardından ayetten biraz uzun bir sessizlik gelir: sesli tekrar edin. Sayı, her ayetin kaç kez tekrarlanacağıdır.",
            echoSwitch = "Okuyucudan sonra tekrar ederim",
            echoSwitchHint = "Her okumadan sonra sessizlik: sesli tekrar edin.",
            yourTurn = reader.yourTurn,
            secondsShort = reader.secondsShort,
            oldButtonPattern = "Eskiyi dinle · ~{n} dk",
            oldPlayingPattern = "Eskiyi dinliyorum · {a} / {b}",
            oldTitle = "Eskiyi dinle",
            oldSubtitlePattern = "Bugünün döngüsü: {passages} · ~{n} dk",
            modeOnce = "Bir kez",
            modeEcho = "Yankılı",
            passageOfPattern = "Bölüm {a} / {b}",
            previousPassage = "Önceki bölüm",
            nextPassage = "Sonraki bölüm",
            play = reader.play,
            pause = reader.pause,
            done = "Bitti",
            markHeard = "İşaretle: kulaktan tekrar ettim",
            heardCountPattern = "Sonuna kadar dinlendi: {passages}",
            markedPattern = "İşaretlendi: dinlendi {passages}",
            listenedBadge = "Dinlendi ✓",
            recalledBadge = "Hatırlandı ✓",
            startAgain = "Yeniden dinle",
            notRecallNote = "Bu bir yardım, sınav değil: \"Hatırlıyorum / Zor\" notunu yalnızca \"Hatırla\" verir, döngü ilerlemez.",
            dockQueuePattern = "Eski {a}/{b}",
            sheetEmpty = "Bugünün döngüsünde bölüm yok.",
            nowPlayingLabel = "Şimdi çalıyor",
            progressListenedPattern = "Dinlendi: {passages}",
            progressListenedMonthPattern = "30 günde dinlendi: {passages}",
        )
    }
}
