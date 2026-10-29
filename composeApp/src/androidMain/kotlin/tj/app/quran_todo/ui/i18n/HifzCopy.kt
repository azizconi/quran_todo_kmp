package tj.app.quran_todo.ui.i18n

import androidx.compose.runtime.Immutable
import tj.app.quran_todo.common.i18n.AppLanguage
import tj.app.quran_todo.hifz.model.ReviewGrade
import tj.app.quran_todo.ui.hifz.AyahMark

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
}

fun hifzCopy(language: AppLanguage): HifzCopy = when (language) {
    AppLanguage.RU -> HifzCopy(
        language = language,
        modeName = "Проверь себя", eyeAction = "Проверь себя", exit = "Выйти из проверки", ayah = "Аят",
        hiddenText = "Текст аята скрыт", peekHint = "Удерживайте текст, чтобы подсмотреть",
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
    )
    AppLanguage.EN -> HifzCopy(
        language = language,
        modeName = "Check yourself", eyeAction = "Check yourself", exit = "Leave the check", ayah = "Ayah",
        hiddenText = "Ayah text is hidden", peekHint = "Hold the text to peek",
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
    )
    AppLanguage.TG -> HifzCopy(
        language = language,
        modeName = "Худро санҷед", eyeAction = "Худро санҷед", exit = "Баромадан аз санҷиш", ayah = "Оят",
        hiddenText = "Матни оят пинҳон аст", peekHint = "Барои дидан матнро нигоҳ доред",
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
    )
    AppLanguage.UZ -> HifzCopy(
        language = language,
        modeName = "O‘zingizni tekshiring", eyeAction = "O‘zingizni tekshiring", exit = "Tekshiruvdan chiqish",
        ayah = "Oyat",
        hiddenText = "Oyat matni yashirilgan", peekHint = "Ko‘rish uchun matnni bosib turing",
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
    )
    AppLanguage.TR -> HifzCopy(
        language = language,
        modeName = "Kendini sına", eyeAction = "Kendini sına", exit = "Sınamadan çık", ayah = "Ayet",
        hiddenText = "Ayet metni gizli", peekHint = "Göz atmak için metne basılı tutun",
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
    )
}
