package tj.app.quran_todo.ui.i18n

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import tj.app.quran_todo.common.i18n.AppLanguage
import tj.app.quran_todo.common.i18n.LocalAppLanguage

/**
 * Strings of the quiz "Check" (task F2b) in the five app languages: the entry on the home screen and in the summary of the lesson,
 * the five kinds of questions, the answers and the result. Texts with numbers are patterns with `{a}`, `{b}`, `{name}` and
 * `{questions}` placeholders filled by the functions below, so each language keeps its own word order.
 */
@Immutable
data class QuizCopy(
    val language: AppLanguage,

    val title: String,
    val back: String,
    val loadingMessage: String,

    // The entry on the home screen ("Check · 5 questions on what you know") and the button of the lesson summary
    val entrySubtitlePattern: String,
    val lessonButtonPattern: String,

    // A question: "2 of 5", the kinds and what to do
    val progressPattern: String,
    val continueTitle: String,
    val continueHint: String,
    val nextTitle: String,
    val nextHint: String,
    val whatNext: String,
    val startTitle: String,
    val startHint: String,
    val orderTitle: String,
    val orderHint: String,
    val whereTitle: String,
    val whereHint: String,
    val tellApartTitle: String,
    val tellApartHint: String,
    val hiddenText: String,

    // The answers
    val show: String,
    val knew: String,
    val didNotKnow: String,
    val check: String,
    val clearOrder: String,
    val next: String,
    val seeResult: String,
    val right: String,
    val wrong: String,
    val correctOrder: String,
    val itIsSurahPattern: String,
    val orderPlacePattern: String,
    val ayahPattern: String,

    // The result
    val resultPattern: String,
    val allRight: String,
    val missedHeader: String,
    val markedNote: String,
    val openInReader: String,
    val moreQuestionsPattern: String,
    val done: String,

    // Too little is memorized
    val emptyTitle: String,
    val emptyBody: String,
) {
    private fun String.fill(vararg values: Pair<String, Any>): String =
        values.fold(this) { text, (key, value) -> text.replace("{$key}", value.toString()) }

    /** "5 questions on what you know": the second line of the entry on the home screen. */
    fun entrySubtitle(questions: Int): String = entrySubtitlePattern.fill("questions" to questionCountLabel(questions, language))

    /** "Check yourself · 5 questions": the button of the lesson summary. */
    fun lessonButton(questions: Int): String = lessonButtonPattern.fill("questions" to questionCountLabel(questions, language))

    /** "2 of 5". */
    fun progress(position: Int, total: Int): String = progressPattern.fill("a" to position, "b" to total)

    /** "It is Al-Mulk": the right surah after a wrong choice of "Where is this ayah?". */
    fun itIsSurah(name: String): String = itIsSurahPattern.fill("name" to name)

    /** "Place 2": the number on the card of an ayah that was tapped in "Put in order". */
    fun orderPlace(place: Int): String = orderPlacePattern.fill("a" to place)

    /** "Ayah 12": an ayah card of "Put in order" for a screen reader. */
    fun ayah(number: Int): String = ayahPattern.fill("a" to number)

    /** "Knew 4 of 5". */
    fun result(known: Int, total: Int): String = resultPattern.fill("a" to known, "b" to total)

    /** "Another 5 questions" (Russian "Ещё 5 вопросов"). */
    fun moreQuestions(questions: Int): String = moreQuestionsPattern.fill("questions" to questionCountLabel(questions, language))
}

@Composable
fun rememberQuizCopy(language: AppLanguage = LocalAppLanguage.current): QuizCopy = remember(language) { quizCopy(language) }

/** "1 question", "5 questions"; Russian 1 вопрос / 2 вопроса / 5 вопросов; Tajik, Uzbek and Turkish do not inflect after a number. */
fun questionCountLabel(n: Int, language: AppLanguage): String = when (language) {
    AppLanguage.RU -> "$n ${russianPlural(n, "вопрос", "вопроса", "вопросов")}"
    AppLanguage.EN -> "$n ${if (n == 1) "question" else "questions"}"
    AppLanguage.TG -> "$n савол"
    AppLanguage.UZ -> "$n ta savol"
    AppLanguage.TR -> "$n soru"
}

fun quizCopy(language: AppLanguage): QuizCopy = when (language) {
    AppLanguage.RU -> QuizCopy(
        language = language,
        title = "Тест",
        back = "Назад",
        loadingMessage = "Готовим вопросы",
        entrySubtitlePattern = "{questions} по пройденному",
        lessonButtonPattern = "Тест · {questions}",
        progressPattern = "{a} из {b}",
        continueTitle = "Продолжите аят",
        continueHint = "Вспомните, как он продолжается.",
        nextTitle = "Какой аят дальше?",
        nextHint = "Вспомните следующий аят.",
        whatNext = "Что дальше?",
        startTitle = "С чего начинается сура",
        startHint = "Вспомните первый аят этой суры.",
        orderTitle = "Расставьте по порядку",
        orderHint = "Нажимайте на аяты по порядку, как они идут в суре.",
        whereTitle = "Где этот аят?",
        whereHint = "Выберите суру, из которой этот аят.",
        tellApartTitle = "Различите похожие",
        tellApartHint = "Вспомните оба аята по общему началу. Потом сравните.",
        hiddenText = "Текст скрыт",
        show = "Показать",
        knew = "Знал",
        didNotKnow = "Не знал",
        check = "Проверить",
        clearOrder = "Сначала",
        next = "Дальше",
        seeResult = "Итог",
        right = "Верно",
        wrong = "Не совсем",
        correctOrder = "Правильный порядок",
        itIsSurahPattern = "Это сура {name}",
        orderPlacePattern = "Место {a}",
        ayahPattern = "Аят {a}",
        resultPattern = "Знал {a} из {b}",
        allRight = "Все ответы верные.",
        missedHeader = "К чему стоит вернуться",
        markedNote = "Аяты, в которых ошибся в тексте, отмечены как трудные.",
        openInReader = "Открыть в Коране",
        moreQuestionsPattern = "Ещё {questions}",
        done = "Готово",
        emptyTitle = "Пока рано для теста",
        emptyBody = "Тест появится, когда пройдено хотя бы 5 аятов.",
    )
    AppLanguage.EN -> QuizCopy(
        language = language,
        title = "Test",
        back = "Back",
        loadingMessage = "Preparing the questions",
        entrySubtitlePattern = "{questions} on what you know",
        lessonButtonPattern = "Test · {questions}",
        progressPattern = "{a} of {b}",
        continueTitle = "Continue the ayah",
        continueHint = "Recall how it continues.",
        nextTitle = "Which ayah comes next?",
        nextHint = "Recall the next ayah.",
        whatNext = "What comes next?",
        startTitle = "How the surah begins",
        startHint = "Recall the first ayah of this surah.",
        orderTitle = "Put them in order",
        orderHint = "Tap the ayahs in the order they come in the surah.",
        whereTitle = "Where is this ayah?",
        whereHint = "Choose the surah this ayah is from.",
        tellApartTitle = "Tell similar verses apart",
        tellApartHint = "Recall both verses from their common start, then compare.",
        hiddenText = "Text is hidden",
        show = "Show",
        knew = "Knew it",
        didNotKnow = "Didn’t know",
        check = "Check",
        clearOrder = "Start over",
        next = "Next",
        seeResult = "Result",
        right = "Correct",
        wrong = "Not quite",
        correctOrder = "The right order",
        itIsSurahPattern = "It is {name}",
        orderPlacePattern = "Place {a}",
        ayahPattern = "Ayah {a}",
        resultPattern = "Knew {a} of {b}",
        allRight = "Every answer was right.",
        missedHeader = "Worth going back to",
        markedNote = "Ayahs you got wrong in the text are marked as difficult.",
        openInReader = "Open in the reader",
        moreQuestionsPattern = "Another {questions}",
        done = "Done",
        emptyTitle = "Too early for a test",
        emptyBody = "The test appears once at least 5 ayahs are covered.",
    )
    AppLanguage.TG -> QuizCopy(
        language = language,
        title = "Тест",
        back = "Бозгашт",
        loadingMessage = "Саволҳо омода мешаванд",
        entrySubtitlePattern = "{questions} аз рӯи гузаштаҳо",
        lessonButtonPattern = "Тест · {questions}",
        progressPattern = "{a} аз {b}",
        continueTitle = "Оятро идома диҳед",
        continueHint = "Ба ёд оред, ки он чӣ тавр идома меёбад.",
        nextTitle = "Ояти минбаъда кадом аст?",
        nextHint = "Ояти навбатиро ба ёд оред.",
        whatNext = "Минбаъд чӣ?",
        startTitle = "Сура аз чӣ оғоз мешавад",
        startHint = "Ояти аввали ин сураро ба ёд оред.",
        orderTitle = "Бо тартиб гузоред",
        orderHint = "Оятҳоро ба тартибе пахш кунед, ки дар сура омадаанд.",
        whereTitle = "Ин оят аз кадом сура аст?",
        whereHint = "Сураеро интихоб кунед, ки ин оят аз он аст.",
        tellApartTitle = "Монандҳоро фарқ кунед",
        tellApartHint = "Ҳар ду оятро аз оғози умумӣ ба ёд оред. Сипас муқоиса кунед.",
        hiddenText = "Матн пинҳон аст",
        show = "Нишон диҳед",
        knew = "Медонистам",
        didNotKnow = "Намедонистам",
        check = "Санҷидан",
        clearOrder = "Аз нав",
        next = "Минбаъд",
        seeResult = "Натиҷа",
        right = "Дуруст",
        wrong = "Комилан не",
        correctOrder = "Тартиби дуруст",
        itIsSurahPattern = "Ин сураи {name} аст",
        orderPlacePattern = "Ҷои {a}",
        ayahPattern = "Оят {a}",
        resultPattern = "Аз {b} то {a}-ро медонистам",
        allRight = "Ҳамаи ҷавобҳо дуруст буданд.",
        missedHeader = "Ба чӣ бояд баргашт",
        markedNote = "Оятҳое, ки матнашон хато шуд, ҳамчун душвор қайд шуданд.",
        openInReader = "Дар хонанда кушодан",
        moreQuestionsPattern = "{questions} дигар",
        done = "Тайёр",
        emptyTitle = "Барои тест ҳоло барвақт аст",
        emptyBody = "Тест пас аз гузаштани ақаллан 5 оят пайдо мешавад.",
    )
    AppLanguage.UZ -> QuizCopy(
        language = language,
        title = "Test",
        back = "Orqaga",
        loadingMessage = "Savollar tayyorlanmoqda",
        entrySubtitlePattern = "O‘tilganlar bo‘yicha {questions}",
        lessonButtonPattern = "Test · {questions}",
        progressPattern = "{a} / {b}",
        continueTitle = "Oyatni davom ettiring",
        continueHint = "Uning davomini eslang.",
        nextTitle = "Keyingi oyat qaysi?",
        nextHint = "Keyingi oyatni eslang.",
        whatNext = "Keyin nima?",
        startTitle = "Sura nimadan boshlanadi",
        startHint = "Bu surening birinchi oyatini eslang.",
        orderTitle = "Tartib bilan joylashtiring",
        orderHint = "Oyatlarni surada kelgan tartibda bosing.",
        whereTitle = "Bu oyat qayerda?",
        whereHint = "Bu oyat qaysi suradan ekanini tanlang.",
        tellApartTitle = "O‘xshashlarini farqlang",
        tellApartHint = "Ikkala oyatni umumiy boshlanishiga qarab eslang. Keyin solishtiring.",
        hiddenText = "Matn yashirilgan",
        show = "Ko‘rsatish",
        knew = "Bilardim",
        didNotKnow = "Bilmasdim",
        check = "Tekshirish",
        clearOrder = "Qaytadan",
        next = "Keyingisi",
        seeResult = "Natija",
        right = "To‘g‘ri",
        wrong = "Unchalik emas",
        correctOrder = "To‘g‘ri tartib",
        itIsSurahPattern = "Bu {name} surasi",
        orderPlacePattern = "{a}-o‘rin",
        ayahPattern = "{a}-oyat",
        resultPattern = "{b} tadan {a} tasini bildim",
        allRight = "Hamma javoblar to‘g‘ri.",
        missedHeader = "Qaytib ko‘rib chiqing",
        markedNote = "Matnda xato qilingan oyatlar qiyin deb belgilandi.",
        openInReader = "O‘qish ekranida ochish",
        moreQuestionsPattern = "Yana {questions}",
        done = "Tayyor",
        emptyTitle = "Test uchun hali erta",
        emptyBody = "Kamida 5 ta oyat o‘tilganda test paydo bo‘ladi.",
    )
    AppLanguage.TR -> QuizCopy(
        language = language,
        title = "Test",
        back = "Geri",
        loadingMessage = "Sorular hazırlanıyor",
        entrySubtitlePattern = "Geçtiklerinizden {questions}",
        lessonButtonPattern = "Test · {questions}",
        progressPattern = "{a} / {b}",
        continueTitle = "Ayeti tamamlayın",
        continueHint = "Devamını hatırlayın.",
        nextTitle = "Sonraki ayet hangisi?",
        nextHint = "Sonraki ayeti hatırlayın.",
        whatNext = "Sırada ne var?",
        startTitle = "Sure nasıl başlar",
        startHint = "Bu surenin ilk ayetini hatırlayın.",
        orderTitle = "Sıraya dizin",
        orderHint = "Ayetlere surede geldikleri sırayla dokunun.",
        whereTitle = "Bu ayet nerede?",
        whereHint = "Bu ayetin hangi sureden olduğunu seçin.",
        tellApartTitle = "Benzerleri ayırt edin",
        tellApartHint = "İki ayeti de ortak başlangıçtan hatırlayın. Sonra karşılaştırın.",
        hiddenText = "Metin gizli",
        show = "Göster",
        knew = "Biliyordum",
        didNotKnow = "Bilmiyordum",
        check = "Kontrol et",
        clearOrder = "Baştan",
        next = "Sonraki",
        seeResult = "Sonuç",
        right = "Doğru",
        wrong = "Tam değil",
        correctOrder = "Doğru sıra",
        itIsSurahPattern = "Bu {name} suresi",
        orderPlacePattern = "{a}. sıra",
        ayahPattern = "{a}. ayet",
        resultPattern = "{b} sorudan {a} tanesini bildim",
        allRight = "Tüm cevaplar doğru.",
        missedHeader = "Dönüp bakmaya değer",
        markedNote = "Metinde yanlış yapılan ayetler zor olarak işaretlendi.",
        openInReader = "Okuyucuda aç",
        moreQuestionsPattern = "{questions} daha",
        done = "Bitti",
        emptyTitle = "Test için henüz erken",
        emptyBody = "En az 5 ayet geçilince test açılır.",
    )
}
