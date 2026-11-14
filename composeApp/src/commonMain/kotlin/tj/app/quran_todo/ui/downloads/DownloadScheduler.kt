package tj.app.quran_todo.ui.downloads

import kotlinx.coroutines.flow.Flow

/** What the work of the downloads is doing, as the screen tells it ("waiting for Wi-Fi"). */
enum class WorkPhase {
    /** No work is scheduled. */
    NONE,

    /** The worker is running. */
    RUNNING,

    /** Scheduled but not running: waiting for the network (or for a retry after a failure). */
    WAITING,
}

/** Starts and stops the one unique work that goes through the queue. A fake in tests. */
interface DownloadScheduler {
    /** Starts the work (or starts it again with the new constraints): [wifiOnly] needs an unmetered network, otherwise any network. */
    fun schedule(wifiOnly: Boolean)

    /** Stops the work (the file in progress is lost, the queue is not touched). */
    fun cancel()

    val phase: Flow<WorkPhase>
}
