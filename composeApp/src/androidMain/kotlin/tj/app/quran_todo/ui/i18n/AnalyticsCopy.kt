package tj.app.quran_todo.ui.i18n

import androidx.compose.runtime.Immutable
import tj.app.quran_todo.common.i18n.AppLanguage

/**
 * Strings of the detailed analytics, the report of a period and "My notes" (task F5a) in the five app languages. Texts with numbers
 * or names are patterns with `{n}`, `{d}`, `{date}`, `{parts}` and `{name}` placeholders, so each language keeps its own word order.
 * Numbers with a noun ("3 ayahs", "5 repetitions") come from the shared labels of the other copies ([ayahCountLabel],
 * [lessonCopy]), which know the plural forms.
 */
@Immutable
data class AnalyticsCopy(
    val language: AppLanguage,

    // Entries on the progress screen and in the settings
    val detailsTitle: String,
    val detailsBody: String,
    val detailsOpen: String,
    val notesRowTitle: String,
    val notesRowBody: String,

    // The detailed screen
    val screenTitle: String,
    val back: String,

    // Retention by surah / juz
    val retentionTitle: String,
    val retentionHint: String,
    val toggleSurahs: String,
    val toggleJuz: String,
    val retentionEmpty: String,

    // The year heat map
    val heatTitle: String,
    val heatHint: String,
    val legendLess: String,
    val legendMore: String,
    val legendPause: String,
    val activeDaysFmt: String,
    val previousYear: String,
    val nextYear: String,
    val bubbleFmt: String,
    val dayNewFmt: String,
    val dayReviewedFmt: String,
    val dayListenedFmt: String,
    val dayNothing: String,
    val dayPause: String,

    // The forecast by surah
    val forecastTitle: String,
    val forecastHint: String,
    val forecastLearned: String,
    val forecastCurrent: String,
    val forecastLeftFmt: String,
    val forecastDateFmt: String,
    val forecastEmpty: String,

    // The hardest places
    val hardPlacesTitle: String,
    val hardPlacesHint: String,
    val hardPlacesTrain: String,
    val hardPlacesEmpty: String,
    val hardPlaceFmt: String,
    val hardMissesFmt: String,
    val hardMarked: String,

    // The report of a period
    val periodCustom: String,
    val formatImage: String,
    val formatPdf: String,
    val formatText: String,
    val formatTitle: String,
    val pickRangeTitle: String,
    val pickRangeConfirm: String,
    val pickRangeCancel: String,
    val reportStripLabel: String,

    // My notes
    val notesTitle: String,
    val notesSearch: String,
    val notesSearchClear: String,
    val notesEmptyTitle: String,
    val notesEmptyBody: String,
    val notesNoMatchTitle: String,
    val notesNoMatchBody: String,
    val notesDeleted: String,
    val notesUndo: String,
    val notesAyahFmt: String,
    val notesDeleteDescription: String,
) {
    private fun String.fill(vararg values: Pair<String, Any>): String =
        values.fold(this) { text, (key, value) -> text.replace("{$key}", value.toString()) }

    fun activeDays(n: Int): String = activeDaysFmt.fill("n" to n)

    fun forecastLeft(ayahs: Int): String = forecastLeftFmt.fill("n" to ayahCountLabel(ayahs, language))

    fun forecastDate(date: String): String = forecastDateFmt.fill("date" to date)

    fun hardPlace(name: String, ayah: Int): String = hardPlaceFmt.fill("name" to name, "n" to ayah)

    fun hardMisses(n: Int): String = hardMissesFmt.fill("n" to n)

    fun notesAyah(name: String, ayah: Int): String = notesAyahFmt.fill("name" to name, "n" to ayah)

    /** The bubble of a day of the heat map: "12 Mar: new 1, repeated 3, listened 2", or "12 Mar: no studying" / "12 Mar: plan pause". */
    fun bubble(date: String, newCount: Int, reviewed: Int, listened: Int, paused: Boolean): String {
        val parts = buildList {
            if (newCount > 0) add(dayNewFmt.fill("n" to newCount))
            if (reviewed > 0) add(dayReviewedFmt.fill("n" to reviewed))
            if (listened > 0) add(dayListenedFmt.fill("n" to listened))
        }
        val text = when {
            parts.isNotEmpty() -> parts.joinToString(", ")
            paused -> dayPause
            else -> dayNothing
        }
        return bubbleFmt.fill("date" to date, "parts" to text)
    }

}

fun analyticsCopy(language: AppLanguage): AnalyticsCopy = when (language) {
    AppLanguage.RU -> AnalyticsCopy(
        language = language,
        detailsTitle = "Подробно", detailsBody = "Удержание по сурам и джузам, календарь года, прогноз по сурам и самые трудные места.",
        detailsOpen = "Открыть подробности", notesRowTitle = "Мои заметки", notesRowBody = "Все ваши заметки к аятам в одном месте.",
        screenTitle = "Подробно", back = "Назад",
        retentionTitle = "Удержание", retentionHint = "Доля повторений без «Забыл» за 90 дней. Только там, где было хотя бы 3 повторения; слабые сверху.",
        toggleSurahs = "Суры", toggleJuz = "Джузы", retentionEmpty = "Пока мало повторений: нужно хотя бы 3 в одной суре.",
        heatTitle = "Календарь года", heatHint = "Каждая клетка — день. Чем темнее, тем больше было занятий.",
        legendLess = "Меньше", legendMore = "Больше", legendPause = "Пауза плана",
        activeDaysFmt = "Дней с занятиями: {n}", previousYear = "Предыдущий год", nextYear = "Следующий год",
        bubbleFmt = "{date}: {parts}", dayNewFmt = "новое {n}", dayReviewedFmt = "повторено {n}", dayListenedFmt = "прослушано {n}",
        dayNothing = "занятий не было", dayPause = "пауза плана",
        forecastTitle = "Прогноз по сурам", forecastHint = "Когда вы закончите каждую суру плана в нынешнем темпе.",
        forecastLearned = "выучена", forecastCurrent = "сейчас", forecastLeftFmt = "осталось {n}", forecastDateFmt = "~{date}",
        forecastEmpty = "Составьте план, и здесь появится прогноз по сурам.",
        hardPlacesTitle = "Самые трудные места", hardPlacesHint = "Аяты, на которых за 90 дней чаще всего ставили «Трудно» или «Забыл», и аяты с отметкой сейчас.",
        hardPlacesTrain = "Тренировать", hardPlacesEmpty = "Трудных мест нет. Так держать.",
        hardPlaceFmt = "{name} · аят {n}", hardMissesFmt = "{n} раз", hardMarked = "отмечен",
        periodCustom = "Свой период", formatImage = "Картинка", formatPdf = "PDF", formatText = "Текст", formatTitle = "Формат",
        pickRangeTitle = "Выберите даты отчёта", pickRangeConfirm = "Готово", pickRangeCancel = "Отмена",
        reportStripLabel = "Дни периода",
        notesTitle = "Мои заметки", notesSearch = "Поиск по заметкам", notesSearchClear = "Очистить поиск",
        notesEmptyTitle = "Заметок пока нет", notesEmptyBody = "Откройте аят в ридере, нажмите «Заметка» в листе аята и запишите мысль. Она появится здесь.",
        notesNoMatchTitle = "Ничего не найдено", notesNoMatchBody = "Попробуйте другое слово.",
        notesDeleted = "Заметка удалена", notesUndo = "Отменить", notesAyahFmt = "{name} · аят {n}", notesDeleteDescription = "Удалить заметку",
    )
    AppLanguage.EN -> AnalyticsCopy(
        language = language,
        detailsTitle = "In detail", detailsBody = "Retention by surah and juz, a calendar of the year, a forecast by surah and the hardest places.",
        detailsOpen = "Open the details", notesRowTitle = "My notes", notesRowBody = "All your notes on ayahs in one place.",
        screenTitle = "In detail", back = "Back",
        retentionTitle = "Retention", retentionHint = "The share of repetitions without \"Forgot\" over 90 days. Only where there were at least 3 repetitions; the weakest on top.",
        toggleSurahs = "Surahs", toggleJuz = "Juz", retentionEmpty = "Not enough repetitions yet: at least 3 in one surah.",
        heatTitle = "Year calendar", heatHint = "Each cell is a day. The darker, the more you studied.",
        legendLess = "Less", legendMore = "More", legendPause = "Plan pause",
        activeDaysFmt = "Days with studying: {n}", previousYear = "Previous year", nextYear = "Next year",
        bubbleFmt = "{date}: {parts}", dayNewFmt = "new {n}", dayReviewedFmt = "repeated {n}", dayListenedFmt = "listened {n}",
        dayNothing = "no studying", dayPause = "plan pause",
        forecastTitle = "Forecast by surah", forecastHint = "When you will finish each surah of the plan at the current pace.",
        forecastLearned = "learned", forecastCurrent = "now", forecastLeftFmt = "{n} left", forecastDateFmt = "~{date}",
        forecastEmpty = "Make a plan and the forecast by surah will appear here.",
        hardPlacesTitle = "Hardest places", hardPlacesHint = "Ayahs graded \"Hard\" or \"Forgot\" most often in 90 days, and ayahs marked now.",
        hardPlacesTrain = "Practice", hardPlacesEmpty = "No hard places. Keep it up.",
        hardPlaceFmt = "{name} · ayah {n}", hardMissesFmt = "{n} times", hardMarked = "marked",
        periodCustom = "Custom period", formatImage = "Image", formatPdf = "PDF", formatText = "Text", formatTitle = "Format",
        pickRangeTitle = "Choose the dates of the report", pickRangeConfirm = "Done", pickRangeCancel = "Cancel",
        reportStripLabel = "Days of the period",
        notesTitle = "My notes", notesSearch = "Search notes", notesSearchClear = "Clear search",
        notesEmptyTitle = "No notes yet", notesEmptyBody = "Open an ayah in the reader, tap \"Note\" in the ayah sheet and write a thought. It will appear here.",
        notesNoMatchTitle = "Nothing found", notesNoMatchBody = "Try another word.",
        notesDeleted = "Note deleted", notesUndo = "Undo", notesAyahFmt = "{name} · ayah {n}", notesDeleteDescription = "Delete the note",
    )
    AppLanguage.TG -> AnalyticsCopy(
        language = language,
        detailsTitle = "Муфассал", detailsBody = "Устуворӣ аз рӯи сура ва ҷузъ, тақвими сол, пешгӯии сураҳо ва душвортарин ҷойҳо.",
        detailsOpen = "Кушодани муфассал", notesRowTitle = "Ёддоштҳои ман", notesRowBody = "Ҳамаи ёддоштҳои шумо ба оятҳо дар як ҷо.",
        screenTitle = "Муфассал", back = "Бозгашт",
        retentionTitle = "Устуворӣ", retentionHint = "Ҳиссаи такрорҳо бе «Фаромӯш кардам» дар 90 рӯз. Танҳо дар ҷое, ки ақаллан 3 такрор буд; заифҳо дар боло.",
        toggleSurahs = "Сураҳо", toggleJuz = "Ҷузъҳо", retentionEmpty = "Ҳоло такрор кам аст: дар як сура ақаллан 3 такрор лозим.",
        heatTitle = "Тақвими сол", heatHint = "Ҳар катак як рӯз аст. Ҳар қадар тиратар, ҳамон қадар машғулият зиёдтар.",
        legendLess = "Камтар", legendMore = "Бештар", legendPause = "Танаффуси нақша",
        activeDaysFmt = "Рӯзҳои машғулият: {n}", previousYear = "Соли гузашта", nextYear = "Соли оянда",
        bubbleFmt = "{date}: {parts}", dayNewFmt = "нав {n}", dayReviewedFmt = "такрор {n}", dayListenedFmt = "шунида шуд {n}",
        dayNothing = "машғулият набуд", dayPause = "танаффуси нақша",
        forecastTitle = "Пешгӯии сураҳо", forecastHint = "Бо суръати ҳозира ҳар сураи нақшаро кай ба охир мерасонед.",
        forecastLearned = "ҳифз шуд", forecastCurrent = "ҳозир", forecastLeftFmt = "монд: {n}", forecastDateFmt = "~{date}",
        forecastEmpty = "Нақша созед, ва пешгӯии сураҳо дар ин ҷо пайдо мешавад.",
        hardPlacesTitle = "Душвортарин ҷойҳо", hardPlacesHint = "Оятҳое, ки дар 90 рӯз бештар «Душвор» ё «Фаромӯш кардам» гирифтанд, ва оятҳои ҳозир қайдшуда.",
        hardPlacesTrain = "Машқ", hardPlacesEmpty = "Ҷойҳои душвор нест. Идома диҳед.",
        hardPlaceFmt = "{name} · оят {n}", hardMissesFmt = "{n} маротиба", hardMarked = "қайд шудааст",
        periodCustom = "Давраи дилхоҳ", formatImage = "Акс", formatPdf = "PDF", formatText = "Матн", formatTitle = "Шакл",
        pickRangeTitle = "Санаҳои ҳисоботро интихоб кунед", pickRangeConfirm = "Тайёр", pickRangeCancel = "Бекор",
        reportStripLabel = "Рӯзҳои давра",
        notesTitle = "Ёддоштҳои ман", notesSearch = "Ҷустуҷӯ дар ёддоштҳо", notesSearchClear = "Пок кардани ҷустуҷӯ",
        notesEmptyTitle = "Ҳоло ёддошт нест", notesEmptyBody = "Оятро дар хонанда кушоед, дар варақаи оят «Ёддошт»-ро пахш кунед ва фикри худро нависед. Он дар ин ҷо пайдо мешавад.",
        notesNoMatchTitle = "Чизе ёфт нашуд", notesNoMatchBody = "Калимаи дигарро санҷед.",
        notesDeleted = "Ёддошт нест карда шуд", notesUndo = "Бекор кардан", notesAyahFmt = "{name} · оят {n}", notesDeleteDescription = "Нест кардани ёддошт",
    )
    AppLanguage.UZ -> AnalyticsCopy(
        language = language,
        detailsTitle = "Batafsil", detailsBody = "Sura va juz bo‘yicha esda saqlash, yil taqvimi, suralar prognozi va eng qiyin joylar.",
        detailsOpen = "Batafsilni ochish", notesRowTitle = "Eslatmalarim", notesRowBody = "Oyatlarga yozgan barcha eslatmalaringiz bir joyda.",
        screenTitle = "Batafsil", back = "Orqaga",
        retentionTitle = "Esda saqlash", retentionHint = "90 kunda «Unutdim»siz takrorlar ulushi. Kamida 3 takror bo‘lgan joylar; zaiflari tepada.",
        toggleSurahs = "Suralar", toggleJuz = "Juzlar", retentionEmpty = "Takror hali kam: bitta surada kamida 3 ta kerak.",
        heatTitle = "Yil taqvimi", heatHint = "Har bir katak — bir kun. Qanchalik to‘q bo‘lsa, shunchalik ko‘p shug‘ullangansiz.",
        legendLess = "Kamroq", legendMore = "Ko‘proq", legendPause = "Reja pauzasi",
        activeDaysFmt = "Mashg‘ulot kunlari: {n}", previousYear = "Oldingi yil", nextYear = "Keyingi yil",
        bubbleFmt = "{date}: {parts}", dayNewFmt = "yangi {n}", dayReviewedFmt = "takror {n}", dayListenedFmt = "tinglangan {n}",
        dayNothing = "mashg‘ulot bo‘lmagan", dayPause = "reja pauzasi",
        forecastTitle = "Suralar prognozi", forecastHint = "Hozirgi sur’atda reja suralarining har birini qachon tugatasiz.",
        forecastLearned = "yodlangan", forecastCurrent = "hozir", forecastLeftFmt = "{n} qoldi", forecastDateFmt = "~{date}",
        forecastEmpty = "Reja tuzing, suralar prognozi shu yerda paydo bo‘ladi.",
        hardPlacesTitle = "Eng qiyin joylar", hardPlacesHint = "90 kunda eng ko‘p «Qiyin» yoki «Unutdim» baho olgan oyatlar va hozir belgilanganlari.",
        hardPlacesTrain = "Mashq qilish", hardPlacesEmpty = "Qiyin joylar yo‘q. Shunday davom eting.",
        hardPlaceFmt = "{name} · {n}-oyat", hardMissesFmt = "{n} marta", hardMarked = "belgilangan",
        periodCustom = "O‘z davrim", formatImage = "Rasm", formatPdf = "PDF", formatText = "Matn", formatTitle = "Format",
        pickRangeTitle = "Hisobot sanalarini tanlang", pickRangeConfirm = "Tayyor", pickRangeCancel = "Bekor qilish",
        reportStripLabel = "Davr kunlari",
        notesTitle = "Eslatmalarim", notesSearch = "Eslatmalardan qidirish", notesSearchClear = "Qidiruvni tozalash",
        notesEmptyTitle = "Hozircha eslatma yo‘q", notesEmptyBody = "O‘quvchida oyatni oching, oyat oynasidagi «Eslatma»ni bosing va fikringizni yozing. U shu yerda ko‘rinadi.",
        notesNoMatchTitle = "Hech narsa topilmadi", notesNoMatchBody = "Boshqa so‘zni sinab ko‘ring.",
        notesDeleted = "Eslatma o‘chirildi", notesUndo = "Bekor qilish", notesAyahFmt = "{name} · {n}-oyat", notesDeleteDescription = "Eslatmani o‘chirish",
    )
    AppLanguage.TR -> AnalyticsCopy(
        language = language,
        detailsTitle = "Ayrıntılı", detailsBody = "Sure ve cüze göre akılda kalma, yıl takvimi, sure tahmini ve en zor yerler.",
        detailsOpen = "Ayrıntıları aç", notesRowTitle = "Notlarım", notesRowBody = "Ayetlere yazdığınız tüm notlar tek yerde.",
        screenTitle = "Ayrıntılı", back = "Geri",
        retentionTitle = "Akılda kalma", retentionHint = "90 günde «Unuttum» denmeyen tekrarların payı. Yalnızca en az 3 tekrar olan yerler; en zayıflar üstte.",
        toggleSurahs = "Sureler", toggleJuz = "Cüzler", retentionEmpty = "Henüz tekrar az: bir surede en az 3 tekrar gerekir.",
        heatTitle = "Yıl takvimi", heatHint = "Her kare bir gün. Ne kadar koyuysa o kadar çok çalıştınız.",
        legendLess = "Az", legendMore = "Çok", legendPause = "Plan molası",
        activeDaysFmt = "Çalışılan gün: {n}", previousYear = "Önceki yıl", nextYear = "Sonraki yıl",
        bubbleFmt = "{date}: {parts}", dayNewFmt = "yeni {n}", dayReviewedFmt = "tekrar {n}", dayListenedFmt = "dinlenen {n}",
        dayNothing = "çalışma yok", dayPause = "plan molası",
        forecastTitle = "Sure tahmini", forecastHint = "Şimdiki hızla planın her suresini ne zaman bitireceğiniz.",
        forecastLearned = "ezberlendi", forecastCurrent = "şimdi", forecastLeftFmt = "{n} kaldı", forecastDateFmt = "~{date}",
        forecastEmpty = "Bir plan yapın, sure tahmini burada görünsün.",
        hardPlacesTitle = "En zor yerler", hardPlacesHint = "90 günde en çok «Zor» veya «Unuttum» denen ayetler ve şu an işaretli olanlar.",
        hardPlacesTrain = "Çalış", hardPlacesEmpty = "Zor yer yok. Böyle devam edin.",
        hardPlaceFmt = "{name} · ayet {n}", hardMissesFmt = "{n} kez", hardMarked = "işaretli",
        periodCustom = "Özel dönem", formatImage = "Resim", formatPdf = "PDF", formatText = "Metin", formatTitle = "Biçim",
        pickRangeTitle = "Raporun tarihlerini seçin", pickRangeConfirm = "Tamam", pickRangeCancel = "Vazgeç",
        reportStripLabel = "Dönemin günleri",
        notesTitle = "Notlarım", notesSearch = "Notlarda ara", notesSearchClear = "Aramayı temizle",
        notesEmptyTitle = "Henüz not yok", notesEmptyBody = "Okuyucuda bir ayeti açın, ayet sayfasında «Not»a dokunun ve düşüncenizi yazın. Burada görünür.",
        notesNoMatchTitle = "Bir şey bulunamadı", notesNoMatchBody = "Başka bir kelime deneyin.",
        notesDeleted = "Not silindi", notesUndo = "Geri al", notesAyahFmt = "{name} · ayet {n}", notesDeleteDescription = "Notu sil",
    )
}
