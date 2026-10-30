package tj.app.quran_todo.ui.downloads

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import kotlinx.coroutines.CancellationException
import org.koin.mp.KoinPlatform

/**
 * The work of "Downloads and storage": goes through the queue file and fetches what is missing ([DownloadRunner]), as a foreground
 * service of type `dataSync` with the progress notification, so it goes on with the app in the background, after the app is swiped
 * away from the recents and after the process was killed (WorkManager starts it again and it carries on from the files that are
 * there). The constraint (Wi-Fi or any network) is set when it is scheduled ([WorkManagerScheduler]).
 *
 * Kept public with the (Context, WorkerParameters) constructor: WorkManager creates it by reflection, and R8 keeps it by the rules
 * of the library.
 */
class DownloadWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun getForegroundInfo(): ForegroundInfo = DownloadNotifications.foregroundInfo(applicationContext, null)

    override suspend fun doWork(): Result {
        // The application class starts Koin before WorkManager runs any work; if it did not, there is nothing to carry on with.
        val manager = downloadsManagerOrNull() ?: return Result.retry()
        try {
            setForeground(getForegroundInfo())
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            // Android 12+ does not let a foreground service start from the background in some cases: the work goes on without it
            // for as long as the system lets it run, and is started again afterwards.
        }
        // The notification is redrawn at most twice a second (files are small, a dozen can arrive in a second), and always at the end
        // of a request.
        var lastUpdate = 0L
        val outcome = manager.run(finalAttempt = runAttemptCount >= MAX_ATTEMPTS - 1) { progress ->
            val now = System.currentTimeMillis()
            if (now - lastUpdate >= NOTIFICATION_INTERVAL_MS || progress.done >= progress.total) {
                lastUpdate = now
                DownloadNotifications.update(applicationContext, progress)
            }
        }
        // Not in a finally: a work that is replaced or cancelled is followed by another one (or by the cancel button), and that one
        // owns the notification.
        DownloadNotifications.dismiss(applicationContext)
        return when (outcome) {
            RunOutcome.NO_NETWORK -> Result.retry()
            RunOutcome.IDLE, RunOutcome.FINISHED, RunOutcome.PAUSED -> Result.success()
        }
    }

    private companion object {
        /** The run is retried this many times (backoff 30 s, 1 min, 2 min, 4 min) before the request is marked failed. */
        const val MAX_ATTEMPTS = 5
        const val NOTIFICATION_INTERVAL_MS = 500L
    }
}

/** The "Cancel" button of the notification: cancels all downloads. Not exported; only our own notification starts it. */
class DownloadActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != DownloadNotifications.ACTION_CANCEL) return
        downloadsManagerOrNull()?.cancelAll()
    }
}

/** The manager from Koin, or null when Koin is not started (it is, before any work or receiver runs, by the application class). */
private fun downloadsManagerOrNull(): DownloadsManager? =
    try {
        KoinPlatform.getKoin().get<DownloadsManager>()
    } catch (_: Exception) {
        null
    }
