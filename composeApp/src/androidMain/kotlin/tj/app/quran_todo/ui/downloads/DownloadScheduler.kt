package tj.app.quran_todo.ui.downloads

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.util.concurrent.TimeUnit

/** What the work of the downloads is doing, as the screen tells it ("waiting for Wi-Fi"). */
internal enum class WorkPhase {
    /** No work is scheduled. */
    NONE,

    /** The worker is running. */
    RUNNING,

    /** Scheduled but not running: waiting for the network (or for a retry after a failure). */
    WAITING,
}

/** Starts and stops the one unique work that goes through the queue. A fake in tests. */
internal interface DownloadScheduler {
    /** Starts the work (or starts it again with the new constraints): [wifiOnly] needs an unmetered network, otherwise any network. */
    fun schedule(wifiOnly: Boolean)

    /** Stops the work (the file in progress is lost, the queue is not touched). */
    fun cancel()

    val phase: Flow<WorkPhase>
}

/**
 * One unique work `quran-downloads`, replaced each time it is scheduled: the worker reads the queue file when it starts, so a
 * replacement just carries on where the old one was stopped (finished files are kept). The constraint is `UNMETERED` for "Wi-Fi only",
 * `CONNECTED` otherwise; a network that goes away stops the worker and WorkManager waits for the constraint again. A failed run is
 * retried with an exponential backoff.
 */
internal class WorkManagerScheduler(private val context: Context) : DownloadScheduler {
    private val workManager: WorkManager get() = WorkManager.getInstance(context)

    override fun schedule(wifiOnly: Boolean) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(if (wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED)
            .build()
        val request = OneTimeWorkRequestBuilder<DownloadWorker>()
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, BACKOFF_SECONDS, TimeUnit.SECONDS)
            .build()
        workManager.enqueueUniqueWork(UNIQUE_NAME, ExistingWorkPolicy.REPLACE, request)
    }

    override fun cancel() {
        workManager.cancelUniqueWork(UNIQUE_NAME)
    }

    override val phase: Flow<WorkPhase>
        get() = workManager.getWorkInfosForUniqueWorkFlow(UNIQUE_NAME)
            .map { infos ->
                when {
                    infos.any { it.state == WorkInfo.State.RUNNING } -> WorkPhase.RUNNING
                    infos.any { it.state == WorkInfo.State.ENQUEUED || it.state == WorkInfo.State.BLOCKED } -> WorkPhase.WAITING
                    else -> WorkPhase.NONE
                }
            }
            .distinctUntilChanged()

    private companion object {
        const val UNIQUE_NAME = "quran-downloads"
        const val BACKOFF_SECONDS = 30L
    }
}
