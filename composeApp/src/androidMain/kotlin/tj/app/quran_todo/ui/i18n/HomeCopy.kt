package tj.app.quran_todo.ui.i18n

import androidx.compose.runtime.Immutable
import tj.app.quran_todo.common.i18n.AppLanguage

/**
 * Strings of the home screen in the five app languages (wording follows the glossary of
 * docs/redesign/ANDROID_REDESIGN.md, section 10). Texts with numbers or names are patterns with
 * `{a}`, `{b}`, `{n}`, `{s}` and `{name}` placeholders, filled by the functions below, so each
 * language keeps its own word order and plural forms.
 */
@Immutable
data class HomeCopy(
    val language: AppLanguage,

    // Top bar
    val greeting: String,
    val title: String,
    val settings: String,
    val progress: String,
    val progressPattern: String,

    // Hero
    val continueLabel: String,
    val startWithFatiha: String,
    val read: String,
    val listen: String,
    val pause: String,
    val ayahOfPattern: String,

    // Today
    val today: String,
    val ofGoalPattern: String,
    val todayProgressPattern: String,
    val kindNew: String,
    val kindReview: String,
    val kindWeak: String,
    val allDoneTitle: String,
    val allDoneBody: String,
    val inviteTitle: String,
    val inviteBody: String,
    val dismiss: String,

    // Recent
    val recent: String,
    val pinned: String,

    // Search and list
    val searchPlaceholder: String,
    val clearSearch: String,
    val filterAll: String,
    val filterLearning: String,
    val filterLearned: String,
    val smartOpen: String,
    val smartTitlePattern: String,
    val noResultsTitle: String,
    val noResultsBody: String,
    val emptyLearningTitle: String,
    val emptyLearningBody: String,
    val emptyLearnedTitle: String,
    val emptyLearnedBody: String,
    val open: String,

    // Loading failed
    val loadErrorTitle: String,
    val loadErrorBody: String,
    val retry: String,

    // Surah sheet
    val sheetStatus: String,
    val statusNotStarted: String,
    val statusLearning: String,
    val statusLearned: String,
    val pin: String,
    val unpin: String,
    val listenSurah: String,

    // Mini player
    val play: String,
    val stop: String,
    val openPlayer: String,
    val audioError: String,
    val playerSubPattern: String,

    // Toasts
    val learnedToastPattern: String,
    val statusUpdateFailed: String,
) {
    /** "Progress: 3%": the spoken label of the ring in the top bar. */
    fun progressDescription(percent: Int): String = progressPattern.fill("n" to percent)

    fun ayahOf(ayah: Int, total: Int): String = ayahOfPattern.fill("a" to ayah, "b" to total)

    fun ofGoal(goal: Int): String = ofGoalPattern.fill("n" to goal)

    fun todayProgress(done: Int, goal: Int): String = todayProgressPattern.fill("a" to done, "b" to goal)

    fun smartTitle(name: String, ayah: Int): String = smartTitlePattern.fill("name" to name, "n" to ayah)

    /** "Ayah 12 · 1.25×": the line under the surah name in the mini player. */
    fun playerSub(ayah: Int, speed: Float): String = playerSubPattern.fill("a" to ayah, "s" to speedLabel(speed))

    fun learnedToast(name: String): String = learnedToastPattern.fill("name" to name)

    fun ayahCount(n: Int): String = ayahCountLabel(n, language)

    fun surahCount(n: Int): String = surahCountLabel(n, language)

    fun ayahRange(start: Int, end: Int): String = ayahRangeLabel(start, end, language)

    /** "1×", "1.25×", "0.75×": the shortest exact form of the playback speed. */
    private fun speedLabel(speed: Float): String {
        val text = speed.toString().trimEnd('0').trimEnd('.')
        return "$text×"
    }
}

private fun String.fill(vararg values: Pair<String, Any>): String {
    var result = this
    values.forEach { (key, value) -> result = result.replace("{$key}", value.toString()) }
    return result
}

/** "1 ayah", "2 ayahs"; Russian 1 аят / 2 аята / 5 аятов; Tajik, Uzbek and Turkish do not inflect after a number. */
fun ayahCountLabel(n: Int, language: AppLanguage): String = when (language) {
    AppLanguage.RU -> "$n ${russianPlural(n, "аят", "аята", "аятов")}"
    AppLanguage.EN -> "$n ${if (n == 1) "ayah" else "ayahs"}"
    AppLanguage.TG -> "$n оят"
    AppLanguage.UZ -> "$n oyat"
    AppLanguage.TR -> "$n ayet"
}

/** The same for surahs: 1 сура / 2 суры / 5 сур. */
fun surahCountLabel(n: Int, language: AppLanguage): String = when (language) {
    AppLanguage.RU -> "$n ${russianPlural(n, "сура", "суры", "сур")}"
    AppLanguage.EN -> "$n ${if (n == 1) "surah" else "surahs"}"
    AppLanguage.TG -> "$n сура"
    AppLanguage.UZ -> "$n sura"
    AppLanguage.TR -> "$n sure"
}

/** "Ayah 5" for a single ayah, "Ayahs 1–5" for a range. */
fun ayahRangeLabel(start: Int, end: Int, language: AppLanguage): String {
    val single = start >= end
    return when (language) {
        AppLanguage.RU -> if (single) "Аят $start" else "Аяты $start–$end"
        AppLanguage.EN -> if (single) "Ayah $start" else "Ayahs $start–$end"
        AppLanguage.TG -> if (single) "Оят $start" else "Оятҳои $start–$end"
        AppLanguage.UZ -> if (single) "$start-oyat" else "$start–$end-oyatlar"
        AppLanguage.TR -> if (single) "Ayet $start" else "Ayet $start–$end"
    }
}

private fun russianPlural(n: Int, one: String, few: String, many: String): String {
    val abs = kotlin.math.abs(n)
    return when {
        abs % 100 in 11..14 -> many
        abs % 10 == 1 -> one
        abs % 10 in 2..4 -> few
        else -> many
    }
}

fun homeCopy(language: AppLanguage): HomeCopy = when (language) {
    AppLanguage.RU -> HomeCopy(
        language = language,
        greeting = "Ассаляму алейкум",
        title = "Коран",
        settings = "Настройки",
        progress = "Прогресс",
        progressPattern = "Прогресс: {n}%",
        continueLabel = "Продолжить",
        startWithFatiha = "Начните с Аль-Фатихи",
        read = "Читать",
        listen = "Слушать",
        pause = "Пауза",
        ayahOfPattern = "Аят {a} из {b}",
        today = "Сегодня",
        ofGoalPattern = "из {n}",
        todayProgressPattern = "Сегодня выполнено {a} из {b}",
        kindNew = "Новое",
        kindReview = "Повторить",
        kindWeak = "Слабые места",
        allDoneTitle = "На сегодня всё повторено",
        allDoneBody = "Возвращайтесь завтра: новые повторения появятся по плану.",
        inviteTitle = "Отметьте суру как «Заучиваю»",
        inviteBody = "Нажмите и удерживайте суру в списке, выберите «Заучиваю» — здесь появятся задачи на день.",
        dismiss = "Закрыть",
        recent = "Недавние",
        pinned = "Закреплена",
        searchPlaceholder = "Сура, номер или 2:255",
        clearSearch = "Очистить",
        filterAll = "Все",
        filterLearning = "Заучиваю",
        filterLearned = "Выучено",
        smartOpen = "Открыть",
        smartTitlePattern = "{name} · аят {n}",
        noResultsTitle = "Ничего не нашлось",
        noResultsBody = "Попробуйте название суры, её номер или аят в виде 2:255.",
        emptyLearningTitle = "Пока ничего не заучивается",
        emptyLearningBody = "Нажмите и удерживайте суру, чтобы отметить её как «Заучиваю».",
        emptyLearnedTitle = "Выученных сур пока нет",
        emptyLearnedBody = "Когда выучите суру, отметьте её — она появится здесь.",
        open = "Открыть",
        loadErrorTitle = "Не удалось загрузить Коран",
        loadErrorBody = "Проверьте интернет. При первом запуске он нужен, чтобы скачать текст.",
        retry = "Повторить",
        sheetStatus = "Статус суры",
        statusNotStarted = "Не начал",
        statusLearning = "Заучиваю",
        statusLearned = "Выучено",
        pin = "Закрепить",
        unpin = "Открепить",
        listenSurah = "Слушать суру",
        play = "Играть",
        stop = "Остановить",
        openPlayer = "Открыть плеер",
        audioError = "Нет связи",
        playerSubPattern = "Аят {a} · {s}",
        learnedToastPattern = "{name} — выучено",
        statusUpdateFailed = "Не удалось изменить статус суры",
    )

    AppLanguage.EN -> HomeCopy(
        language = language,
        greeting = "Assalamu alaikum",
        title = "Quran",
        settings = "Settings",
        progress = "Progress",
        progressPattern = "Progress: {n}%",
        continueLabel = "Continue",
        startWithFatiha = "Start with Al-Fatihah",
        read = "Read",
        listen = "Listen",
        pause = "Pause",
        ayahOfPattern = "Ayah {a} of {b}",
        today = "Today",
        ofGoalPattern = "of {n}",
        todayProgressPattern = "{a} of {b} done today",
        kindNew = "New",
        kindReview = "Review",
        kindWeak = "Weak spots",
        allDoneTitle = "All reviewed for today",
        allDoneBody = "Come back tomorrow: the next reviews arrive on schedule.",
        inviteTitle = "Mark a surah as “Memorizing”",
        inviteBody = "Press and hold a surah in the list and choose “Memorizing” to get your daily tasks here.",
        dismiss = "Dismiss",
        recent = "Recent",
        pinned = "Pinned",
        searchPlaceholder = "Surah, number or 2:255",
        clearSearch = "Clear",
        filterAll = "All",
        filterLearning = "Memorizing",
        filterLearned = "Memorized",
        smartOpen = "Open",
        smartTitlePattern = "{name} · ayah {n}",
        noResultsTitle = "Nothing found",
        noResultsBody = "Try a surah name, its number, or an ayah like 2:255.",
        emptyLearningTitle = "Nothing memorizing yet",
        emptyLearningBody = "Press and hold a surah to mark it as “Memorizing”.",
        emptyLearnedTitle = "No memorized surahs yet",
        emptyLearnedBody = "Mark a surah as “Memorized” once you know it by heart.",
        open = "Open",
        loadErrorTitle = "Can’t load the Quran",
        loadErrorBody = "Check your connection. The first launch needs it to download the text.",
        retry = "Try again",
        sheetStatus = "Surah status",
        statusNotStarted = "Not started",
        statusLearning = "Memorizing",
        statusLearned = "Memorized",
        pin = "Pin",
        unpin = "Unpin",
        listenSurah = "Listen to surah",
        play = "Play",
        stop = "Stop",
        openPlayer = "Open player",
        audioError = "No connection",
        playerSubPattern = "Ayah {a} · {s}",
        learnedToastPattern = "{name} — memorized",
        statusUpdateFailed = "Couldn’t change the surah status",
    )

    AppLanguage.TG -> HomeCopy(
        language = language,
        greeting = "Ассалому алайкум",
        title = "Қуръон",
        settings = "Танзимот",
        progress = "Пешрафт",
        progressPattern = "Пешрафт: {n}%",
        continueLabel = "Идома",
        startWithFatiha = "Аз сураи Фотиҳа оғоз кунед",
        read = "Хондан",
        listen = "Гӯш кардан",
        pause = "Таваққуф",
        ayahOfPattern = "Оят {a} аз {b}",
        today = "Имрӯз",
        ofGoalPattern = "аз {n}",
        todayProgressPattern = "Имрӯз {a} аз {b} иҷро шуд",
        kindNew = "Нав",
        kindReview = "Такрор",
        kindWeak = "Ҷойҳои заиф",
        allDoneTitle = "Барои имрӯз ҳама такрор шуд",
        allDoneBody = "Фардо баргардед: такрорҳои нав аз рӯи ҷадвал пайдо мешаванд.",
        inviteTitle = "Сураро «Аз бар мекунам» ишора кунед",
        inviteBody = "Сураро дар рӯйхат дароз пахш кунед ва «Аз бар мекунам»-ро интихоб намоед — вазифаҳои рӯз дар ин ҷо пайдо мешаванд.",
        dismiss = "Пӯшидан",
        recent = "Охирин",
        pinned = "Пин шудааст",
        searchPlaceholder = "Сура, рақам ё 2:255",
        clearSearch = "Пок кардан",
        filterAll = "Ҳама",
        // The segment is a third of the screen: the full "Аз бар мекунам" would be cut off.
        filterLearning = "Дар ҳифз",
        filterLearned = "Аз бар шуд",
        smartOpen = "Кушодан",
        smartTitlePattern = "{name} · оят {n}",
        noResultsTitle = "Чизе ёфт нашуд",
        noResultsBody = "Номи сура, рақами он ё оятро чун 2:255 нависед.",
        emptyLearningTitle = "Ҳоло чизе аз бар намешавад",
        emptyLearningBody = "Сураро дароз пахш кунед, то «Аз бар мекунам» ишора шавад.",
        emptyLearnedTitle = "Ҳоло сураи аз бар шуда нест",
        emptyLearnedBody = "Вақте сураро аз бар кардед, онро ишора кунед — дар ин ҷо пайдо мешавад.",
        open = "Кушодан",
        loadErrorTitle = "Қуръон бор нашуд",
        loadErrorBody = "Пайвастро санҷед. Дар оғоз барои боргирии матн интернет лозим аст.",
        retry = "Такрор",
        sheetStatus = "Ҳолати сура",
        statusNotStarted = "Оғоз нашудааст",
        statusLearning = "Аз бар мекунам",
        statusLearned = "Аз бар шуд",
        pin = "Пин кардан",
        unpin = "Пинро бардоштан",
        listenSurah = "Гӯш кардани сура",
        play = "Пахш",
        stop = "Қатъ",
        openPlayer = "Кушодани плеер",
        audioError = "Пайваст нест",
        playerSubPattern = "Оят {a} · {s}",
        learnedToastPattern = "{name} — аз бар шуд",
        statusUpdateFailed = "Ҳолати сура тағйир наёфт",
    )

    AppLanguage.UZ -> HomeCopy(
        language = language,
        greeting = "Assalomu alaykum",
        title = "Qur'on",
        settings = "Sozlamalar",
        progress = "Natija",
        progressPattern = "Natija: {n}%",
        continueLabel = "Davom etish",
        startWithFatiha = "Fotiha surasidan boshlang",
        read = "O‘qish",
        listen = "Tinglash",
        pause = "Pauza",
        ayahOfPattern = "Oyat {a} / {b}",
        today = "Bugun",
        ofGoalPattern = "/ {n}",
        todayProgressPattern = "Bugun {b} tadan {a} tasi bajarildi",
        kindNew = "Yangi",
        kindReview = "Takrorlash",
        kindWeak = "Zaif joylar",
        allDoneTitle = "Bugungi takrorlar tugadi",
        allDoneBody = "Ertaga qaytib keling: yangi takrorlar jadval bo‘yicha paydo bo‘ladi.",
        inviteTitle = "Surani «Yodlayapman» deb belgilang",
        inviteBody = "Ro‘yxatda surani bosib turing va «Yodlayapman»ni tanlang — kunlik vazifalar shu yerda paydo bo‘ladi.",
        dismiss = "Yopish",
        recent = "So‘nggilar",
        pinned = "Mahkamlangan",
        searchPlaceholder = "Sura, raqam yoki 2:255",
        clearSearch = "Tozalash",
        filterAll = "Barchasi",
        filterLearning = "Yodlayapman",
        filterLearned = "Yodlangan",
        smartOpen = "Ochish",
        smartTitlePattern = "{name} · {n}-oyat",
        noResultsTitle = "Hech narsa topilmadi",
        noResultsBody = "Sura nomi, raqami yoki 2:255 kabi oyatni kiriting.",
        emptyLearningTitle = "Hozircha yodlanayotgan sura yo‘q",
        emptyLearningBody = "Surani bosib turing va «Yodlayapman» deb belgilang.",
        emptyLearnedTitle = "Yodlangan suralar hali yo‘q",
        emptyLearnedBody = "Surani yodlab bo‘lgach, uni belgilang — u shu yerda paydo bo‘ladi.",
        open = "Ochish",
        loadErrorTitle = "Qur'on yuklanmadi",
        loadErrorBody = "Internetni tekshiring. Birinchi ishga tushirishda matnni yuklab olish uchun u kerak.",
        retry = "Qayta urinish",
        sheetStatus = "Sura holati",
        statusNotStarted = "Boshlanmagan",
        statusLearning = "Yodlayapman",
        statusLearned = "Yodlangan",
        pin = "Mahkamlash",
        unpin = "Mahkamlashni olib tashlash",
        listenSurah = "Surani tinglash",
        play = "Ijro etish",
        stop = "To‘xtatish",
        openPlayer = "Pleyerni ochish",
        audioError = "Aloqa yo‘q",
        playerSubPattern = "{a}-oyat · {s}",
        learnedToastPattern = "{name} — yodlangan",
        statusUpdateFailed = "Sura holatini o‘zgartirib bo‘lmadi",
    )

    AppLanguage.TR -> HomeCopy(
        language = language,
        greeting = "Selamün aleyküm",
        title = "Kur'an",
        settings = "Ayarlar",
        progress = "İlerleme",
        progressPattern = "İlerleme: {n}%",
        continueLabel = "Devam et",
        startWithFatiha = "Fâtiha Suresi ile başlayın",
        read = "Oku",
        listen = "Dinle",
        pause = "Duraklat",
        ayahOfPattern = "Ayet {a} / {b}",
        today = "Bugün",
        ofGoalPattern = "/ {n}",
        todayProgressPattern = "Bugün {a} / {b} tamamlandı",
        kindNew = "Yeni",
        kindReview = "Tekrar et",
        kindWeak = "Zayıf yerler",
        allDoneTitle = "Bugünkü tekrarlar tamam",
        allDoneBody = "Yarın tekrar gelin: yeni tekrarlar plana göre görünecek.",
        inviteTitle = "Bir sureyi “Ezberliyorum” olarak işaretleyin",
        inviteBody = "Listede bir sureye basılı tutun ve “Ezberliyorum”u seçin — günlük görevler burada görünür.",
        dismiss = "Kapat",
        recent = "Son açılanlar",
        pinned = "Sabitlendi",
        searchPlaceholder = "Sure, numara veya 2:255",
        clearSearch = "Temizle",
        filterAll = "Tümü",
        filterLearning = "Ezberliyorum",
        filterLearned = "Ezberlendi",
        smartOpen = "Aç",
        smartTitlePattern = "{name} · ayet {n}",
        noResultsTitle = "Sonuç bulunamadı",
        noResultsBody = "Sure adını, numarasını veya 2:255 gibi bir ayeti yazın.",
        emptyLearningTitle = "Henüz ezberlenen sure yok",
        emptyLearningBody = "Bir sureye basılı tutup “Ezberliyorum” olarak işaretleyin.",
        emptyLearnedTitle = "Henüz ezberlenmiş sure yok",
        emptyLearnedBody = "Bir sureyi ezberleyince işaretleyin — burada görünür.",
        open = "Aç",
        loadErrorTitle = "Kur'an yüklenemedi",
        loadErrorBody = "Bağlantınızı kontrol edin. İlk açılışta metni indirmek için internet gerekir.",
        retry = "Tekrar dene",
        sheetStatus = "Sure durumu",
        statusNotStarted = "Başlanmadı",
        statusLearning = "Ezberliyorum",
        statusLearned = "Ezberlendi",
        pin = "Sabitle",
        unpin = "Sabitlemeyi kaldır",
        listenSurah = "Sureyi dinle",
        play = "Oynat",
        stop = "Durdur",
        openPlayer = "Oynatıcıyı aç",
        audioError = "Bağlantı yok",
        playerSubPattern = "Ayet {a} · {s}",
        learnedToastPattern = "{name} — ezberlendi",
        statusUpdateFailed = "Sure durumu değiştirilemedi",
    )
}
