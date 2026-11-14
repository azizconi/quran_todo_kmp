package tj.app.quran_todo.ui.platform

import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOf
import platform.Foundation.NSBundle
import platform.Foundation.NSCalendar
import platform.Foundation.NSCalendarIdentifierIslamicUmmAlQura
import platform.Foundation.NSCalendarUnitDay
import platform.Foundation.NSCalendarUnitMonth
import platform.Foundation.NSCalendarUnitYear
import platform.Foundation.NSDate
import platform.Foundation.NSFileManager
import platform.Foundation.NSFileSystemFreeSize
import platform.Foundation.NSHomeDirectory
import platform.Foundation.NSNumber
import platform.Foundation.NSString
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSTimeZone
import platform.Foundation.NSURL
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.NSUserDefaults
import platform.Foundation.create
import platform.Foundation.dateWithTimeIntervalSince1970
import platform.Foundation.stringWithContentsOfFile
import platform.Foundation.timeZoneForSecondsFromGMT
import platform.Foundation.writeToFile
import tj.app.quran_todo.common.audio.timing.WordTimings
import tj.app.quran_todo.common.mushaf.MushafPage
import tj.app.quran_todo.common.tafsir.TafsirChapter
import tj.app.quran_todo.common.tafsir.TafsirSource
import tj.app.quran_todo.data.translation.TranslationEdition
import tj.app.quran_todo.khatm.HijriConverter
import tj.app.quran_todo.khatm.HijriDate
import tj.app.quran_todo.ui.downloads.DownloadQueue
import tj.app.quran_todo.ui.downloads.DownloadRequest
import tj.app.quran_todo.ui.downloads.DownloadedFiles
import tj.app.quran_todo.ui.downloads.DownloadsPrefs
import tj.app.quran_todo.ui.downloads.DownloadsService
import tj.app.quran_todo.ui.downloads.ListeningCache
import tj.app.quran_todo.ui.downloads.PinnedPages
import tj.app.quran_todo.ui.downloads.QueueState
import tj.app.quran_todo.ui.downloads.QueueStore
import tj.app.quran_todo.ui.downloads.StorageSpace
import tj.app.quran_todo.ui.downloads.WorkPhase
import tj.app.quran_todo.ui.mushaf.data.LoadedPage
import tj.app.quran_todo.ui.mushaf.data.MushafPageProvider
import tj.app.quran_todo.ui.reminder.ReminderPlanner
import tj.app.quran_todo.ui.settings.ReadOutcome
import tj.app.quran_todo.ui.settings.TransferFiles
import tj.app.quran_todo.ui.tafsir.TafsirChapters
import tj.app.quran_todo.ui.words.WordTimingsLoader

// The iOS side of the platform interfaces of the UI layer. Everything here is a minimal working or no-op implementation so that the
// framework links and the shared view models run; the real services are written in IOS-S2 (each has a TODO(IOS-S2)).

/** Hijri dates by the Umm al-Qura calendar of Foundation: the same calendar as `android.icu` on Android. */
object IosHijriConverter : HijriConverter {
    override fun toHijri(epochDay: Int): HijriDate {
        val calendar = NSCalendar(calendarIdentifier = NSCalendarIdentifierIslamicUmmAlQura)
        calendar.timeZone = NSTimeZone.timeZoneForSecondsFromGMT(0)
        // The epoch day is a calendar day: read it at noon of that civil date in GMT.
        val date = NSDate.dateWithTimeIntervalSince1970(epochDay * 86_400.0 + 43_200.0)
        val parts = calendar.components(NSCalendarUnitYear or NSCalendarUnitMonth or NSCalendarUnitDay, fromDate = date)
        return HijriDate(parts.year.toInt(), parts.month.toInt(), parts.day.toInt())
    }
}

/** TODO(IOS-S2): the word timings files (`WordTimingsStore` on Android). Without them the reader has no word highlight. */
object IosWordTimingsLoader : WordTimingsLoader {
    override suspend fun load(recitationId: Int, surah: Int): WordTimings? = null
}

/**
 * Backup files through plain paths. A location is the `file://` url string of the document; the security scope of a url from the
 * document picker is opened by the Swift side around the call. TODO(IOS-S2): share sheet and picker glue.
 */
@OptIn(BetaInteropApi::class, ExperimentalForeignApi::class)
object IosTransferFiles : TransferFiles {
    private fun pathOf(location: String): String? = NSURL.URLWithString(location)?.path ?: location.removePrefix("file://")

    override fun appVersion(): String {
        val info = NSBundle.mainBundle.infoDictionary
        val version = info?.get("CFBundleShortVersionString") as? String ?: "?"
        val build = info?.get("CFBundleVersion") as? String ?: "?"
        return "$version ($build)"
    }

    override suspend fun readText(location: String): ReadOutcome {
        val path = pathOf(location) ?: return ReadOutcome.Unreadable
        val text = NSString.stringWithContentsOfFile(path, NSUTF8StringEncoding, null) ?: return ReadOutcome.Unreadable
        val value = text as String
        return if (value.length > tj.app.quran_todo.hifz.backup.BackupFormat.MAX_FILE_BYTES) ReadOutcome.TooLarge else ReadOutcome.Text(value)
    }

    override suspend fun writeText(location: String, text: String) {
        val path = pathOf(location) ?: error("no path")
        val done = NSString.create(string = text).writeToFile(path, atomically = true, encoding = NSUTF8StringEncoding, error = null)
        if (!done) error("could not write the file")
    }

    override suspend fun createShareFile(fileName: String, text: String): String {
        clearShareFiles()
        val folder = NSTemporaryDirectory() + "transfer"
        NSFileManager.defaultManager.createDirectoryAtPath(folder, withIntermediateDirectories = true, attributes = null, error = null)
        val path = "$folder/$fileName"
        writeText("file://$path", text)
        return "file://$path"
    }

    override suspend fun clearShareFiles() {
        NSFileManager.defaultManager.removeItemAtPath(NSTemporaryDirectory() + "transfer", error = null)
    }
}

/** The key-value store of a preferences file of Android, on NSUserDefaults. */
fun iosPrefs(fileName: String): KeyValueStore = UserDefaultsStore(fileName, NSUserDefaults.standardUserDefaults)
