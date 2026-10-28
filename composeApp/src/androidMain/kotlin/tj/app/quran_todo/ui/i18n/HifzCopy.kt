package tj.app.quran_todo.ui.i18n

import androidx.compose.runtime.Immutable
import tj.app.quran_todo.common.i18n.AppLanguage
import tj.app.quran_todo.presentation.review.ReviewQuality

/**
 * Strings of the memorize mode in the reader. The wording comes from the old session screen
 * (`hifzSessionCopy`) and the glossary of docs/redesign/ANDROID_REDESIGN.md, section 10. Texts with a value
 * in them use `{name}`-style placeholders filled by the helpers below.
 */
@Immutable
data class HifzCopy(
    val language: AppLanguage,
    /** Title of the mode: "Memorize mode" (the glossary term). */
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
    val rateTitle: String,
    val forgot: String,
    val hard: String,
    val good: String,
    val easy: String,
    val ratedFmt: String,
    // Leaving
    val exitTitle: String,
    val exitBodyFmt: String,
    val keepGoing: String,
    val leave: String,
    // Summary
    val summaryTitle: String,
    val recallCaption: String,
    val retryHard: String,
    val done: String,
) {
    fun label(quality: ReviewQuality): String = when (quality) {
        ReviewQuality.FORGOT -> forgot
        ReviewQuality.HARD -> hard
        ReviewQuality.GOOD -> good
        ReviewQuality.EASY -> easy
    }

    /** "Rated: Good" for screen readers and the mark of a rated ayah. */
    fun rated(quality: ReviewQuality): String = ratedFmt.replace("{rating}", label(quality))

    fun exitBody(done: Int, total: Int): String =
        exitBodyFmt.replace("{done}", done.toString()).replace("{total}", total.toString())
}

fun hifzCopy(language: AppLanguage): HifzCopy = when (language) {
    AppLanguage.RU -> HifzCopy(
        language = language,
        modeName = "Заучивание", eyeAction = "Режим заучивания", exit = "Выйти из режима", ayah = "Аят",
        hiddenText = "Текст аята скрыт", peekHint = "Удерживайте текст, чтобы подсмотреть",
        reciteFromMemory = "Повторите по памяти", listen = "Послушать", listenPause = "Пауза", reveal = "Показать",
        rateTitle = "Оцените повторение",
        forgot = "Забыл", hard = "Трудно", good = "Хорошо", easy = "Легко", ratedFmt = "Оценка: {rating}",
        exitTitle = "Выйти из заучивания?",
        exitBodyFmt = "Оценки сохранены: {done} из {total}. Остальные аяты можно пройти позже.",
        keepGoing = "Продолжить", leave = "Выйти",
        summaryTitle = "Сессия завершена", recallCaption = "Хорошо и легко",
        retryHard = "Повторить трудные", done = "Готово",
    )
    AppLanguage.EN -> HifzCopy(
        language = language,
        modeName = "Memorize mode", eyeAction = "Memorize mode", exit = "Leave memorize mode", ayah = "Ayah",
        hiddenText = "Ayah text is hidden", peekHint = "Hold the text to peek",
        reciteFromMemory = "Recite from memory", listen = "Listen", listenPause = "Pause", reveal = "Reveal",
        rateTitle = "Rate this review",
        forgot = "Forgot", hard = "Hard", good = "Good", easy = "Easy", ratedFmt = "Rated: {rating}",
        exitTitle = "Leave memorize mode?",
        exitBodyFmt = "Ratings saved: {done} of {total}. You can go through the rest later.",
        keepGoing = "Keep going", leave = "Leave",
        summaryTitle = "Session complete", recallCaption = "Good and easy",
        retryHard = "Repeat the hard ones", done = "Done",
    )
    AppLanguage.TG -> HifzCopy(
        language = language,
        modeName = "Ҳифз", eyeAction = "Реҷаи ҳифз", exit = "Баромадан аз реҷа", ayah = "Оят",
        hiddenText = "Матни оят пинҳон аст", peekHint = "Барои дидан матнро нигоҳ доред",
        reciteFromMemory = "Аз ёд такрор кунед", listen = "Гӯш кардан", listenPause = "Таваққуф", reveal = "Нишон додан",
        rateTitle = "Такрорро баҳо диҳед",
        forgot = "Фаромӯш", hard = "Душвор", good = "Хуб", easy = "Осон", ratedFmt = "Баҳо: {rating}",
        exitTitle = "Аз ҳифз мебароед?",
        exitBodyFmt = "Баҳоҳо захира шуданд: {done} аз {total}. Оятҳои боқимонда баъдтар гузаронида мешаванд.",
        keepGoing = "Идома додан", leave = "Баромадан",
        summaryTitle = "Машқ анҷом ёфт", recallCaption = "Хуб ва осон",
        retryHard = "Душворҳоро такрор кардан", done = "Тайёр",
    )
    AppLanguage.UZ -> HifzCopy(
        language = language,
        modeName = "Yodlash", eyeAction = "Yodlash rejimi", exit = "Rejimdan chiqish", ayah = "Oyat",
        hiddenText = "Oyat matni yashirilgan", peekHint = "Ko‘rish uchun matnni bosib turing",
        reciteFromMemory = "Yoddan takrorlang", listen = "Tinglash", listenPause = "Pauza", reveal = "Ko‘rsatish",
        rateTitle = "Takrorni baholang",
        forgot = "Unutdim", hard = "Qiyin", good = "Yaxshi", easy = "Oson", ratedFmt = "Baho: {rating}",
        exitTitle = "Yodlashdan chiqasizmi?",
        exitBodyFmt = "Baholar saqlandi: {done} / {total}. Qolgan oyatlarni keyinroq o‘tish mumkin.",
        keepGoing = "Davom etish", leave = "Chiqish",
        summaryTitle = "Mashg‘ulot tugadi", recallCaption = "Yaxshi va oson",
        retryHard = "Qiyinlarini takrorlash", done = "Tayyor",
    )
    AppLanguage.TR -> HifzCopy(
        language = language,
        modeName = "Ezber modu", eyeAction = "Ezber modu", exit = "Ezber modundan çık", ayah = "Ayet",
        hiddenText = "Ayet metni gizli", peekHint = "Göz atmak için metne basılı tutun",
        reciteFromMemory = "Ezberden oku", listen = "Dinle", listenPause = "Duraklat", reveal = "Göster",
        rateTitle = "Tekrarı değerlendir",
        forgot = "Unuttum", hard = "Zor", good = "İyi", easy = "Kolay", ratedFmt = "Değerlendirme: {rating}",
        exitTitle = "Ezber modundan çıkılsın mı?",
        exitBodyFmt = "Değerlendirmeler kaydedildi: {done} / {total}. Kalan ayetleri sonra geçebilirsiniz.",
        keepGoing = "Devam et", leave = "Çık",
        summaryTitle = "Oturum tamamlandı", recallCaption = "İyi ve kolay",
        retryHard = "Zorlananları tekrarla", done = "Bitti",
    )
}
