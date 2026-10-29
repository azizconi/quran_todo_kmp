package tj.app.quran_todo.ui.i18n

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import tj.app.quran_todo.common.i18n.AppLanguage
import tj.app.quran_todo.common.i18n.LocalAppLanguage
import tj.app.quran_todo.ui.components.spotlight.SpotlightTip

/** The title and the text of one tip. */
@Immutable
data class SpotlightText(val title: String, val body: String)

/**
 * Strings of the spotlight tips (task C2) in the five app languages. Wording follows the glossary of
 * docs/redesign/ANDROID_REDESIGN.md, section 10, and the words the screens already use ("Today's lesson", "Continue",
 * "Check yourself").
 */
@Immutable
data class SpotlightCopy(
    val language: AppLanguage,
    val next: String,
    val gotIt: String,
    val skip: String,
    private val tips: Map<SpotlightTip, SpotlightText>,
) {
    /** "Next · 2/4". */
    fun nextLabel(number: Int, total: Int): String = "$next · $number/$total"

    fun tip(tip: SpotlightTip): SpotlightText = tips.getValue(tip)

    internal fun covers(): Set<SpotlightTip> = tips.keys
}

@Composable
fun rememberSpotlightCopy(language: AppLanguage = LocalAppLanguage.current): SpotlightCopy =
    remember(language) { spotlightCopy(language) }

fun spotlightCopy(language: AppLanguage): SpotlightCopy = when (language) {
    AppLanguage.RU -> SpotlightCopy(
        language = language,
        next = "Далее", gotIt = "Понятно", skip = "Пропустить",
        tips = mapOf(
            SpotlightTip.HomeLesson to SpotlightText("Урок дня", "Каждый день здесь готовый урок: новое, недавнее и старое"),
            SpotlightTip.HomeContinue to SpotlightText("Продолжить", "Вернёт туда, где вы остановились, в чтении или слушании"),
            SpotlightTip.HomeSurah to SpotlightText("Удерживайте суру", "Так отмечают, что знаете её наизусть или учите сейчас"),
            SpotlightTip.HomeProgress to SpotlightText("Ваш прогресс", "Сколько Корана вы уже знаете. Нажмите — подробности"),
            SpotlightTip.ReaderTap to SpotlightText("Нажмите на аят", "Звук начнётся с этого аята"),
            SpotlightTip.ReaderHold to SpotlightText("Удерживайте аят", "Заметка, статус, поделиться"),
            SpotlightTip.ReaderPlayer to SpotlightText("Плеер", "Потяните вверх: повтор отрывка, паузы и скорость"),
            SpotlightTip.ReaderText to SpotlightText("Текст", "Шрифт, размер и перевод"),
            SpotlightTip.ReaderCheck to SpotlightText("Проверь себя", "Закройте текст и проверьте, помните ли отрывок"),
            SpotlightTip.LessonSteps to SpotlightText("Шаги урока", "Новое, недавнее и старое. Галочка — шаг сделан"),
            SpotlightTip.LessonPeek to SpotlightText("Подсмотреть", "Удерживайте текст, если забыли"),
            SpotlightTip.LessonGrade to SpotlightText("Честная оценка", "От ответа зависит, когда отрывок вернётся"),
            SpotlightTip.PlayerRepeat to SpotlightText("Повтор отрывка", "Выберите аяты и сколько раз повторить"),
            SpotlightTip.PlayerPause to SpotlightText("Пауза", "Время повторить за чтецом"),
        ),
    )

    AppLanguage.EN -> SpotlightCopy(
        language = language,
        next = "Next", gotIt = "Got it", skip = "Skip",
        tips = mapOf(
            SpotlightTip.HomeLesson to SpotlightText("Today’s lesson", "A ready lesson every day: new, recent and old"),
            SpotlightTip.HomeContinue to SpotlightText("Continue", "Takes you back to where you stopped, reading or listening"),
            SpotlightTip.HomeSurah to SpotlightText("Hold a surah", "That is how you mark it as memorized or as one you are learning now"),
            SpotlightTip.HomeProgress to SpotlightText("Your progress", "How much of the Quran you already know. Tap for details"),
            SpotlightTip.ReaderTap to SpotlightText("Tap an ayah", "Audio starts from this ayah"),
            SpotlightTip.ReaderHold to SpotlightText("Hold an ayah", "Note, status, share"),
            SpotlightTip.ReaderPlayer to SpotlightText("Player", "Pull up for passage repeat, pauses and speed"),
            SpotlightTip.ReaderText to SpotlightText("Text", "Font, size and translation"),
            SpotlightTip.ReaderCheck to SpotlightText("Check yourself", "Hide the text and see whether you remember the passage"),
            SpotlightTip.LessonSteps to SpotlightText("Lesson steps", "New, recent and old. A check mark means the step is done"),
            SpotlightTip.LessonPeek to SpotlightText("Peek", "Hold the text if you forgot"),
            SpotlightTip.LessonGrade to SpotlightText("An honest rating", "Your answer decides when the passage comes back"),
            SpotlightTip.PlayerRepeat to SpotlightText("Repeat a passage", "Choose the ayahs and how many times to repeat"),
            SpotlightTip.PlayerPause to SpotlightText("Pause", "Time to repeat after the reciter"),
        ),
    )

    AppLanguage.TG -> SpotlightCopy(
        language = language,
        next = "Минбаъд", gotIt = "Фаҳмидам", skip = "Гузаштан",
        tips = mapOf(
            SpotlightTip.HomeLesson to SpotlightText("Дарси рӯз", "Ҳар рӯз дарси тайёр: нав, наздик ва қадим"),
            SpotlightTip.HomeContinue to SpotlightText("Идома", "Шуморо ба ҷое бармегардонад, ки хондан ё гӯш карданро қатъ кардед"),
            SpotlightTip.HomeSurah to SpotlightText("Сураро нигоҳ доред", "Бо ин тарз қайд мекунанд, ки онро аз бар медонед ё ҳозир меомӯзед"),
            SpotlightTip.HomeProgress to SpotlightText("Пешрафти шумо", "Чӣ қадар аз Қуръонро аллакай медонед. Зер кунед — тафсилот"),
            SpotlightTip.ReaderTap to SpotlightText("Ба оят зер кунед", "Садо аз ҳамин оят оғоз мешавад"),
            SpotlightTip.ReaderHold to SpotlightText("Оятро нигоҳ доред", "Ёддошт, ҳолат, мубодила"),
            SpotlightTip.ReaderPlayer to SpotlightText("Плеер", "Ба боло кашед: такрори порча, таваққуф ва суръат"),
            SpotlightTip.ReaderText to SpotlightText("Матн", "Ҳуруф, андоза ва тарҷума"),
            SpotlightTip.ReaderCheck to SpotlightText("Худро санҷед", "Матнро пинҳон кунед ва санҷед, ки порчаро дар ёд доред ё не"),
            SpotlightTip.LessonSteps to SpotlightText("Қадамҳои дарс", "Нав, наздик ва қадим. Аломат — қадам иҷро шуд"),
            SpotlightTip.LessonPeek to SpotlightText("Нигоҳ кардан", "Агар фаромӯш кардед, матнро нигоҳ доред"),
            SpotlightTip.LessonGrade to SpotlightText("Баҳои ростқавлона", "Аз ҷавоби шумо вобаста аст, ки порча кай бармегардад"),
            SpotlightTip.PlayerRepeat to SpotlightText("Такрори порча", "Оятҳо ва шумораи такрорро интихоб кунед"),
            SpotlightTip.PlayerPause to SpotlightText("Таваққуф", "Вақт барои такрор пас аз қорӣ"),
        ),
    )

    AppLanguage.UZ -> SpotlightCopy(
        language = language,
        next = "Keyingi", gotIt = "Tushunarli", skip = "O‘tkazish",
        tips = mapOf(
            SpotlightTip.HomeLesson to SpotlightText("Kunlik dars", "Har kuni tayyor dars: yangi, yaqinda o‘tilgan va eski"),
            SpotlightTip.HomeContinue to SpotlightText("Davom etish", "O‘qish yoki tinglashda to‘xtagan joyingizga qaytaradi"),
            SpotlightTip.HomeSurah to SpotlightText("Surani bosib turing", "Shunday qilib uni yod bilishingizni yoki hozir yodlayotganingizni belgilaysiz"),
            SpotlightTip.HomeProgress to SpotlightText("Natijangiz", "Qur’onning qancha qismini allaqachon bilasiz. Bosing — batafsil"),
            SpotlightTip.ReaderTap to SpotlightText("Oyatni bosing", "Ovoz shu oyatdan boshlanadi"),
            SpotlightTip.ReaderHold to SpotlightText("Oyatni bosib turing", "Eslatma, holat, ulashish"),
            SpotlightTip.ReaderPlayer to SpotlightText("Pleyer", "Tepaga torting: parcha takrori, tanaffuslar va tezlik"),
            SpotlightTip.ReaderText to SpotlightText("Matn", "Shrift, o‘lcham va tarjima"),
            SpotlightTip.ReaderCheck to SpotlightText("O‘zingizni tekshiring", "Matnni yoping va parchani eslab qolganingizni tekshiring"),
            SpotlightTip.LessonSteps to SpotlightText("Dars qadamlari", "Yangi, yaqinda o‘tilgan va eski. Belgi — qadam bajarildi"),
            SpotlightTip.LessonPeek to SpotlightText("Ko‘rib olish", "Unutgan bo‘lsangiz, matnni bosib turing"),
            SpotlightTip.LessonGrade to SpotlightText("Halol baho", "Javobingizga qarab parcha qachon qaytishi belgilanadi"),
            SpotlightTip.PlayerRepeat to SpotlightText("Parcha takrori", "Oyatlarni va necha marta takrorlashni tanlang"),
            SpotlightTip.PlayerPause to SpotlightText("Tanaffus", "Qori ortidan takrorlash uchun vaqt"),
        ),
    )

    AppLanguage.TR -> SpotlightCopy(
        language = language,
        next = "İleri", gotIt = "Anladım", skip = "Atla",
        tips = mapOf(
            SpotlightTip.HomeLesson to SpotlightText("Günün dersi", "Her gün hazır bir ders: yeni, yakın ve eski"),
            SpotlightTip.HomeContinue to SpotlightText("Devam et", "Okurken ya da dinlerken kaldığınız yere götürür"),
            SpotlightTip.HomeSurah to SpotlightText("Sureye basılı tutun", "Böylece sureyi ezbere bildiğinizi ya da şimdi çalıştığınızı işaretlersiniz"),
            SpotlightTip.HomeProgress to SpotlightText("İlerlemeniz", "Kur’an’ın ne kadarını şimdiden biliyorsunuz. Dokunun — ayrıntılar"),
            SpotlightTip.ReaderTap to SpotlightText("Ayete dokunun", "Ses bu ayetten başlar"),
            SpotlightTip.ReaderHold to SpotlightText("Ayete basılı tutun", "Not, durum, paylaşma"),
            SpotlightTip.ReaderPlayer to SpotlightText("Oynatıcı", "Yukarı çekin: bölüm tekrarı, aralar ve hız"),
            SpotlightTip.ReaderText to SpotlightText("Metin", "Yazı tipi, boyut ve çeviri"),
            SpotlightTip.ReaderCheck to SpotlightText("Kendini sına", "Metni kapatın ve bölümü hatırlayıp hatırlamadığınıza bakın"),
            SpotlightTip.LessonSteps to SpotlightText("Ders adımları", "Yeni, yakın ve eski. İşaret — adım tamam"),
            SpotlightTip.LessonPeek to SpotlightText("Göz atın", "Unuttuysanız metne basılı tutun"),
            SpotlightTip.LessonGrade to SpotlightText("Dürüst değerlendirme", "Cevabınıza göre bölümün ne zaman geri geleceği belirlenir"),
            SpotlightTip.PlayerRepeat to SpotlightText("Bölüm tekrarı", "Ayetleri ve kaç kez tekrarlanacağını seçin"),
            SpotlightTip.PlayerPause to SpotlightText("Ara", "Okuyucudan sonra tekrarlamak için süre"),
        ),
    )
}
