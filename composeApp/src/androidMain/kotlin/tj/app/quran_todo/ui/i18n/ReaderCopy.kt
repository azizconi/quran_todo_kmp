package tj.app.quran_todo.ui.i18n

import tj.app.quran_todo.common.i18n.AppLanguage

/**
 * Strings of the reader and its player. Wording comes from the old `surahReaderCopy` and
 * `PlayerFeatureCopy`; status names follow the glossary of docs/redesign/ANDROID_REDESIGN.md, section 10.
 * Texts with a value in them use `{name}`-style placeholders filled by the helper functions below.
 */
data class ReaderCopy(
    private val language: AppLanguage,
    // Top bar and menu
    val back: String,
    val textSettings: String,
    val more: String,
    val readingProgress: String,
    val ayah: String,
    val meccan: String,
    val medinan: String,
    /** Between the ayah number and the ayah count in the player ("12 of 286"). */
    val ofWord: String,
    val ayahWord: String,
    val ayahWordFew: String,
    val ayahWordMany: String,
    val goToAyah: String,
    val goToTitle: String,
    val goTo: String,
    // Surah status
    val surahStatusTitle: String,
    val surahStatusHint: String,
    val notStarted: String,
    val learning: String,
    val learned: String,
    val learnedToastFmt: String,
    val statusError: String,
    /** The status page of the surah sheet: "Learning" (adds the surah to the plan) and "I know it by heart". */
    val choiceLearning: String,
    val choiceKnown: String,
    /** "Remove {n} learned ayahs from the repetitions?" in the plural forms of the language (see [removeLearned]). */
    val removeLearnedOne: String,
    val removeLearnedFew: String,
    val removeLearnedMany: String,
    val removeLearnedHint: String,
    val removeAction: String,
    val cancelAction: String,
    // End of surah
    val nextSurah: String,
    val quranFinished: String,
    val startOver: String,
    val notLoaded: String,
    val retry: String,
    /** Shown in place of the translation when it could not be loaded; followed by [retry] ("... · Retry"). */
    val translationNotLoaded: String,
    // Following the playing ayah
    val jumpToCurrent: String,
    val playingElsewhereFmt: String,
    // Player dock
    val reciter: String,
    val play: String,
    val pause: String,
    val previous: String,
    val next: String,
    val expandPlayer: String,
    val collapsePlayer: String,
    val noConnectionRetry: String,
    val yourTurn: String,
    val secondsShort: String,
    val repeatPassage: String,
    val fromAyah: String,
    val toAyah: String,
    val startRepeat: String,
    val stopRepeat: String,
    val pauseBetween: String,
    val pauseHint: String,
    val none: String,
    val speed: String,
    val sleepTimer: String,
    val sleepOff: String,
    val sleepEndOfSurah: String,
    val sleepMinutesFmt: String,
    val sleepLeftFmt: String,
    val audioPosition: String,
    // Ayah sheet
    val selectAyahFmt: String,
    val ayahActions: String,
    val listenFromHere: String,
    val repeatThisAyah: String,
    val ayahStatus: String,
    val note: String,
    val noteHint: String,
    val saveNote: String,
    val noteSaved: String,
    val noteRemoved: String,
    val hasNote: String,
    val weakAyah: String,
    val reviewDue: String,
    val reviewInFmt: String,
    /** The ayah is in a passage of the manzil circle (no due day). */
    val inOldCircle: String,
    val checkFromHere: String,
    val knowThisAyah: String,
    val removeFromLearned: String,
    val copyAction: String,
    val copied: String,
    val share: String,
    val quranRef: String,
    // Appearance sheet
    val textSize: String,
    val fontStyle: String,
    val translation: String,
    val theme: String,
    val highlightWords: String,
    val keepScreenOn: String,
) {
    /** "286 ayahs", with the plural forms of the language. */
    fun ayahCount(count: Int): String {
        val word = when (language) {
            AppLanguage.RU -> {
                val mod10 = count % 10
                val mod100 = count % 100
                when {
                    mod10 == 1 && mod100 != 11 -> ayahWord
                    mod10 in 2..4 && mod100 !in 12..14 -> ayahWordFew
                    else -> ayahWordMany
                }
            }
            AppLanguage.EN -> if (count == 1) ayahWord else ayahWordMany
            else -> ayahWordMany
        }
        return "$count $word"
    }

    /** "Meccan" / "Medinan" for the place the surah was revealed in (any spelling the data uses). */
    fun revelationPlace(value: String): String {
        val normalized = value.lowercase()
        return when {
            normalized.contains("meccan") || normalized.contains("makka") || normalized.contains("mekka") ||
                normalized.contains("мекка") || normalized.contains("макка") -> meccan
            normalized.contains("medinan") || normalized.contains("madina") || normalized.contains("medina") ||
                normalized.contains("медина") || normalized.contains("мадина") -> medinan
            else -> value
        }
    }

    /** "Ayah 12 / 286" for the compact bar. */
    fun ayahOf(index: Int, total: Int): String = "$ayah $index / $total"

    fun learnedToast(surahName: String): String = learnedToastFmt.replace("{name}", surahName)

    fun playingElsewhere(surahName: String): String = playingElsewhereFmt.replace("{name}", surahName)

    fun selectAyah(number: Int): String = selectAyahFmt.replace("{n}", number.toString())

    fun sleepMinutes(minutes: Int): String = sleepMinutesFmt.replace("{m}", minutes.toString())

    fun sleepLeft(time: String): String = sleepLeftFmt.replace("{time}", time)

    fun reviewIn(days: Int): String = reviewInFmt.replace("{n}", days.toString())

    /** "Remove 12 learned ayahs from the repetitions?" */
    fun removeLearned(count: Int): String {
        val pattern = if (language == AppLanguage.RU) {
            val mod10 = count % 10
            val mod100 = count % 100
            when {
                mod10 == 1 && mod100 != 11 -> removeLearnedOne
                mod10 in 2..4 && mod100 !in 12..14 -> removeLearnedFew
                else -> removeLearnedMany
            }
        } else if (language == AppLanguage.EN && count == 1) {
            removeLearnedOne
        } else {
            removeLearnedMany
        }
        return pattern.replace("{n}", count.toString())
    }

    fun repeatLabel(passage: String, times: String) = "↻ $passage $times"
}

fun readerCopy(language: AppLanguage): ReaderCopy = when (language) {
    AppLanguage.RU -> ReaderCopy(
        language = language,
        back = "Назад", textSettings = "Вид текста", more = "Ещё", readingProgress = "Прогресс чтения",
        ayah = "Аят", meccan = "Мекканская", medinan = "Мединская", ofWord = "из", ayahWord = "аят", ayahWordFew = "аята", ayahWordMany = "аятов",
        goToAyah = "Перейти к аяту…", goToTitle = "Перейти к аяту", goTo = "Перейти",
        surahStatusTitle = "Статус суры", surahStatusHint = "«Знаю наизусть» добавит суру в повторения, «Учу» — в ваш план.",
        notStarted = "Не начал", learning = "Заучиваю", learned = "Выучено",
        learnedToastFmt = "{name} — выучено", statusError = "Не удалось обновить статус",
        choiceLearning = "Учу", choiceKnown = "Знаю наизусть",
        removeLearnedOne = "Убрать {n} выученный аят из повторений?",
        removeLearnedFew = "Убрать {n} выученных аята из повторений?",
        removeLearnedMany = "Убрать {n} выученных аятов из повторений?",
        removeLearnedHint = "Прогресс повторений этих аятов будет потерян.",
        removeAction = "Убрать", cancelAction = "Отмена",
        nextSurah = "Следующая сура", quranFinished = "Вы дочитали Коран до конца", startOver = "Начать сначала",
        notLoaded = "Не удалось загрузить суру", retry = "Повторить",
        translationNotLoaded = "Перевод не загружен",
        jumpToCurrent = "К текущему аяту", playingElsewhereFmt = "Играет: {name}",
        reciter = "Аль-Афаси", play = "Воспроизвести", pause = "Пауза",
        previous = "Предыдущий аят", next = "Следующий аят",
        expandPlayer = "Развернуть плеер", collapsePlayer = "Свернуть плеер",
        noConnectionRetry = "Нет связи · Повторить", yourTurn = "Ваша очередь", secondsShort = "сек",
        repeatPassage = "Повтор отрывка", fromAyah = "С аята", toAyah = "По аят",
        startRepeat = "Повторять", stopRepeat = "Остановить повтор",
        pauseBetween = "Пауза между аятами", pauseHint = "Время, чтобы повторить за чтецом.", none = "Нет",
        speed = "Скорость", sleepTimer = "Таймер сна", sleepOff = "Выкл", sleepEndOfSurah = "Конец суры",
        sleepMinutesFmt = "{m} мин", sleepLeftFmt = "Осталось {time}", audioPosition = "Позиция в аяте",
        selectAyahFmt = "Выбрать аят {n}", ayahActions = "Действия с аятом",
        listenFromHere = "Слушать отсюда", repeatThisAyah = "Повторять этот аят", ayahStatus = "Статус аята",
        note = "Заметка", noteHint = "Добавьте заметку к этому аяту", saveNote = "Сохранить",
        noteSaved = "Заметка сохранена", noteRemoved = "Заметка удалена", hasNote = "Есть заметка",
        weakAyah = "Трудный аят", reviewDue = "Пора повторить", reviewInFmt = "Повторение через {n} дн.",
        inOldCircle = "В круге старого", checkFromHere = "Проверь себя отсюда",
        knowThisAyah = "Знаю этот аят", removeFromLearned = "Убрать из выученных",
        copyAction = "Копировать", copied = "Скопировано", share = "Поделиться", quranRef = "Коран",
        textSize = "Размер текста", fontStyle = "Шрифт", translation = "Перевод", theme = "Тема",
        highlightWords = "Подсветка слов при слушании",
        keepScreenOn = "Не гасить экран",
    )
    AppLanguage.EN -> ReaderCopy(
        language = language,
        back = "Back", textSettings = "Text appearance", more = "More", readingProgress = "Reading progress",
        ayah = "Ayah", meccan = "Meccan", medinan = "Medinan", ofWord = "of", ayahWord = "ayah", ayahWordFew = "ayahs", ayahWordMany = "ayahs",
        goToAyah = "Go to ayah…", goToTitle = "Go to ayah", goTo = "Go",
        surahStatusTitle = "Surah status", surahStatusHint = "\"I know it by heart\" adds the surah to your reviews, \"Learning\" adds it to your plan.",
        notStarted = "Not started", learning = "Memorizing", learned = "Memorized",
        learnedToastFmt = "{name} — memorized", statusError = "Couldn't update the status",
        choiceLearning = "Learning", choiceKnown = "I know it by heart",
        removeLearnedOne = "Remove {n} learned ayah from your reviews?",
        removeLearnedFew = "Remove {n} learned ayahs from your reviews?",
        removeLearnedMany = "Remove {n} learned ayahs from your reviews?",
        removeLearnedHint = "The review progress of these ayahs will be lost.",
        removeAction = "Remove", cancelAction = "Cancel",
        nextSurah = "Next surah", quranFinished = "You have reached the end of the Quran", startOver = "Start over",
        notLoaded = "Couldn't load the surah", retry = "Retry",
        translationNotLoaded = "Translation not loaded",
        jumpToCurrent = "Back to current ayah", playingElsewhereFmt = "Playing: {name}",
        reciter = "Al-Afasy", play = "Play", pause = "Pause",
        previous = "Previous ayah", next = "Next ayah",
        expandPlayer = "Expand player", collapsePlayer = "Collapse player",
        noConnectionRetry = "No connection · Retry", yourTurn = "Your turn", secondsShort = "s",
        repeatPassage = "Repeat a passage", fromAyah = "From ayah", toAyah = "To ayah",
        startRepeat = "Repeat", stopRepeat = "Stop repeating",
        pauseBetween = "Pause between ayahs", pauseHint = "Time to recite after the reciter.", none = "None",
        speed = "Speed", sleepTimer = "Sleep timer", sleepOff = "Off", sleepEndOfSurah = "End of surah",
        sleepMinutesFmt = "{m} min", sleepLeftFmt = "{time} left", audioPosition = "Position in the ayah",
        selectAyahFmt = "Select ayah {n}", ayahActions = "Ayah actions",
        listenFromHere = "Listen from here", repeatThisAyah = "Repeat this ayah", ayahStatus = "Ayah status",
        note = "Note", noteHint = "Add a note to this ayah", saveNote = "Save",
        noteSaved = "Note saved", noteRemoved = "Note removed", hasNote = "Has a note",
        weakAyah = "Hard ayah", reviewDue = "Due for review", reviewInFmt = "Review in {n} d",
        inOldCircle = "In the review circle", checkFromHere = "Check yourself from here",
        knowThisAyah = "I know this ayah", removeFromLearned = "Remove from learned",
        copyAction = "Copy", copied = "Copied", share = "Share", quranRef = "Quran",
        textSize = "Text size", fontStyle = "Font", translation = "Translation", theme = "Theme",
        highlightWords = "Highlight words while listening",
        keepScreenOn = "Keep screen on",
    )
    AppLanguage.TG -> ReaderCopy(
        language = language,
        back = "Бозгашт", textSettings = "Намуди матн", more = "Бештар", readingProgress = "Пешрафти хониш",
        ayah = "Оят", meccan = "Маккӣ", medinan = "Маданӣ", ofWord = "аз", ayahWord = "оят", ayahWordFew = "оят", ayahWordMany = "оят",
        goToAyah = "Гузаштан ба оят…", goToTitle = "Гузаштан ба оят", goTo = "Гузаштан",
        surahStatusTitle = "Ҳолати сура", surahStatusHint = "«Аз бар медонам» сураро ба такрорҳо, «Аз бар мекунам» — ба нақшаи шумо илова мекунад.",
        notStarted = "Оғоз нашудааст", learning = "Аз бар мекунам", learned = "Аз бар шуд",
        learnedToastFmt = "{name} — аз бар шуд", statusError = "Навсозии ҳолат муяссар нашуд",
        choiceLearning = "Аз бар мекунам", choiceKnown = "Аз бар медонам",
        removeLearnedOne = "{n} ояти аз баршударо аз такрорҳо мебароред?",
        removeLearnedFew = "{n} ояти аз баршударо аз такрорҳо мебароред?",
        removeLearnedMany = "{n} ояти аз баршударо аз такрорҳо мебароред?",
        removeLearnedHint = "Пешрафти такрори ин оятҳо гум мешавад.",
        removeAction = "Баровардан", cancelAction = "Бекор кардан",
        nextSurah = "Сураи навбатӣ", quranFinished = "Шумо Қуръонро то охир хондед", startOver = "Аз сари нав оғоз кардан",
        notLoaded = "Сура бор нашуд", retry = "Такрор",
        translationNotLoaded = "Тарҷума бор нашуд",
        jumpToCurrent = "Ба ояти ҷорӣ", playingElsewhereFmt = "Садо медиҳад: {name}",
        reciter = "Ал-Афасӣ", play = "Навохтан", pause = "Таваққуф",
        previous = "Ояти пешина", next = "Ояти баъдӣ",
        expandPlayer = "Кушодани плеер", collapsePlayer = "Пӯшидани плеер",
        noConnectionRetry = "Пайваст нест · Такрор", yourTurn = "Навбати шумо", secondsShort = "с",
        repeatPassage = "Такрори порча", fromAyah = "Аз оят", toAyah = "То оят",
        startRepeat = "Такрор кардан", stopRepeat = "Қатъи такрор",
        pauseBetween = "Таваққуф миёни оятҳо", pauseHint = "Вақт барои такрор пас аз қорӣ.", none = "Не",
        speed = "Суръат", sleepTimer = "Таймери хоб", sleepOff = "Хомӯш", sleepEndOfSurah = "Охири сура",
        sleepMinutesFmt = "{m} дақ", sleepLeftFmt = "Боқӣ: {time}", audioPosition = "Мавқеъ дар оят",
        selectAyahFmt = "Интихоби оят {n}", ayahActions = "Амалҳо бо оят",
        listenFromHere = "Гӯш кардан аз ин ҷо", repeatThisAyah = "Ин оятро такрор кардан", ayahStatus = "Ҳолати оят",
        note = "Ёддошт", noteHint = "Ба ин оят ёддошт илова кунед", saveNote = "Захира кардан",
        noteSaved = "Ёддошт захира шуд", noteRemoved = "Ёддошт нест карда шуд", hasNote = "Ёддошт дорад",
        weakAyah = "Ояти душвор", reviewDue = "Вақти такрор расид", reviewInFmt = "Такрор баъд аз {n} рӯз",
        inOldCircle = "Дар доираи такрор", checkFromHere = "Аз ин ҷо худро санҷед",
        knowThisAyah = "Ин оятро медонам", removeFromLearned = "Аз оятҳои аз баршуда баровардан",
        copyAction = "Нусха бардоштан", copied = "Нусха бардошта шуд", share = "Мубодила кардан", quranRef = "Қуръон",
        textSize = "Андозаи матн", fontStyle = "Ҳуруф", translation = "Тарҷума", theme = "Мавзӯъ",
        highlightWords = "Равшан кардани калима ҳангоми шунидан",
        keepScreenOn = "Экранро хомӯш накардан",
    )
    AppLanguage.UZ -> ReaderCopy(
        language = language,
        back = "Orqaga", textSettings = "Matn ko‘rinishi", more = "Yana", readingProgress = "O‘qish jarayoni",
        ayah = "Oyat", meccan = "Makkiy", medinan = "Madaniy", ofWord = "/", ayahWord = "oyat", ayahWordFew = "oyat", ayahWordMany = "oyat",
        goToAyah = "Oyatga o‘tish…", goToTitle = "Oyatga o‘tish", goTo = "O‘tish",
        surahStatusTitle = "Sura holati", surahStatusHint = "«Yoddan bilaman» surani takrorlarga, «Yodlayapman» — rejangizga qo‘shadi.",
        notStarted = "Boshlanmagan", learning = "Yodlayapman", learned = "Yodlangan",
        learnedToastFmt = "{name} — yodlandi", statusError = "Holatni yangilab bo‘lmadi",
        choiceLearning = "Yodlayapman", choiceKnown = "Yoddan bilaman",
        removeLearnedOne = "{n} ta yodlangan oyatni takrorlardan olib tashlaysizmi?",
        removeLearnedFew = "{n} ta yodlangan oyatni takrorlardan olib tashlaysizmi?",
        removeLearnedMany = "{n} ta yodlangan oyatni takrorlardan olib tashlaysizmi?",
        removeLearnedHint = "Bu oyatlarning takror jarayoni yo‘qoladi.",
        removeAction = "Olib tashlash", cancelAction = "Bekor qilish",
        nextSurah = "Keyingi sura", quranFinished = "Siz Qur’onni oxirigacha o‘qib chiqdingiz", startOver = "Boshidan boshlash",
        notLoaded = "Surani yuklab bo‘lmadi", retry = "Qayta urinish",
        translationNotLoaded = "Tarjima yuklanmadi",
        jumpToCurrent = "Joriy oyatga", playingElsewhereFmt = "Ijro etilmoqda: {name}",
        reciter = "Al-Afasi", play = "Tinglash", pause = "Pauza",
        previous = "Oldingi oyat", next = "Keyingi oyat",
        expandPlayer = "Pleyerni ochish", collapsePlayer = "Pleyerni yig‘ish",
        noConnectionRetry = "Aloqa yo‘q · Qayta urinish", yourTurn = "Sizning navbatingiz", secondsShort = "s",
        repeatPassage = "Parcha takrori", fromAyah = "Oyatdan", toAyah = "Oyatgacha",
        startRepeat = "Takrorlash", stopRepeat = "Takrorni to‘xtatish",
        pauseBetween = "Oyatlar orasida tanaffus", pauseHint = "Qoridan keyin takrorlash uchun vaqt.", none = "Yo‘q",
        speed = "Tezlik", sleepTimer = "Uyqu taymeri", sleepOff = "O‘chiq", sleepEndOfSurah = "Sura oxiri",
        sleepMinutesFmt = "{m} daq", sleepLeftFmt = "Qoldi: {time}", audioPosition = "Oyatdagi o‘rni",
        selectAyahFmt = "{n}-oyatni tanlash", ayahActions = "Oyat amallari",
        listenFromHere = "Shu yerdan tinglash", repeatThisAyah = "Shu oyatni takrorlash", ayahStatus = "Oyat holati",
        note = "Eslatma", noteHint = "Bu oyatga eslatma qo‘shing", saveNote = "Saqlash",
        noteSaved = "Eslatma saqlandi", noteRemoved = "Eslatma o‘chirildi", hasNote = "Eslatma bor",
        weakAyah = "Qiyin oyat", reviewDue = "Takrorlash vaqti keldi", reviewInFmt = "Takror {n} kundan keyin",
        inOldCircle = "Takror doirasida", checkFromHere = "Shu yerdan o‘zingizni tekshiring",
        knowThisAyah = "Bu oyatni bilaman", removeFromLearned = "Yodlanganlardan olib tashlash",
        copyAction = "Nusxalash", copied = "Nusxalandi", share = "Ulashish", quranRef = "Qur’on",
        textSize = "Matn o‘lchami", fontStyle = "Shrift", translation = "Tarjima", theme = "Mavzu",
        highlightWords = "Tinglaganda so‘zlarni ajratib ko‘rsatish",
        keepScreenOn = "Ekranni o‘chirmaslik",
    )
    AppLanguage.TR -> ReaderCopy(
        language = language,
        back = "Geri", textSettings = "Metin görünümü", more = "Daha fazla", readingProgress = "Okuma ilerlemesi",
        ayah = "Ayet", meccan = "Mekki", medinan = "Medeni", ofWord = "/", ayahWord = "ayet", ayahWordFew = "ayet", ayahWordMany = "ayet",
        goToAyah = "Ayete git…", goToTitle = "Ayete git", goTo = "Git",
        surahStatusTitle = "Sure durumu", surahStatusHint = "«Ezbere biliyorum» sureyi tekrarlara, «Ezberliyorum» planınıza ekler.",
        notStarted = "Başlanmadı", learning = "Ezberliyorum", learned = "Ezberlendi",
        learnedToastFmt = "{name} — ezberlendi", statusError = "Durum güncellenemedi",
        choiceLearning = "Ezberliyorum", choiceKnown = "Ezbere biliyorum",
        removeLearnedOne = "{n} ezberlenmiş ayet tekrarlardan çıkarılsın mı?",
        removeLearnedFew = "{n} ezberlenmiş ayet tekrarlardan çıkarılsın mı?",
        removeLearnedMany = "{n} ezberlenmiş ayet tekrarlardan çıkarılsın mı?",
        removeLearnedHint = "Bu ayetlerin tekrar ilerlemesi kaybolacak.",
        removeAction = "Çıkar", cancelAction = "Vazgeç",
        nextSurah = "Sonraki sure", quranFinished = "Kur’an’ı sonuna kadar okudunuz", startOver = "Baştan başla",
        notLoaded = "Sure yüklenemedi", retry = "Tekrar dene",
        translationNotLoaded = "Çeviri yüklenmedi",
        jumpToCurrent = "Geçerli ayete", playingElsewhereFmt = "Çalıyor: {name}",
        reciter = "El-Afasi", play = "Oynat", pause = "Duraklat",
        previous = "Önceki ayet", next = "Sonraki ayet",
        expandPlayer = "Oynatıcıyı genişlet", collapsePlayer = "Oynatıcıyı daralt",
        noConnectionRetry = "Bağlantı yok · Tekrar dene", yourTurn = "Sıra sizde", secondsShort = "sn",
        repeatPassage = "Bölüm tekrarı", fromAyah = "İlk ayet", toAyah = "Son ayet",
        startRepeat = "Tekrarla", stopRepeat = "Tekrarı durdur",
        pauseBetween = "Ayetler arasında ara", pauseHint = "Okuyucudan sonra tekrarlamak için süre.", none = "Yok",
        speed = "Hız", sleepTimer = "Uyku zamanlayıcısı", sleepOff = "Kapalı", sleepEndOfSurah = "Sure sonu",
        sleepMinutesFmt = "{m} dk", sleepLeftFmt = "Kalan: {time}", audioPosition = "Ayetteki konum",
        selectAyahFmt = "{n}. ayeti seç", ayahActions = "Ayet işlemleri",
        listenFromHere = "Buradan dinle", repeatThisAyah = "Bu ayeti tekrarla", ayahStatus = "Ayet durumu",
        note = "Not", noteHint = "Bu ayete not ekleyin", saveNote = "Kaydet",
        noteSaved = "Not kaydedildi", noteRemoved = "Not silindi", hasNote = "Notu var",
        weakAyah = "Zor ayet", reviewDue = "Tekrar zamanı geldi", reviewInFmt = "{n} gün sonra tekrar",
        inOldCircle = "Tekrar döngüsünde", checkFromHere = "Buradan kendini sına",
        knowThisAyah = "Bu ayeti biliyorum", removeFromLearned = "Ezberlenenlerden çıkar",
        copyAction = "Kopyala", copied = "Kopyalandı", share = "Paylaş", quranRef = "Kur’an",
        textSize = "Metin boyutu", fontStyle = "Yazı tipi", translation = "Çeviri", theme = "Tema",
        highlightWords = "Dinlerken kelimeyi vurgula",
        keepScreenOn = "Ekranı açık tut",
    )
}
