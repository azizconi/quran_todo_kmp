package tj.app.quran_todo.common.i18n

data class PlayerFeatureCopy(
    val repeatAction: String = "Repeat",
    val pauseAction: String = "Pause",
    val chooseAyahs: String = "Which ayahs to repeat?",
    val startRepeat: String = "Start repeating",
    val stopRepeat: String = "Stop repeating",
    val onLoop: String = "On loop",
    val noPause: String = "No pause",
    val pauseHint: String = "Time to recite after the reciter.",
    val ayahsLabel: String = "Ayahs",
    val timesLabel: String = "times",
    val timesFew: String = timesLabel,
    val rangeTitle: String = "Repeat a passage",
    val rangeBody: String = "Choose ayahs and listen to them on repeat.",
    val pauseTitle: String = "Recite after the reciter",
    val pauseBody: String = "Leave a pause after each ayah to recite it yourself.",
    val controlsTitle: String = "Everything within reach",
    val controlsBody: String = "Adjust speed, repeats and pauses right in the player.",
    val settings: String = "Player",
    val range: String = "Choose a passage",
    val from: String = "From ayah",
    val to: String = "To ayah",
    val repeatAyah: String = "Ayah repeats",
    val repeatRange: String = "Passage repeats",
    val pause: String = "Pause after each ayah",
    val speed: String = "Speed",
    val off: String = "Off",
    val seconds: String = "s",
    val next: String = "Next",
    val tryIt: String = "Try it",
    val done: String = "Done",
    val close: String = "Close",
    val help: String = "How it works",
    val invalidRange: String = "Enter ayah numbers in order, within this surah.",
    val waiting: String = "Your turn",
    val audioError: String = "Audio unavailable. Check your connection and try again.",
    val rangeHint: String = "The whole passage repeats, from the first ayah to the last.",
) {
    fun guideTitle(step: Int): String = when (step) {
        0 -> rangeTitle
        1 -> pauseTitle
        else -> controlsTitle
    }

    fun guideBody(step: Int): String = when (step) {
        0 -> rangeBody
        1 -> pauseBody
        else -> controlsBody
    }
}

fun playerFeatureCopy(language: AppLanguage): PlayerFeatureCopy = when (language) {
    AppLanguage.EN -> PlayerFeatureCopy()
    AppLanguage.RU -> PlayerFeatureCopy(
        repeatAction = "Повтор", pauseAction = "Пауза", chooseAyahs = "Какие аяты повторять?", startRepeat = "Начать повтор", stopRepeat = "Выключить повтор", onLoop = "По кругу", noPause = "Без паузы", pauseHint = "Время, чтобы повторить за чтецом.", ayahsLabel = "Аяты", timesLabel = "раз", timesFew = "раза",
        rangeTitle = "Повторяйте отрывок", rangeBody = "Выберите аяты и слушайте их по кругу.",
        pauseTitle = "Повторяйте за чтецом", pauseBody = "Добавьте паузу после аята, чтобы повторить его вслух.",
        controlsTitle = "Всё под рукой", controlsBody = "Меняйте скорость, повторы и паузу прямо в плеере.",
        settings = "Плеер", range = "Отрывок аятов", from = "С аята", to = "По аят",
        repeatAyah = "Повтор аята", repeatRange = "Повтор отрывка", pause = "Пауза после аята", speed = "Скорость",
        off = "Нет", seconds = "сек", next = "Далее", tryIt = "Попробовать", done = "Готово", close = "Закрыть",
        help = "Как это работает", invalidRange = "Укажите аяты по порядку, в пределах этой суры.", waiting = "Ваша очередь",
        audioError = "Аудио недоступно. Проверьте соединение и попробуйте снова.",
        rangeHint = "Повторяется весь отрывок — от первого до последнего аята.",
    )
    AppLanguage.TG -> PlayerFeatureCopy(
        repeatAction = "Такрор", pauseAction = "Таваққуф", chooseAyahs = "Кадом оятҳоро такрор мекунед?", startRepeat = "Оғози такрор", stopRepeat = "Қатъи такрор", onLoop = "Пайваста", noPause = "Бе таваққуф", pauseHint = "Вақт барои такрор пас аз қорӣ.", ayahsLabel = "Оятҳо", timesLabel = "бор",
        rangeTitle = "Порчаро такрор кунед", rangeBody = "Оятҳоро интихоб кунед ва пайваста гӯш кунед.",
        pauseTitle = "Пас аз қорӣ бихонед", pauseBody = "Пас аз ҳар оят таваққуф кунед, то худатон такрор кунед.",
        controlsTitle = "Ҳама чиз дар даст", controlsBody = "Суръат, такрор ва таваққуфро дар плеер танзим кунед.",
        settings = "Плеер", range = "Порчаи оятҳо", from = "Аз оят", to = "То оят",
        repeatAyah = "Такрори оят", repeatRange = "Такрори порча", pause = "Таваққуф пас аз оят", speed = "Суръат",
        off = "Не", seconds = "с", next = "Баъдӣ", tryIt = "Санҷидан", done = "Тайёр", close = "Пӯшидан",
        help = "Чӣ тавр кор мекунад", invalidRange = "Рақами оятҳоро бо тартиб дар доираи ин сура ворид кунед.", waiting = "Навбати шумо",
        audioError = "Аудио дастрас нест. Пайвастро санҷед ва боз кӯшиш кунед.",
        rangeHint = "Тамоми порча аз ояти аввал то охирин такрор мешавад.",
    )
    AppLanguage.UZ -> PlayerFeatureCopy(
        repeatAction = "Takror", pauseAction = "Tanaffus", chooseAyahs = "Qaysi oyatlar takrorlansin?", startRepeat = "Takrorlashni boshlash", stopRepeat = "Takrorni o‘chirish", onLoop = "Doimiy", noPause = "Tanaffussiz", pauseHint = "Qoridan keyin takrorlash uchun vaqt.", ayahsLabel = "Oyatlar", timesLabel = "marta",
        rangeTitle = "Parchani takrorlang", rangeBody = "Oyatlarni tanlang va qayta-qayta tinglang.",
        pauseTitle = "Qoridan keyin takrorlang", pauseBody = "O‘zingiz takrorlash uchun har oyatdan keyin tanaffus qoldiring.",
        controlsTitle = "Hammasi qo‘l ostida", controlsBody = "Tezlik, takror va tanaffusni pleyerda sozlang.",
        settings = "Pleyer", range = "Oyatlar oralig‘i", from = "Oyatdan", to = "Oyatgacha",
        repeatAyah = "Oyat takrori", repeatRange = "Parcha takrori", pause = "Oyatdan keyin tanaffus", speed = "Tezlik",
        off = "Yo‘q", seconds = "s", next = "Keyingi", tryIt = "Sinab ko‘rish", done = "Tayyor", close = "Yopish",
        help = "Qanday ishlaydi", invalidRange = "Oyatlarni ushbu sura doirasida tartib bilan kiriting.", waiting = "Sizning navbatingiz",
        audioError = "Audio mavjud emas. Ulanishni tekshiring va qayta urinib ko‘ring.",
        rangeHint = "Parcha birinchi oyatdan oxirgisigacha to‘liq takrorlanadi.",
    )
    AppLanguage.TR -> PlayerFeatureCopy(
        repeatAction = "Tekrar", pauseAction = "Ara", chooseAyahs = "Hangi ayetler tekrarlansın?", startRepeat = "Tekrarı başlat", stopRepeat = "Tekrarı kapat", onLoop = "Sürekli", noPause = "Ara yok", pauseHint = "Okuyucudan sonra tekrarlamak için süre.", ayahsLabel = "Ayetler", timesLabel = "kez",
        rangeTitle = "Bir bölümü tekrarlayın", rangeBody = "Ayetleri seçin ve tekrar tekrar dinleyin.",
        pauseTitle = "Okuyucudan sonra okuyun", pauseBody = "Kendiniz okumak için her ayetten sonra ara verin.",
        controlsTitle = "Her şey elinizin altında", controlsBody = "Hızı, tekrarları ve arayı oynatıcıdan ayarlayın.",
        settings = "Oynatıcı", range = "Ayet aralığı", from = "İlk ayet", to = "Son ayet",
        repeatAyah = "Ayet tekrarı", repeatRange = "Bölüm tekrarı", pause = "Ayetten sonra ara", speed = "Hız",
        off = "Yok", seconds = "sn", next = "İleri", tryIt = "Dene", done = "Bitti", close = "Kapat",
        help = "Nasıl çalışır", invalidRange = "Bu suredeki ayet numaralarını sırayla girin.", waiting = "Sıra sizde",
        audioError = "Ses kullanılamıyor. Bağlantınızı kontrol edip tekrar deneyin.",
        rangeHint = "Bölüm, ilk ayetten son ayete kadar bütünüyle tekrarlanır.",
    )
}
