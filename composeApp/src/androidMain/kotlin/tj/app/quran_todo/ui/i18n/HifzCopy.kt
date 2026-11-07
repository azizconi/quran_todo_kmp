package tj.app.quran_todo.ui.i18n

import androidx.compose.runtime.Immutable
import tj.app.quran_todo.common.i18n.AppLanguage
import tj.app.quran_todo.hifz.model.ReviewGrade
import tj.app.quran_todo.ui.hifz.AyahMark
import tj.app.quran_todo.ui.state.HifzHintMode

/**
 * Strings of the recall component: "Check yourself" in the reader and "Recall" in the lesson. The grade names
 * follow the glossary of docs/redesign/ANDROID_REDESIGN.md, section 10. Texts with a value in them use
 * `{name}`-style placeholders filled by the helpers below.
 */
@Immutable
data class HifzCopy(
    val language: AppLanguage,
    /** Title of the mode: "Check yourself". */
    val modeName: String,
    /** Label of the eye button that starts the mode. */
    val eyeAction: String,
    /** Label of the close button of the mode's top bar. */
    val exit: String,
    val ayah: String,
    /** What a screen reader says instead of the hidden text. */
    val hiddenText: String,
    val peekHint: String,
    val reciteFromMemory: String,
    val listen: String,
    val listenPause: String,
    val reveal: String,
    // The two answers to an ayah
    val markTitle: String,
    val remembered: String,
    val hardMark: String,
    val answeredFmt: String,
    // The grades of a passage
    val forgot: String,
    val hard: String,
    val good: String,
    val easy: String,
    // Leaving
    val exitTitle: String,
    val exitBodyFmt: String,
    val keepGoing: String,
    val leave: String,
    // The grade sheet
    val gradeTitle: String,
    val gradeSuggested: String,
    val gradeChosen: String,
    val hardAyahsTitle: String,
    val hardAyahsHint: String,
    val done: String,
    // What was written
    val savedReview: String,
    val savedCheck: String,
    // The hint levels (task F2a)
    val hintChipFmt: String,
    val hintTitle: String,
    val hintSubtitle: String,
    val hintNone: String,
    val hintFirstWord: String,
    val hintFirstLetters: String,
    val hintEveryOther: String,
    val hintProgressive: String,
    val hintNoneInfo: String,
    val hintFirstWordInfo: String,
    val hintFirstLettersInfo: String,
    val hintEveryOtherInfo: String,
    val hintProgressiveInfo: String,
    val hideMore: String,
    val hiddenShareFmt: String,
    // Peeking
    val peekedFmt: String,
) {
    fun label(grade: ReviewGrade): String = when (grade) {
        ReviewGrade.FORGOT -> forgot
        ReviewGrade.HARD -> hard
        ReviewGrade.GOOD -> good
        ReviewGrade.EASY -> easy
    }

    fun label(mark: AyahMark): String = when (mark) {
        AyahMark.REMEMBERED -> remembered
        AyahMark.HARD -> hardMark
    }

    /** "Answer: Hard" for screen readers and the mark of an answered ayah. */
    fun answered(mark: AyahMark): String = answeredFmt.replace("{mark}", label(mark))

    fun exitBody(done: Int, total: Int): String =
        exitBodyFmt.replace("{done}", done.toString()).replace("{total}", total.toString())

    /** The name of a hint level: "Every other word". */
    fun hintName(mode: HifzHintMode): String = when (mode) {
        HifzHintMode.NONE -> hintNone
        HifzHintMode.FIRST_WORD -> hintFirstWord
        HifzHintMode.FIRST_LETTERS -> hintFirstLetters
        HifzHintMode.EVERY_OTHER -> hintEveryOther
        HifzHintMode.PROGRESSIVE -> hintProgressive
    }

    /** One line about what a hint level leaves on screen. */
    fun hintDescription(mode: HifzHintMode): String = when (mode) {
        HifzHintMode.NONE -> hintNoneInfo
        HifzHintMode.FIRST_WORD -> hintFirstWordInfo
        HifzHintMode.FIRST_LETTERS -> hintFirstLettersInfo
        HifzHintMode.EVERY_OTHER -> hintEveryOtherInfo
        HifzHintMode.PROGRESSIVE -> hintProgressiveInfo
    }

    /** The chip next to the buttons of the recall: "Hint: First word". */
    fun hintChip(mode: HifzHintMode): String = hintChipFmt.replace("{level}", hintName(mode))

    /** "25 % hidden": how much of the ayah the progressive hint has hidden so far. */
    fun hiddenShare(percent: Int): String = hiddenShareFmt.replace("{n}", percent.toString())

    /** "Peeked: 2 words": [n] counts a tapped word as one and a held ayah as two. */
    fun peeked(n: Int): String = peekedFmt.replace("{n}", wordCountLabel(n, language))
}

/** "1 word", "2 words"; Russian 1 слово / 2 слова / 5 слов; Tajik, Uzbek and Turkish do not inflect after a number. */
fun wordCountLabel(n: Int, language: AppLanguage): String = when (language) {
    AppLanguage.RU -> "$n ${russianPlural(n, "слово", "слова", "слов")}"
    AppLanguage.EN -> "$n ${if (n == 1) "word" else "words"}"
    AppLanguage.TG -> "$n калима"
    AppLanguage.UZ -> "$n ta so‘z"
    AppLanguage.TR -> "$n kelime"
}

fun hifzCopy(language: AppLanguage): HifzCopy = when (language) {
    AppLanguage.RU -> HifzCopy(
        language = language,
        modeName = "Проверь себя", eyeAction = "Проверь себя", exit = "Выйти из проверки", ayah = "Аят",
        hiddenText = "Текст аята скрыт", peekHint = "Нажмите на скрытое слово или удерживайте аят, чтобы подсмотреть",
        reciteFromMemory = "Повторите по памяти", listen = "Послушать", listenPause = "Пауза", reveal = "Показать",
        markTitle = "Как получилось с этим аятом?", remembered = "Помню", hardMark = "Трудно",
        answeredFmt = "Ответ: {mark}",
        forgot = "Забыл", hard = "Трудно", good = "Хорошо", easy = "Легко",
        exitTitle = "Выйти из проверки?",
        exitBodyFmt = "Отвечено {done} из {total}. Результат запишется, только когда вы дойдёте до конца.",
        keepGoing = "Продолжить", leave = "Выйти",
        gradeTitle = "Оцените отрывок", gradeSuggested = "Предложено по вашим ответам", gradeChosen = "Вы выбрали сами",
        hardAyahsTitle = "Трудные аяты", hardAyahsHint = "Нажмите на аят, чтобы поставить или снять отметку",
        done = "Готово",
        savedReview = "Повторение отрывка засчитано", savedCheck = "Проверка записана",
        hintChipFmt = "Подсказка: {level}", hintTitle = "Подсказка",
        hintSubtitle = "Сколько аята остаётся на экране, пока вы вспоминаете",
        hintNone = "Нет", hintFirstWord = "Первое слово", hintFirstLetters = "Первые буквы",
        hintEveryOther = "Каждое второе слово", hintProgressive = "Постепенно скрывать",
        hintNoneInfo = "Аят скрыт целиком",
        hintFirstWordInfo = "Видно первое слово",
        hintFirstLettersInfo = "На месте каждого слова — его первая буква",
        hintEveryOtherInfo = "Видны 1-е, 3-е, 5-е слова, остальные скрыты",
        hintProgressiveInfo = "Аят виден целиком, вы скрываете его по четвертям",
        hideMore = "Скрыть ещё", hiddenShareFmt = "Скрыто {n} %",
        peekedFmt = "Подсмотрено: {n}",
    )
    AppLanguage.EN -> HifzCopy(
        language = language,
        modeName = "Check yourself", eyeAction = "Check yourself", exit = "Leave the check", ayah = "Ayah",
        hiddenText = "Ayah text is hidden", peekHint = "Tap a hidden word or hold the ayah to peek",
        reciteFromMemory = "Recite from memory", listen = "Listen", listenPause = "Pause", reveal = "Reveal",
        markTitle = "How did this ayah go?", remembered = "I remember", hardMark = "Hard",
        answeredFmt = "Answer: {mark}",
        forgot = "Forgot", hard = "Hard", good = "Good", easy = "Easy",
        exitTitle = "Leave the check?",
        exitBodyFmt = "Answered {done} of {total}. The result is written only when you reach the end.",
        keepGoing = "Keep going", leave = "Leave",
        gradeTitle = "Grade the passage", gradeSuggested = "Suggested from your answers", gradeChosen = "Your own choice",
        hardAyahsTitle = "Hard ayahs", hardAyahsHint = "Tap an ayah to mark or unmark it",
        done = "Done",
        savedReview = "Passage repetition counted", savedCheck = "Check recorded",
        hintChipFmt = "Hint: {level}", hintTitle = "Hint",
        hintSubtitle = "How much of the ayah stays on screen while you recall",
        hintNone = "None", hintFirstWord = "First word", hintFirstLetters = "First letters",
        hintEveryOther = "Every other word", hintProgressive = "Hide gradually",
        hintNoneInfo = "The whole ayah is hidden",
        hintFirstWordInfo = "The first word stays visible",
        hintFirstLettersInfo = "Each word shows only its first letter",
        hintEveryOtherInfo = "Words 1, 3, 5... stay visible, the rest is hidden",
        hintProgressiveInfo = "The ayah starts whole and you hide it a quarter at a time",
        hideMore = "Hide more", hiddenShareFmt = "{n}% hidden",
        peekedFmt = "Peeked: {n}",
    )
    AppLanguage.TG -> HifzCopy(
        language = language,
        modeName = "Худро санҷед", eyeAction = "Худро санҷед", exit = "Баромадан аз санҷиш", ayah = "Оят",
        hiddenText = "Матни оят пинҳон аст", peekHint = "Барои дидан ба калимаи пинҳон зер кунед ё оятро нигоҳ доред",
        reciteFromMemory = "Аз ёд такрор кунед", listen = "Гӯш кардан", listenPause = "Таваққуф", reveal = "Нишон додан",
        markTitle = "Ин оят чӣ тавр гузашт?", remembered = "Дар ёд дорам", hardMark = "Душвор",
        answeredFmt = "Ҷавоб: {mark}",
        forgot = "Фаромӯш", hard = "Душвор", good = "Хуб", easy = "Осон",
        exitTitle = "Аз санҷиш мебароед?",
        exitBodyFmt = "Ҷавоб дода шуд: {done} аз {total}. Натиҷа танҳо вақте сабт мешавад, ки то охир расед.",
        keepGoing = "Идома додан", leave = "Баромадан",
        gradeTitle = "Порчаро баҳо диҳед", gradeSuggested = "Аз рӯи ҷавобҳои шумо пешниҳод шуд",
        gradeChosen = "Интихоби худи шумо",
        hardAyahsTitle = "Оятҳои душвор", hardAyahsHint = "Барои гузоштан ё бардоштани аломат ба оят зер кунед",
        done = "Тайёр",
        savedReview = "Такрори порча ҳисоб шуд", savedCheck = "Санҷиш сабт шуд",
        hintChipFmt = "Ишора: {level}", hintTitle = "Ишора",
        hintSubtitle = "Ҳангоми ба ёд овардан аз оят чӣ қадар дар экран мемонад",
        hintNone = "Нест", hintFirstWord = "Калимаи аввал", hintFirstLetters = "Ҳарфҳои аввал",
        hintEveryOther = "Ҳар калимаи дуюм", hintProgressive = "Тадриҷан пинҳон кардан",
        hintNoneInfo = "Оят пурра пинҳон аст",
        hintFirstWordInfo = "Калимаи аввал намоён аст",
        hintFirstLettersInfo = "Ба ҷои ҳар калима ҳарфи аввали он меистад",
        hintEveryOtherInfo = "Калимаҳои 1, 3, 5 намоёнанд, боқӣ пинҳон",
        hintProgressiveInfo = "Оят аввал пурра аст, шумо онро қисм-қисм пинҳон мекунед",
        hideMore = "Боз пинҳон кардан", hiddenShareFmt = "{n}% пинҳон аст",
        peekedFmt = "Нигоҳ карда шуд: {n}",
    )
    AppLanguage.UZ -> HifzCopy(
        language = language,
        modeName = "O‘zingizni tekshiring", eyeAction = "O‘zingizni tekshiring", exit = "Tekshiruvdan chiqish",
        ayah = "Oyat",
        hiddenText = "Oyat matni yashirilgan", peekHint = "Ko‘rish uchun yashirin so‘zni bosing yoki oyatni bosib turing",
        reciteFromMemory = "Yoddan takrorlang", listen = "Tinglash", listenPause = "Pauza", reveal = "Ko‘rsatish",
        markTitle = "Bu oyat qanday chiqdi?", remembered = "Esimda", hardMark = "Qiyin",
        answeredFmt = "Javob: {mark}",
        forgot = "Unutdim", hard = "Qiyin", good = "Yaxshi", easy = "Oson",
        exitTitle = "Tekshiruvdan chiqasizmi?",
        exitBodyFmt = "Javob berildi: {done} / {total}. Natija faqat oxirigacha yetganingizda yoziladi.",
        keepGoing = "Davom etish", leave = "Chiqish",
        gradeTitle = "Parchani baholang", gradeSuggested = "Javoblaringiz asosida taklif qilindi",
        gradeChosen = "O‘zingiz tanladingiz",
        hardAyahsTitle = "Qiyin oyatlar", hardAyahsHint = "Belgilash yoki olib tashlash uchun oyatni bosing",
        done = "Tayyor",
        savedReview = "Parchani takrorlash hisobga olindi", savedCheck = "Tekshiruv yozildi",
        hintChipFmt = "Yordam: {level}", hintTitle = "Yordam",
        hintSubtitle = "Yodga olayotganda oyatning qancha qismi ekranda qoladi",
        hintNone = "Yo‘q", hintFirstWord = "Birinchi so‘z", hintFirstLetters = "Birinchi harflar",
        hintEveryOther = "Har ikkinchi so‘z", hintProgressive = "Asta-sekin yashirish",
        hintNoneInfo = "Oyat butunlay yashirilgan",
        hintFirstWordInfo = "Birinchi so‘z ko‘rinib turadi",
        hintFirstLettersInfo = "Har bir so‘z o‘rnida uning birinchi harfi turadi",
        hintEveryOtherInfo = "1, 3, 5-so‘zlar ko‘rinadi, qolganlari yashirin",
        hintProgressiveInfo = "Oyat avval butun, uni choraklab yashirasiz",
        hideMore = "Yana yashirish", hiddenShareFmt = "{n}% yashirilgan",
        peekedFmt = "Qarab olindi: {n}",
    )
    AppLanguage.TR -> HifzCopy(
        language = language,
        modeName = "Kendini sına", eyeAction = "Kendini sına", exit = "Sınamadan çık", ayah = "Ayet",
        hiddenText = "Ayet metni gizli", peekHint = "Göz atmak için gizli kelimeye dokunun ya da ayete basılı tutun",
        reciteFromMemory = "Ezberden oku", listen = "Dinle", listenPause = "Duraklat", reveal = "Göster",
        markTitle = "Bu ayet nasıl geçti?", remembered = "Hatırlıyorum", hardMark = "Zor",
        answeredFmt = "Cevap: {mark}",
        forgot = "Unuttum", hard = "Zor", good = "İyi", easy = "Kolay",
        exitTitle = "Sınamadan çıkılsın mı?",
        exitBodyFmt = "{done} / {total} ayet cevaplandı. Sonuç yalnızca sonuna geldiğinizde kaydedilir.",
        keepGoing = "Devam et", leave = "Çık",
        gradeTitle = "Bölümü değerlendir", gradeSuggested = "Cevaplarınıza göre önerildi",
        gradeChosen = "Kendi seçiminiz",
        hardAyahsTitle = "Zor ayetler", hardAyahsHint = "İşareti koymak veya kaldırmak için ayete dokunun",
        done = "Bitti",
        savedReview = "Bölüm tekrarı sayıldı", savedCheck = "Sınama kaydedildi",
        hintChipFmt = "İpucu: {level}", hintTitle = "İpucu",
        hintSubtitle = "Hatırlarken ayetin ne kadarı ekranda kalır",
        hintNone = "Yok", hintFirstWord = "İlk kelime", hintFirstLetters = "İlk harfler",
        hintEveryOther = "Her ikinci kelime", hintProgressive = "Giderek gizle",
        hintNoneInfo = "Ayet tamamen gizli",
        hintFirstWordInfo = "İlk kelime görünür kalır",
        hintFirstLettersInfo = "Her kelimenin yerinde ilk harfi durur",
        hintEveryOtherInfo = "1., 3., 5. kelimeler görünür, gerisi gizli",
        hintProgressiveInfo = "Ayet önce bütün, siz onu parça parça gizlersiniz",
        hideMore = "Biraz daha gizle", hiddenShareFmt = "%{n} gizli",
        peekedFmt = "Göz atıldı: {n}",
    )
}
