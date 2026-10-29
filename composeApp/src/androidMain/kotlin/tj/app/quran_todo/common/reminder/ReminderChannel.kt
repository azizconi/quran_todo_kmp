package tj.app.quran_todo.common.reminder

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import tj.app.quran_todo.common.i18n.AppLanguage
import tj.app.quran_todo.common.i18n.LanguageStorage
import tj.app.quran_todo.ui.i18n.reminderCopy

/**
 * The notification channel "Today's lesson" (default importance: sound, no heads-up). Its name and description are in the language
 * of the app and follow it: [ensure] creates the channel or renames the existing one, [renameIfExists] only renames.
 */
internal object ReminderChannel {
    const val ID = "lesson_reminder"

    // The channel of the previous implementation, English only. Removed so that it does not show next to the new one.
    private const val LEGACY_ID = "hifz_reminders"

    /** The language of the app: the saved choice, otherwise the language of the phone. */
    fun currentLanguage(): AppLanguage = LanguageStorage.getSavedLanguage() ?: LanguageStorage.getDeviceLanguage()

    /** Creates the channel (or renames it to [language]) and removes the old one. Nothing before Android 8. */
    fun ensure(context: Context, language: AppLanguage = currentLanguage()) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
        manager.deleteNotificationChannel(LEGACY_ID)
        val copy = reminderCopy(language)
        // Creating a channel that exists only updates its name and description; the importance the person chose stays.
        manager.createNotificationChannel(
            NotificationChannel(ID, copy.channelName, NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = copy.channelDescription
            },
        )
    }

    /** Gives the existing channel the name of [language]; does nothing while the person never turned the reminder on. */
    fun renameIfExists(context: Context, language: AppLanguage = currentLanguage()) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
        if (manager.getNotificationChannel(ID) == null) return
        ensure(context, language)
    }

    /** The person turned this channel off in the system settings. */
    fun isBlocked(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return false
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return false
        return manager.getNotificationChannel(ID)?.importance == NotificationManager.IMPORTANCE_NONE
    }
}
