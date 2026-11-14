package tj.app.quran_todo.ui.reminder

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.datetime.LocalDate
import org.koin.mp.KoinPlatform
import platform.Foundation.NSCalendar
import platform.Foundation.NSCalendarIdentifierGregorian
import platform.Foundation.NSDateComponents
import platform.Foundation.NSTimeZone
import platform.Foundation.localTimeZone
import platform.Foundation.timeIntervalSinceNow
import platform.UserNotifications.UNAuthorizationOptionAlert
import platform.UserNotifications.UNAuthorizationOptionSound
import platform.UserNotifications.UNAuthorizationStatusNotDetermined
import platform.UserNotifications.UNCalendarNotificationTrigger
import platform.UserNotifications.UNMutableNotificationContent
import platform.UserNotifications.UNNotificationAction
import platform.UserNotifications.UNNotificationActionOptionNone
import platform.UserNotifications.UNNotificationCategory
import platform.UserNotifications.UNNotificationCategoryOptionNone
import platform.UserNotifications.UNNotificationRequest
import platform.UserNotifications.UNNotificationSound
import platform.UserNotifications.UNNotificationTrigger
import platform.UserNotifications.UNUserNotificationCenter
import tj.app.quran_todo.common.analytics.AppTelemetry
import tj.app.quran_todo.common.i18n.AppLanguage
import tj.app.quran_todo.common.i18n.LanguageStorage
import tj.app.quran_todo.common.reminder.ReminderDecision
import tj.app.quran_todo.common.reminder.reminderDecision
import tj.app.quran_todo.hifz.EpochDays
import tj.app.quran_todo.hifz.HifzRepository
import tj.app.quran_todo.ui.i18n.reminderCopy
import tj.app.quran_todo.ui.platform.KeyValueStore
import tj.app.quran_todo.ui.platform.SyncLock
import tj.app.quran_todo.ui.platform.iosPrefs
import tj.app.quran_todo.ui.platform.withLock
import tj.app.quran_todo.ui.state.UiPrefs
import tj.app.quran_todo.ui.state.reminderDayOn
import kotlin.coroutines.resume

/**
 * The daily reminder on iOS (Decision 4 of `IOS_PORT_PLAN.md`). iOS cannot look at the lesson at the moment the notification is shown,
 * so the next [HORIZON_DAYS] days are planned ahead as one-off local notifications, each with the text of the lesson of that day:
 *
 * - the time of the day is `UiPrefs.reminderMinuteOfDay`, the switch `UiPrefs.reminderEnabled`, the weekdays `UiPrefs.reminderDays`;
 * - a day whose lesson is paused (`LessonPlan.paused`) or already done gets none ([ReminderNotices.build] is null);
 * - the tone follows how long the person has been away ([reminderDecision] with the days away grown by the days ahead): the usual
 *   text, the gentle one, or silence;
 * - [sync] runs at the start, at every foreground, on the background refresh ([IosServicesRuntime.refreshReminders]) and whenever the
 *   lesson changes (the widget refresher calls it), so a lesson that was graded today takes today's notification down.
 *
 * A tap carries the deep link `quranhifz://lesson` (or `quranhifz://reader?surah=&ayah=`) in `userInfo["url"]`; the Swift notification
 * delegate opens it. The notification has the "In an hour" action (handled by the delegate).
 */
@OptIn(ExperimentalForeignApi::class)
class IosReminderPlanner(
    private val absence: IosReminderAbsence = IosReminderAbsence(),
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
) : ReminderPlanner {
    private val lock = SyncLock()
    private var job: Job? = null

    override fun sync() {
        lock.withLock {
            job?.cancel()
            job = scope.launch {
                delay(DEBOUNCE_MS)
                try {
                    plan()
                } catch (cancelled: kotlinx.coroutines.CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    AppTelemetry.logError(throwable = error, context = "reminder_plan_failed")
                }
            }
        }
    }

    /** Plans at once and returns when done (the background refresh waits for it). */
    suspend fun syncNow() {
        lock.withLock { job?.cancel() }
        plan()
    }

    /** The app came to the foreground: the days away restart from today. */
    fun recordForeground() = absence.recordForeground()

    private fun gregorian(): NSCalendar = NSCalendar(calendarIdentifier = NSCalendarIdentifierGregorian).also {
        it.timeZone = NSTimeZone.localTimeZone
    }

    private suspend fun plan() {
        val center = UNUserNotificationCenter.currentNotificationCenter()
        val koin = KoinPlatform.getKoin()
        val prefs = koin.get<UiPrefs>()
        val today = EpochDays.now()
        // Everything planned earlier goes first: the result does not depend on what was planned before.
        center.removePendingNotificationRequestsWithIdentifiers(
            (today - 1..today + HORIZON_DAYS + 1).map(::idOf) + LEGACY_ID,
        )
        if (!prefs.reminderEnabled.value) return
        registerCategory(center)
        if (!authorized(center)) return

        val hifz = koin.get<HifzRepository>()
        val language = LanguageStorage.getSavedLanguage() ?: LanguageStorage.getDeviceLanguage()
        val names = ReminderSurahNames(koin.get(), koin.get()).forLanguage(language)
        val minuteOfDay = prefs.reminderMinuteOfDay.value
        val days = prefs.reminderDays.value
        val lastRead = prefs.lastReadPosition.value
        val away = absence.daysAway(today)
        val calendar = gregorian()
        var planned = 0
        for (ahead in 0 until HORIZON_DAYS) {
            val day = today + ahead
            val lesson = hifz.lesson(day).first()
            // The days of a pause are not absence: the count restarts every day while the plan rests.
            if (lesson.paused != null && ahead == 0) absence.recordPauseDay(today)
            // A weekday the person switched off gets none; the days away still count.
            if (!reminderDayOn(days, day)) continue
            val decision = reminderDecision(away + ahead)
            if (decision == ReminderDecision.Skip) continue
            val notice = ReminderNotices.build(
                language, lesson, lastRead,
                gentle = decision == ReminderDecision.ShowGentle,
                surahName = names,
            ) ?: continue
            val components = componentsOf(calendar, day, minuteOfDay)
            val date = calendar.dateFromComponents(components) ?: continue
            if (date.timeIntervalSinceNow < MIN_LEAD_SECONDS) continue
            add(center, idOf(day), notice, UNCalendarNotificationTrigger.triggerWithDateMatchingComponents(components, repeats = false))
            planned++
        }
        AppTelemetry.logEvent("reminders_planned", mapOf("count" to planned.toString()))
    }

    private fun componentsOf(calendar: NSCalendar, day: Int, minuteOfDay: Int): NSDateComponents {
        val date = LocalDate.fromEpochDays(day)
        return NSDateComponents().also {
            it.calendar = calendar
            it.timeZone = calendar.timeZone
            it.year = date.year.toLong()
            it.month = date.monthNumber.toLong()
            it.day = date.dayOfMonth.toLong()
            it.hour = (minuteOfDay / 60).toLong()
            it.minute = (minuteOfDay % 60).toLong()
        }
    }

    private suspend fun add(center: UNUserNotificationCenter, id: String, notice: ReminderNotice, trigger: UNNotificationTrigger) {
        val content = UNMutableNotificationContent()
        content.setTitle(notice.title)
        content.setBody(notice.body)
        content.setSound(UNNotificationSound.defaultSound)
        content.setThreadIdentifier(THREAD)
        content.setCategoryIdentifier(CATEGORY)
        content.setUserInfo(
            mapOf<Any?, Any?>(
                "url" to linkOf(notice.target),
                "kind" to notice.kind.analyticsName,
            ),
        )
        val request = UNNotificationRequest.requestWithIdentifier(id, content, trigger)
        suspendCancellableCoroutine { continuation ->
            center.addNotificationRequest(request) { _ -> if (continuation.isActive) continuation.resume(Unit) }
        }
    }

    private fun registerCategory(center: UNUserNotificationCenter) {
        val language = LanguageStorage.getSavedLanguage() ?: LanguageStorage.getDeviceLanguage()
        val snooze = UNNotificationAction.actionWithIdentifier(ACTION_SNOOZE, reminderCopy(language).snoozeAction, UNNotificationActionOptionNone)
        val category = UNNotificationCategory.categoryWithIdentifier(CATEGORY, listOf(snooze), emptyList<String>(), UNNotificationCategoryOptionNone)
        center.setNotificationCategories(setOf(category))
    }

    /** True when notifications are allowed; asks once when the person was not asked yet (the reminder was just turned on). */
    private suspend fun authorized(center: UNUserNotificationCenter): Boolean {
        val status = suspendCancellableCoroutine { continuation ->
            center.getNotificationSettingsWithCompletionHandler { settings ->
                if (continuation.isActive) continuation.resume(settings?.authorizationStatus ?: UNAuthorizationStatusNotDetermined)
            }
        }
        if (status == UNAuthorizationStatusNotDetermined) {
            return suspendCancellableCoroutine { continuation ->
                center.requestAuthorizationWithOptions(UNAuthorizationOptionAlert or UNAuthorizationOptionSound) { granted, _ ->
                    if (continuation.isActive) continuation.resume(granted)
                }
            }
        }
        // Authorized (2), provisional (3), ephemeral (4).
        return status >= 2L
    }

    /** A test notification [seconds] from now with the real text of today's lesson (for the debug screen); false when none is due. */
    suspend fun scheduleTest(seconds: Int): Boolean {
        val center = UNUserNotificationCenter.currentNotificationCenter()
        if (!authorized(center)) return false
        registerCategory(center)
        val koin = KoinPlatform.getKoin()
        val language = LanguageStorage.getSavedLanguage() ?: LanguageStorage.getDeviceLanguage()
        val names = ReminderSurahNames(koin.get(), koin.get()).forLanguage(language)
        val lesson = koin.get<HifzRepository>().lesson(EpochDays.now()).first()
        val notice = ReminderNotices.build(language, lesson, koin.get<UiPrefs>().lastReadPosition.value, names)
            ?: ReminderNotice(ReminderKind.LESSON, reminderCopy(language).lessonTitle, "test", ReminderTarget.Lesson)
        val trigger = platform.UserNotifications.UNTimeIntervalNotificationTrigger.triggerWithTimeInterval(seconds.toDouble(), repeats = false)
        add(center, "$ID_PREFIX-test", notice, trigger)
        return true
    }

    companion object {
        const val HORIZON_DAYS = 14
        const val ID_PREFIX = "hifz.reminder"
        const val CATEGORY = "hifz.lesson"
        const val THREAD = "hifz.lesson"
        const val ACTION_SNOOZE = "hifz.snooze"
        private const val LEGACY_ID = "daily_hifz_reminder"
        private const val DEBOUNCE_MS = 400L
        private const val MIN_LEAD_SECONDS = 5.0

        fun idOf(epochDay: Int) = "$ID_PREFIX.$epochDay"

        fun linkOf(target: ReminderTarget): String = when (target) {
            ReminderTarget.Lesson -> "quranhifz://lesson"
            is ReminderTarget.Reader ->
                "quranhifz://reader?surah=${target.surah}" + (target.ayah?.let { "&ayah=$it" } ?: "")
        }
    }
}

/** The days since the person last had the app in the foreground (task U18), in the preferences. */
class IosReminderAbsence(private val prefs: KeyValueStore = iosPrefs("reminder_absence")) {
    fun recordForeground(today: Int = EpochDays.now()) = prefs.putInt(KEY, today)

    /** The first call of a version that never recorded a day starts the count from today. */
    fun daysAway(today: Int = EpochDays.now()): Int {
        val last = if (prefs.contains(KEY)) prefs.getInt(KEY, today) else null
        if (last == null) prefs.putInt(KEY, today)
        return tj.app.quran_todo.common.reminder.ReminderAbsence.daysAway(last, today)
    }

    fun recordPauseDay(today: Int = EpochDays.now()) = prefs.putInt(KEY, today)

    private companion object {
        const val KEY = "last_foreground_day"
    }
}
