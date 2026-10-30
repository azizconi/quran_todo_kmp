package tj.app.quran_todo.ui.downloads

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.ForegroundInfo
import tj.app.quran_todo.MainActivity
import tj.app.quran_todo.R
import tj.app.quran_todo.common.i18n.AppLanguage
import tj.app.quran_todo.common.i18n.LanguageStorage
import tj.app.quran_todo.common.platform.AndroidContextHolder
import tj.app.quran_todo.ui.i18n.DownloadsCopy
import tj.app.quran_todo.ui.i18n.downloadsCopy

/**
 * The progress notification of the download: "Download: Juz Amma · 120 of 564" with a bar and a "Cancel" button, on its own channel
 * "Downloads" (low importance: no sound, no pop-up) named in the language of the app. The notification is the one the work shows
 * while it runs as a foreground service ([DownloadWorker.getForegroundInfo]); the work updates it in place with [update].
 */
internal object DownloadNotifications {
    const val CHANNEL_ID = "downloads"
    const val NOTIFICATION_ID = 9120

    /** The action of the "Cancel" button; [DownloadActionReceiver] cancels all downloads. */
    const val ACTION_CANCEL = "tj.app.quran_todo.action.CANCEL_DOWNLOADS"

    private const val REQUEST_OPEN = 9121
    private const val REQUEST_CANCEL = 9122

    /** The emerald of the app, tints the small icon in the shade (the same as the reminder's). */
    private const val ACCENT = 0xFF0E6B52.toInt()

    /** The language of the app: the saved choice, otherwise the language of the phone. */
    fun currentLanguage(): AppLanguage = LanguageStorage.getSavedLanguage() ?: LanguageStorage.getDeviceLanguage()

    /** Creates the channel (or gives the existing one the name of [copy]'s language). Nothing before Android 8. */
    fun ensureChannel(context: Context, copy: DownloadsCopy) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, copy.channelName, NotificationManager.IMPORTANCE_LOW).apply {
                description = copy.channelDescription
                setShowBadge(false)
            },
        )
    }

    /** The notification for [progress]; a bar without a number while nothing is known yet ([progress] null). */
    fun build(context: Context, progress: DownloadProgress?): Notification {
        val copy = downloadsCopy(currentLanguage())
        ensureChannel(context, copy)
        val flags = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        val open = PendingIntent.getActivity(
            context,
            REQUEST_OPEN,
            context.packageManager.getLaunchIntentForPackage(context.packageName) ?: Intent(context, MainActivity::class.java),
            flags,
        )
        val cancel = PendingIntent.getBroadcast(
            context,
            REQUEST_CANCEL,
            Intent(context, DownloadActionReceiver::class.java).setAction(ACTION_CANCEL).setPackage(context.packageName),
            flags,
        )
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_quran)
            .setColor(ACCENT)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setContentIntent(open)
            .addAction(0, copy.notificationCancel, cancel)
        if (progress == null) {
            builder.setContentTitle(copy.title).setProgress(0, 0, true)
        } else {
            builder
                .setContentTitle(copy.notificationTitle(progress.request.title, progress.done, progress.total))
                .setProgress(progress.total, progress.done.coerceAtMost(progress.total), false)
            if (progress.waiting > 0) builder.setContentText(copy.notificationQueuedLabel(progress.waiting))
        }
        return builder.build()
    }

    /** What the work passes to `setForeground`: the data-sync foreground service type is declared in the manifest. */
    fun foregroundInfo(context: Context, progress: DownloadProgress?): ForegroundInfo {
        val notification = build(context, progress)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(NOTIFICATION_ID, notification)
        }
    }

    /**
     * Takes the notification down. While the work runs as a foreground service Android keeps its notification and WorkManager removes
     * it when the work stops; this is for the case where the service could not start (the notification was only posted by [update]).
     */
    fun dismiss(context: Context = AndroidContextHolder.context) {
        NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
    }

    /** Replaces the notification with the state of [progress]; does nothing when notifications are not allowed. */
    @SuppressLint("MissingPermission") // areNotificationsEnabled() checked the permission.
    fun update(context: Context, progress: DownloadProgress) {
        val manager = NotificationManagerCompat.from(context)
        if (!manager.areNotificationsEnabled()) return
        try {
            manager.notify(NOTIFICATION_ID, build(context, progress))
        } catch (_: SecurityException) {
            // The permission was taken away just now: the work goes on without a notification.
        }
    }
}
