package tj.app.quran_todo.ui.platform

import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.koin.mp.KoinPlatform
import platform.Foundation.NSFileManager
import platform.Foundation.NSNumber
import platform.Foundation.NSURL
import platform.Foundation.NSURLVolumeAvailableCapacityForImportantUsageKey
import platform.Foundation.NSUserDefaults
import platform.Foundation.NSHomeDirectory
import tj.app.quran_todo.common.analytics.AppTelemetry
import tj.app.quran_todo.common.i18n.AppLanguage
import tj.app.quran_todo.common.i18n.LanguageStorage
import tj.app.quran_todo.common.i18n.code
import tj.app.quran_todo.hifz.EpochDays
import tj.app.quran_todo.hifz.HifzRepository
import tj.app.quran_todo.ios.AppFolders
import tj.app.quran_todo.ios.File
import tj.app.quran_todo.ios.IosServices
import tj.app.quran_todo.ios.nowMillis
import tj.app.quran_todo.ui.home.LessonCardBuilder
import tj.app.quran_todo.ui.progress.report.ReportDoc
import tj.app.quran_todo.ui.progress.report.ReportExporter
import tj.app.quran_todo.ui.downloads.StorageSpace
import tj.app.quran_todo.ui.reminder.IosReminderPlanner
import tj.app.quran_todo.ui.reminder.ReminderSurahNames
import tj.app.quran_todo.ui.store.ReviewPrompt
import tj.app.quran_todo.ui.widget.LessonWidgetBuilder
import tj.app.quran_todo.ui.widget.WidgetRefresher

/** The key-value store on the App Group suite: the values the widget (another process) reads too. */
fun iosGroupPrefs(fileName: String): KeyValueStore {
    val suite = NSUserDefaults(suiteName = IosServices.APP_GROUP) ?: NSUserDefaults.standardUserDefaults
    return UserDefaultsStore(fileName, suite)
}

/** The App Group folder (widget snapshot); the Application Support folder of the app when the group is not available (no entitlement). */
@OptIn(ExperimentalForeignApi::class)
internal fun appGroupFolder(): File {
    val url = NSFileManager.defaultManager.containerURLForSecurityApplicationGroupIdentifier(IosServices.APP_GROUP)
    return url?.path?.let(::File) ?: AppFolders.applicationSupport
}

/** Free space of the volume, the amount the system would make available for something important (it may purge caches for it). */
object IosStorageSpace : StorageSpace {
    @OptIn(ExperimentalForeignApi::class)
    override fun freeBytes(): Long {
        val url = NSURL.fileURLWithPath(NSHomeDirectory())
        val values = url.resourceValuesForKeys(listOf(NSURLVolumeAvailableCapacityForImportantUsageKey), error = null)
        return (values?.get(NSURLVolumeAvailableCapacityForImportantUsageKey) as? NSNumber)?.longLongValue ?: Long.MAX_VALUE
    }
}

/** Nothing to apply at run time: the copy comes from the shared layer. `AppleLanguages` is written so that the system sheets follow at the next launch. */
object IosLocaleApplier : LocaleApplier {
    override fun apply(language: AppLanguage) {
        val defaults = NSUserDefaults.standardUserDefaults
        val current = (defaults.arrayForKey("AppleLanguages")?.firstOrNull() as? String)?.substringBefore("-")
        if (current != language.code) defaults.setObject(listOf(language.code), forKey = "AppleLanguages")
    }
}

/**
 * The rate-the-app card: the same gate as Android (lessons finished on [MIN_LESSON_DAYS] different days, [MIN_DAYS_SINCE_INSTALL] days
 * since the first start, [ASK_AGAIN_DAYS] days between two asks), then `AppStore.requestReview(in:)` through the Swift hook. The system
 * has its own quota and may show nothing; the app never waits for it.
 */
class IosReviewPrompt(
    private val prefs: KeyValueStore = iosPrefs("store_prompts"),
    private val clock: () -> Long = ::nowMillis,
) : ReviewPrompt {
    override suspend fun onLessonFinished() {
        val now = clock()
        val today = now / DAY_MS
        var lessonDays = prefs.getInt(KEY_LESSON_DAYS, 0)
        if (prefs.getLong(KEY_LAST_LESSON_DAY, -1L) != today) {
            lessonDays += 1
            prefs.putInt(KEY_LESSON_DAYS, lessonDays)
            prefs.putLong(KEY_LAST_LESSON_DAY, today)
        }
        if (lessonDays < MIN_LESSON_DAYS) return
        if ((now - installTime(now)) / DAY_MS < MIN_DAYS_SINCE_INSTALL) return
        val lastAsked = prefs.getLong(KEY_LAST_ASKED_AT, 0L)
        if (lastAsked > 0L && (now - lastAsked) / DAY_MS < ASK_AGAIN_DAYS) return
        val hooks = IosServices.hooks ?: return
        withContext(Dispatchers.Main) { hooks.requestReview() }
        prefs.putLong(KEY_LAST_ASKED_AT, now)
        AppTelemetry.logEvent("review_prompt_requested", mapOf("lesson_days" to lessonDays.toString()))
    }

    /** The first start of the app is the install for the rule (recorded by [recordFirstLaunch], here as a fallback). */
    private fun installTime(now: Long): Long {
        if (!prefs.contains(KEY_INSTALLED_AT)) prefs.putLong(KEY_INSTALLED_AT, now)
        return prefs.getLong(KEY_INSTALLED_AT, now)
    }

    fun recordFirstLaunch() {
        installTime(clock())
    }

    private companion object {
        const val KEY_LESSON_DAYS = "review_lesson_days"
        const val KEY_LAST_LESSON_DAY = "review_last_lesson_day"
        const val KEY_LAST_ASKED_AT = "review_last_asked_at"
        const val KEY_INSTALLED_AT = "review_installed_at"
        const val MIN_LESSON_DAYS = 3
        const val MIN_DAYS_SINCE_INSTALL = 3L
        const val ASK_AGAIN_DAYS = 90L
        const val DAY_MS = 86_400_000L
    }
}

/**
 * The widget "Lesson of the day": writes what it shows (the [tj.app.quran_todo.ui.widget.LessonWidgetModel] of the same builder as the
 * Android widget, in the language of the app) as JSON into the App Group (`lesson_widget.json`) and asks WidgetKit to reload. The lesson
 * reminders are planned again at the same moments (a lesson was graded, the plan or the language changed).
 */
class IosWidgetRefresher(
    private val reminders: IosReminderPlanner,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
) : WidgetRefresher {
    private val lock = SyncLock()
    private var job: Job? = null

    override fun refresh() {
        lock.withLock {
            job?.cancel()
            job = scope.launch {
                delay(DEBOUNCE_MS)
                try {
                    writeSnapshot()
                    IosServices.hooks?.reloadWidgets()
                } catch (cancelled: kotlinx.coroutines.CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    AppTelemetry.logError(throwable = error, context = "widget_snapshot_failed")
                }
            }
        }
        reminders.sync()
    }

    /** Writes the snapshot at once (and returns the file); the debug screen and the start of the app call it. */
    suspend fun writeSnapshot(): File {
        val koin = KoinPlatform.getKoin()
        val hifz = koin.get<HifzRepository>()
        val today = EpochDays.now()
        val language = LanguageStorage.getSavedLanguage() ?: LanguageStorage.getDeviceLanguage()
        val lesson = hifz.lesson(today).first()
        val streak = hifz.streak(today).first()
        val names = ReminderSurahNames(koin.get(), koin.get()).forLanguage(language)
        val model = LessonWidgetBuilder.build(LessonCardBuilder.build(lesson, nameOf = names), streak, language)
        val json = JsonObject(
            mapOf(
                "version" to JsonPrimitive(1),
                "language" to JsonPrimitive(language.code),
                "epochDay" to JsonPrimitive(today),
                "updatedAtMs" to JsonPrimitive(nowMillis()),
                "state" to JsonPrimitive(model.state.name),
                "title" to JsonPrimitive(model.title),
                "detail" to JsonPrimitive(model.detail),
                "streak" to (model.streak?.let(::JsonPrimitive) ?: JsonNull),
                "action" to (model.action?.let(::JsonPrimitive) ?: JsonNull),
                "opensLesson" to JsonPrimitive(model.opensLesson),
            ),
        )
        val folder = appGroupFolder()
        folder.mkdirs()
        val target = File(folder, SNAPSHOT_NAME)
        val temporary = File(folder, "$SNAPSHOT_NAME.tmp")
        temporary.writeText(json.toString())
        target.delete()
        temporary.renameTo(target)
        return target
    }

    companion object {
        const val SNAPSHOT_NAME = "lesson_widget.json"
        private const val DEBOUNCE_MS = 500L
    }
}

/** The PDF of the report, drawn by Swift (`UIGraphicsPDFRenderer`) from the same [ReportDoc], and the share sheet. */
class IosReportExporter : ReportExporter {
    override suspend fun createPdf(doc: ReportDoc, fileName: String): String? = withContext(Dispatchers.Default) {
        val hooks = IosServices.hooks ?: return@withContext null
        val folder = File(AppFolders.temporary, "report")
        folder.deleteRecursively()
        folder.mkdirs()
        val file = File(folder, fileName)
        if (hooks.renderReportPdf(doc, file.path)) "file://${file.path}" else null
    }

    override fun share(location: String, mimeType: String, title: String) {
        IosServices.hooks?.presentShare(location, title)
    }
}

/** Entry points for the Swift side that are not an interface of the shared layer: start, foreground, the background refresh. */
object IosServicesRuntime {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private fun planner() = KoinPlatform.getKoin().get<tj.app.quran_todo.ui.reminder.ReminderPlanner>() as IosReminderPlanner

    /** The app started (after Koin): the background session hears the finished transfers, unfinished downloads go on, the reminders are planned. */
    fun onLaunch() {
        val koin = KoinPlatform.getKoin()
        (koin.get<tj.app.quran_todo.ui.store.ReviewPrompt>() as? IosReviewPrompt)?.recordFirstLaunch()
        tj.app.quran_todo.ui.downloads.BackgroundFetch.attach()
        onForeground()
    }

    /** The app came to the foreground: the days away restart, the reminders are planned again, the downloads that waited go on. */
    fun onForeground() {
        val koin = KoinPlatform.getKoin()
        planner().recordForeground()
        planner().sync()
        (koin.get<tj.app.quran_todo.ui.downloads.DownloadsService>() as? tj.app.quran_todo.ui.downloads.IosDownloadsManager)?.resumeIfNeeded()
        koin.get<WidgetRefresher>().refresh()
    }

    /** The `BGAppRefreshTask`: plans the next days again; [done] gets true when it finished. */
    fun refreshReminders(done: (Boolean) -> Unit) {
        scope.launch {
            val ok = try {
                planner().syncNow()
                true
            } catch (_: Exception) {
                false
            }
            done(ok)
        }
    }

    /** `handleEventsForBackgroundURLSession`: [completion] is called when the delegate has heard the events. */
    fun handleBackgroundSessionEvents(completion: () -> Unit) {
        tj.app.quran_todo.ui.downloads.BackgroundFetch.eventsDone = completion
        tj.app.quran_todo.ui.downloads.BackgroundFetch.attach()
    }

    /** A test notification [seconds] from now (for the debug screen). */
    fun scheduleTestReminder(seconds: Int, done: (Boolean) -> Unit) {
        scope.launch { done(planner().scheduleTest(seconds)) }
    }

    /** Writes the widget snapshot now; the path of the file (for the debug screen). */
    fun writeWidgetSnapshot(done: (String?) -> Unit) {
        scope.launch {
            val path = try {
                (KoinPlatform.getKoin().get<WidgetRefresher>() as IosWidgetRefresher).writeSnapshot().path
            } catch (_: Exception) {
                null
            }
            done(path)
        }
    }
}
