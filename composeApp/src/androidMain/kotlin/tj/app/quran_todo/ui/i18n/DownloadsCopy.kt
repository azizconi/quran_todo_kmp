package tj.app.quran_todo.ui.i18n

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import tj.app.quran_todo.common.i18n.AppLanguage
import tj.app.quran_todo.common.i18n.LocalAppLanguage
import kotlin.math.roundToLong

/**
 * Strings of "Downloads and storage" in the five app languages: the screen, its dialogs, the notification of the download and the
 * row in the settings. Texts with values are patterns with `{title}`, `{done}`, `{total}`, `{n}`, `{size}`, `{language}`, `{a}`,
 * `{b}` placeholders, filled by the functions below, so each language keeps its own word order; the plural forms of ayahs and
 * surahs come from [ayahCountLabel] and [surahCountLabel].
 *
 * The Tajik, Uzbek and Turkish wording should be checked by a native speaker.
 */
@Immutable
data class DownloadsCopy(
    val language: AppLanguage,
    val title: String,
    val back: String,

    // Options
    val reciter: String,
    val reciterHint: String,
    val wifiOnly: String,
    val wifiOnlyHint: String,
    /** Pattern with `{language}`. */
    val withTranslation: String,
    val withTranslationHint: String,

    // The queue
    /** Pattern with `{title}`, `{done}`, `{total}`: also the title of the notification. */
    val queueTitle: String,
    val queueWaiting: String,
    val queueRemaining: String,
    val queuePaused: String,
    val queueWaitingWifi: String,
    val queueWaitingNetwork: String,
    val pauseAll: String,
    val resume: String,
    val cancelAll: String,
    val retry: String,
    val failedAyahsOne: String,
    val failedAyahsMany: String,
    val failedTranslation: String,
    val notEnoughSpace: String,
    val nothingToDownload: String,

    // Sections
    val sectionPlan: String,
    val sectionJuz: String,
    val sectionSurahs: String,
    val sectionStorage: String,
    val planAll: String,
    /** Pattern with `{n}` surahs. */
    val planAllSubtitle: String,
    val searchPlaceholder: String,
    val searchClear: String,
    val searchEmpty: String,
    /** Patterns with `{n}`. */
    val juzTitle: String,
    val juzAmma: String,
    val juzTabarak: String,
    /** Pattern with `{a}` and `{b}`: the surahs of a juz ("Surahs 78–114"). */
    val juzSurahs: String,
    val juzSurahsOne: String,

    // Rows
    val rowEstimate: String,
    val rowPartial: String,
    val rowComplete: String,
    val rowQueued: String,
    val rowDownloading: String,
    val actionDownload: String,
    val actionPause: String,
    val actionResume: String,
    val actionCancel: String,
    val actionDelete: String,

    // Dialogs
    val cancel: String,
    val deleteTitle: String,
    val deleteBody: String,
    val deleteConfirm: String,
    val deleteAllTitle: String,
    val deleteAllBody: String,
    val deleteAllConfirm: String,
    val deletedToast: String,
    val cacheClearTitle: String,
    val cacheClearBody: String,
    val cacheClearConfirm: String,
    val cacheClearedToast: String,
    val cancelAllTitle: String,
    val cancelAllBody: String,
    val cancelAllConfirm: String,

    // Storage
    val storageDownloads: String,
    val storageDownloadsHint: String,
    val storageDeleteAll: String,
    val storageCache: String,
    val storageCacheHint: String,
    val storageClear: String,
    val storageFree: String,
    // "My recordings" (task F2a): the person's own reading, kept on the phone only
    val storageRecordings: String,
    val storageRecordingsHint: String,
    val storageDeleteRecordings: String,
    val recordingsDeleteTitle: String,
    val recordingsDeleteBody: String,
    val recordingsDeleteConfirm: String,
    val recordingsDeletedToast: String,

    // Notification
    val channelName: String,
    val channelDescription: String,
    val notificationQueued: String,
    val notificationCancel: String,

    val unitKb: String,
    val unitMb: String,
    val unitGb: String,
    val decimalSeparator: Char,
) {
    private fun String.fill(vararg values: Pair<String, Any>): String {
        var result = this
        values.forEach { (key, value) -> result = result.replace("{$key}", value.toString()) }
        return result
    }

    /** "230 MB", "2.4 MB", "1.4 GB", "120 KB": a size in the units of the language. */
    fun sizeLabel(bytes: Long): String = formatSize(bytes, unitKb, unitMb, unitGb, decimalSeparator)

    /** "≈ 54 MB": a size that was estimated, not measured. */
    fun estimateLabel(bytes: Long): String = rowEstimate.fill("size" to sizeLabel(bytes))

    fun withTranslationLabel(languageName: String): String = withTranslation.fill("language" to languageName)

    fun queueTitleLabel(title: String, done: Int, total: Int): String =
        // A request with nothing to count (a whole translation, task F4a) is named without "0 of 0": every pattern has the title first,
        // then " · " and the count.
        if (total <= 0) queueTitle.substringBefore(" · ").fill("title" to title) else queueTitle.fill("title" to title, "done" to done, "total" to total)

    fun queueWaitingLabel(n: Int): String = queueWaiting.fill("n" to n)

    fun queueRemainingLabel(bytes: Long): String = queueRemaining.fill("size" to sizeLabel(bytes))

    fun planAllSubtitleLabel(surahs: Int): String = planAllSubtitle.fill("n" to surahCountLabel(surahs, language))

    fun juzTitleLabel(juz: Int): String = when (juz) {
        30 -> juzAmma
        29 -> juzTabarak
        else -> juzTitle.fill("n" to juz)
    }

    /** "Surahs 78–114", or "Surah 2" for a juz inside one surah. */
    fun juzSurahsLabel(first: Int, last: Int): String =
        if (first == last) juzSurahsOne.fill("a" to first) else juzSurahs.fill("a" to first, "b" to last)

    fun rowPartialLabel(done: Int, total: Int, bytes: Long): String =
        rowPartial.fill("done" to done, "total" to total, "size" to sizeLabel(bytes))

    fun rowCompleteLabel(bytes: Long): String = rowComplete.fill("size" to sizeLabel(bytes))

    fun rowDownloadingLabel(done: Int, total: Int): String = rowDownloading.fill("done" to done, "total" to total)

    fun deleteBodyLabel(title: String, bytes: Long): String = deleteBody.fill("title" to title, "size" to sizeLabel(bytes))

    fun deleteAllBodyLabel(bytes: Long): String = deleteAllBody.fill("size" to sizeLabel(bytes))

    fun recordingsDeleteBodyLabel(bytes: Long): String = recordingsDeleteBody.fill("size" to sizeLabel(bytes))

    /**
     * What is left undone: "3 ayahs did not download", "Translation did not download", or both joined by a dot. Empty when nothing
     * failed.
     */
    fun failureLabel(ayahs: Int, translations: Int): String {
        val ayahPart = when {
            ayahs <= 0 -> null
            language == AppLanguage.RU && ayahs % 10 == 1 && ayahs % 100 != 11 -> failedAyahsOne.fill("n" to ayahCountLabel(ayahs, language))
            else -> failedAyahsMany.fill("n" to ayahCountLabel(ayahs, language))
        }
        val translationPart = failedTranslation.takeIf { translations > 0 }
        return listOfNotNull(ayahPart, translationPart).joinToString(" · ")
    }

    fun notificationQueuedLabel(n: Int): String = notificationQueued.fill("n" to n)

    /** The title of the notification and of the queue card: "Download: Juz Amma · 120 of 564". */
    fun notificationTitle(title: String, done: Int, total: Int): String = queueTitleLabel(title, done, total)
}

/**
 * "230 MB": under 1 MB in KB, under 10 MB with one decimal ("2.4 MB"), under 1 GB in whole MB, over that in GB with one decimal.
 * The units and the decimal separator are those of the language. A size that rounds up to the next unit is written in that unit
 * ("1.0 MB", not "1024 KB").
 */
internal fun formatSize(bytes: Long, unitKb: String, unitMb: String, unitGb: String, separator: Char): String {
    val kb = 1024.0
    val mb = kb * 1024
    val gb = mb * 1024
    fun oneDecimal(value: Double): String {
        val tenths = (value * 10).roundToLong()
        return "${tenths / 10}$separator${tenths % 10}"
    }
    return when {
        bytes < mb - kb / 2 -> "${(bytes / kb).roundToLong()} $unitKb"
        bytes < 10 * mb -> "${oneDecimal(bytes / mb)} $unitMb"
        bytes < gb - mb / 2 -> "${(bytes / mb).roundToLong()} $unitMb"
        else -> "${oneDecimal(bytes / gb)} $unitGb"
    }
}

@Composable
fun rememberDownloadsCopy(language: AppLanguage = LocalAppLanguage.current): DownloadsCopy =
    remember(language) { downloadsCopy(language) }

fun downloadsCopy(language: AppLanguage): DownloadsCopy = when (language) {
    AppLanguage.EN -> DownloadsCopy(
        language = language,
        title = "Downloads and storage",
        back = "Back",
        reciter = "Reciter",
        reciterHint = "Change it in the sound settings.",
        wifiOnly = "Wi-Fi only",
        wifiOnlyHint = "Mobile data is not used.",
        withTranslation = "With translation: {language}",
        withTranslationHint = "The translation is small and comes before the audio.",
        queueTitle = "Downloading: {title} · {done} of {total}",
        queueWaiting = "{n} more in the queue",
        queueRemaining = "{size} left",
        queuePaused = "Paused",
        queueWaitingWifi = "Waiting for Wi-Fi",
        queueWaitingNetwork = "Waiting for a connection",
        pauseAll = "Pause all",
        resume = "Resume",
        cancelAll = "Cancel",
        retry = "Retry",
        failedAyahsOne = "{n} did not download",
        failedAyahsMany = "{n} did not download",
        failedTranslation = "Translation did not download",
        notEnoughSpace = "Not enough space on the phone",
        nothingToDownload = "Nothing to download yet",
        sectionPlan = "My plan",
        sectionJuz = "Juz",
        sectionSurahs = "Surahs",
        sectionStorage = "Storage",
        planAll = "Everything in my plan",
        planAllSubtitle = "{n}",
        searchPlaceholder = "Search surahs",
        searchClear = "Clear",
        searchEmpty = "No surah found",
        juzTitle = "Juz {n}",
        juzAmma = "Juz Amma",
        juzTabarak = "Juz Tabarak",
        juzSurahs = "Surahs {a}–{b}",
        juzSurahsOne = "Surah {a}",
        rowEstimate = "≈ {size}",
        rowPartial = "Downloaded {done} of {total} · {size}",
        rowComplete = "Downloaded · {size}",
        rowQueued = "In the queue",
        rowDownloading = "Downloading {done} of {total}",
        actionDownload = "Download",
        actionPause = "Pause",
        actionResume = "Resume",
        actionCancel = "Remove from the queue",
        actionDelete = "Delete",
        cancel = "Cancel",
        deleteTitle = "Delete this download?",
        deleteBody = "The audio of {title} ({size}) will be removed from this phone. You can download it again at any time.",
        deleteConfirm = "Delete",
        deleteAllTitle = "Delete all downloads?",
        deleteAllBody = "All downloaded audio ({size}) will be removed from this phone. Without a connection it will not play until you download it again.",
        deleteAllConfirm = "Delete all",
        deletedToast = "Downloads deleted",
        cacheClearTitle = "Clear the listening cache?",
        cacheClearBody = "Ayahs you listened to are fetched again when you play them. Downloads stay.",
        cacheClearConfirm = "Clear",
        cacheClearedToast = "Cache cleared",
        cancelAllTitle = "Cancel all downloads?",
        cancelAllBody = "What has been downloaded stays on the phone. The rest is removed from the queue.",
        cancelAllConfirm = "Cancel downloads",
        storageDownloads = "Downloads",
        storageDownloadsHint = "Kept until you delete them.",
        storageDeleteAll = "Delete all downloads",
        storageCache = "Listening cache",
        storageCacheHint = "Up to 1 GB; the oldest files go first.",
        storageClear = "Clear",
        storageFree = "Free on the phone",
        storageRecordings = "My recordings",
        storageRecordingsHint = "Your own reading, on this phone only.",
        storageDeleteRecordings = "Delete all recordings",
        recordingsDeleteTitle = "Delete all recordings?",
        recordingsDeleteBody = "All your recordings ({size}) will be removed from this phone. They cannot be brought back.",
        recordingsDeleteConfirm = "Delete all",
        recordingsDeletedToast = "Recordings deleted",
        channelName = "Downloads",
        channelDescription = "Progress of audio downloads.",
        notificationQueued = "{n} more in the queue",
        notificationCancel = "Cancel",
        unitKb = "KB", unitMb = "MB", unitGb = "GB", decimalSeparator = '.',
    )
    AppLanguage.RU -> DownloadsCopy(
        language = language,
        title = "Загрузки и хранилище",
        back = "Назад",
        reciter = "Чтец",
        reciterHint = "Меняется в настройках звука.",
        wifiOnly = "Только по Wi-Fi",
        wifiOnlyHint = "Мобильный интернет не используется.",
        withTranslation = "С переводом: {language}",
        withTranslationHint = "Перевод небольшой, он скачивается раньше аудио.",
        queueTitle = "Загрузка: {title} · {done} из {total}",
        queueWaiting = "Ещё в очереди: {n}",
        queueRemaining = "Осталось {size}",
        queuePaused = "На паузе",
        queueWaitingWifi = "Ждём Wi-Fi",
        queueWaitingNetwork = "Ждём сеть",
        pauseAll = "Пауза всего",
        resume = "Продолжить",
        cancelAll = "Отменить",
        retry = "Повторить",
        failedAyahsOne = "{n} не скачался",
        failedAyahsMany = "{n} не скачались",
        failedTranslation = "Перевод не скачался",
        notEnoughSpace = "На телефоне не хватает места",
        nothingToDownload = "Пока нечего скачивать",
        sectionPlan = "Мой план",
        sectionJuz = "Джузы",
        sectionSurahs = "Суры",
        sectionStorage = "Хранилище",
        planAll = "Всё из моего плана",
        planAllSubtitle = "{n}",
        searchPlaceholder = "Поиск суры",
        searchClear = "Очистить",
        searchEmpty = "Сура не найдена",
        juzTitle = "Джуз {n}",
        juzAmma = "Джуз Амма",
        juzTabarak = "Джуз Таборак",
        juzSurahs = "Суры {a}–{b}",
        juzSurahsOne = "Сура {a}",
        rowEstimate = "≈ {size}",
        rowPartial = "Скачано {done} из {total} · {size}",
        rowComplete = "Скачано · {size}",
        rowQueued = "В очереди",
        rowDownloading = "Скачиваем {done} из {total}",
        actionDownload = "Скачать",
        actionPause = "Пауза",
        actionResume = "Продолжить",
        actionCancel = "Убрать из очереди",
        actionDelete = "Удалить",
        cancel = "Отмена",
        deleteTitle = "Удалить загрузку?",
        deleteBody = "Аудио «{title}» ({size}) будет удалено с телефона. Скачать его можно снова в любой момент.",
        deleteConfirm = "Удалить",
        deleteAllTitle = "Удалить все загрузки?",
        deleteAllBody = "Всё скачанное аудио ({size}) будет удалено с телефона. Без сети оно не будет играть, пока вы не скачаете его снова.",
        deleteAllConfirm = "Удалить всё",
        deletedToast = "Загрузки удалены",
        cacheClearTitle = "Очистить кэш прослушивания?",
        cacheClearBody = "Прослушанные аяты скачаются снова, когда вы их включите. Загрузки останутся.",
        cacheClearConfirm = "Очистить",
        cacheClearedToast = "Кэш очищен",
        cancelAllTitle = "Отменить все загрузки?",
        cancelAllBody = "Уже скачанное останется на телефоне. Остальное уйдёт из очереди.",
        cancelAllConfirm = "Отменить загрузки",
        storageDownloads = "Загрузки",
        storageDownloadsHint = "Хранятся, пока вы их не удалите.",
        storageDeleteAll = "Удалить все загрузки",
        storageCache = "Кэш прослушивания",
        storageCacheHint = "До 1 ГБ; старые файлы удаляются первыми.",
        storageClear = "Очистить",
        storageFree = "Свободно на телефоне",
        storageRecordings = "Мои записи",
        storageRecordingsHint = "Ваше чтение, только на этом телефоне.",
        storageDeleteRecordings = "Удалить все записи",
        recordingsDeleteTitle = "Удалить все записи?",
        recordingsDeleteBody = "Все ваши записи чтения ({size}) будут удалены с этого телефона. Вернуть их нельзя.",
        recordingsDeleteConfirm = "Удалить все",
        recordingsDeletedToast = "Записи удалены",
        channelName = "Загрузки",
        channelDescription = "Ход загрузки аудио.",
        notificationQueued = "Ещё в очереди: {n}",
        notificationCancel = "Отменить",
        unitKb = "КБ", unitMb = "МБ", unitGb = "ГБ", decimalSeparator = ',',
    )
    AppLanguage.TG -> DownloadsCopy(
        language = language,
        title = "Боргириҳо ва захира",
        back = "Бозгашт",
        reciter = "Қорӣ",
        reciterHint = "Дар танзимоти садо иваз карда мешавад.",
        wifiOnly = "Танҳо тавассути Wi-Fi",
        wifiOnlyHint = "Интернети мобилӣ истифода намешавад.",
        withTranslation = "Бо тарҷума: {language}",
        withTranslationHint = "Тарҷума хурд аст ва пеш аз аудио бор карда мешавад.",
        queueTitle = "Боргирӣ: {title} · {done} аз {total}",
        queueWaiting = "Дар навбат боз: {n}",
        queueRemaining = "Боқӣ: {size}",
        queuePaused = "Таваққуф шуд",
        queueWaitingWifi = "Интизори Wi-Fi",
        queueWaitingNetwork = "Интизори пайвастшавӣ",
        pauseAll = "Ҳамаро таваққуф кардан",
        resume = "Идома додан",
        cancelAll = "Бекор кардан",
        retry = "Такроран",
        failedAyahsOne = "{n} боргирӣ нашуд",
        failedAyahsMany = "{n} боргирӣ нашуд",
        failedTranslation = "Тарҷума боргирӣ нашуд",
        notEnoughSpace = "Дар телефон ҷой намерасад",
        nothingToDownload = "Ҳоло чизе барои боргирӣ нест",
        sectionPlan = "Нақшаи ман",
        sectionJuz = "Ҷузъҳо",
        sectionSurahs = "Сураҳо",
        sectionStorage = "Захира",
        planAll = "Ҳамаи нақшаи ман",
        planAllSubtitle = "{n}",
        searchPlaceholder = "Ҷустуҷӯи сура",
        searchClear = "Тоза кардан",
        searchEmpty = "Сура ёфт нашуд",
        juzTitle = "Ҷузъи {n}",
        juzAmma = "Ҷузъи Амма",
        juzTabarak = "Ҷузъи Табораку",
        juzSurahs = "Сураҳои {a}–{b}",
        juzSurahsOne = "Сураи {a}",
        rowEstimate = "≈ {size}",
        rowPartial = "Боргирӣ шуд: {done} аз {total} · {size}",
        rowComplete = "Боргирӣ шуд · {size}",
        rowQueued = "Дар навбат",
        rowDownloading = "Боргирӣ: {done} аз {total}",
        actionDownload = "Боргирӣ",
        actionPause = "Таваққуф",
        actionResume = "Идома додан",
        actionCancel = "Аз навбат баровардан",
        actionDelete = "Нест кардан",
        cancel = "Бекор кардан",
        deleteTitle = "Боргирӣ нест карда шавад?",
        deleteBody = "Аудиои «{title}» ({size}) аз телефон нест карда мешавад. Онро ҳар вақт дубора боргирӣ кардан мумкин аст.",
        deleteConfirm = "Нест кардан",
        deleteAllTitle = "Ҳамаи боргириҳо нест карда шаванд?",
        deleteAllBody = "Тамоми аудиои боргиришуда ({size}) аз телефон нест карда мешавад. Бе интернет он то дубора боргирӣ шуданаш садо намедиҳад.",
        deleteAllConfirm = "Ҳамаро нест кардан",
        deletedToast = "Боргириҳо нест карда шуданд",
        cacheClearTitle = "Кэши шунавоӣ тоза карда шавад?",
        cacheClearBody = "Оятҳои шунидашуда ҳангоми пахш кардан аз нав бор карда мешаванд. Боргириҳо мемонанд.",
        cacheClearConfirm = "Тоза кардан",
        cacheClearedToast = "Кэш тоза карда шуд",
        cancelAllTitle = "Ҳамаи боргириҳо бекор карда шаванд?",
        cancelAllBody = "Он чи боргирӣ шудааст, дар телефон мемонад. Боқӣ аз навбат бароварда мешавад.",
        cancelAllConfirm = "Бекор кардани боргириҳо",
        storageDownloads = "Боргириҳо",
        storageDownloadsHint = "То нест кардани шумо нигоҳ дошта мешаванд.",
        storageDeleteAll = "Нест кардани ҳамаи боргириҳо",
        storageCache = "Кэши шунавоӣ",
        storageCacheHint = "То 1 ГБ; файлҳои кӯҳна аввал нест мешаванд.",
        storageClear = "Тоза кардан",
        storageFree = "Дар телефон озод",
        storageRecordings = "Сабтҳои ман",
        storageRecordingsHint = "Қироати худи шумо, танҳо дар ҳамин телефон.",
        storageDeleteRecordings = "Нест кардани ҳамаи сабтҳо",
        recordingsDeleteTitle = "Ҳамаи сабтҳо нест карда шаванд?",
        recordingsDeleteBody = "Ҳамаи сабтҳои қироати шумо ({size}) аз ин телефон нест карда мешаванд. Онҳоро баргардондан имкон надорад.",
        recordingsDeleteConfirm = "Ҳамаро нест кардан",
        recordingsDeletedToast = "Сабтҳо нест карда шуданд",
        channelName = "Боргириҳо",
        channelDescription = "Ҷараёни боргирии аудио.",
        notificationQueued = "Дар навбат боз: {n}",
        notificationCancel = "Бекор кардан",
        unitKb = "КБ", unitMb = "МБ", unitGb = "ГБ", decimalSeparator = ',',
    )
    AppLanguage.UZ -> DownloadsCopy(
        language = language,
        title = "Yuklashlar va xotira",
        back = "Orqaga",
        reciter = "Qori",
        reciterHint = "Ovoz sozlamalarida o‘zgartiriladi.",
        wifiOnly = "Faqat Wi-Fi orqali",
        wifiOnlyHint = "Mobil internet ishlatilmaydi.",
        withTranslation = "Tarjima bilan: {language}",
        withTranslationHint = "Tarjima kichik, u audiodan oldin yuklanadi.",
        queueTitle = "Yuklanmoqda: {title} · {done} / {total}",
        queueWaiting = "Navbatda yana: {n}",
        queueRemaining = "Qoldi: {size}",
        queuePaused = "To‘xtatilgan",
        queueWaitingWifi = "Wi-Fi kutilmoqda",
        queueWaitingNetwork = "Aloqa kutilmoqda",
        pauseAll = "Hammasini to‘xtatish",
        resume = "Davom ettirish",
        cancelAll = "Bekor qilish",
        retry = "Qayta urinish",
        failedAyahsOne = "{n} yuklanmadi",
        failedAyahsMany = "{n} yuklanmadi",
        failedTranslation = "Tarjima yuklanmadi",
        notEnoughSpace = "Telefonda joy yetarli emas",
        nothingToDownload = "Hozircha yuklaydigan narsa yo‘q",
        sectionPlan = "Mening rejam",
        sectionJuz = "Juzlar",
        sectionSurahs = "Suralar",
        sectionStorage = "Xotira",
        planAll = "Rejamdagi hammasi",
        planAllSubtitle = "{n}",
        searchPlaceholder = "Sura qidirish",
        searchClear = "Tozalash",
        searchEmpty = "Sura topilmadi",
        juzTitle = "{n}-juz",
        juzAmma = "Amma juzi",
        juzTabarak = "Tabarak juzi",
        juzSurahs = "{a}–{b}-suralar",
        juzSurahsOne = "{a}-sura",
        rowEstimate = "≈ {size}",
        rowPartial = "Yuklandi: {done} / {total} · {size}",
        rowComplete = "Yuklab olingan · {size}",
        rowQueued = "Navbatda",
        rowDownloading = "Yuklanmoqda: {done} / {total}",
        actionDownload = "Yuklab olish",
        actionPause = "To‘xtatish",
        actionResume = "Davom ettirish",
        actionCancel = "Navbatdan olib tashlash",
        actionDelete = "O‘chirish",
        cancel = "Bekor qilish",
        deleteTitle = "Yuklab olingan o‘chirilsinmi?",
        deleteBody = "«{title}» audiosi ({size}) telefondan o‘chiriladi. Uni istalgan vaqtda qayta yuklab olish mumkin.",
        deleteConfirm = "O‘chirish",
        deleteAllTitle = "Barcha yuklashlar o‘chirilsinmi?",
        deleteAllBody = "Yuklab olingan barcha audio ({size}) telefondan o‘chiriladi. Aloqasiz u qayta yuklab olinmaguncha ijro etilmaydi.",
        deleteAllConfirm = "Hammasini o‘chirish",
        deletedToast = "Yuklashlar o‘chirildi",
        cacheClearTitle = "Tinglash keshi tozalansinmi?",
        cacheClearBody = "Tinglangan oyatlar ijro etilganda qayta yuklanadi. Yuklab olinganlar qoladi.",
        cacheClearConfirm = "Tozalash",
        cacheClearedToast = "Kesh tozalandi",
        cancelAllTitle = "Barcha yuklashlar bekor qilinsinmi?",
        cancelAllBody = "Yuklab olingani telefonda qoladi. Qolgani navbatdan olib tashlanadi.",
        cancelAllConfirm = "Yuklashlarni bekor qilish",
        storageDownloads = "Yuklashlar",
        storageDownloadsHint = "Siz o‘chirmaguncha saqlanadi.",
        storageDeleteAll = "Barcha yuklashlarni o‘chirish",
        storageCache = "Tinglash keshi",
        storageCacheHint = "1 GB gacha; eng eski fayllar birinchi o‘chadi.",
        storageClear = "Tozalash",
        storageFree = "Telefonda bo‘sh joy",
        storageRecordings = "Mening yozuvlarim",
        storageRecordingsHint = "O‘zingizning o‘qishingiz, faqat shu telefonda.",
        storageDeleteRecordings = "Barcha yozuvlarni o‘chirish",
        recordingsDeleteTitle = "Barcha yozuvlar o‘chirilsinmi?",
        recordingsDeleteBody = "Barcha o‘qish yozuvlaringiz ({size}) shu telefondan o‘chiriladi. Ularni qaytarib bo‘lmaydi.",
        recordingsDeleteConfirm = "Hammasini o‘chirish",
        recordingsDeletedToast = "Yozuvlar o‘chirildi",
        channelName = "Yuklashlar",
        channelDescription = "Audio yuklash jarayoni.",
        notificationQueued = "Navbatda yana: {n}",
        notificationCancel = "Bekor qilish",
        unitKb = "KB", unitMb = "MB", unitGb = "GB", decimalSeparator = ',',
    )
    AppLanguage.TR -> DownloadsCopy(
        language = language,
        title = "İndirmeler ve depolama",
        back = "Geri",
        reciter = "Kâri",
        reciterHint = "Ses ayarlarından değiştirilir.",
        wifiOnly = "Yalnızca Wi-Fi ile",
        wifiOnlyHint = "Mobil veri kullanılmaz.",
        withTranslation = "Çeviriyle: {language}",
        withTranslationHint = "Çeviri küçüktür ve sesten önce indirilir.",
        queueTitle = "İndiriliyor: {title} · {done} / {total}",
        queueWaiting = "Sırada {n} tane daha",
        queueRemaining = "Kalan: {size}",
        queuePaused = "Duraklatıldı",
        queueWaitingWifi = "Wi-Fi bekleniyor",
        queueWaitingNetwork = "Bağlantı bekleniyor",
        pauseAll = "Tümünü duraklat",
        resume = "Devam et",
        cancelAll = "İptal",
        retry = "Tekrar dene",
        failedAyahsOne = "{n} indirilemedi",
        failedAyahsMany = "{n} indirilemedi",
        failedTranslation = "Çeviri indirilemedi",
        notEnoughSpace = "Telefonda yeterli alan yok",
        nothingToDownload = "Henüz indirilecek bir şey yok",
        sectionPlan = "Planım",
        sectionJuz = "Cüzler",
        sectionSurahs = "Sureler",
        sectionStorage = "Depolama",
        planAll = "Planımdaki her şey",
        planAllSubtitle = "{n}",
        searchPlaceholder = "Sure ara",
        searchClear = "Temizle",
        searchEmpty = "Sure bulunamadı",
        juzTitle = "{n}. cüz",
        juzAmma = "Amme cüzü",
        juzTabarak = "Tebâreke cüzü",
        juzSurahs = "{a}–{b}. sureler",
        juzSurahsOne = "{a}. sure",
        rowEstimate = "≈ {size}",
        rowPartial = "İndirildi: {done} / {total} · {size}",
        rowComplete = "İndirildi · {size}",
        rowQueued = "Sırada",
        rowDownloading = "İndiriliyor: {done} / {total}",
        actionDownload = "İndir",
        actionPause = "Duraklat",
        actionResume = "Devam et",
        actionCancel = "Sıradan çıkar",
        actionDelete = "Sil",
        cancel = "Vazgeç",
        deleteTitle = "İndirme silinsin mi?",
        deleteBody = "«{title}» sesi ({size}) telefondan silinecek. İstediğiniz zaman yeniden indirebilirsiniz.",
        deleteConfirm = "Sil",
        deleteAllTitle = "Tüm indirmeler silinsin mi?",
        deleteAllBody = "İndirilen tüm sesler ({size}) telefondan silinecek. Bağlantı yokken, yeniden indirene kadar çalmaz.",
        deleteAllConfirm = "Hepsini sil",
        deletedToast = "İndirmeler silindi",
        cacheClearTitle = "Dinleme önbelleği temizlensin mi?",
        cacheClearBody = "Dinlenen ayetler çalındıkça yeniden indirilir. İndirmeler kalır.",
        cacheClearConfirm = "Temizle",
        cacheClearedToast = "Önbellek temizlendi",
        cancelAllTitle = "Tüm indirmeler iptal edilsin mi?",
        cancelAllBody = "İndirilenler telefonda kalır. Geri kalanı sıradan çıkarılır.",
        cancelAllConfirm = "İndirmeleri iptal et",
        storageDownloads = "İndirmeler",
        storageDownloadsHint = "Siz silene kadar saklanır.",
        storageDeleteAll = "Tüm indirmeleri sil",
        storageCache = "Dinleme önbelleği",
        storageCacheHint = "En çok 1 GB; en eski dosyalar önce silinir.",
        storageClear = "Temizle",
        storageFree = "Telefonda boş alan",
        storageRecordings = "Kayıtlarım",
        storageRecordingsHint = "Kendi okuyuşunuz, yalnızca bu telefonda.",
        storageDeleteRecordings = "Tüm kayıtları sil",
        recordingsDeleteTitle = "Tüm kayıtlar silinsin mi?",
        recordingsDeleteBody = "Tüm okuma kayıtlarınız ({size}) bu telefondan silinecek. Geri getirilemezler.",
        recordingsDeleteConfirm = "Hepsini sil",
        recordingsDeletedToast = "Kayıtlar silindi",
        channelName = "İndirmeler",
        channelDescription = "Ses indirmelerinin ilerlemesi.",
        notificationQueued = "Sırada {n} tane daha",
        notificationCancel = "İptal",
        unitKb = "KB", unitMb = "MB", unitGb = "GB", decimalSeparator = ',',
    )
}
