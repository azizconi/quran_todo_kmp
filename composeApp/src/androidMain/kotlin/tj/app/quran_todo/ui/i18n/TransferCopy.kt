package tj.app.quran_todo.ui.i18n

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import tj.app.quran_todo.common.i18n.AppLanguage
import tj.app.quran_todo.common.i18n.LocalAppLanguage
import tj.app.quran_todo.hifz.backup.BackupPreview
import tj.app.quran_todo.hifz.model.HifzScope

/** How a language writes "N things": Russian has three forms, English two, Tajik, Uzbek and Turkish keep one after a number. */
internal enum class PluralRule { RUSSIAN, ONE_OTHER, ALWAYS_ONE }

/** A counted noun: "1 note", "34 passages". [few] is used by Russian only. */
internal class CountNoun(
    private val one: String,
    private val few: String,
    private val many: String,
    private val rule: PluralRule,
) {
    fun form(n: Int): String = when (rule) {
        PluralRule.ALWAYS_ONE -> one
        PluralRule.ONE_OTHER -> if (n == 1) one else many
        PluralRule.RUSSIAN -> {
            val tens = n % 100
            val units = n % 10
            when {
                units == 1 && tens != 11 -> one
                units in 2..4 && tens !in 12..14 -> few
                else -> many
            }
        }
    }

    fun format(n: Int): String = "$n ${form(n)}"
}

/**
 * Strings of "Transfer and backup": the settings section, the import confirmation with the numbers of the file, the toasts.
 * Kept apart from [SettingsCopy] and reached through [SettingsCopy.transfer].
 */
class TransferCopy internal constructor(
    val sectionTitle: String,
    val backupTitle: String,
    val backupDescription: String,
    val saveTitle: String,
    val saveDescription: String,
    val shareTitle: String,
    val shareDescription: String,
    val restoreTitle: String,
    val restoreDescription: String,
    /** Template with `{date}`. */
    val lastBackup: String,
    val footnote: String,
    val shareChooserTitle: String,
    val importDialogTitle: String,
    /** Template with `{date}`: "Copy of 12 March 2026". */
    val previewCopyOf: String,
    /** Template with `{plan}`. */
    val previewPlan: String,
    val previewEmpty: String,
    val previewReplace: String,
    val replace: String,
    val cancel: String,
    val exportSaved: String,
    val exportFailed: String,
    val importDone: String,
    val importNotBackup: String,
    val importNewer: String,
    val importCorrupt: String,
    val importOutOfRange: String,
    val importUnreadable: String,
    val importFailed: String,
    private val planJuz30: String,
    private val planJuz29: String,
    private val planFromStart: String,
    private val planCustom: String,
    private val months: List<String>,
    private val passages: CountNoun,
    private val hardAyahs: CountNoun,
    private val notes: CountNoun,
) {
    init {
        require(months.size == 12) { "twelve months" }
    }

    /** "12 March 2026", or "12 March" when [withYear] is false. The day is the calendar day in [zone]. */
    fun formatDate(epochMillis: Long, withYear: Boolean = true, zone: TimeZone = TimeZone.currentSystemDefault()): String {
        val date = Instant.fromEpochMilliseconds(epochMillis).toLocalDateTime(zone).date
        val dayAndMonth = "${date.dayOfMonth} ${months[date.monthNumber - 1]}"
        return if (withYear) "$dayAndMonth ${date.year}" else dayAndMonth
    }

    /**
     * "Last copy: 12 March". The year is added only when the copy is from another year than [nowMillis], so an old copy is not
     * mistaken for a recent one.
     */
    fun lastBackupLabel(epochMillis: Long, nowMillis: Long, zone: TimeZone = TimeZone.currentSystemDefault()): String {
        val year = Instant.fromEpochMilliseconds(epochMillis).toLocalDateTime(zone).date.year
        val thisYear = Instant.fromEpochMilliseconds(nowMillis).toLocalDateTime(zone).date.year
        return lastBackup.replace("{date}", formatDate(epochMillis, withYear = year != thisYear, zone = zone))
    }

    fun planName(scope: HifzScope): String = when (scope) {
        HifzScope.JUZ_30 -> planJuz30
        HifzScope.JUZ_29 -> planJuz29
        HifzScope.FROM_START -> planFromStart
        HifzScope.CUSTOM -> planCustom
    }

    /**
     * What the file holds, in one sentence: "Copy of 12 March 2026: plan "Juz Amma", 34 passages, 2 hard ayahs, 120 notes."
     * Parts that are zero are left out.
     */
    fun previewSummary(preview: BackupPreview, zone: TimeZone = TimeZone.currentSystemDefault()): String {
        val head = previewCopyOf.replace("{date}", formatDate(preview.exportedAt, zone = zone))
        val parts = buildList {
            preview.planScope?.let { add(previewPlan.replace("{plan}", planName(it))) }
            if (preview.passages > 0) add(passages.format(preview.passages))
            if (preview.hardAyahs > 0) add(hardAyahs.format(preview.hardAyahs))
            if (preview.notes > 0) add(notes.format(preview.notes))
        }
        return if (parts.isEmpty()) "$head: $previewEmpty." else "$head: ${parts.joinToString(", ")}."
    }

    /** The whole message of the confirmation: the summary, then the warning that the data of the phone is replaced. */
    fun importMessage(preview: BackupPreview, zone: TimeZone = TimeZone.currentSystemDefault()): String =
        "${previewSummary(preview, zone)} $previewReplace"

    /** Every plain string, for the test that no translation is left blank. */
    internal fun allStrings(): List<String> = listOf(
        sectionTitle, saveTitle, saveDescription, shareTitle, shareDescription, restoreTitle, restoreDescription, lastBackup,
        footnote, shareChooserTitle, importDialogTitle, previewCopyOf, previewPlan, previewEmpty, previewReplace, replace, cancel,
        exportSaved, exportFailed, importDone, importNotBackup, importNewer, importCorrupt, importOutOfRange, importUnreadable,
        importFailed, planJuz30, planJuz29, planFromStart, planCustom,
    ) + months + listOf(passages.form(1), passages.form(2), passages.form(5)) +
        listOf(hardAyahs.form(1), hardAyahs.form(2), hardAyahs.form(5)) + listOf(notes.form(1), notes.form(2), notes.form(5))
}

@Composable
fun rememberTransferCopy(language: AppLanguage = LocalAppLanguage.current): TransferCopy =
    remember(language) { transferCopy(language) }

fun transferCopy(language: AppLanguage): TransferCopy = when (language) {
    AppLanguage.EN -> TransferCopy(
        sectionTitle = "Transfer and backup",
        backupTitle = "Backup", backupDescription = "Save, share or restore a copy of your data",
        saveTitle = "Save a copy to a file",
        saveDescription = "Progress, notes and settings in one file",
        shareTitle = "Send a copy",
        shareDescription = "For example to yourself in Telegram or Google Drive",
        restoreTitle = "Restore from a file",
        restoreDescription = "Replaces the data on this phone",
        lastBackup = "Last copy: {date}",
        footnote = "Your progress is stored on this phone. Android also keeps it in your Google account if backup is turned on.",
        shareChooserTitle = "Send a copy",
        importDialogTitle = "Restore from a file?",
        previewCopyOf = "Copy of {date}",
        previewPlan = "plan “{plan}”",
        previewEmpty = "it holds no memorization data",
        previewReplace = "The current data on this phone will be replaced.",
        replace = "Replace",
        cancel = "Cancel",
        exportSaved = "Copy saved",
        exportFailed = "Could not save the copy",
        importDone = "Data restored",
        importNotBackup = "This is not a Quran Hifz backup file",
        importNewer = "This copy was made by a newer version of the app. Please update the app",
        importCorrupt = "The backup file is damaged",
        importOutOfRange = "The file holds invalid data, restore cancelled",
        importUnreadable = "Could not read the file",
        importFailed = "Could not restore. Your data is unchanged",
        planJuz30 = "Juz Amma",
        planJuz29 = "Juz Tabarak",
        planFromStart = "From the start of the Quran",
        planCustom = "My own list of surahs",
        months = listOf(
            "January", "February", "March", "April", "May", "June",
            "July", "August", "September", "October", "November", "December",
        ),
        passages = CountNoun("passage", "passages", "passages", PluralRule.ONE_OTHER),
        hardAyahs = CountNoun("hard ayah", "hard ayahs", "hard ayahs", PluralRule.ONE_OTHER),
        notes = CountNoun("note", "notes", "notes", PluralRule.ONE_OTHER),
    )

    AppLanguage.RU -> TransferCopy(
        sectionTitle = "Перенос и копия",
        backupTitle = "Резервная копия", backupDescription = "Сохранить, отправить или восстановить копию данных",
        saveTitle = "Сохранить копию в файл",
        saveDescription = "Прогресс, заметки и настройки — одним файлом",
        shareTitle = "Отправить копию",
        shareDescription = "Например, себе в Telegram или в Google Диск",
        restoreTitle = "Восстановить из файла",
        restoreDescription = "Заменит данные на этом телефоне",
        lastBackup = "Последняя копия: {date}",
        footnote = "Прогресс хранится на этом телефоне. Android также сохраняет его в вашем аккаунте Google, если резервное копирование включено.",
        shareChooserTitle = "Отправить копию",
        importDialogTitle = "Восстановить из файла?",
        previewCopyOf = "Копия от {date}",
        previewPlan = "план «{plan}»",
        previewEmpty = "данных заучивания в ней нет",
        previewReplace = "Текущие данные на этом телефоне будут заменены.",
        replace = "Заменить",
        cancel = "Отмена",
        exportSaved = "Копия сохранена",
        exportFailed = "Не удалось сохранить копию",
        importDone = "Данные восстановлены",
        importNotBackup = "Это не файл копии Quran Hifz",
        importNewer = "Копия сделана более новой версией приложения. Обновите приложение",
        importCorrupt = "Файл копии повреждён",
        importOutOfRange = "В файле неверные данные, восстановление отменено",
        importUnreadable = "Не удалось прочитать файл",
        importFailed = "Не удалось восстановить. Ваши данные не изменены",
        planJuz30 = "Джуз Амма",
        planJuz29 = "Джуз Табарак",
        planFromStart = "С начала Корана",
        planCustom = "Свой список сур",
        months = listOf(
            "января", "февраля", "марта", "апреля", "мая", "июня",
            "июля", "августа", "сентября", "октября", "ноября", "декабря",
        ),
        passages = CountNoun("отрывок", "отрывка", "отрывков", PluralRule.RUSSIAN),
        hardAyahs = CountNoun("трудный аят", "трудных аята", "трудных аятов", PluralRule.RUSSIAN),
        notes = CountNoun("заметка", "заметки", "заметок", PluralRule.RUSSIAN),
    )

    AppLanguage.TG -> TransferCopy(
        sectionTitle = "Интиқол ва нусха",
        backupTitle = "Нусхаи захиравӣ", backupDescription = "Нусха гирифтан, фиристодан ё барқарор кардан",
        saveTitle = "Нусхаро дар файл захира кардан",
        saveDescription = "Пешрафт, ёддоштҳо ва танзимот дар як файл",
        shareTitle = "Фиристодани нусха",
        shareDescription = "Масалан, ба худатон дар Telegram ё Google Drive",
        restoreTitle = "Барқарор кардан аз файл",
        restoreDescription = "Маълумоти ин телефонро иваз мекунад",
        lastBackup = "Нусхаи охирин: {date}",
        footnote = "Пешрафт дар ҳамин телефон нигоҳ дошта мешавад. Android онро дар ҳисоби Google-и шумо низ нигоҳ медорад, агар нусхабардории захиравӣ фаъол бошад.",
        shareChooserTitle = "Фиристодани нусха",
        importDialogTitle = "Аз файл барқарор мекунед?",
        previewCopyOf = "Нусха аз {date}",
        previewPlan = "нақшаи «{plan}»",
        previewEmpty = "дар он маълумоти ҳифз нест",
        previewReplace = "Маълумоти ҷории ин телефон иваз карда мешавад.",
        replace = "Иваз кардан",
        cancel = "Бекор кардан",
        exportSaved = "Нусха захира шуд",
        exportFailed = "Нусха захира нашуд",
        importDone = "Маълумот барқарор шуд",
        importNotBackup = "Ин файли нусхаи Quran Hifz нест",
        importNewer = "Ин нусха бо варианти навтари барнома сохта шудааст. Барномаро нав кунед",
        importCorrupt = "Файли нусха вайрон шудааст",
        importOutOfRange = "Дар файл маълумоти нодуруст ҳаст, барқарорсозӣ бекор шуд",
        importUnreadable = "Файл хонда нашуд",
        importFailed = "Барқарор карда нашуд. Маълумоти шумо тағйир наёфт",
        planJuz30 = "Ҷузъи Амма",
        planJuz29 = "Ҷузъи Табораку",
        planFromStart = "Аз аввали Қуръон",
        planCustom = "Рӯйхати сураҳои худам",
        months = listOf(
            "январ", "феврал", "март", "апрел", "май", "июн",
            "июл", "август", "сентябр", "октябр", "ноябр", "декабр",
        ),
        passages = CountNoun("порча", "порча", "порча", PluralRule.ALWAYS_ONE),
        hardAyahs = CountNoun("ояти душвор", "ояти душвор", "ояти душвор", PluralRule.ALWAYS_ONE),
        notes = CountNoun("ёддошт", "ёддошт", "ёддошт", PluralRule.ALWAYS_ONE),
    )

    AppLanguage.UZ -> TransferCopy(
        sectionTitle = "Ko‘chirish va nusxa",
        backupTitle = "Zaxira nusxa", backupDescription = "Nusxani saqlash, yuborish yoki tiklash",
        saveTitle = "Nusxani faylga saqlash",
        saveDescription = "Jarayon, eslatmalar va sozlamalar bitta faylda",
        shareTitle = "Nusxani yuborish",
        shareDescription = "Masalan, o‘zingizga Telegram yoki Google Drive orqali",
        restoreTitle = "Fayldan tiklash",
        restoreDescription = "Shu telefondagi ma’lumotlarni almashtiradi",
        lastBackup = "So‘nggi nusxa: {date}",
        footnote = "Jarayon shu telefonda saqlanadi. Zaxira nusxa yoqilgan bo‘lsa, Android uni Google hisobingizda ham saqlaydi.",
        shareChooserTitle = "Nusxani yuborish",
        importDialogTitle = "Fayldan tiklansinmi?",
        previewCopyOf = "{date} dagi nusxa",
        previewPlan = "«{plan}» rejasi",
        previewEmpty = "unda yodlash ma’lumotlari yo‘q",
        previewReplace = "Shu telefondagi joriy ma’lumotlar almashtiriladi.",
        replace = "Almashtirish",
        cancel = "Bekor qilish",
        exportSaved = "Nusxa saqlandi",
        exportFailed = "Nusxani saqlab bo‘lmadi",
        importDone = "Ma’lumotlar tiklandi",
        importNotBackup = "Bu Quran Hifz nusxa fayli emas",
        importNewer = "Bu nusxa ilovaning yangiroq versiyasida yaratilgan. Ilovani yangilang",
        importCorrupt = "Nusxa fayli shikastlangan",
        importOutOfRange = "Faylda noto‘g‘ri ma’lumotlar bor, tiklash bekor qilindi",
        importUnreadable = "Faylni o‘qib bo‘lmadi",
        importFailed = "Tiklab bo‘lmadi. Ma’lumotlaringiz o‘zgarmadi",
        planJuz30 = "Amma juzi",
        planJuz29 = "Tabarak juzi",
        planFromStart = "Qur’onning boshidan",
        planCustom = "O‘z suralar ro‘yxatim",
        months = listOf(
            "yanvar", "fevral", "mart", "aprel", "may", "iyun",
            "iyul", "avgust", "sentabr", "oktabr", "noyabr", "dekabr",
        ),
        passages = CountNoun("ta parcha", "ta parcha", "ta parcha", PluralRule.ALWAYS_ONE),
        hardAyahs = CountNoun("ta qiyin oyat", "ta qiyin oyat", "ta qiyin oyat", PluralRule.ALWAYS_ONE),
        notes = CountNoun("ta eslatma", "ta eslatma", "ta eslatma", PluralRule.ALWAYS_ONE),
    )

    AppLanguage.TR -> TransferCopy(
        sectionTitle = "Aktarım ve yedek",
        backupTitle = "Yedek", backupDescription = "Verilerin kopyasını kaydet, paylaş veya geri yükle",
        saveTitle = "Yedeği dosyaya kaydet",
        saveDescription = "İlerleme, notlar ve ayarlar tek dosyada",
        shareTitle = "Yedeği gönder",
        shareDescription = "Örneğin kendinize Telegram veya Google Drive ile",
        restoreTitle = "Dosyadan geri yükle",
        restoreDescription = "Bu telefondaki verilerin yerine geçer",
        lastBackup = "Son yedek: {date}",
        footnote = "İlerleme bu telefonda saklanır. Yedekleme açıksa Android bunu Google hesabınızda da saklar.",
        shareChooserTitle = "Yedeği gönder",
        importDialogTitle = "Dosyadan geri yüklensin mi?",
        previewCopyOf = "{date} tarihli yedek",
        previewPlan = "“{plan}” planı",
        previewEmpty = "içinde ezber verisi yok",
        previewReplace = "Bu telefondaki mevcut veriler değiştirilecek.",
        replace = "Değiştir",
        cancel = "Vazgeç",
        exportSaved = "Yedek kaydedildi",
        exportFailed = "Yedek kaydedilemedi",
        importDone = "Veriler geri yüklendi",
        importNotBackup = "Bu bir Quran Hifz yedek dosyası değil",
        importNewer = "Bu yedek uygulamanın daha yeni bir sürümünde oluşturulmuş. Lütfen uygulamayı güncelleyin",
        importCorrupt = "Yedek dosyası bozuk",
        importOutOfRange = "Dosyada geçersiz veriler var, geri yükleme iptal edildi",
        importUnreadable = "Dosya okunamadı",
        importFailed = "Geri yüklenemedi. Verileriniz değişmedi",
        planJuz30 = "Amme cüzü",
        planJuz29 = "Tebareke cüzü",
        planFromStart = "Kur’an’ın başından",
        planCustom = "Kendi sure listem",
        months = listOf(
            "Ocak", "Şubat", "Mart", "Nisan", "Mayıs", "Haziran",
            "Temmuz", "Ağustos", "Eylül", "Ekim", "Kasım", "Aralık",
        ),
        passages = CountNoun("bölüm", "bölüm", "bölüm", PluralRule.ALWAYS_ONE),
        hardAyahs = CountNoun("zor ayet", "zor ayet", "zor ayet", PluralRule.ALWAYS_ONE),
        notes = CountNoun("not", "not", "not", PluralRule.ALWAYS_ONE),
    )
}
