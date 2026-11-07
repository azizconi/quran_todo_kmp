package tj.app.quran_todo.common.reminder

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import kotlinx.coroutines.flow.first
import org.koin.mp.KoinPlatform
import tj.app.quran_todo.R
import tj.app.quran_todo.common.analytics.AppTelemetry
import tj.app.quran_todo.hifz.EpochDays
import tj.app.quran_todo.hifz.HifzRepository
import tj.app.quran_todo.ui.i18n.reminderCopy
import tj.app.quran_todo.ui.reminder.ReminderKind
import tj.app.quran_todo.ui.reminder.ReminderLink
import tj.app.quran_todo.ui.reminder.ReminderNotice
import tj.app.quran_todo.ui.reminder.ReminderNotices
import tj.app.quran_todo.ui.reminder.ReminderSurahNames
import tj.app.quran_todo.ui.reminder.ReminderTarget
import tj.app.quran_todo.ui.state.UiPrefs

/**
 * Looks at today's lesson and posts the reminder when there is something to say (task B4): the lesson with what is left to do, or
 * "Continue reading" when there is no lesson. A lesson that is already done posts nothing (and takes down an earlier reminder
 * of the day). What it says and when it stays silent is decided by [ReminderNotices].
 *
 * Runs in a receiver, possibly in a process that was just started for it: the singletons come from Koin, which the application
 * class starts before any receiver runs.
 */
internal object ReminderNotifier {
    const val NOTIFICATION_ID = 9108

    private const val REQUEST_OPEN_LESSON = 9111
    private const val REQUEST_OPEN_READER = 9112
    private const val REQUEST_SNOOZE = 9110

    /** The emerald of the app, tints the small icon in the shade. */
    private const val ACCENT = 0xFF0E6B52.toInt()

    /** The kind of the reminder that was posted, or null when none was (nothing to say, or notifications are not allowed). */
    suspend fun showIfDue(context: Context): ReminderKind? {
        if (!canPost(context)) return null
        val koin = KoinPlatform.getKoin()
        val language = ReminderChannel.currentLanguage()
        val lesson = koin.get<HifzRepository>().lesson(EpochDays.now()).first()
        val names = ReminderSurahNames(koin.get(), koin.get()).forLanguage(language)
        val notice = ReminderNotices.build(language, lesson, koin.get<UiPrefs>().lastReadPosition.value, names)
        if (notice == null) {
            // The lesson was done after an earlier reminder (or by a snooze that comes later): nothing to remind of.
            NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
            return null
        }
        ReminderChannel.ensure(context, language)
        post(context, notice)
        AppTelemetry.logEvent("reminder_shown", mapOf("kind" to notice.kind.analyticsName))
        return notice.kind
    }

    /** Notifications are allowed for the app and the channel is not switched off (before Android 13 there is no permission to ask). */
    fun canPost(context: Context): Boolean =
        NotificationManagerCompat.from(context).areNotificationsEnabled() && !ReminderChannel.isBlocked(context)

    @SuppressLint("MissingPermission") // canPost() checked the permission.
    private fun post(context: Context, notice: ReminderNotice) {
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        val open = PendingIntent.getActivity(
            context,
            if (notice.target is ReminderTarget.Lesson) REQUEST_OPEN_LESSON else REQUEST_OPEN_READER,
            ReminderLink.intentFor(context, notice.target),
            flags,
        )
        val snooze = PendingIntent.getBroadcast(
            context,
            REQUEST_SNOOZE,
            Intent(context, ReminderReceiver::class.java).setAction(ReminderReceiver.ACTION_SNOOZE),
            flags,
        )
        val notification = NotificationCompat.Builder(context, ReminderChannel.ID)
            .setSmallIcon(R.drawable.ic_stat_quran)
            .setColor(ACCENT)
            .setContentTitle(notice.title)
            .setContentText(notice.body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(notice.body))
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(open)
            .addAction(0, reminderCopy(ReminderChannel.currentLanguage()).snoozeAction, snooze)
            .build()
        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
    }
}
